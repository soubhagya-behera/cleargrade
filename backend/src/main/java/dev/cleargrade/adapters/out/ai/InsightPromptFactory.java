package dev.cleargrade.adapters.out.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.InsightEvidenceMetric;

/** Builds the only payload providers receive: trusted aggregates and static instructions. */
public final class InsightPromptFactory {

	private InsightPromptFactory() {
	}

	public static String prompt(ImportQualityContext context, int maxPromptChars) {
		String instruction = """
				You are a data-quality analyst. Interpret ONLY the measured aggregate facts provided below.
				Never invent numbers. Every finding must reference one allowlisted evidenceMetric and its exact
				evidenceValue as supplied. Findings and actions are advisory: they must not change marks, grades,
				pass/fail decisions, accepted/rejected rows or batch counters. Do not include student names, IDs,
				emails, dates of birth, individual marks, line numbers or raw CSV content in any output.
				Return JSON matching the supplied schema. Keep text concise and administrator-friendly.
				Measured facts (JSON):
				""";
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
		context.errorCounts().forEach((code, count) -> byCode.put(code, count));
		facts.put("errorCountsByCode", byCode);
		Map<String, Object> allowed = new LinkedHashMap<>();
		for (InsightEvidenceMetric metric : InsightEvidenceMetric.values()) {
			allowed.put(metric.name(), context.evidenceValue(metric));
		}
		facts.put("allowedEvidenceValues", allowed);
		facts.put("schemaColumns", context.schemaColumns());

		String prompt = instruction + write(facts);
		if (prompt.length() > maxPromptChars) {
			throw new InsightProviderException("Configured prompt size limit is too small for the aggregate context",
					InsightProviderException.Category.CONFIGURATION, null);
		}
		return prompt;
	}

	private static String write(Object value) {
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException error) {
			throw new IllegalStateException("Could not build the aggregate analysis context", error);
		}
	}

	public static Map<String, Object> responseSchema() {
		Map<String, Object> finding = new LinkedHashMap<>();
		finding.put("type", "object");
		finding.put("additionalProperties", false);
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("title", text("Short finding title", 120));
		Map<String, Object> severity = new LinkedHashMap<>();
		severity.put("type", "string");
		severity.put("enum", List.of("INFO", "LOW", "MEDIUM", "HIGH"));
		properties.put("severity", severity);
		Map<String, Object> metric = new LinkedHashMap<>();
		metric.put("type", "string");
		metric.put("enum", java.util.Arrays.stream(InsightEvidenceMetric.values())
				.map(Enum::name).toList());
		properties.put("evidenceMetric", metric);
		properties.put("evidenceValue", text("Exact value copied from allowedEvidenceValues", 80));
		properties.put("explanation", text("Plain-language interpretation", 600));
		properties.put("recommendedAction", text("Advisory next step", 400));
		finding.put("properties", properties);
		finding.put("required", List.of("title", "severity", "evidenceMetric", "evidenceValue",
				"explanation", "recommendedAction"));

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("type", "object");
		root.put("additionalProperties", false);
		Map<String, Object> rootProperties = new LinkedHashMap<>();
		rootProperties.put("summary", text("Short summary", 600));
		rootProperties.put("overallAssessment", text("Overall assessment", 1200));
		Map<String, Object> findings = new LinkedHashMap<>();
		findings.put("type", "array");
		findings.put("maxItems", 8);
		findings.put("items", finding);
		rootProperties.put("findings", findings);
		Map<String, Object> actions = new LinkedHashMap<>();
		actions.put("type", "array");
		actions.put("maxItems", 8);
		actions.put("items", text("Advisory action", 400));
		rootProperties.put("recommendedActions", actions);
		rootProperties.put("limitations", text("Limitations of this interpretation", 1200));
		root.put("properties", rootProperties);
		root.put("required", List.of("summary", "overallAssessment", "findings",
				"recommendedActions", "limitations"));
		return root;
	}

	private static Map<String, Object> text(String description, int maxLength) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("type", "string");
		value.put("description", description);
		value.put("maxLength", maxLength);
		return value;
	}

	/** Test and guard markers that must never appear in provider output free text. */
	public static List<String> blockedSentinels() {
		return List.of("@example.com", "2001-01-01", "PII_SENTINEL", "SENSITIVE_STUDENT_MARKER_12345");
	}
}
