package dev.cleargrade.application.model;

import java.util.List;

/** Java-owned assessment: the only source of findings, severity, evidence and advice. */
public record QualityAssessment(
		ImportQualityContext context,
		List<ApprovedInsightFinding> approvedFindings,
		String javaSummary,
		String javaOverallAssessment,
		List<String> javaRecommendedActions,
		String javaLimitations) {

	public QualityAssessment {
		approvedFindings = List.copyOf(approvedFindings);
		javaRecommendedActions = List.copyOf(javaRecommendedActions);
	}
}
