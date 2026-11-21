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
		String contextFingerprint,
		InsightTelemetry telemetry) {

	public ImportInsightReport(Engine engine, String engineModel, String fallbackReason, boolean cached,
			ImportQualityContext measuredFacts, String summary, String overallAssessment,
			List<ImportInsightFinding> findings, List<String> recommendedActions, String limitations,
			String contextFingerprint) {
		this(engine, engineModel, fallbackReason, cached, measuredFacts, summary, overallAssessment,
				findings, recommendedActions, limitations, contextFingerprint,
				InsightTelemetry.unavailable(engineModel, "NOT_RECORDED", null, 0L));
	}

	public enum Engine {
		GEMINI, OLLAMA, RULE_BASED
	}
}
