package dev.cleargrade.adapters.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import org.junit.jupiter.api.Test;

class InsightResponseParserTest {

	private static final ImportQualityContext CONTEXT =
			new ImportQualityContext(100, 80, 20, 80, 0, 0, 20, Map.of("DUPLICATE_ID", 4));
	private static final String VALID = """
			{"summary":"summary","overallAssessment":"assessment",
			 "findings":[{"title":"title","severity":"LOW","evidenceMetric":"DUPLICATE_ID_COUNT",
			 "evidenceValue":"4","explanation":"explanation","recommendedAction":"action"}],
			 "recommendedActions":["action"],"limitations":"limits"}
			""";

	@Test
	void validStructuredResponseIsAccepted() {
		ImportInsightReport report = InsightResponseParser.parse(VALID, CONTEXT,
				ImportInsightReport.Engine.GEMINI, "gemini-3.8-flash", null);
		assertEquals(ImportInsightReport.Engine.GEMINI, report.engine());
		assertEquals("4", report.findings().get(0).evidenceValue());
	}

	@Test
	void evidenceValueMustExactlyMatchBackendContext() {
		String altered = VALID.replace("\"evidenceValue\":\"4\"", "\"evidenceValue\":\"999\"");
		assertThrows(InsightProviderException.class, () -> InsightResponseParser.parse(altered, CONTEXT,
				ImportInsightReport.Engine.GEMINI, "model", null));
	}

	@Test
	void unexpectedFieldsAreRejected() {
		String extra = VALID.replace("\"limitations\":\"limits\"",
				"\"limitations\":\"limits\",\"imported\":999999");
		assertThrows(InsightProviderException.class, () -> InsightResponseParser.parse(extra, CONTEXT,
				ImportInsightReport.Engine.GEMINI, "model", null));
	}

	@Test
	void malformedJsonAndBadSeverityAreRejected() {
		assertThrows(InsightProviderException.class, () -> InsightResponseParser.parse("{not-json", CONTEXT,
				ImportInsightReport.Engine.GEMINI, "model", null));
		assertThrows(InsightProviderException.class, () -> InsightResponseParser.parse(
				VALID.replace("\"severity\":\"LOW\"", "\"severity\":\"CRITICAL\""), CONTEXT,
				ImportInsightReport.Engine.OLLAMA, "model", null));
	}

	@Test
	void unknownEvidenceMetricIsRejected() {
		assertThrows(InsightProviderException.class, () -> InsightResponseParser.parse(
				VALID.replace("\"evidenceMetric\":\"DUPLICATE_ID_COUNT\"", "\"evidenceMetric\":\"SECRET_METRIC\""),
				CONTEXT, ImportInsightReport.Engine.GEMINI, "model", null));
	}

	@Test
	void missingAndOversizedFieldsAreRejected() {
		assertThrows(InsightProviderException.class, () -> InsightResponseParser.parse(
				VALID.replace("\"limitations\":\"limits\"", ""), CONTEXT,
				ImportInsightReport.Engine.GEMINI, "model", null));
		assertThrows(InsightProviderException.class, () -> InsightResponseParser.parse(
				VALID.replace("\"summary\":\"summary\"", "\"summary\":\"" + "x".repeat(601) + "\""), CONTEXT,
				ImportInsightReport.Engine.GEMINI, "model", null));
	}

	@Test
	void findingAndActionCountsAreBounded() {
		String finding = "{\"title\":\"title\",\"severity\":\"LOW\",\"evidenceMetric\":\"DUPLICATE_ID_COUNT\","
				+ "\"evidenceValue\":\"4\",\"explanation\":\"explanation\",\"recommendedAction\":\"action\"}";
		String nineFindings = "[" + (finding + ",").repeat(8) + finding + "]";
		assertThrows(InsightProviderException.class, () -> InsightResponseParser.parse(
				withArray(VALID, "findings", nineFindings), CONTEXT,
				ImportInsightReport.Engine.GEMINI, "model", null));
		String nineActions = "[" + ("\"action\",".repeat(8)) + "\"action\"]";
		assertThrows(InsightProviderException.class, () -> InsightResponseParser.parse(
				withArray(VALID, "recommendedActions", nineActions), CONTEXT,
				ImportInsightReport.Engine.GEMINI, "model", null));
	}

	@Test
	void blockedIdentifierMarkersInModelTextAreRejected() {
		assertThrows(InsightProviderException.class, () -> InsightResponseParser.parse(
				VALID.replace("\"summary\":\"summary\"", "\"summary\":\"Student alice@example.com\""), CONTEXT,
				ImportInsightReport.Engine.GEMINI, "model", null));
		assertThrows(InsightProviderException.class, () -> InsightResponseParser.parse(
				VALID.replace("\"explanation\":\"explanation\"", "\"explanation\":\"PII_SENTINEL explanation\""), CONTEXT,
				ImportInsightReport.Engine.OLLAMA, "model", null));
	}

	private static String withArray(String json, String field, String replacement) {
		try {
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
			((com.fasterxml.jackson.databind.node.ObjectNode) root).set(field, mapper.readTree(replacement));
			return mapper.writeValueAsString(root);
		}
		catch (Exception error) {
			throw new IllegalStateException(error);
		}
	}

	@Test
	void engineModelAndFallbackReasonArePreserved() {
		ImportInsightReport report = InsightResponseParser.parse(VALID, CONTEXT,
				ImportInsightReport.Engine.OLLAMA, "ollama-model", "GEMINI_UNAVAILABLE");
		assertEquals(ImportInsightReport.Engine.OLLAMA, report.engine());
		assertEquals("ollama-model", report.engineModel());
		assertEquals("GEMINI_UNAVAILABLE", report.fallbackReason());
		assertFalse(report.cached());
	}
}
