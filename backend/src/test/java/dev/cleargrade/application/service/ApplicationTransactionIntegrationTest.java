package dev.cleargrade.application.service;

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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import dev.cleargrade.application.exception.DigestConflictException;
import dev.cleargrade.application.exception.DuplicateDigestException;
import dev.cleargrade.application.model.BatchImportResult;
import dev.cleargrade.application.model.BatchReservation;
import dev.cleargrade.application.model.BatchSubmission;
import dev.cleargrade.application.model.DigestMatch;
import dev.cleargrade.application.port.out.ImportBatchOutputPort;
import dev.cleargrade.domain.DomainValidationException;
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

/** Real PostgreSQL verification of T1-T4 boundaries, upserts and digest handling. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class ApplicationTransactionIntegrationTest {

	private static final String DIGEST = "a".repeat(64);
	private static final String MARK = "85.123456789012345678901234567890123456";

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
			.withDatabaseName("cleargrade_app_test")
			.withUsername("cleargrade_test")
			.withPassword("cleargrade_test");

	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private Clock clock;
	@Autowired
	private ImportApplicationService applicationService;
	@Autowired
	private BatchReservationTx reservationTx;
	@Autowired
	private BatchDigestLookupTx digestLookupTx;
	@Autowired
	private BatchProcessingTransitionTx processingTransitionTx;
	@Autowired
	private BatchFinalisationTx finalisationTx;
	@Autowired
	private BatchFailureTx failureTx;
	@Autowired
	private ImportBatchOutputPort batchPort;

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
		this.jdbc.execute("TRUNCATE TABLE import_row_error, student, import_batch RESTART IDENTITY");
	}

	@Test
	void applicationBeansStartWithFlywayAndHibernateValidation() {
		assertNotNull(this.applicationContext.getBean(ImportApplicationService.class));
		assertNotNull(this.applicationContext.getBean(BatchReservationTx.class));
		assertNotNull(this.applicationContext.getBean(BatchFinalisationTx.class));
		assertEquals(1, this.jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE", Integer.class));
	}

	@Test
	void t1ReservationIsCommittedWithInjectedClockAndNullCounts() {
		long batchId = this.reservationTx.reserve(reservation(DIGEST));

		String status = this.jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?", String.class, batchId);
		Instant startedAt = this.jdbc.queryForObject("SELECT started_at FROM import_batch WHERE id = ?",
				OffsetDateTime.class, batchId).toInstant();
		assertEquals("RECEIVED", status);
		assertEquals(this.clock.instant(), startedAt);
		Integer countColumns = this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch "
				+ "WHERE rows_read IS NULL AND imported_count IS NULL AND error_count IS NULL", Integer.class);
		assertEquals(1, countColumns);
	}

	@Test
	void duplicateActiveDigestIsDetected() {
		this.reservationTx.reserve(reservation(DIGEST));

		assertThrows(DuplicateDigestException.class, () -> this.reservationTx.reserve(reservation(DIGEST)));
		assertEquals(1, batchCount());
	}

	@Test
	void duplicateCompletedDigestIsDetected() {
		long batchId = readyBatch(DIGEST);
		this.finalisationTx.process(batchId, List.of(), List.of(), 0, this.clock.instant());

		assertThrows(DuplicateDigestException.class, () -> this.reservationTx.reserve(reservation(DIGEST)));
		assertEquals(1, batchCount());
	}

	@Test
	void failedDigestCanBeReservedAgain() {
		long first = this.reservationTx.reserve(reservation(DIGEST));
		this.failureTx.markFailed(first, this.clock.instant());

		long second = this.reservationTx.reserve(reservation(DIGEST));

		assertTrue(second > first);
		assertEquals(2, batchCount());
		assertEquals(2, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch WHERE sha256 = ?",
				Integer.class, DIGEST));
	}

	@Test
	void unrelatedViolationIsNotMisclassifiedAsDuplicateDigest() {
		DataIntegrityViolationException error = assertThrows(DataIntegrityViolationException.class,
				() -> this.reservationTx.reserve(new BatchReservation("a".repeat(64), " ", 10L, this.clock.instant())));
		assertFalse(DuplicateDigestException.class.isInstance(error));
		assertEquals(0, batchCount());
	}

	@Test
	void duplicateLookupSucceedsAfterFailedReservationRolledBack() {
		long existingId = readyBatch(DIGEST);
		this.finalisationTx.process(existingId, List.of(), List.of(), 0, this.clock.instant());

		DigestConflictException conflict = assertThrows(DigestConflictException.class,
				() -> this.applicationService.importBatch(submission(DIGEST, List.of(), List.of(), 0)));

		assertEquals(DIGEST, conflict.sha256());
		assertEquals(ImportStatus.COMPLETED, conflict.existing().status());
		assertEquals(existingId, conflict.existing().batchId());
		assertEquals(1, batchCount());
	}

	@Test
	void t2TransitionIsIndependentAndRejectsInvalidStatus() {
		long batchId = this.reservationTx.reserve(reservation(DIGEST));
		this.processingTransitionTx.markProcessing(batchId);
		assertEquals("PROCESSING", statusOf(batchId));

		this.failureTx.markFailed(batchId, this.clock.instant());
		assertThrows(DomainValidationException.class, () -> this.processingTransitionTx.markProcessing(batchId));
		assertEquals("FAILED", statusOf(batchId));
		assertEquals(1, batchCount());
	}

	@Test
	void t3CommitsStudentsErrorsCountsAndCompletionTogether() {
		long batchId = readyBatch(DIGEST);
		List<Student> accepted = List.of(
				student("STU-001", "Alice Smith"), student("STU-002", "Bob Jones"));
		List<RowError> errors = List.of(
				rowError(5, RowErrorCode.OUT_OF_RANGE, "maths"),
				rowError(5, RowErrorCode.INVALID_FORMAT, "science"),
				rowError(6, RowErrorCode.WRONG_FIELD_COUNT, "(whole row)"));

		BatchImportResult result = this.finalisationTx.process(batchId, accepted, errors, 1, this.clock.instant());

		assertEquals(new ImportCounts(3, 2, 0, 0, 1, 3), result.counts());
		assertEquals("COMPLETED", statusOf(batchId));
		assertEquals(2, this.jdbc.queryForObject("SELECT COUNT(*) FROM student", Integer.class));
		assertEquals(3, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_row_error", Integer.class));
		Integer completedWithCounts = this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch "
				+ "WHERE status = 'COMPLETED' AND rows_read = 3 AND imported_count = 2 "
				+ "AND error_count = 3 AND rejected_count = 1", Integer.class);
		assertEquals(1, completedWithCounts);
	}

	@Test
	void t3FailureRollsBackAllWritesAndT4MarksFailedIndependently() {
		DomainValidationException failure = assertThrows(DomainValidationException.class,
				() -> this.applicationService.importBatch(submission(DIGEST,
						List.of(student("STU-001", "Alice Smith")),
						List.of(rowError(5, RowErrorCode.OUT_OF_RANGE, "maths")), 2)));

		assertTrue(failure.getMessage().contains("errorCount"));
		assertEquals(0, this.jdbc.queryForObject("SELECT COUNT(*) FROM student", Integer.class));
		assertEquals(0, this.jdbc.queryForObject("SELECT COUNT(*) FROM import_row_error", Integer.class));
		assertEquals("FAILED", statusOf(1L));
		Integer partialCounts = this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch "
				+ "WHERE rows_read IS NOT NULL OR imported_count IS NOT NULL OR error_count IS NOT NULL",
				Integer.class);
		assertEquals(0, partialCounts);
	}

	@Test
	void upsertCountsAndBatchIdFieldsArePreservedAcrossImports() {
		BatchImportResult first = this.applicationService.importBatch(submission("a".repeat(64),
				List.of(student("STU-001", "Alice Smith"), student("STU-002", "Bob Jones")), List.of(), 0));
		long firstBatch = first.batchId();

		Student changed = new Student(StudentId.of("STU-001"), FullName.of("Alice Brown"),
				EmailAddress.of("test@example.com"), DateOfBirth.of(LocalDate.of(2001, 11, 19), this.clock),
				GradeLevel.of(10), Section.of('B'), marks("91.5"));
		BatchImportResult second = this.applicationService.importBatch(submission("b".repeat(64),
				List.of(changed, student("STU-002", "Bob Jones"), student("STU-003", "Carol Lee")),
				List.of(), 0));

		assertEquals(new ImportCounts(2, 2, 0, 0, 0, 0), first.counts());
		assertEquals(new ImportCounts(3, 1, 1, 1, 0, 0), second.counts());
		Integer preserved = this.jdbc.queryForObject("SELECT COUNT(*) FROM student "
				+ "WHERE student_id = 'STU-001' AND first_seen_batch_id = ? AND last_updated_batch_id = ? "
				+ "AND full_name = 'Alice Brown'", Integer.class, firstBatch, second.batchId());
		assertEquals(1, preserved);
		Integer unchangedKept = this.jdbc.queryForObject("SELECT COUNT(*) FROM student "
				+ "WHERE student_id = 'STU-002' AND first_seen_batch_id = ? AND last_updated_batch_id = ?",
				Integer.class, firstBatch, firstBatch);
		assertEquals(1, unchangedKept);
	}

	@Test
	void concurrentUpsertsKeepOneCompleteVersionAndCorrectBatchIds() throws Exception {
		long initialBatch = readyBatch("c".repeat(64));
		Student initial = student("STU-001", "Alice Initial", "alice.initial@example.com", 9, 'A', "70.5");
		this.finalisationTx.process(initialBatch, List.of(initial), List.of(), 0, this.clock.instant());

		long batchA = readyBatch("d".repeat(64));
		long batchB = readyBatch("e".repeat(64));
		Student versionA = student("STU-001", "Alice Alpha", "alice.alpha@example.com", 10, 'B', "81.125");
		Student versionB = student("STU-001", "Alice Beta", "alice.beta@example.com", 11, 'C', "92.375");

		Map<Long, Student> submissions = Map.of(batchA, versionA, batchB, versionB);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<BatchImportResult>> futures = new ArrayList<>();
			for (Map.Entry<Long, Student> submission : submissions.entrySet()) {
				futures.add(pool.submit(() -> {
					start.await();
					return this.finalisationTx.process(submission.getKey(), List.of(submission.getValue()),
							List.of(), 0, this.clock.instant());
				}));
			}
			start.countDown();
			for (Future<BatchImportResult> future : futures) {
				BatchImportResult result = future.get();
				assertEquals(0, result.counts().newCount());
				assertEquals(1, result.counts().changedCount());
				assertEquals(0, result.counts().unchangedCount());
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertEquals(1, this.jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE student_id = 'STU-001'",
				Integer.class));
		StudentSnapshot stored = this.jdbc.queryForObject("SELECT full_name, email, grade_level, section, "
				+ "maths, science, english, social_studies, second_language, first_seen_batch_id, "
				+ "last_updated_batch_id FROM student WHERE student_id = 'STU-001'",
				(row, index) -> new StudentSnapshot(
						row.getString("full_name"),
						row.getString("email"),
						row.getInt("grade_level"),
						row.getString("section"),
						row.getBigDecimal("maths"),
						row.getBigDecimal("science"),
						row.getBigDecimal("english"),
						row.getBigDecimal("social_studies"),
						row.getBigDecimal("second_language"),
						row.getLong("first_seen_batch_id"),
						row.getLong("last_updated_batch_id")));

		boolean versionAWon = versionA.fullName().value().equals(stored.fullName());
		boolean versionBWon = versionB.fullName().value().equals(stored.fullName());
		assertTrue(versionAWon || versionBWon, "Stored business values must be one complete submitted version");
		Student winner = versionAWon ? versionA : versionB;
		long winningBatch = versionAWon ? batchA : batchB;

		assertEquals(winner.email().value(), stored.email());
		assertEquals(winner.gradeLevel().value(), stored.gradeLevel());
		assertEquals(String.valueOf(winner.section().value()), stored.section());
		assertEquals(winner.marks().mark(Subject.MATHS), stored.maths());
		assertEquals(winner.marks().mark(Subject.SCIENCE), stored.science());
		assertEquals(winner.marks().mark(Subject.ENGLISH), stored.english());
		assertEquals(winner.marks().mark(Subject.SOCIAL_STUDIES), stored.socialStudies());
		assertEquals(winner.marks().mark(Subject.SECOND_LANGUAGE), stored.secondLanguage());
		assertEquals(initialBatch, stored.firstSeenBatchId());
		assertEquals(winningBatch, stored.lastUpdatedBatchId());
	}

	private BatchReservation reservation(String digest) {
		return new BatchReservation(digest, "marks.csv", 100L, this.clock.instant());
	}

	private BatchSubmission submission(String digest, List<Student> students, List<RowError> errors, int rejectedRows) {
		return new BatchSubmission(reservation(digest), students, errors, rejectedRows);
	}

	private long readyBatch(String digest) {
		long batchId = this.reservationTx.reserve(reservation(digest));
		this.processingTransitionTx.markProcessing(batchId);
		return batchId;
	}

	private Student student(String studentId, String fullName) {
		return new Student(StudentId.of(studentId), FullName.of(fullName), EmailAddress.of("test@example.com"),
				DateOfBirth.of(LocalDate.of(2001, 11, 19), this.clock), GradeLevel.of(9), Section.of('A'), marks(MARK));
	}

	private Student student(String studentId, String fullName, String email, int gradeLevel, char section, String mark) {
		return new Student(StudentId.of(studentId), FullName.of(fullName), EmailAddress.of(email),
				DateOfBirth.of(LocalDate.of(2001, 11, 19), this.clock), GradeLevel.of(gradeLevel),
				Section.of(section), marks(mark));
	}

	private static SubjectMarks marks(String value) {
		BigDecimal mark = new BigDecimal(value);
		return SubjectMarks.of(Map.of(
				Subject.MATHS, mark,
				Subject.SCIENCE, mark,
				Subject.ENGLISH, mark,
				Subject.SOCIAL_STUDIES, mark,
				Subject.SECOND_LANGUAGE, mark));
	}

	private static RowError rowError(int line, RowErrorCode code, String field) {
		return RowError.forField(PhysicalLineNumber.ofDataRow(line), code, field, "raw-id", "invalid value");
	}

	private String statusOf(long batchId) {
		return this.jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?", String.class, batchId);
	}

	private int batchCount() {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM import_batch", Integer.class);
	}

	private record StudentSnapshot(String fullName, String email, int gradeLevel, String section,
			BigDecimal maths, BigDecimal science, BigDecimal english, BigDecimal socialStudies,
			BigDecimal secondLanguage, long firstSeenBatchId, long lastUpdatedBatchId) {
	}
}
