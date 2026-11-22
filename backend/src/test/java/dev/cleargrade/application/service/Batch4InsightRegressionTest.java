package dev.cleargrade.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.cleargrade.InsightFixtures;
import dev.cleargrade.adapters.out.ai.InsightPromptFactory;
import dev.cleargrade.adapters.out.ai.InsightResponseParser;
import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ApprovedInsightFinding;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.InsightEvidenceMetric;
import dev.cleargrade.application.model.InsightFindingId;
import dev.cleargrade.application.model.InsightTelemetry;
import dev.cleargrade.application.model.QualityAssessment;
import dev.cleargrade.application.port.out.ImportInsightProvider;
import org.junit.jupiter.api.Test;

/** Explicit regression coverage for the exact real batch-4 error distribution. */
class Batch4InsightRegressionTest {

	private static final QualityAssessment BATCH4 = InsightFixtures.realBatch4();

	@Test
	void measuredCountersAndCanonicalEvidenceAreCorrect() {
		ImportQualityContext context = BATCH4.context();
		assertEquals(25, context.rowsRead());
		assertEquals(16, context.importedCount());
		assertEquals(9, context.rejectedCount());
		assertEquals(9, context.errorCount());
		assertEquals("64.0", context.importedPercentage());
		assertEquals("36.0", context.rejectedPercentage());
		assertEquals("1.00", context.errorsPerRejectedRow());
		assertEquals("16", context.evidenceValue(InsightEvidenceMetric.NEW_COUNT));
		assertEquals("0", context.evidenceValue(InsightEvidenceMetric.CHANGED_COUNT));
		assertEquals("0", context.evidenceValue(InsightEvidenceMetric.UNCHANGED_COUNT));
		assertEquals(2, context.errorCount(dev.cleargrade.domain.batch.RowErrorCode.MISSING_REQUIRED));
		assertEquals(3, context.errorCount(dev.cleargrade.domain.batch.RowErrorCode.INVALID_FORMAT));
		assertEquals(2, context.errorCount(dev.cleargrade.domain.batch.RowErrorCode.OUT_OF_RANGE));
		assertEquals(1, context.errorCount(dev.cleargrade.domain.batch.RowErrorCode.DUPLICATE_ID));
		assertEquals(1, context.errorCount(dev.cleargrade.domain.batch.RowErrorCode.WRONG_FIELD_COUNT));
	}

	@Test
	void javaSelectsExactlySixFindingsWithPolicySeveritiesAndEvidence() {
		List<ApprovedInsightFinding> findings = BATCH4.approvedFindings();
		assertEquals(6, findings.size());
		assertEquals(List.of(
				InsightFindingId.REJECTED_SHARE_ELEVATED,
				InsightFindingId.DUPLICATE_ID,
				InsightFindingId.MISSING_REQUIRED,
				InsightFindingId.WRONG_FIELD_COUNT,
				InsightFindingId.INVALID_FORMAT,
				InsightFindingId.OUT_OF_RANGE),
				findings.stream().map(ApprovedInsightFinding::id).toList());
		assertEquals(6, findings.stream().map(f -> f.id().name()).distinct().count());
		assertFalse(findings.stream().anyMatch(f -> f.id() == InsightFindingId.MULTIPLE_ERRORS_PER_REJECTED_ROW));

		Map<InsightFindingId, ApprovedInsightFinding> byId = new LinkedHashMap<>();
		findings.forEach(finding -> byId.put(finding.id(), finding));
		assertEquals("HIGH", byId.get(InsightFindingId.REJECTED_SHARE_ELEVATED).severity());
		assertEquals("36.0", byId.get(InsightFindingId.REJECTED_SHARE_ELEVATED).evidenceValue());
		assertEquals("MEDIUM", byId.get(InsightFindingId.DUPLICATE_ID).severity());
		assertEquals("1", byId.get(InsightFindingId.DUPLICATE_ID).evidenceValue());
		assertEquals("MEDIUM", byId.get(InsightFindingId.MISSING_REQUIRED).severity());
		assertEquals("2", byId.get(InsightFindingId.MISSING_REQUIRED).evidenceValue());
		assertEquals("MEDIUM", byId.get(InsightFindingId.WRONG_FIELD_COUNT).severity());
		assertEquals("1", byId.get(InsightFindingId.WRONG_FIELD_COUNT).evidenceValue());
		assertEquals("LOW", byId.get(InsightFindingId.INVALID_FORMAT).severity());
		assertEquals("3", byId.get(InsightFindingId.INVALID_FORMAT).evidenceValue());
		assertEquals("LOW", byId.get(InsightFindingId.OUT_OF_RANGE).severity());
		assertEquals("2", byId.get(InsightFindingId.OUT_OF_RANGE).evidenceValue());

		assertEquals("Review the rejected rows by error code and correct the source export.",
				byId.get(InsightFindingId.REJECTED_SHARE_ELEVATED).recommendedAction());
		assertEquals("De-duplicate student IDs before the next upload.",
				byId.get(InsightFindingId.DUPLICATE_ID).recommendedAction());
		assertEquals("Populate all required columns in the source export.",
				byId.get(InsightFindingId.MISSING_REQUIRED).recommendedAction());
		assertEquals("Check delimiters, quoting and trailing columns in the source export.",
				byId.get(InsightFindingId.WRONG_FIELD_COUNT).recommendedAction());
		assertEquals("Normalise dates, identifiers and email formatting before export.",
				byId.get(InsightFindingId.INVALID_FORMAT).recommendedAction());
		assertEquals("Verify marks and age bounds in the source system.",
				byId.get(InsightFindingId.OUT_OF_RANGE).recommendedAction());
		assertFalse(BATCH4.javaRecommendedActions().stream()
				.anyMatch(action -> action.toLowerCase().contains("adjust import settings")));
	}

