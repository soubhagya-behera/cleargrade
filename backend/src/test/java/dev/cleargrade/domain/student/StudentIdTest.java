package dev.cleargrade.domain.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.cleargrade.domain.DomainValidationException;

class StudentIdTest {

	@Test
	void normalizesToUppercase() {
		assertEquals("STU-001", StudentId.of("stu-001").value());
	}

	@Test
	void equalityIsCaseInsensitiveAfterNormalization() {
		StudentId lower = StudentId.of("abc-123");
		StudentId upper = StudentId.of("ABC-123");
		assertEquals(lower, upper);
		assertEquals(lower.hashCode(), upper.hashCode());
	}

	@Test
	void acceptsLengthBoundaries() {
		assertEquals("ABC", StudentId.of("abc").value());
		assertEquals("A".repeat(20).length(), StudentId.of("a".repeat(20)).value().length());
		assertThrows(DomainValidationException.class, () -> StudentId.of("ab"));
		assertThrows(DomainValidationException.class, () -> StudentId.of("a".repeat(21)));
	}

	@Test
	void acceptsOnlyAsciiLettersDigitsAndHyphens() {
		assertEquals("A1-B2", StudentId.of("a1-b2").value());
		assertThrows(DomainValidationException.class, () -> StudentId.of("ab_c"));
		assertThrows(DomainValidationException.class, () -> StudentId.of("ab c"));
		assertThrows(DomainValidationException.class, () -> StudentId.of("ab.c"));
		assertThrows(DomainValidationException.class, () -> StudentId.of("ÄBC"));
	}

	@Test
	void rejectsUnicodeCharactersThatWouldUppercaseIntoAscii() {
		assertThrows(DomainValidationException.class, () -> StudentId.of("ßab"));
		assertThrows(DomainValidationException.class, () -> StudentId.of("ſab"));
	}

	@Test
	void rejectsNullOrBlank() {
		assertThrows(DomainValidationException.class, () -> StudentId.of(null));
		assertThrows(DomainValidationException.class, () -> StudentId.of("   "));
	}
}
