package dev.cleargrade.adapters.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cleargrade.InsightFixtures;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.InsightEvidenceMetric;
import dev.cleargrade.application.model.InsightFindingId;
import dev.cleargrade.application.model.QualityAssessment;
import org.junit.jupiter.api.Test;

/** Semantic validation: providers may only word approved findings, and only in plain text. */
class InsightResponseParserTest {

	private static final QualityAssessment BATCH4 = InsightFixtures.syntheticBatch4();

	@Test
	void validPlainExplanationIsAcceptedAndJavaTextIsUnchanged() {
		ImportInsightReport report = InsightResponseParser.parse(InsightFixtures.validAiJson(BATCH4),
				BATCH4, ImportInsightReport.Engine.GEMINI, "gemini-3.8-flash", null);
		assertEquals(ImportInsightReport.Engine.GEMINI, report.engine());
		assertEquals(2, report.findings().size());
		assertEquals("36.0", report.findings().get(0).evidenceValue());
		assertEquals("HIGH", report.findings().get(0).severity());
		assertEquals(InsightEvidenceMetric.REJECTED_PERCENTAGE.name(),
				report.findings().get(0).evidenceMetric());
		assertEquals(BATCH4.approvedFindings().get(0).recommendedAction(),
				report.findings().get(0).recommendedAction());
		assertEquals(BATCH4.javaRecommendedActions(), report.recommendedActions());
		assertEquals(BATCH4.javaSummary(), report.summary());
		assertEquals(BATCH4.javaOverallAssessment(), report.overallAssessment());
		assertEquals(BATCH4.javaLimitations(), report.limitations());
	}

	@Test
	void screenshotMalformedExplanationIsRejected() {
		String json = InsightFixtures.malformedExplanationJson(BATCH4);
		assertEquals("OLLAMA_SEMANTIC_REJECTED", reject(json, ImportInsightReport.Engine.OLLAMA).code());
	}

	@Test
	void embeddedJavaLabelsAndDuplicatedBaselineAreRejected() {
		String embedded = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
				InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "explanation",
				"A substantial share of the file was rejected. javaRecommendedAction: correct the export.");
		assertEquals("GEMINI_SEMANTIC_REJECTED", reject(embedded, ImportInsightReport.Engine.GEMINI).code());

