package dev.cleargrade.domain.batch;

/** The five administrator-facing row problem codes required by the assignment. */
public enum RowErrorCode {

	MISSING_REQUIRED,
	INVALID_FORMAT,
	OUT_OF_RANGE,
	DUPLICATE_ID,
	WRONG_FIELD_COUNT
}
