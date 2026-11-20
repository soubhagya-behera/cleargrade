package dev.cleargrade.adapters.out.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.cleargrade.adapters.out.persistence.entity.ImportBatchEntity;
import dev.cleargrade.adapters.out.persistence.entity.ImportRowErrorEntity;
import dev.cleargrade.adapters.out.persistence.entity.StudentEntity;
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

class PersistenceMapperTest {

	private static final String DIGEST = "a".repeat(64);
	private static final String PRECISE_MARK = "99.123456789012345678901234567890123456789012345";
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void batchEntityMapsToDomainForNonCompletedBatchWithNullCounts() {
		ImportBatchEntity entity = batchEntity(ImportStatus.RECEIVED, null);

		ImportBatch domain = ImportBatchMapper.toDomain(entity);

		assertEquals(DIGEST, domain.sha256());
		assertEquals(ImportStatus.RECEIVED, domain.status());
		assertNull(domain.counts());
	}

	@Test
	void batchEntityMapsToDomainForCompletedBatchWithCounts() {
		ImportBatchEntity entity = batchEntity(ImportStatus.COMPLETED, counts(2, 1, 1, 0, 0, 0));

		ImportBatch domain = ImportBatchMapper.toDomain(entity);

		assertEquals(ImportStatus.COMPLETED, domain.status());
		assertEquals(new ImportCounts(2, 1, 1, 0, 0, 0), domain.counts());
	}

	@Test
	void partialBatchCountsCannotBeMappedToDomain() {
		ImportBatchEntity entity = batchEntity(ImportStatus.COMPLETED, counts(2, 1, 1, 0, 0, 0));
		entity.setErrorCount(null);

		assertThrows(IllegalStateException.class, () -> ImportBatchMapper.toDomain(entity));
	}

	@Test
	void domainBatchMapsToEntityWithAllPersistenceFields() {
		ImportBatch domain = ImportBatch.received(DIGEST);
		Instant started = Instant.parse("2026-11-19T10:00:00Z");
		Instant finished = Instant.parse("2026-11-19T10:00:05Z");

		ImportBatchEntity entity = ImportBatchMapper.toEntity(domain, "marks.csv", 100L, started, finished, 5000L);

		assertNull(entity.getId());
		assertEquals("marks.csv", entity.getFilename());
		assertEquals(100L, entity.getFileSizeBytes());
		assertEquals(DIGEST, entity.getSha256());
		assertEquals(ImportStatus.RECEIVED, entity.getStatus());
		assertEquals(started, entity.getStartedAt());
		assertEquals(finished, entity.getFinishedAt());
		assertEquals(5000L, entity.getDurationMs());
		assertNull(entity.getRowsRead());
		assertNull(entity.getImportedCount());
		assertNull(entity.getNewCount());
		assertNull(entity.getChangedCount());
		assertNull(entity.getUnchangedCount());
		assertNull(entity.getRejectedCount());
		assertNull(entity.getErrorCount());
	}

	@Test
	void completedDomainBatchMapsCountsToEntity() {
		ImportCounts counts = counts(5, 2, 1, 1, 1, 2);
		ImportBatch domain = new ImportBatch(DIGEST, ImportStatus.COMPLETED, counts);

		ImportBatchEntity entity = ImportBatchMapper.toEntity(domain, "marks.csv", 100L,
				Instant.parse("2026-11-19T10:00:00Z"), null, null);

		assertEquals(5, entity.getRowsRead());
		assertEquals(4, entity.getImportedCount());
		assertEquals(2, entity.getNewCount());
		assertEquals(1, entity.getChangedCount());
		assertEquals(1, entity.getUnchangedCount());
		assertEquals(1, entity.getRejectedCount());
		assertEquals(2, entity.getErrorCount());
	}

	@Test
	void studentEntityMapsToDomainPreservingUnicodeAndPreciseMarks() {
		BigDecimal precise = new BigDecimal(PRECISE_MARK);
		StudentEntity entity = studentEntity("Zoë O'Connor", precise);

		Student domain = StudentMapper.toDomain(entity, CLOCK);

		assertEquals("STU-001", domain.studentId().value());
		assertEquals("Zoë O'Connor", domain.fullName().value());
		assertEquals("test+tag@example.com", domain.email().value());
		assertEquals(LocalDate.of(2001, 11, 19), domain.dateOfBirth().value());
		assertEquals(GradeLevel.of(9), domain.gradeLevel());
		assertEquals(Section.of('A'), domain.section());
		assertEquals(precise, domain.marks().mark(Subject.MATHS));
		assertEquals(precise, domain.marks().mark(Subject.SECOND_LANGUAGE));
	}

