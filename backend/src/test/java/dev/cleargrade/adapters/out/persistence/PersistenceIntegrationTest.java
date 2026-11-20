package dev.cleargrade.adapters.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.cleargrade.adapters.out.persistence.entity.ImportBatchEntity;
import dev.cleargrade.adapters.out.persistence.entity.ImportRowErrorEntity;
import dev.cleargrade.adapters.out.persistence.entity.StudentEntity;
import dev.cleargrade.adapters.out.persistence.mapper.ImportBatchMapper;
import dev.cleargrade.adapters.out.persistence.mapper.ImportRowErrorMapper;
import dev.cleargrade.adapters.out.persistence.mapper.StudentMapper;
import dev.cleargrade.adapters.out.persistence.repository.ImportBatchRepository;
import dev.cleargrade.adapters.out.persistence.repository.ImportRowErrorRepository;
import dev.cleargrade.adapters.out.persistence.repository.StudentRepository;
import dev.cleargrade.domain.DomainValidationException;
import dev.cleargrade.domain.batch.ImportBatch;
import dev.cleargrade.domain.batch.ImportCounts;
import dev.cleargrade.domain.batch.ImportStatus;
import dev.cleargrade.domain.batch.PhysicalLineNumber;
import dev.cleargrade.domain.batch.RowError;
import dev.cleargrade.domain.batch.RowErrorCode;
import dev.cleargrade.domain.student.DateOfBirth;
import dev.cleargrade.domain.student.EmailAddress;
import dev.cleargrade.domain.student.FullName;
import dev.cleargrade.domain.student.GradeLevel;
import dev.cleargrade.domain.student.Section;
import dev.cleargrade.domain.student.Student;
import dev.cleargrade.domain.student.StudentId;
import dev.cleargrade.domain.student.Subject;
import dev.cleargrade.domain.student.SubjectMarks;

