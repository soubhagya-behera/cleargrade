package dev.cleargrade.application.exception;

/** Bad pagination, filter or sort value; mapped to 400 INVALID_PARAMETER. */
public class InvalidParameterException extends RuntimeException {

	public InvalidParameterException(String message) {
		super(message);
	}
}
