package dev.cleargrade.adapters.in.web.dto;

import java.util.List;

import dev.cleargrade.application.model.ImportInsightReport;

/** Insight response with separate measured facts, interpretation and advisory actions. */
public record InsightResponse(
		long batchId,
		String engine,
		String engineModel,
		String fallbackReason,
		boolean cached,
		MeasuredMetricsResponse measured,
		String summary,
		String overallAssessment,
		List<InsightFindingResponse> findings,
		List<String> recommendedActions,
		String limitations,
		boolean advisoryOnly) {

	public static InsightResponse from(long batchId, ImportInsightReport report) {
		return new InsightResponse(batchId, report.engine().name(), report.engineModel(), report.fallbackReason(),
				report.cached(), MeasuredMetricsResponse.from(report.measuredFacts()),
				report.summary(), report.overallAssessment(),
				report.findings().stream().map(InsightFindingResponse::from).toList(),
				report.recommendedActions(), report.limitations(), true);
	}
}
