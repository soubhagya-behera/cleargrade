package dev.cleargrade.application.model;

/** In-memory snapshot of the last explicit analysis; no provider payloads or student data. */
public record LastAnalysis(String engine, String reason, boolean cached, InsightTelemetry telemetry) {

	public static final LastAnalysis NONE = new LastAnalysis(null, null, false, null);
}
