package dev.cleargrade.domain.student;

import dev.cleargrade.domain.DomainValidationException;

/** Section label A to F, normalized to uppercase. */
public final class Section {

	private final char value;

	private Section(char value) {
		this.value = value;
	}

	public static Section of(char raw) {
		char normalized = Character.toUpperCase(raw);
		if (normalized < 'A' || normalized > 'F') {
			throw new DomainValidationException("Section must be A to F");
		}
		return new Section(normalized);
	}

	public static Section of(String raw) {
		if (raw == null || raw.strip().length() != 1) {
			throw new DomainValidationException("Section must be a single letter A to F");
		}
		return of(raw.strip().charAt(0));
	}

	public char value() {
		return this.value;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Section that && this.value == that.value;
	}

	@Override
	public int hashCode() {
		return Character.hashCode(this.value);
	}

	@Override
	public String toString() {
		return Character.toString(this.value);
	}
}
