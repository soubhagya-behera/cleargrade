package dev.cleargrade.application.model;

/** Validated directory query; sort property is restricted to an allowlist by the query service. */
public record StudentSearchCriteria(
		String query,
		Integer grade,
		String section,
		int page,
		int size,
		String sortProperty,
		boolean ascending) {
}
