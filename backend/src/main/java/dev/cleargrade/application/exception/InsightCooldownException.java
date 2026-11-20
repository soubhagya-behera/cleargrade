package dev.cleargrade.application.exception;

/** Analysis cooldown requested too soon after the previous analysis. */
public class InsightCooldownException extends RuntimeException {

	public InsightCooldownException() {
		super("An analysis was requested too recently for this import");
	}
}
