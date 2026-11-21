package dev.cleargrade.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import dev.cleargrade.domain.grading.Grade;

/** Full student report; marks keep their stored precision and pass follows D-07. */
public record StudentReportView(
		String studentId,
		String fullName,
		String email,
		LocalDate dateOfBirth,
		int gradeLevel,
		String section,
		Map<String, BigDecimal> marks,
		BigDecimal total,
		int maximum,
		BigDecimal percentage,
		Grade grade,
		boolean passed) {
}
