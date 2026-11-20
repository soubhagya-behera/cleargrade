package dev.cleargrade.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.cleargrade.application.exception.CsvImportException;
import dev.cleargrade.application.model.ParsedCsv;
import dev.cleargrade.application.model.ValidatedRows;
import dev.cleargrade.domain.batch.RowErrorCode;
import dev.cleargrade.domain.student.Subject;
import org.junit.jupiter.api.Test;

class CsvRowValidatorTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);
	private static final Set<String> HEADERS = Set.of("student_id", "full_name", "email", "date_of_birth",
			"grade_level", "section", "maths", "science", "english", "social_studies", "second_language");
	private final CsvRowValidator validator = new CsvRowValidator(CLOCK);

	@Test
	void collectsEveryErrorForOneRejectedRow() {
		Map<String, String> fields = fields("STU001", "A", "not-an-email", "2001-01-01", "9", "A", "101", "x", "50", "50", "50");
		ValidatedRows result = validator.validate(csv(new ParsedCsv.Row(2, fields, false)));

		assertEquals(0, result.acceptedStudents().size());
		assertEquals(1, result.rejectedRows());
		assertEquals(4, result.rowErrors().size());
		assertTrue(result.rowErrors().stream().anyMatch(error -> error.code() == RowErrorCode.INVALID_FORMAT
				&& "full_name".equals(error.fieldName())));
		assertTrue(result.rowErrors().stream().anyMatch(error -> error.code() == RowErrorCode.INVALID_FORMAT
				&& "email".equals(error.fieldName())));
		assertTrue(result.rowErrors().stream().anyMatch(error -> error.code() == RowErrorCode.OUT_OF_RANGE
				&& Subject.MATHS.columnName().equals(error.fieldName())));
		assertTrue(result.rowErrors().stream().anyMatch(error -> error.code() == RowErrorCode.INVALID_FORMAT
				&& Subject.SCIENCE.columnName().equals(error.fieldName())));
	}

	@Test
	void firstIdReservesEvenWhenItsRowIsRejectedAndLaterOccurrenceGetsDuplicateId() {
		Map<String, String> invalidFirst = fields("STU001", "A", "broken", "2001-01-01", "9", "A", "50", "50", "50", "50", "50");
		Map<String, String> validSecond = fields("stu001", "Valid Name", "valid@example.com", "2001-01-01", "9", "A",
				"50", "50", "50", "50", "50");
		ValidatedRows result = validator.validate(csv(
				new ParsedCsv.Row(2, invalidFirst, false),
				new ParsedCsv.Row(4, validSecond, false)));

		assertEquals(0, result.acceptedStudents().size());
		assertEquals(2, result.rejectedRows());
		assertTrue(result.rowErrors().stream().anyMatch(error -> error.code() == RowErrorCode.DUPLICATE_ID
				&& error.lineNumber().value() == 4));
	}

	@Test
	void physicalLinesAndWrongFieldCountArePreserved() {
		ValidatedRows result = validator.validate(csv(new ParsedCsv.Row(7, Map.of(), true)));

		assertEquals(1, result.rejectedRows());
		assertEquals(1, result.rowErrors().size());
		assertEquals(RowErrorCode.WRONG_FIELD_COUNT, result.rowErrors().get(0).code());
		assertEquals(7, result.rowErrors().get(0).lineNumber().value());
	}

	@Test
	void missingRequiredHeadersAreFatal() {
		ParsedCsv csv = new ParsedCsv(List.of(), Optional.empty(), Set.of("student_id"));
		assertThrows(CsvImportException.class, () -> validator.validate(csv));
	}

	@Test
	void fatalParserFailurePropagatesWithSafeMessage() {
		RuntimeException original = new RuntimeException("parser detail");
		ParsedCsv csv = new ParsedCsv(List.of(),
				Optional.of(new ParsedCsv.FatalFailure(3, "The CSV file could not be parsed", original)), HEADERS);
		CsvImportException error = assertThrows(CsvImportException.class, () -> validator.validate(csv));
		assertEquals(3, error.physicalLine());
		assertEquals("The CSV file could not be parsed", error.getMessage());
		assertEquals(original, error.getCause());
	}

	@Test
	void bothDocumentedDateFormatsAreAcceptedAndOutOfAgeRangeIsRejected() {
		Map<String, String> slash = fields("STU001", "Valid Name", "valid@example.com", "19/11/2001", "9", "A",
				"50.25", "50", "50", "50", "50");
		assertEquals(1, validator.validate(csv(new ParsedCsv.Row(2, slash, false))).acceptedStudents().size());

		Map<String, String> tooOld = new HashMap<>(slash);
		tooOld.put("date_of_birth", "1970-01-01");
		ValidatedRows rejected = validator.validate(csv(new ParsedCsv.Row(2, tooOld, false)));
		assertEquals(1, rejected.rejectedRows());
		assertTrue(rejected.rowErrors().stream().anyMatch(error -> error.code() == RowErrorCode.OUT_OF_RANGE
				&& "date_of_birth".equals(error.fieldName())));
	}

	private static ParsedCsv csv(ParsedCsv.Row... rows) {
		return new ParsedCsv(List.of(rows), Optional.empty(), HEADERS);
	}

	private static Map<String, String> fields(String id, String name, String email, String dob, String grade,
			String section, String maths, String science, String english, String social, String secondLanguage) {
		Map<String, String> fields = new HashMap<>();
		fields.put("student_id", id);
		fields.put("full_name", name);
		fields.put("email", email);
		fields.put("date_of_birth", dob);
		fields.put("grade_level", grade);
		fields.put("section", section);
		fields.put(Subject.MATHS.columnName(), maths);
		fields.put(Subject.SCIENCE.columnName(), science);
		fields.put(Subject.ENGLISH.columnName(), english);
		fields.put(Subject.SOCIAL_STUDIES.columnName(), social);
		fields.put(Subject.SECOND_LANGUAGE.columnName(), secondLanguage);
		return fields;
	}
}
