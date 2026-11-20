package dev.cleargrade.domain.student;

import dev.cleargrade.domain.DomainValidationException;

/** Whole-number grade level from 1 to 12. */
public final class GradeLevel {

	private final int value;

	private GradeLevel(int value) {
		this.value = value;
	}

	public static GradeLevel of(int value) {
		if (value < 1 || value > 12) {
			throw new DomainValidationException("Grade level must be between 1 and 12");
		}
		return new GradeLevel(value);
	}

	public static GradeLevel of(String raw) {
		if (raw == null) {
			throw new DomainValidationException("Grade level is required");
		}
		try {
			return of(Integer.parseInt(raw.strip()));
		}
		catch (NumberFormatException exception) {
			throw new DomainValidationException("Grade level must be a whole number between 1 and 12");
		}
	}

	public int value() {
		return this.value;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GradeLevel that && this.value == that.value;
	}

	@Override
	public int hashCode() {
		return Integer.hashCode(this.value);
	}

	@Override
	public String toString() {
		return Integer.toString(this.value);
	}
}