		String duplicated = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
				InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "explanation",
				"Measured rejected_percentage: 36.0. Measured rejected_percentage: 36.0 again.");
		assertEquals("GEMINI_SEMANTIC_REJECTED", reject(duplicated, ImportInsightReport.Engine.GEMINI).code());
	}

	@Test
	void jsonFragmentsAreRejected() {
		String json = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
				InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "explanation",
				"{\"findingId\":\"REJECTED_SHARE_ELEVATED\",\"explanation\":\"36.0 percent rejected\"}");
		assertEquals("GEMINI_SEMANTIC_REJECTED", reject(json, ImportInsightReport.Engine.GEMINI).code());
	}

	@Test
	void unapprovedAndDuplicateFindingsAreRejected() {
		String unknown = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
				InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "findingId", "NEW_COUNT");
		assertEquals("GEMINI_SEMANTIC_REJECTED", reject(unknown, ImportInsightReport.Engine.GEMINI).code());

		String duplicate = InsightFixtures.validAiJson(BATCH4).replace(
				"\"findingId\":\"" + InsightFindingId.MISSING_REQUIRED.name() + "\"",
				"\"findingId\":\"" + InsightFindingId.REJECTED_SHARE_ELEVATED.name() + "\"");
		assertEquals("OLLAMA_SEMANTIC_REJECTED", reject(duplicate, ImportInsightReport.Engine.OLLAMA).code());
	}

	@Test
	void providerCannotChangeMetricSeverityOrValueOrRecommendation() {
		for (String field : new String[] { "severity", "evidenceValue", "recommendedAction", "title" }) {
			String injected = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
					InsightFindingId.REJECTED_SHARE_ELEVATED.name(), field, "changed");
			assertEquals("PROVIDER_VALIDATION", reject(injected, ImportInsightReport.Engine.GEMINI).code());
		}
	}

	@Test
	void unexpectedRootFieldsAreRejected() {
		String withActions = InsightFixtures.validAiJson(BATCH4)
				.replace("{\"findings\":", "{\"recommendedActions\":[\"generic action\"],\"findings\":");
		assertEquals("PROVIDER_VALIDATION", reject(withActions, ImportInsightReport.Engine.GEMINI).code());
		assertEquals("MALFORMED_JSON", reject("{not-json", ImportInsightReport.Engine.GEMINI).code());
	}

	@Test
	void groundedNaturalDenominatorIsAccepted() {
		String json = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
				InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "explanation",
				"9 of 25 rows were rejected (36.0%).");
		assertTrue(InsightResponseParser.parse(json, BATCH4, ImportInsightReport.Engine.OLLAMA,
				"llama3.2:1b", null).findings().get(0).explanation().contains("9 of 25 rows"));
	}

	@Test
	void wrongDenominatorFormsAndMultipleExpressionsAreRejected() {
		for (String explanation : new String[] {
				"The 36.0% share means 9 out of 16 rows were rejected.",
				"The 36.0% share means 9 of 16 rows were rejected.",
				"The 36.0% share means 9/16 rows were rejected.",
				"9 of 25 rows were rejected (36.0%) and 9 out of 16 were imported." }) {
			String json = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
					InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "explanation", explanation);
			assertEquals("GEMINI_SEMANTIC_REJECTED", reject(json, ImportInsightReport.Engine.GEMINI).code());
		}
	}

	@Test
	void equivalentDecimalNotationIsAccepted() {
		QualityAssessment mixed = InsightFixtures.mixed();
		String json = InsightFixtures.withFindingField(InsightFixtures.validAiJson(mixed),
				InsightFindingId.MULTIPLE_ERRORS_PER_REJECTED_ROW.name(), "explanation",
				"The 1.1 measured problems per row exceed one problem per rejected row.");
		ImportInsightReport report = InsightResponseParser.parse(json, mixed,
				ImportInsightReport.Engine.GEMINI, "model", null);
		assertTrue(report.findings().stream().anyMatch(finding ->
				InsightEvidenceMetric.ERRORS_PER_REJECTED_ROW.name().equals(finding.evidenceMetric())
						&& finding.explanation().contains("1.1")));
	}

	@Test
	void invalidNumericAndBaselineClaimsAreRejected() {
		String baseline = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
				InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "explanation",
				"The 36.0% share is above the expected 30.0% baseline.");
		assertEquals("GEMINI_SEMANTIC_REJECTED", reject(baseline, ImportInsightReport.Engine.GEMINI).code());

		String percentage = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
				InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "explanation",
				"The rejected share is 36.0%, which is worse than 54.2% elsewhere.");
		assertEquals("OLLAMA_SEMANTIC_REJECTED", reject(percentage, ImportInsightReport.Engine.OLLAMA).code());
	}

	@Test
	void genericAndIrrelevantExplanationsAreRejected() {
		String generic = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
				InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "explanation",
				"Review and adjust import settings to improve this count of 36.0.");
		assertEquals("GEMINI_SEMANTIC_REJECTED", reject(generic, ImportInsightReport.Engine.GEMINI).code());

		String irrelevant = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
				InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "explanation",
				"The weather forecast for 36.0 days is quite pleasant this week.");
		assertEquals("GEMINI_SEMANTIC_REJECTED", reject(irrelevant, ImportInsightReport.Engine.GEMINI).code());
	}

	@Test
	void fallbackReasonTelemetryAndFactsArePreserved() {
		var telemetry = dev.cleargrade.application.model.InsightTelemetry.unavailable(
				"test-model", "HTTP_500", "completed", 123L);
		ImportInsightReport report = InsightResponseParser.parse(InsightFixtures.validAiJson(BATCH4),
				BATCH4, ImportInsightReport.Engine.OLLAMA, "llama3.2:1b",
				"GEMINI:GEMINI_SERVER", telemetry);
		assertEquals("GEMINI:GEMINI_SERVER", report.fallbackReason());
		assertEquals("HTTP_500", report.telemetry().providerStatus());
		assertEquals(25, report.measuredFacts().rowsRead());
		assertFalse(report.findings().get(0).explanation().contains("javaExplanation"));
		assertTrue(report.providerFailures().isEmpty());
	}

	private static InsightProviderException reject(String json, ImportInsightReport.Engine engine) {
		return assertThrows(InsightProviderException.class,
				() -> InsightResponseParser.parse(json, BATCH4, engine, "model", null));
	}
}
