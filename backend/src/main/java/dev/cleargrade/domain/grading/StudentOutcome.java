package dev.cleargrade.domain.grading;

import java.math.BigDecimal;

import dev.cleargrade.domain.DomainValidationException;
import dev.cleargrade.domain.student.Student;

/** Computed, non-persisted result for one student (D-07, D-11). */
public record StudentOutcome(
		Student student,
		BigDecimal total,
		BigDecimal exactPercentage,
		BigDecimal displayPercentage,
		Grade grade,
		boolean passed) {

	private static final GradingPolicy POLICY = new GradingPolicy();

	public StudentOutcome {
		if (student == null || total == null || exactPercentage == null || displayPercentage == null || grade == null) {
			throw new DomainValidationException("Student outcome fields are required");
		}
		if (total.compareTo(student.marks().total()) != 0
				|| exactPercentage.compareTo(POLICY.exactPercentage(total)) != 0
				|| displayPercentage.scale() != 1
				|| displayPercentage.compareTo(POLICY.displayPercentage(exactPercentage)) != 0
				|| grade != POLICY.grade(exactPercentage)
				|| passed != POLICY.passed(exactPercentage)) {
			throw new DomainValidationException("Student outcome values are inconsistent with GradingPolicy");
		}
	}
}
