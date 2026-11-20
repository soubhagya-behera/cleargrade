package dev.cleargrade.domain.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import dev.cleargrade.domain.DomainValidationException;

class DateOfBirthTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void usesConfiguredBusinessZoneByDefault() {
		assertEquals(ZoneId.of("Asia/Kolkata"), DateOfBirth.BUSINESS_ZONE);
	}

	@Test
	void acceptsInclusiveAgeBoundaries() {
		assertEquals(5, DateOfBirth.of(LocalDate.of(2021, 11, 19), FIXED_CLOCK)
				.ageOn(FIXED_CLOCK, DateOfBirth.BUSINESS_ZONE));
		assertEquals(25, DateOfBirth.of(LocalDate.of(2001, 11, 19), FIXED_CLOCK)
				.ageOn(FIXED_CLOCK, DateOfBirth.BUSINESS_ZONE));
	}

	@Test
	void rejectsAgesOutsideTheInclusiveRange() {
		assertThrows(DomainValidationException.class,
				() -> DateOfBirth.of(LocalDate.of(2021, 11, 20), FIXED_CLOCK));
		assertThrows(DomainValidationException.class,
				() -> DateOfBirth.of(LocalDate.of(2000, 11, 19), FIXED_CLOCK));
	}

	@Test
	void rejectsFutureDates() {
		assertThrows(DomainValidationException.class,
				() -> DateOfBirth.of(LocalDate.of(2026, 11, 20), FIXED_CLOCK));
	}

	@Test
	void businessZoneIsConfigurableAndNotTheJvmDefault() {
		Clock eveningUtc = Clock.fixed(Instant.parse("2026-11-18T20:00:00Z"), ZoneOffset.UTC);
		LocalDate dateOfBirth = LocalDate.of(2021, 11, 19);

		assertEquals(5, DateOfBirth.of(dateOfBirth, eveningUtc).ageOn(eveningUtc, DateOfBirth.BUSINESS_ZONE));
		assertThrows(DomainValidationException.class,
				() -> DateOfBirth.of(dateOfBirth, eveningUtc, ZoneOffset.UTC));
	}

	@Test
	void rejectsNullInputs() {
		assertThrows(DomainValidationException.class, () -> DateOfBirth.of(null, FIXED_CLOCK));
		assertThrows(NullPointerException.class, () -> DateOfBirth.of(LocalDate.of(2001, 11, 19), null));
	}
}
