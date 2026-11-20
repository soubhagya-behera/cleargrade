package dev.cleargrade.application.exception;

/** Signals that the exact active-digest unique index rejected a reservation. */
public class DuplicateDigestException extends RuntimeException {

	private final String sha256;

	public DuplicateDigestException(String sha256, Throwable cause) {
		super("Active digest is already reserved: " + sha256, cause);
		this.sha256 = sha256;
	}

	public String sha256() {
		return this.sha256;
	}
}
