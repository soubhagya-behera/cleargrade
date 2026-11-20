package dev.cleargrade.domain.student;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Objects;

import dev.cleargrade.domain.DomainValidationException;

/** Date of birth validated against an injected clock and an explicit business zone (I-03, I-04). */
public final class DateOfBirth {

	public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
	private static final int MIN_AGE = 5;
	private static final int MAX_AGE = 25;

	private final LocalDate value;

	private DateOfBirth(LocalDate value) {
		this.value = value;
	}

	public static DateOfBirth of(LocalDate dateOfBirth, Clock clock) {
		return of(dateOfBirth, clock, BUSINESS_ZONE);
	}

	public static DateOfBirth of(LocalDate dateOfBirth, Clock clock, ZoneId businessZone) {
		if (dateOfBirth == null) {
			throw new DomainValidationException("Date of birth is required");
		}
		Objects.requireNonNull(clock, "clock");
		Objects.requireNonNull(businessZone, "businessZone");

		LocalDate today = LocalDate.now(clock.withZone(businessZone));
		if (dateOfBirth.isAfter(today)) {
			throw new DomainValidationException("Date of birth is in the future");
		}
		int age = Period.between(dateOfBirth, today).getYears();
		if (age < MIN_AGE || age > MAX_AGE) {
			throw new DomainValidationException("Age must be between 5 and 25 years inclusive");
		}
		return new DateOfBirth(dateOfBirth);
	}

	/**
	 * Reconstructs a date of birth that was already validated at the creation/import boundary.
	 * Eligibility (I-03) is an input rule: a stored date does not become invalid as time passes.
	 */
	public static DateOfBirth fromPersisted(LocalDate value) {
		if (value == null) {
			throw new DomainValidationException("Date of birth is required");
		}
		return new DateOfBirth(value);
	}

	public LocalDate value() {
		return this.value;
	}

	public int ageOn(Clock clock, ZoneId businessZone) {
		Objects.requireNonNull(clock, "clock");
		Objects.requireNonNull(businessZone, "businessZone");
		return Period.between(this.value, LocalDate.now(clock.withZone(businessZone))).getYears();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof DateOfBirth that && this.value.equals(that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.value);
	}

	@Override
	public String toString() {
		return this.value.toString();
	}
}
