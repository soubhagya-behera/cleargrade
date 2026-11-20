package dev.cleargrade.application.exception;

import dev.cleargrade.application.model.DigestMatch;

/** Application-level duplicate result produced after the failed reservation has rolled back. */
public class DigestConflictException extends RuntimeException {

	private final String sha256;
	private final DigestMatch existing;

	public DigestConflictException(String sha256, DigestMatch existing, Throwable cause) {
		super("Digest belongs to existing batch " + existing.batchId() + " in " + existing.status(), cause);
		this.sha256 = sha256;
		this.existing = existing;
	}

	public String sha256() {
		return this.sha256;
	}

	public DigestMatch existing() {
		return this.existing;
	}
}
