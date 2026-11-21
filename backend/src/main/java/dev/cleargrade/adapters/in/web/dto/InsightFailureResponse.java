package dev.cleargrade.adapters.in.web.dto;

/** Sanitized provider failure diagnostics: no body, key, credential or environment value. */
public record InsightFailureResponse(
		String engine,
		String code,
		String providerStatus,
		long durationMs,
		boolean fallbackSucceeded) {
}
