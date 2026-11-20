package dev.cleargrade.domain;

/** Raised when a value or state violates a documented domain rule. */
public class DomainValidationException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	public DomainValidationException(String message) {
		super(message);
	}
}
