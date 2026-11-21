package dev.cleargrade.application.model;

/**
 * Safe provider diagnostics: numeric usage counters, status labels and timings only.
 * Never contains prompts, request bodies, provider payloads or student data.
 */
public record InsightTelemetry(
		boolean usageAvailable,
		Integer inputTokens,
		Integer outputTokens,
		Integer thinkingTokens,
		Integer totalTokens,
		String modelId,
		String providerStatus,
		String interactionStatus,
		long durationMs) {

	public static InsightTelemetry unavailable(String modelId, String providerStatus,
			String interactionStatus, long durationMs) {
		return new InsightTelemetry(false, null, null, null, null,
				modelId, providerStatus, interactionStatus, durationMs);
	}
}
