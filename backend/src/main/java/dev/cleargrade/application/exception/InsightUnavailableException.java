package dev.cleargrade.application.exception;

/** No usable analysis engine (including the deterministic fallback) is available. */
public class InsightUnavailableException extends RuntimeException {

	public InsightUnavailableException() {
		super("No import insight engine is available");
	}
}
