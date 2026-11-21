package dev.cleargrade.application.model;

/** Sanitized provider failure retained for diagnostics; never contains a body, key or credential. */
public record ProviderFailure(String engine, String code, InsightTelemetry telemetry) {
}
