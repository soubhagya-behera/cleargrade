package dev.cleargrade.application.model;

/**
 * A finding selected and fully specified by Java. AI providers may only explain it and improve the
 * wording of its recommendation; they may not change any of these fields.
 */
public record ApprovedInsightFinding(
		InsightFindingId id,
		String title,
		String severity,
		InsightEvidenceMetric evidenceMetric,
		String evidenceValue,
		String explanation,
		String recommendedAction) {
}
