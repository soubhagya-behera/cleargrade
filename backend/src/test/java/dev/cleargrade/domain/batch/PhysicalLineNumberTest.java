package dev.cleargrade.domain.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.cleargrade.domain.DomainValidationException;

class PhysicalLineNumberTest {

	@Test
	void headerIsLineOne() {
		assertEquals(1, PhysicalLineNumber.HEADER_LINE);
		assertEquals(1, PhysicalLineNumber.of(1).value());
	}

	@Test
	void rejectsZeroAndNegativeNumbers() {
		assertThrows(DomainValidationException.class, () -> PhysicalLineNumber.of(0));
		assertThrows(DomainValidationException.class, () -> PhysicalLineNumber.of(-1));
	}

	@Test
	void dataRowErrorsStartAtLineTwo() {
		assertEquals(2, PhysicalLineNumber.ofDataRow(2).value());
		assertEquals(7, PhysicalLineNumber.ofDataRow(7).value());
		assertThrows(DomainValidationException.class, () -> PhysicalLineNumber.ofDataRow(1));
		assertThrows(DomainValidationException.class, () -> PhysicalLineNumber.ofDataRow(0));
	}

	@Test
	void valueEqualityAndDisplay() {
		assertEquals(PhysicalLineNumber.of(2), PhysicalLineNumber.ofDataRow(2));
		assertEquals(PhysicalLineNumber.of(2).hashCode(), PhysicalLineNumber.ofDataRow(2).hashCode());
		assertNotEquals(PhysicalLineNumber.of(2), PhysicalLineNumber.of(3));
		assertEquals("2", PhysicalLineNumber.of(2).toString());
	}
}