	@Test
	void operationalCountersAreContextOnlyAndNeverBecomeFindings() {
		assertTrue(BATCH4.approvedFindings().stream().noneMatch(finding ->
				finding.evidenceMetric().name().contains("NEW_COUNT")
						|| finding.evidenceMetric().name().contains("CHANGED_COUNT")
						|| finding.evidenceMetric().name().contains("UNCHANGED_COUNT")
						|| finding.evidenceMetric() == InsightEvidenceMetric.ROWS_READ));
	}

	@Test
	void promptExposesOnlyJavaApprovedFindings() {
		String prompt = InsightPromptFactory.prompt(BATCH4, 12000);
		assertTrue(prompt.contains("\"findingId\":\"REJECTED_SHARE_ELEVATED\""));
		assertTrue(prompt.contains("\"evidenceValue\":\"36.0\""));
		assertTrue(prompt.contains("approvedFindings"));
		assertFalse(prompt.contains("\"findingId\":\"NEW_COUNT\""));
		assertFalse(prompt.contains("\"findingId\":\"MULTIPLE_ERRORS_PER_REJECTED_ROW\""));
	}

	@Test
	void aiReportCannotClaimTheWrongDenominatorOrGenericAction() {
		ImportInsightReport report = InsightResponseParser.parse(InsightFixtures.validAiJson(BATCH4),
				BATCH4, ImportInsightReport.Engine.OLLAMA, "llama3.2:1b", null);
		assertFalse(report.findings().stream().anyMatch(f -> f.explanation().contains("9 out of 16")));
		assertFalse(report.recommendedActions().stream()
				.anyMatch(action -> action.toLowerCase().contains("adjust import settings")));

		var wrongDenominator = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
				InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "explanation",
				"The 36.0% share means 9 out of 16 rows were rejected.");
		assertEquals("OLLAMA_SEMANTIC_REJECTED", assertThrows(InsightProviderException.class,
				() -> InsightResponseParser.parse(wrongDenominator, BATCH4,
						ImportInsightReport.Engine.OLLAMA, "llama3.2:1b", null)).code());
	}

	@Test
	void deterministicFallbackGroundsEveryFindingAndKeepsJavaActions() {
		ImportInsightReport report = new DeterministicInsightService().reportFrom(BATCH4);
		assertEquals(ImportInsightReport.Engine.RULE_BASED, report.engine());
		assertEquals(6, report.findings().size());
		assertEquals(BATCH4.approvedFindings().stream().map(ApprovedInsightFinding::id).map(Enum::name).toList(),
				report.findings().stream().map(finding -> finding.evidenceMetric()).toList().stream()
						.map(metric -> switch (metric) {
							case "REJECTED_PERCENTAGE" -> "REJECTED_SHARE_ELEVATED";
							case "DUPLICATE_ID_COUNT" -> "DUPLICATE_ID";
							case "MISSING_REQUIRED_COUNT" -> "MISSING_REQUIRED";
							case "WRONG_FIELD_COUNT_COUNT" -> "WRONG_FIELD_COUNT";
							case "INVALID_FORMAT_COUNT" -> "INVALID_FORMAT";
							case "OUT_OF_RANGE_COUNT" -> "OUT_OF_RANGE";
							default -> metric;
						}).toList());
		assertTrue(report.findings().stream().allMatch(finding -> finding.explanation()
				.contains(finding.evidenceValue())));
		assertEquals(BATCH4.javaRecommendedActions(), report.recommendedActions());
		assertTrue(report.summary().contains("25"));
		assertTrue(report.summary().contains("16"));
		assertTrue(report.summary().contains("9"));
	}

	@Test
	void healthyImportHasNoManufacturedFindings() {
		QualityAssessment clean = InsightFixtures.assessment(new ImportQualityContext(
				25, 25, 0, 25, 0, 0, 0, Map.of()));
		assertTrue(clean.approvedFindings().isEmpty());
		assertTrue(new DeterministicInsightService().reportFrom(clean).findings().isEmpty());
	}

	@Test
	void invalidGeminiOutputFallsBackToOllamaAndInvalidOllamaOutputFallsBackToRules() {
		InsightProperties properties = new InsightProperties();
		properties.getGemini().setApiKey("test-placeholder-not-a-secret");
		properties.getOllama().setBaseUrl("http://127.0.0.1:11434");
		properties.getOllama().setModel("llama3.2:1b");

		String badOutput = InsightFixtures.withFindingField(InsightFixtures.validAiJson(BATCH4),
				InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "explanation",
				"The 36.0% share is above the expected 30.0%.");
		InsightTelemetry geminiTelemetry = InsightTelemetry.unavailable(
				"gemini-3.8-flash", "HTTP_200", "completed", 40L);
		InsightProviderException rawRejection = assertThrows(InsightProviderException.class,
				() -> InsightResponseParser.parse(badOutput, BATCH4,
						ImportInsightReport.Engine.GEMINI, "gemini-3.8-flash", null, geminiTelemetry));
		InsightProviderException geminiRejection = new InsightProviderException(rawRejection.getMessage(),
				rawRejection.category(), rawRejection.code(), null, geminiTelemetry);

		ImportInsightProvider gemini = mock(ImportInsightProvider.class);
		when(gemini.engine()).thenReturn(ImportInsightReport.Engine.GEMINI);
		when(gemini.modelVersion()).thenReturn("gemini-3.8-flash");
		when(gemini.available()).thenReturn(true);
		when(gemini.analyse(any())).thenThrow(geminiRejection);

		ImportInsightProvider ollama = mock(ImportInsightProvider.class);
		when(ollama.engine()).thenReturn(ImportInsightReport.Engine.OLLAMA);
		when(ollama.modelVersion()).thenReturn("llama3.2:1b");
		when(ollama.available()).thenReturn(true);
		when(ollama.analyse(any())).thenAnswer(invocation -> InsightResponseParser.parse(
				InsightFixtures.validAiJson(BATCH4), BATCH4, ImportInsightReport.Engine.OLLAMA,
				"llama3.2:1b", null));

		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
				new DeterministicInsightService(), properties);
		ImportInsightReport report = router.analyse(BATCH4);

		assertEquals(ImportInsightReport.Engine.OLLAMA, report.engine());
		assertEquals("GEMINI:GEMINI_SEMANTIC_REJECTED", report.fallbackReason());
		assertEquals("HTTP_200", report.providerFailures().get(0).telemetry().providerStatus());

		when(ollama.analyse(any())).thenThrow(new InsightProviderException("invalid",
				InsightProviderException.Category.FALLBACK, "OLLAMA_SEMANTIC_REJECTED", null, null));
		ImportInsightReport ruleBased = router.analyse(BATCH4);
		assertEquals(ImportInsightReport.Engine.RULE_BASED, ruleBased.engine());
		assertEquals("GEMINI:GEMINI_SEMANTIC_REJECTED;OLLAMA:OLLAMA_SEMANTIC_REJECTED",
				ruleBased.fallbackReason());
	}
}