/** Real PostgreSQL verification of Flyway, Hibernate validation and the persistence adapter foundation. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class PersistenceIntegrationTest {

	private static final String DIGEST = "a".repeat(64);
	private static final String PRECISE_MARK = "99.123456789012345678901234567890123456789012345";
	private static final Instant STARTED = Instant.parse("2026-11-19T10:00:00.123456Z");
	private static final Instant FINISHED = Instant.parse("2026-11-19T10:00:05.654321Z");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
			.withDatabaseName("cleargrade_persistence_test")
			.withUsername("cleargrade_test")
			.withPassword("cleargrade_test");

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ImportBatchRepository batchRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private ImportRowErrorRepository rowErrorRepository;

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@BeforeEach
	void cleanData() {
		this.jdbcTemplate.execute("TRUNCATE TABLE import_row_error, student, import_batch RESTART IDENTITY");
	}

	@Test
	void flywayMigrationAndHibernateValidationSucceed() {
		Integer migrations = this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE", Integer.class);
		assertEquals(1, migrations);
		Integer tables = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
				+ "WHERE table_schema = 'public' AND table_name IN ('import_batch', 'student', 'import_row_error')",
				Integer.class);
		assertEquals(3, tables);
	}

	@Test
	void springDataRepositoryBeansAreCreated() {
		assertFalse(this.applicationContext.getBeansOfType(ImportBatchRepository.class).isEmpty());
		assertFalse(this.applicationContext.getBeansOfType(StudentRepository.class).isEmpty());
		assertFalse(this.applicationContext.getBeansOfType(ImportRowErrorRepository.class).isEmpty());
	}

	@Test
	void batchRoundTripPreservesStatusAndNullableCounts() {
		ImportBatchEntity saved = saveBatch(DIGEST, ImportStatus.RECEIVED, null);
		assertNotNull(saved.getId());

		List<ImportBatchEntity> found = this.batchRepository.findAllBySha256(DIGEST);
		assertEquals(1, found.size());
		ImportBatch domain = ImportBatchMapper.toDomain(found.get(0));
		assertEquals(DIGEST, domain.sha256());
		assertEquals(ImportStatus.RECEIVED, domain.status());
		assertNull(domain.counts());

		Integer countColumns = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM import_batch "
				+ "WHERE rows_read IS NULL AND imported_count IS NULL AND new_count IS NULL "
				+ "AND changed_count IS NULL AND unchanged_count IS NULL "
				+ "AND rejected_count IS NULL AND error_count IS NULL", Integer.class);
		assertEquals(1, countColumns);
	}

	@Test
	void completedBatchRoundTripPreservesCountsAndExactEnumRepresentation() {
		ImportCounts counts = new ImportCounts(5, 2, 1, 1, 1, 2);
		saveBatch(DIGEST, ImportStatus.COMPLETED, counts);

		String status = this.jdbcTemplate.queryForObject("SELECT status FROM import_batch WHERE sha256 = ?",
				String.class, DIGEST);
		assertEquals("COMPLETED", status);
		ImportBatch domain = ImportBatchMapper.toDomain(this.batchRepository.findAllBySha256(DIGEST).get(0));
		assertEquals(counts, domain.counts());
	}

	@Test
	void studentRoundTripPreservesPreciseMarksUnicodeAndForeignKeys() {
		ImportBatchEntity batch = saveBatch(DIGEST, ImportStatus.COMPLETED, new ImportCounts(2, 2, 0, 0, 0, 0));
		BigDecimal precise = new BigDecimal(PRECISE_MARK);
		Student student = new Student(
				StudentId.of("stu-001"),
				FullName.of("Zoë O'Connor"),
				EmailAddress.of("Test+Tag@Example.COM"),
				DateOfBirth.of(LocalDate.of(2001, 11, 19), CLOCK),
				GradeLevel.of(9),
				Section.of('a'),
				SubjectMarks.of(Map.of(
						Subject.MATHS, precise,
						Subject.SCIENCE, precise,
						Subject.ENGLISH, precise,
						Subject.SOCIAL_STUDIES, precise,
						Subject.SECOND_LANGUAGE, precise)));
		StudentEntity entity = StudentMapper.toEntity(student, batch.getId(), batch.getId(),
				STARTED, FINISHED);
		this.studentRepository.saveAndFlush(entity);

		StudentEntity loaded = this.studentRepository.findByStudentId("STU-001").orElseThrow();
		assertEquals(batch.getId(), loaded.getFirstSeenBatchId());
		assertEquals(batch.getId(), loaded.getLastUpdatedBatchId());
		assertEquals(STARTED, loaded.getCreatedAt());
		assertEquals(FINISHED, loaded.getUpdatedAt());
		assertEquals(LocalDate.of(2001, 11, 19), loaded.getDateOfBirth());

		Student domain = StudentMapper.toDomain(loaded);
		assertEquals(student, domain);
		assertEquals(precise, domain.marks().mark(Subject.MATHS));
		assertEquals(precise.scale(), domain.marks().mark(Subject.MATHS).scale());
	}

	@Test
	void persistedStudentLoadsAfterEnrollmentAgeHasPassed() {
		Clock laterClock = Clock.fixed(Instant.parse("2027-11-20T00:00:00Z"), ZoneOffset.UTC);
		LocalDate dateOfBirth = LocalDate.of(2001, 11, 19);
		BigDecimal precise = new BigDecimal(PRECISE_MARK);
		ImportBatchEntity batch = saveBatch(DIGEST, ImportStatus.RECEIVED, null);
		Student student = new Student(
				StudentId.of("stu-003"),
				FullName.of("Test Student"),
				EmailAddress.of("test@example.com"),
				DateOfBirth.of(dateOfBirth, CLOCK),
				GradeLevel.of(9),
				Section.of('A'),
				marks(precise));
		this.studentRepository.saveAndFlush(
				StudentMapper.toEntity(student, batch.getId(), batch.getId(), STARTED, FINISHED));

		StudentEntity loaded = this.studentRepository.findByStudentId("STU-003").orElseThrow();
		Student reconstructed = StudentMapper.toDomain(loaded);

		assertEquals(dateOfBirth, reconstructed.dateOfBirth().value());
		assertEquals(precise, reconstructed.marks().mark(Subject.MATHS));
		assertThrows(DomainValidationException.class, () -> DateOfBirth.of(dateOfBirth, laterClock));
	}

	@Test
	void rowErrorsRoundTripAndRepositoryQueries() {
		ImportBatchEntity batch = saveBatch(DIGEST, ImportStatus.RECEIVED, null);
		this.rowErrorRepository.saveAndFlush(ImportRowErrorMapper.toEntity(
				RowError.forField(PhysicalLineNumber.ofDataRow(2), RowErrorCode.OUT_OF_RANGE,
						"maths", "raw-ß", "Value out of range"), batch.getId()));
		this.rowErrorRepository.saveAndFlush(ImportRowErrorMapper.toEntity(
				RowError.forField(PhysicalLineNumber.ofDataRow(2), RowErrorCode.INVALID_FORMAT,
						"science", "raw-ß", "Not numeric"), batch.getId()));
		this.rowErrorRepository.saveAndFlush(ImportRowErrorMapper.toEntity(
				RowError.wholeRow(PhysicalLineNumber.ofDataRow(3), RowErrorCode.WRONG_FIELD_COUNT,
						"東京 — unexpected columns"), batch.getId()));

		List<ImportRowErrorEntity> all = this.rowErrorRepository
				.findAllByBatchIdOrderByLineNumberAsc(batch.getId());
		assertEquals(3, all.size());
		assertEquals(2, all.get(0).getLineNumber());
		assertEquals(3, all.get(2).getLineNumber());
		assertNull(all.get(2).getRawStudentId());
		assertEquals("東京 — unexpected columns", ImportRowErrorMapper.toDomain(all.get(2)).message());

		List<ImportRowErrorEntity> outOfRange = this.rowErrorRepository
				.findAllByBatchIdAndErrorCodeOrderByLineNumberAsc(batch.getId(), RowErrorCode.OUT_OF_RANGE);
		assertEquals(1, outOfRange.size());
		assertEquals(RowErrorCode.OUT_OF_RANGE, outOfRange.get(0).getErrorCode());
	}

	@Test
	void unknownBatchForeignKeyIsRejected() {
		StudentEntity entity = StudentMapper.toEntity(
				new Student(StudentId.of("stu-002"), FullName.of("Test Student"),
						EmailAddress.of("test@example.com"),
						DateOfBirth.of(LocalDate.of(2001, 11, 19), CLOCK),
						GradeLevel.of(9), Section.of('A'), marks(new BigDecimal("75.5"))),
				999999L, 999999L, STARTED, FINISHED);

		assertThrows(DataIntegrityViolationException.class, () -> this.studentRepository.saveAndFlush(entity));
	}

	private ImportBatchEntity saveBatch(String digest, ImportStatus status, ImportCounts counts) {
		ImportBatch domain = new ImportBatch(digest, status, counts);
		return batchRepository.saveAndFlush(ImportBatchMapper.toEntity(domain, "marks.csv", 100L,
				STARTED, FINISHED, 5000L));
	}

	private static SubjectMarks marks(BigDecimal mark) {
		return SubjectMarks.of(Map.of(
				Subject.MATHS, mark,
				Subject.SCIENCE, mark,
				Subject.ENGLISH, mark,
				Subject.SOCIAL_STUDIES, mark,
				Subject.SECOND_LANGUAGE, mark));
	}
}
