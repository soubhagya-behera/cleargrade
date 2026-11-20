package dev.cleargrade.domain.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.cleargrade.domain.DomainValidationException;

class GradeLevelAndSectionTest {

	@Test
	void gradeLevelAcceptsWholeNumbersOneToTwelve() {
		assertEquals(1, GradeLevel.of(1).value());
		assertEquals(12, GradeLevel.of(12).value());
		assertEquals(9, GradeLevel.of("09").value());
		assertThrows(DomainValidationException.class, () -> GradeLevel.of(0));
		assertThrows(DomainValidationException.class, () -> GradeLevel.of(13));
	}

	@Test
	void gradeLevelRejectsNonNumericAndNullInput() {
		assertThrows(DomainValidationException.class, () -> GradeLevel.of("7.5"));
		assertThrows(DomainValidationException.class, () -> GradeLevel.of("zero"));
		assertThrows(DomainValidationException.class, () -> GradeLevel.of((String) null));
	}

	@Test
	void sectionNormalizesToUppercase() {
		assertEquals('A', Section.of('a').value());
		assertEquals('F', Section.of(" f ").value());
		assertEquals(Section.of('c'), Section.of("C"));
	}

	@Test
	void sectionRejectsAnythingOutsideAToF() {
		assertThrows(DomainValidationException.class, () -> Section.of('G'));
		assertThrows(DomainValidationException.class, () -> Section.of('0'));
		assertThrows(DomainValidationException.class, () -> Section.of(""));
		assertThrows(DomainValidationException.class, () -> Section.of("AB"));
		assertThrows(DomainValidationException.class, () -> Section.of((String) null));
	}
}
