package dev.cleargrade.application.exception;

/** Non-CSV upload; mapped to 415 INVALID_FILE_TYPE. */
public class InvalidFileTypeException extends RuntimeException {

	public InvalidFileTypeException() {
		super("Only CSV files are accepted");
	}
}
