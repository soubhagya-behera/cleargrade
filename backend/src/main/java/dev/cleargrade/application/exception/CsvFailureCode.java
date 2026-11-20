package dev.cleargrade.application.exception;

/** Safe file-level import failure categories used by the REST error mapping. */
public enum CsvFailureCode {

	EMPTY_FILE,
	MISSING_REQUIRED_COLUMNS,
	MALFORMED_CSV,
	INVALID_ENCODING
}
