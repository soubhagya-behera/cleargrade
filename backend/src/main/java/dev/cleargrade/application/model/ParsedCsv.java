package dev.cleargrade.application.model;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Parser output in application-owned types; no CSV library types cross this boundary. */
public record ParsedCsv(List<Row> rows, Optional<FatalFailure> fatalFailure, Set<String> headers) {

	public record Row(int physicalLine, java.util.Map<String, String> fields, boolean wrongFieldCount) {
	}

	public record FatalFailure(int physicalLine, String safeMessage, Throwable cause) {
	}
}
