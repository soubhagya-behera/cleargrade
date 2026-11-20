package dev.cleargrade.domain.student;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import dev.cleargrade.domain.DomainValidationException;

/** Normalized, case-insensitive student identifier. */
public final class StudentId {

	private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9-]{3,20}");

	private final String value;

	private StudentId(String value) {
		this.value = value;
	}

	public static StudentId of(String raw) {
		if (raw == null) {
			throw new DomainValidationException("Student ID is required");
		}
		String stripped = raw.strip();
		if (!FORMAT.matcher(stripped).matches()) {
			throw new DomainValidationException("Student ID must be 3-20 ASCII letters, digits or hyphens");
		}
		return new StudentId(stripped.toUpperCase(Locale.ROOT));
	}

	public String value() {
		return this.value;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof StudentId that && this.value.equals(that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.value);
	}

	@Override
	public String toString() {
		return this.value;
	}
}