	@Test
	void domainStudentMapsToEntityPreservingPrecisionAndForeignKeys() {
		BigDecimal precise = new BigDecimal(PRECISE_MARK);
		Student domain = student(precise);

		StudentEntity entity = StudentMapper.toEntity(domain, 11L, 12L,
				Instant.parse("2026-11-19T10:00:00Z"), Instant.parse("2026-11-19T11:00:00Z"));

		assertNull(entity.getId());
		assertEquals("STU-001", entity.getStudentId());
		assertEquals("Zoë O'Connor", entity.getFullName());
		assertEquals("test+tag@example.com", entity.getEmail());
		assertEquals(LocalDate.of(2001, 11, 19), entity.getDateOfBirth());
		assertEquals(9, entity.getGradeLevel());
		assertEquals("A", entity.getSection());
		assertEquals(precise, entity.getMaths());
		assertEquals(precise, entity.getSecondLanguage());
		assertEquals(11L, entity.getFirstSeenBatchId());
		assertEquals(12L, entity.getLastUpdatedBatchId());
	}

	@Test
	void rowErrorEntityMapsToDomainWithNullableRawStudentId() {
		ImportRowErrorEntity entity = new ImportRowErrorEntity(1L, 7L, 5, null,
				RowError.WHOLE_ROW, RowErrorCode.WRONG_FIELD_COUNT, "Unicode: 東京 — bad row");

		RowError domain = ImportRowErrorMapper.toDomain(entity);

		assertEquals(PhysicalLineNumber.ofDataRow(5), domain.lineNumber());
		assertEquals(RowErrorCode.WRONG_FIELD_COUNT, domain.code());
		assertEquals(RowError.WHOLE_ROW, domain.fieldName());
		assertNull(domain.rawStudentId());
		assertEquals("Unicode: 東京 — bad row", domain.message());
	}

	@Test
	void domainRowErrorMapsToEntity() {
		RowError error = RowError.forField(PhysicalLineNumber.ofDataRow(3), RowErrorCode.INVALID_FORMAT,
				"maths", "raw-ß", "not a number");

		ImportRowErrorEntity entity = ImportRowErrorMapper.toEntity(error, 9L);

		assertNull(entity.getId());
		assertEquals(9L, entity.getBatchId());
		assertEquals(3, entity.getLineNumber());
		assertEquals("raw-ß", entity.getRawStudentId());
		assertEquals("maths", entity.getFieldName());
		assertEquals(RowErrorCode.INVALID_FORMAT, entity.getErrorCode());
		assertEquals("not a number", entity.getMessage());
	}

	private static ImportBatchEntity batchEntity(ImportStatus status, ImportCounts counts) {
		return new ImportBatchEntity(1L, "marks.csv", 100L, DIGEST, status,
				Instant.parse("2026-11-19T10:00:00Z"), null, null,
				counts == null ? null : counts.rowsRead(),
				counts == null ? null : counts.imported(),
				counts == null ? null : counts.newCount(),
				counts == null ? null : counts.changedCount(),
				counts == null ? null : counts.unchangedCount(),
				counts == null ? null : counts.rejectedCount(),
				counts == null ? null : counts.errorCount());
	}

	private static ImportCounts counts(int rowsRead, int newCount, int changedCount, int unchangedCount,
			int rejectedCount, int errorCount) {
		return new ImportCounts(rowsRead, newCount, changedCount, unchangedCount, rejectedCount, errorCount);
	}

	private static StudentEntity studentEntity(String fullName, BigDecimal mark) {
		return new StudentEntity(1L, "STU-001", fullName, "test+tag@example.com",
				LocalDate.of(2001, 11, 19), (short) 9, "A",
				mark, mark, mark, mark, mark, 11L, 12L,
				Instant.parse("2026-11-19T10:00:00Z"), Instant.parse("2026-11-19T11:00:00Z"));
	}

	private static Student student(BigDecimal mark) {
		return new Student(
				StudentId.of("stu-001"),
				FullName.of("Zoë O'Connor"),
				EmailAddress.of("Test+Tag@Example.COM"),
				DateOfBirth.of(LocalDate.of(2001, 11, 19), CLOCK),
				GradeLevel.of(9),
				Section.of('a'),
				SubjectMarks.of(Map.of(
						Subject.MATHS, mark,
						Subject.SCIENCE, mark,
						Subject.ENGLISH, mark,
						Subject.SOCIAL_STUDIES, mark,
						Subject.SECOND_LANGUAGE, mark)));
	}
}
