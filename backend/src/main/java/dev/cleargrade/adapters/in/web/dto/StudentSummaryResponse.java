package dev.cleargrade.adapters.in.web.dto;

import java.math.BigDecimal;

import dev.cleargrade.application.model.StudentSummaryView;

/** Directory row with derived display percentage and grade. */
public record StudentSummaryResponse(
		String studentId,
		String fullName,
		int gradeLevel,
		String section,
		BigDecimal percentage,
		String grade) {

	public static StudentSummaryResponse from(StudentSummaryView view) {
		return new StudentSummaryResponse(view.studentId(), view.fullName(), view.gradeLevel(),
				view.section(), view.percentage(), view.grade().name());
	}
}
