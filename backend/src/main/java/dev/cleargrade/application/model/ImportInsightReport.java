package dev.cleargrade.application.model;

import java.util.List;

/** Insight report: measured facts (Java) are kept separate from interpretation and advice. */
public record ImportInsightReport(
		Engine engine,
		String engineModel,
		String fallbackReason,
		boolean cached,
		ImportQualityContext measuredFacts,
		String summary,
		String overallAssessment,
		List<ImportInsightFinding> findings,
		List<String> recommendedActions,
		String limitations,
		String contextFingerprint) {

	public enum Engine {
		GEMINI, OLLAMA, RULE_BASED
	}
}
