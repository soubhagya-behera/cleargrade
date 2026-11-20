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

	public StudentOutcome {
		if (student == null || total == null || exactPercentage == null || displayPercentage == null || grade == null) {
			throw new DomainValidationException("Student outcome fields are required");
		}
	}
}
