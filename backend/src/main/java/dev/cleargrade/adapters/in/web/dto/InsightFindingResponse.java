package dev.cleargrade.adapters.in.web.dto;

import java.util.List;

import dev.cleargrade.application.model.ImportInsightFinding;
import dev.cleargrade.application.model.ImportInsightReport;

/** Finding DTO: evidence value is visibly separate from interpretation. */
public record InsightFindingResponse(
		String title,
		String severity,
		String evidenceMetric,
		String evidenceValue,
		String explanation,
		String recommendedAction) {

	public static InsightFindingResponse from(ImportInsightFinding finding) {
		return new InsightFindingResponse(finding.title(), finding.severity(), finding.evidenceMetric(),
				finding.evidenceValue(), finding.explanation(), finding.recommendedAction());
	}
}
