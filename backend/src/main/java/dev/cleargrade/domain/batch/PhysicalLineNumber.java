package dev.cleargrade.domain.batch;

import java.util.Objects;

import dev.cleargrade.domain.DomainValidationException;

/** Physical file line number: header is 1, data rows start at 2. */
public final class PhysicalLineNumber {

	public static final int HEADER_LINE = 1;
	public static final int FIRST_DATA_LINE = 2;

	private final int value;

	private PhysicalLineNumber(int value) {
		this.value = value;
	}

	public static PhysicalLineNumber of(int value) {
		if (value < HEADER_LINE) {
			throw new DomainValidationException("Physical line number must be positive");
		}
		return new PhysicalLineNumber(value);
	}

	public static PhysicalLineNumber ofDataRow(int value) {
		if (value < FIRST_DATA_LINE) {
			throw new DomainValidationException("A data-row error must be on line 2 or later");
		}
		return new PhysicalLineNumber(value);
	}

	public int value() {
		return this.value;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof PhysicalLineNumber that && this.value == that.value;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.value);
	}

	@Override
	public String toString() {
		return Integer.toString(this.value);
	}
}
