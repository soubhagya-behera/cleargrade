package dev.cleargrade.application.model;

/** One stored row-problem occurrence, including its physical file line. */
public record RowErrorView(
		int lineNumber,
		String rawStudentId,
		String field,
		String errorCode,
		String message) {
}
