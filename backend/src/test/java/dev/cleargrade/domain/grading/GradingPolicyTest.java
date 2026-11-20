package dev.cleargrade.domain.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.cleargrade.domain.DomainValidationException;
import dev.cleargrade.domain.student.DateOfBirth;
import dev.cleargrade.domain.student.EmailAddress;
import dev.cleargrade.domain.student.FullName;
import dev.cleargrade.domain.student.GradeLevel;
import dev.cleargrade.domain.student.Section;
import dev.cleargrade.domain.student.Student;
import dev.cleargrade.domain.student.StudentId;
import dev.cleargrade.domain.student.Subject;
import dev.cleargrade.domain.student.SubjectMarks;

class GradingPolicyTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);
	private final GradingPolicy policy = new GradingPolicy();

	private static SubjectMarks marks(String maths, String science, String english,
			String socialStudies, String secondLanguage) {
		Map<Subject, BigDecimal> values = new EnumMap<>(Subject.class);
		values.put(Subject.MATHS, new BigDecimal(maths));
		values.put(Subject.SCIENCE, new BigDecimal(science));
		values.put(Subject.ENGLISH, new BigDecimal(english));
		values.put(Subject.SOCIAL_STUDIES, new BigDecimal(socialStudies));
		values.put(Subject.SECOND_LANGUAGE, new BigDecimal(secondLanguage));
		return SubjectMarks.of(values);
	}

	private static Student student(SubjectMarks subjectMarks) {
		return new Student(
				StudentId.of("stu-001"),
				FullName.of("Test Student"),
				EmailAddress.of("test@example.com"),
				DateOfBirth.of(LocalDate.of(2001, 11, 19), FIXED_CLOCK),
				GradeLevel.of(9),
				Section.of("A"),
				subjectMarks);
	}

	@Test
	void exactPercentageIsTotalDividedByFive() {
		assertEquals(0, policy.exactPercentage(new BigDecimal("400")).compareTo(new BigDecimal("80")));
		assertEquals(0, policy.exactPercentage(marks("100", "100", "100", "100", "100"))
				.compareTo(new BigDecimal("100")));
		assertEquals(0, policy.exactPercentage(new BigDecimal("249.8"))
				.compareTo(new BigDecimal("49.96")));
	}

	@Test
	void gradeBandsUseExactPercentages() {
		assertEquals(Grade.A, policy.grade(new BigDecimal("80")));
		assertEquals(Grade.A, policy.grade(new BigDecimal("100")));
		assertEquals(Grade.B, policy.grade(new BigDecimal("79.999999")));
		assertEquals(Grade.B, policy.grade(new BigDecimal("70")));
		assertEquals(Grade.C, policy.grade(new BigDecimal("69.999999")));
		assertEquals(Grade.C, policy.grade(new BigDecimal("60")));
		assertEquals(Grade.D, policy.grade(new BigDecimal("59.999999")));
		assertEquals(Grade.D, policy.grade(new BigDecimal("50")));
		assertEquals(Grade.F, policy.grade(new BigDecimal("49.999999")));
		assertEquals(Grade.F, policy.grade(new BigDecimal("0")));
	}

	@Test
	void onlyDisplayedPercentageIsRoundedHalfUpToOneDecimal() {
		assertEquals(new BigDecimal("49.9"), policy.displayPercentage(new BigDecimal("49.94")));
		assertEquals(new BigDecimal("50.0"), policy.displayPercentage(new BigDecimal("49.95")));
		assertEquals(new BigDecimal("50.0"), policy.displayPercentage(new BigDecimal("49.96")));
		assertEquals(new BigDecimal("80.0"), policy.displayPercentage(new BigDecimal("79.96")));
		assertEquals(1, policy.displayPercentage(new BigDecimal("49.96")).scale());
	}

	@Test
	void gradeAndPassIgnoreDisplayRounding() {
		SubjectMarks almostFifty = marks("50", "50", "50", "50", "49.8");
		StudentOutcome outcome = policy.outcome(student(almostFifty));
		assertEquals(0, outcome.exactPercentage().compareTo(new BigDecimal("49.96")));
		assertEquals(new BigDecimal("50.0"), outcome.displayPercentage());
		assertEquals(Grade.F, outcome.grade());
		assertFalse(outcome.passed());

		SubjectMarks almostEighty = marks("100", "100", "100", "99.8", "0");
		StudentOutcome roundedToEighty = policy.outcome(student(almostEighty));
		assertEquals(new BigDecimal("80.0"), roundedToEighty.displayPercentage());
		assertEquals(Grade.B, roundedToEighty.grade());
	}

	@Test
	void passBoundaryIsExactFiftyPercent() {
		assertTrue(policy.passed(new BigDecimal("50")));
		assertTrue(policy.passed(new BigDecimal("50.000001")));
		assertFalse(policy.passed(new BigDecimal("49.999999")));
	}

	@Test
	void noPerSubjectPassThreshold() {
		StudentOutcome outcome = policy.outcome(student(marks("0", "100", "100", "100", "100")));
		assertEquals(0, outcome.total().compareTo(new BigDecimal("400")));
		assertEquals(Grade.A, outcome.grade());
		assertTrue(outcome.passed());
	}

	@Test
	void outcomeCarriesTheComputedValuesForTheStudent() {
		Student student = student(marks("60", "70", "80", "90", "100"));
		StudentOutcome outcome = policy.outcome(student);
		assertSame(student, outcome.student());
		assertEquals(0, outcome.total().compareTo(new BigDecimal("400")));
		assertEquals(0, outcome.exactPercentage().compareTo(new BigDecimal("80")));
		assertEquals(new BigDecimal("80.0"), outcome.displayPercentage());
		assertEquals(Grade.A, outcome.grade());
		assertTrue(outcome.passed());
	}

	@Test
	void rejectsNullInputs() {
		assertThrows(DomainValidationException.class, () -> policy.exactPercentage((SubjectMarks) null));
		assertThrows(DomainValidationException.class, () -> policy.exactPercentage((BigDecimal) null));
		assertThrows(DomainValidationException.class, () -> policy.grade(null));
		assertThrows(DomainValidationException.class, () -> policy.passed(null));
		assertThrows(DomainValidationException.class, () -> policy.displayPercentage(null));
		assertThrows(DomainValidationException.class, () -> policy.outcome(null));
	}
}
