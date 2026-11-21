package dev.cleargrade.adapters.out.ai;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.InsightEvidenceMetric;

/** Builds the only payload providers receive: trusted aggregates and static instructions. */
public final class InsightPromptFactory {

	private static final String INSTRUCTION = """
			Analyse the aggregate import metrics only. Return one compact JSON object with exactly these keys:
			summary (<=400 chars), overallAssessment (<=600), findings (max 5), recommendedActions (max 5,
			each <=300 chars), limitations (<=400). Each finding has exactly title (<=120), severity
			(INFO, LOW, MEDIUM or HIGH), evidenceMetric (one of the allowed metric keys), evidenceValue
			(copied exactly from allowedEvidenceValues), explanation (<=400) and recommendedAction (<=300).
			Copy every evidenceValue character-for-character: no symbols, spaces, currency marks or formatting.
			Never invent numbers. Never change marks, grades, pass/fail decisions, counters or accepted rows.
			Never mention student names, IDs, emails, dates of birth, marks, row messages or line numbers.
			Facts (JSON):
			""";

	private InsightPromptFactory() {
	}

	public static String prompt(ImportQualityContext context, int maxPromptChars) {
		Map<String, Object> facts = new LinkedHashMap<>();
		facts.put("rowsRead", context.rowsRead());
		facts.put("importedCount", context.importedCount());
		facts.put("rejectedCount", context.rejectedCount());
		facts.put("newCount", context.newCount());
		facts.put("changedCount", context.changedCount());
		facts.put("unchangedCount", context.unchangedCount());
		facts.put("errorCount", context.errorCount());
		facts.put("importedPercentage", context.importedPercentage());
		facts.put("rejectedPercentage", context.rejectedPercentage());
		facts.put("errorsPerRejectedRow", context.errorsPerRejectedRow());
		facts.put("newPercentage", context.newPercentage());
		facts.put("changedPercentage", context.changedPercentage());
		facts.put("unchangedPercentage", context.unchangedPercentage());
		Map<String, Object> byCode = new LinkedHashMap<>();
		context.errorCounts().forEach(byCode::put);
		facts.put("errorCountsByCode", byCode);
		Map<String, Object> allowed = new LinkedHashMap<>();
		for (InsightEvidenceMetric metric : InsightEvidenceMetric.values()) {
			allowed.put(metric.name(), context.evidenceValue(metric));
		}
		facts.put("allowedEvidenceValues", allowed);
		facts.put("schemaColumns", context.schemaColumns());

		String prompt = INSTRUCTION + write(facts);
		if (prompt.length() > maxPromptChars) {
			throw new InsightProviderException("Configured prompt size limit is too small for the aggregate context",
					InsightProviderException.Category.CONFIGURATION, "PROMPT_LIMIT", null, null);
		}
		return prompt;
	}

	/**
	 * Provider-facing schema. Google's Interactions API ignores maxLength, and exact-key enforcement
	 * lives in InsightResponseParser, so neither maxLength nor additionalProperties is sent. Strict
	 * validation is never relaxed.
	 */
	public static Map<String, Object> responseSchema() {
		return schema(false);
	}

	/**
	 * Ollama's small local model loops on open-ended text fields unless maxLength is present, so the
	 * local schema repeats those already-enforced Java parser bounds. Google ignores maxLength, which is
	 * why the Gemini schema omits it. Java validation is identical for both providers.
	 */
	public static Map<String, Object> ollamaFormat() {
		return schema(true);
	}

	private static Map<String, Object> schema(boolean withMaxLengths) {
		Map<String, Object> finding = new LinkedHashMap<>();
		finding.put("type", "object");
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("title", text("Short finding title", withMaxLengths ? 120 : null));
		Map<String, Object> severity = new LinkedHashMap<>();
		severity.put("type", "string");
		severity.put("enum", List.of("INFO", "LOW", "MEDIUM", "HIGH"));
		properties.put("severity", severity);
		Map<String, Object> metric = new LinkedHashMap<>();
		metric.put("type", "string");
		metric.put("enum", Arrays.stream(InsightEvidenceMetric.values()).map(Enum::name).toList());
		properties.put("evidenceMetric", metric);
		properties.put("evidenceValue", text("Exact value copied from allowedEvidenceValues", withMaxLengths ? 80 : null));
		properties.put("explanation", text("Plain-language interpretation", withMaxLengths ? 400 : null));
		properties.put("recommendedAction", text("Advisory next step", withMaxLengths ? 300 : null));
		finding.put("properties", properties);
		finding.put("required", List.of("title", "severity", "evidenceMetric", "evidenceValue",
				"explanation", "recommendedAction"));

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("type", "object");
		Map<String, Object> rootProperties = new LinkedHashMap<>();
		rootProperties.put("summary", text("Short summary", withMaxLengths ? 400 : null));
		rootProperties.put("overallAssessment", text("Overall assessment", withMaxLengths ? 600 : null));
		Map<String, Object> findings = new LinkedHashMap<>();
		findings.put("type", "array");
		findings.put("maxItems", 5);
		findings.put("items", finding);
		rootProperties.put("findings", findings);
		Map<String, Object> actions = new LinkedHashMap<>();
		actions.put("type", "array");
		actions.put("maxItems", 5);
		actions.put("items", text("Advisory action", withMaxLengths ? 300 : null));
		rootProperties.put("recommendedActions", actions);
		rootProperties.put("limitations", text("Brief limitations", withMaxLengths ? 400 : null));
		root.put("properties", rootProperties);
		root.put("required", List.of("summary", "overallAssessment", "findings",
				"recommendedActions", "limitations"));
		return root;
	}

	private static Map<String, Object> text(String description, Integer maxLength) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("type", "string");
		value.put("description", description);
		if (maxLength != null) value.put("maxLength", maxLength);
		return value;
	}

	/** Test and guard markers that must never appear in provider output free text. */
	public static List<String> blockedSentinels() {
		return List.of("@example.com", "2001-01-01", "PII_SENTINEL", "SENSITIVE_STUDENT_MARKER_12345");
	}

	private static String write(Object value) {
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException error) {
			throw new IllegalStateException("Could not build the aggregate analysis context", error);
		}
	}
}
