package dev.cleargrade.domain.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.cleargrade.domain.DomainValidationException;

class FullNameTest {

	@Test
	void trimsAndCollapsesRepeatedWhitespace() {
		assertEquals("John Michael Pandey", FullName.of("  John   Michael\t Pandey  ").value());
	}

	@Test
	void preservesUnicodeLettersAndCommas() {
		assertEquals("Müller Patel", FullName.of("Müller Patel").value());
		assertEquals("Patel, Sahil", FullName.of("Patel, Sahil").value());
		assertEquals("O'Brien, Seán", FullName.of("O'Brien, Seán").value());
	}

	@Test
	void measuresLengthInUnicodeCodePoints() {
		String min = "Li";
		String max = "a".repeat(100);
		String tooLong = "a".repeat(101);
		assertEquals(min, FullName.of(min).value());
		assertEquals(100, FullName.of(max).value().codePointCount(0, max.length()));
		assertThrows(DomainValidationException.class, () -> FullName.of("a"));
		assertThrows(DomainValidationException.class, () -> FullName.of(tooLong));
	}

	@Test
	void supplementaryLettersCountAsOneCodePointEach() {
		String deseretLetter = new String(Character.toChars(0x10400));
		String hundredCodePoints = deseretLetter.repeat(100);
		assertEquals(200, hundredCodePoints.length());
		assertEquals(hundredCodePoints, FullName.of(hundredCodePoints).value());
		assertThrows(DomainValidationException.class, () -> FullName.of(deseretLetter.repeat(101)));
	}

	@Test
	void rejectsDigitsSymbolsAndNullOrBlankInput() {
		assertThrows(DomainValidationException.class, () -> FullName.of("Ravi 123"));
		assertThrows(DomainValidationException.class, () -> FullName.of("Ravi@School"));
		assertThrows(DomainValidationException.class, () -> FullName.of(null));
		assertThrows(DomainValidationException.class, () -> FullName.of("   "));
	}
}
