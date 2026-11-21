package dev.cleargrade.adapters.in.web.dto;

import dev.cleargrade.application.model.InsightTelemetry;

/** Safe usage counters only. Missing provider metadata is reported as unavailable, never estimated. */
public record InsightUsageResponse(
		boolean available,
		Integer inputTokens,
		Integer outputTokens,
		Integer thinkingTokens,
		Integer totalTokens,
		String modelId,
		String providerStatus,
		String interactionStatus,
		long durationMs) {

	public static InsightUsageResponse from(InsightTelemetry telemetry) {
		if (telemetry == null) {
			return new InsightUsageResponse(false, null, null, null, null, "", "", "", 0L);
		}
		return new InsightUsageResponse(telemetry.usageAvailable(), telemetry.inputTokens(),
				telemetry.outputTokens(), telemetry.thinkingTokens(), telemetry.totalTokens(),
				nullToEmpty(telemetry.modelId()), nullToEmpty(telemetry.providerStatus()),
				nullToEmpty(telemetry.interactionStatus()), telemetry.durationMs());
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
