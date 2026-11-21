package dev.cleargrade.adapters.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import dev.cleargrade.application.model.StudentReportView;

/** Full student report; marks retain stored precision (C-3). */
public record StudentReportResponse(
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
		String grade,
		boolean passed) {

	public static StudentReportResponse from(StudentReportView view) {
		return new StudentReportResponse(view.studentId(), view.fullName(), view.email(),
				view.dateOfBirth(), view.gradeLevel(), view.section(), view.marks(),
				view.total(), view.maximum(), view.percentage(), view.grade().name(), view.passed());
	}
}
