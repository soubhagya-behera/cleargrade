package dev.cleargrade.application.model;

/** One interpretation of a measured metric. Values are advisory only. */
public record ImportInsightFinding(
		String title,
		String severity,
		String evidenceMetric,
		String evidenceValue,
		String explanation,
		String recommendedAction) {
}
