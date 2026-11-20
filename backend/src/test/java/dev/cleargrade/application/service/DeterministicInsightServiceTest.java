package dev.cleargrade.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import dev.cleargrade.application.model.ImportInsightFinding;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.InsightEvidenceMetric;
import org.junit.jupiter.api.Test;

/** Rule-based engine behaviour: no network, no key, no AI. */
class DeterministicInsightServiceTest {

	private final DeterministicInsightService engine = new DeterministicInsightService();

	@Test
	void analysesAllMeasuredCategoriesWithEvidenceBackedRules() {
		ImportQualityContext context = mixedContext();
		ImportInsightReport report = this.engine.analyse(context);

		assertEquals(ImportInsightReport.Engine.RULE_BASED, report.engine());
		assertTrue(report.limitations().startsWith("Rule-based analysis uses aggregate counters only."));
		assertEquals(7, report.findings().size());
		assertEquals("HIGH", report.findings().get(0).severity());
		assertEquals(InsightEvidenceMetric.REJECTED_PERCENTAGE.name(),
				report.findings().get(0).evidenceMetric());
		for (ImportInsightFinding finding : report.findings()) {
			InsightEvidenceMetric metric = InsightEvidenceMetric.valueOf(finding.evidenceMetric());
			assertEquals(context.evidenceValue(metric), finding.evidenceValue());
			assertTrue(finding.explanation().contains(finding.evidenceValue()));
		}
		assertEquals(report.findings().size(), report.recommendedActions().size());
		for (int index = 0; index < report.recommendedActions().size(); index++) {
			ImportInsightFinding finding = report.findings().get(index);
			assertTrue(report.recommendedActions().get(index).contains("Evidence: "
					+ finding.evidenceMetric() + " = " + finding.evidenceValue()));
		}
	}

	@Test
	void cleanImportProducesNoConcerns() {
		ImportQualityContext context = new ImportQualityContext(10, 10, 0, 10, 0, 0, 0, Map.of());
		ImportInsightReport report = this.engine.analyse(context);

		assertTrue(report.findings().isEmpty());
		assertTrue(report.recommendedActions().isEmpty());
		assertTrue(report.overallAssessment().contains("No rule-based quality concerns"));
	}

	@Test
	void sameContextAlwaysProducesSameReport() {
		assertEquals(this.engine.analyse(mixedContext()), this.engine.analyse(mixedContext()));
	}

	@Test
	void thresholdsAreGroundedInActualCounters() {
		ImportQualityContext oneError = new ImportQualityContext(100, 99, 1, 99, 0, 0, 1,
				Map.of("MISSING_REQUIRED", 1));
		ImportInsightReport report = this.engine.analyse(oneError);

		assertEquals(1, report.findings().size());
		assertEquals("MEDIUM", report.findings().get(0).severity());
		assertEquals("1", report.findings().get(0).evidenceValue());
		assertFalse(report.summary().isEmpty());
		assertTrue(report.summary().contains("1.0%"));
	}

	private static ImportQualityContext mixedContext() {
		return new ImportQualityContext(100, 80, 20, 80, 0, 0, 22,
				Map.of("MISSING_REQUIRED", 5, "INVALID_FORMAT", 4, "OUT_OF_RANGE", 3,
						"DUPLICATE_ID", 5, "WRONG_FIELD_COUNT", 5));
	}
}
