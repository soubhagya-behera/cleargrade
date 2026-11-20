package dev.cleargrade.domain.batch;

import dev.cleargrade.domain.DomainValidationException;

/** One validation error occurrence on one physical line; a row may produce several. */
public record RowError(
		PhysicalLineNumber lineNumber,
		RowErrorCode code,
		String fieldName,
		String rawStudentId,
		String message) {

	public static final String WHOLE_ROW = "(whole row)";

	public RowError {
		if (lineNumber == null || code == null) {
			throw new DomainValidationException("Row error line number and code are required");
		}
		if (fieldName == null || fieldName.isBlank() || message == null || message.isBlank()) {
			throw new DomainValidationException("Row error field and message are required");
		}
	}

	public static RowError wholeRow(PhysicalLineNumber lineNumber, RowErrorCode code, String message) {
		return new RowError(lineNumber, code, WHOLE_ROW, null, message);
	}

	public static RowError forField(PhysicalLineNumber lineNumber, RowErrorCode code,
			String fieldName, String rawStudentId, String message) {
		return new RowError(lineNumber, code, fieldName, rawStudentId, message);
	}
}
