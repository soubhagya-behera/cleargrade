package dev.cleargrade.domain.batch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.cleargrade.domain.DomainValidationException;

class RowErrorVocabularyTest {

	@Test
	void errorCodeContainsExactlyTheFiveAssignmentCodes() {
		assertArrayEquals(new RowErrorCode[] {
				RowErrorCode.MISSING_REQUIRED,
				RowErrorCode.INVALID_FORMAT,
				RowErrorCode.OUT_OF_RANGE,
				RowErrorCode.DUPLICATE_ID,
				RowErrorCode.WRONG_FIELD_COUNT
		}, RowErrorCode.values());
	}

	@Test
	void wholeRowErrorHasNoFieldOrRawId() {
		RowError error = RowError.wholeRow(PhysicalLineNumber.ofDataRow(3),
				RowErrorCode.WRONG_FIELD_COUNT, "Expected 11 fields but found 10");
		assertEquals(RowError.WHOLE_ROW, error.fieldName());
		assertNull(error.rawStudentId());
		assertEquals(RowErrorCode.WRONG_FIELD_COUNT, error.code());
	}

	@Test
	void fieldErrorCarriesItsContext() {
		RowError error = RowError.forField(PhysicalLineNumber.ofDataRow(2),
				RowErrorCode.OUT_OF_RANGE, "maths", "STU-001", "Mark must be between 0 and 100");
		assertEquals(2, error.lineNumber().value());
		assertEquals("maths", error.fieldName());
		assertEquals("STU-001", error.rawStudentId());
	}

	@Test
	void rejectsIncompleteErrors() {
		PhysicalLineNumber line = PhysicalLineNumber.of(2);
		assertThrows(DomainValidationException.class,
				() -> new RowError(null, RowErrorCode.INVALID_FORMAT, "email", null, "Bad email"));
		assertThrows(DomainValidationException.class,
				() -> new RowError(line, null, "email", null, "Bad email"));
		assertThrows(DomainValidationException.class,
				() -> new RowError(line, RowErrorCode.INVALID_FORMAT, " ", null, "Bad email"));
		assertThrows(DomainValidationException.class,
				() -> new RowError(line, RowErrorCode.INVALID_FORMAT, "email", null, " "));
	}
}
