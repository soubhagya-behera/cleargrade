package dev.cleargrade.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import dev.cleargrade.domain.DomainValidationException;

class BatchReservationTest {

	private static final String VALID_DIGEST = "a".repeat(64);
	private static final Instant STARTED = Instant.parse("2026-11-19T10:00:00Z");

	@Test
	void acceptsValidLowercaseHexDigest() {
		BatchReservation reservation = new BatchReservation(VALID_DIGEST, "marks.csv", 100L, STARTED);
		assertEquals(VALID_DIGEST, reservation.sha256());
	}

	@Test
	void rejectsNullDigestUsingDomainRule() {
		assertDomainRejection(null);
	}

	@Test
	void rejectsShortDigest() {
		assertDomainRejection("a".repeat(63));
	}

	@Test
	void rejectsUppercaseDigest() {
		assertDomainRejection("A".repeat(64));
	}

	@Test
	void rejectsInvalidCharacters() {
		assertDomainRejection("g".repeat(64));
		assertDomainRejection("a".repeat(63) + "!");
	}

	private static void assertDomainRejection(String digest) {
		DomainValidationException error = assertThrows(DomainValidationException.class,
				() -> new BatchReservation(digest, "marks.csv", 100L, STARTED));
		assertEquals("A lowercase SHA-256 digest is required", error.getMessage());
	}
}
