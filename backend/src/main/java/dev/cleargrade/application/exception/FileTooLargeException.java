package dev.cleargrade.application.exception;

/** Upload exceeds the configured limit; mapped to 413 FILE_TOO_LARGE. */
public class FileTooLargeException extends RuntimeException {

	public FileTooLargeException() {
		super("The uploaded file exceeds the configured size limit");
	}
}
