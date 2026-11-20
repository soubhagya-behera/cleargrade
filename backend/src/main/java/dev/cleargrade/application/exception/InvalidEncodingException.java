package dev.cleargrade.application.exception;

/** Invalid UTF-8 payload; mapped to 400 INVALID_ENCODING. */
public class InvalidEncodingException extends RuntimeException {

	public InvalidEncodingException() {
		super("The file is not valid UTF-8 text");
	}
}
