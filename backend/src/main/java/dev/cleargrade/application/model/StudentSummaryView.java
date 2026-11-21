package dev.cleargrade.application.model;

import java.math.BigDecimal;

import dev.cleargrade.domain.grading.Grade;

/** Student row for the paginated directory; grading is computed on read (D-07). */
public record StudentSummaryView(
		String studentId,
		String fullName,
		int gradeLevel,
		String section,
		BigDecimal percentage,
		Grade grade) {
}
