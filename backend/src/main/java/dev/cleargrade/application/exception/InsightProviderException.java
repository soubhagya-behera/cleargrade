package dev.cleargrade.application.exception;

import dev.cleargrade.application.model.InsightTelemetry;

/** Provider failure with an explicit routing category; never carries response bodies or keys. */
public class InsightProviderException extends RuntimeException {

	public enum Category { FALLBACK, CONFIGURATION }

	private final Category category;
	private final String code;
	private final InsightTelemetry telemetry;

	public InsightProviderException(String safeMessage, Category category, Throwable cause) {
		this(safeMessage, category, "PROVIDER_FAILURE", cause, null);
	}

	public InsightProviderException(String safeMessage, Category category, String code,
			Throwable cause, InsightTelemetry telemetry) {
		super(safeMessage, cause);
		this.category = category;
		this.code = code == null ? "PROVIDER_FAILURE" : code;
		this.telemetry = telemetry;
	}

	public Category category() {
		return this.category;
	}

	public String code() {
		return this.code;
	}

	public InsightTelemetry telemetry() {
		return this.telemetry;
	}
}
