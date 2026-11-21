package dev.cleargrade.application.model;

import java.util.List;

/** In-memory snapshot of the last explicit analysis; no provider payloads or student data. */
public record LastAnalysis(String engine, String reason, boolean cached, InsightTelemetry telemetry,
		List<ProviderFailure> providerFailures) {

	public LastAnalysis(String engine, String reason, boolean cached, InsightTelemetry telemetry) {
		this(engine, reason, cached, telemetry, List.of());
	}

	public static final LastAnalysis NONE = new LastAnalysis(null, null, false, null, List.of());
}
