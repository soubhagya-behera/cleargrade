package dev.cleargrade.domain.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.cleargrade.domain.DomainValidationException;

class SubjectMarksTest {

	private static Map<Subject, BigDecimal> allMarks(String value) {
		Map<Subject, BigDecimal> marks = new EnumMap<>(Subject.class);
		for (Subject subject : Subject.values()) {
			marks.put(subject, new BigDecimal(value));
		}
		return marks;
	}

	@Test
	void requiresExactlyTheFiveConfiguredSubjects() {
		assertEquals(5, Subject.values().length);
		assertEquals(new BigDecimal("500"), SubjectMarks.of(allMarks("100")).total());

		Map<Subject, BigDecimal> missing = allMarks("50");
		missing.remove(Subject.ENGLISH);
		assertThrows(DomainValidationException.class, () -> SubjectMarks.of(missing));
		assertThrows(DomainValidationException.class, () -> SubjectMarks.of(null));
	}

	@Test
	void acceptsInclusiveNumericBoundariesUsingCompareTo() {
		assertEquals(new BigDecimal("0"), SubjectMarks.of(allMarks("0")).total());
		assertEquals(0, SubjectMarks.of(allMarks("100.00")).total().compareTo(new BigDecimal("500")));
		assertThrows(DomainValidationException.class, () -> SubjectMarks.of(allMarks("-0.01")));
		assertThrows(DomainValidationException.class, () -> SubjectMarks.of(allMarks("100.01")));
	}

	@Test
	void preservesHighPrecisionWithoutAFractionalDigitCap() {
		String precise = "99.123456789012345678901234567890123456789012345";
		SubjectMarks marks = SubjectMarks.of(allMarks(precise));
		for (Subject subject : Subject.values()) {
			assertEquals(new BigDecimal(precise), marks.mark(subject));
		}
		assertEquals(new BigDecimal(precise).multiply(BigDecimal.valueOf(5)), marks.total());
	}

	@Test
	void doesNotRoundOrRescaleSubmittedMarks() {
		Map<Subject, BigDecimal> values = allMarks("20.00");
		values.put(Subject.MATHS, new BigDecimal("33.100"));
		SubjectMarks marks = SubjectMarks.of(values);
		assertEquals(new BigDecimal("33.100"), marks.mark(Subject.MATHS));
		assertEquals(new BigDecimal("113.100"), marks.total());
	}

	@Test
	void exposesAnUnmodifiableSnapshot() {
		SubjectMarks marks = SubjectMarks.of(allMarks("50"));
		assertThrows(UnsupportedOperationException.class,
				() -> marks.marks().put(Subject.MATHS, BigDecimal.ONE));
	}
}
