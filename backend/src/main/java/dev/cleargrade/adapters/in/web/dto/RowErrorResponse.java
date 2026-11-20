package dev.cleargrade.adapters.in.web.dto;

import dev.cleargrade.application.model.RowErrorView;

/** Row problem with physical line number and plain-language explanation. */
public record RowErrorResponse(
		int lineNumber,
		String rawStudentId,
		String field,
		String errorCode,
		String message) {

	public static RowErrorResponse from(RowErrorView view) {
		return new RowErrorResponse(view.lineNumber(), view.rawStudentId(), view.field(),
				view.errorCode(), view.message());
	}
}
