package dev.cleargrade.adapters.out.ai;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ApprovedInsightFinding;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.InsightEvidenceMetric;
import dev.cleargrade.application.model.InsightFindingId;
import dev.cleargrade.application.model.QualityAssessment;

/** Builds the only payload providers receive: Java-approved findings plus aggregate context. */
public final class InsightPromptFactory {

	private static final String INSTRUCTION = """
			Return one compact JSON object with exactly one key: findings. It contains one entry per
			approved finding, and each entry has exactly findingId and explanation (<=400 chars).
			Copy findingId character-for-character from approvedFindings. NEVER add, omit or rename a finding.
			Write only plain English in explanation: no JSON, no quoted prompt text and no Java field names.
			Do not copy, echo or mention javaExplanation, javaRecommendedAction or any other field label.
			Do not write summaries, assessments, limitations or recommendations: Java owns all of those.
			Explain only why this approved issue matters. Use only canonical numbers supplied below and keep
			the same denominator as rowsRead when describing a ratio. Never mention student names, IDs,
			emails, dates of birth, marks, row messages or line numbers.
			Facts (JSON):
			""";

	private InsightPromptFactory() {
	}

	public static String prompt(QualityAssessment assessment, int maxPromptChars) {
		ImportQualityContext context = assessment.context();
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
		facts.put("canonicalEvidenceValues", allowed);
		facts.put("schemaColumns", context.schemaColumns());

		List<Map<String, Object>> approved = new java.util.ArrayList<>();
		for (ApprovedInsightFinding finding : assessment.approvedFindings()) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("findingId", finding.id().name());
			entry.put("title", finding.title());
			entry.put("severity", finding.severity());
			entry.put("evidenceMetric", finding.evidenceMetric().name());
			entry.put("evidenceValue", finding.evidenceValue());
			entry.put("javaExplanation", finding.explanation());
			approved.add(entry);
		}
		facts.put("approvedFindings", approved);

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
		Map<String, Object> findingId = new LinkedHashMap<>();
		findingId.put("type", "string");
		findingId.put("enum", Arrays.stream(InsightFindingId.values()).map(Enum::name).toList());
		properties.put("findingId", findingId);
		properties.put("explanation", text("Why this approved finding matters, using only canonical values",
				withMaxLengths ? 400 : null));
		finding.put("properties", properties);
		finding.put("required", List.of("findingId", "explanation"));

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("type", "object");
		Map<String, Object> rootProperties = new LinkedHashMap<>();
		Map<String, Object> findings = new LinkedHashMap<>();
		findings.put("type", "array");
		findings.put("maxItems", InsightFindingId.values().length);
		findings.put("items", finding);
		rootProperties.put("findings", findings);
		root.put("properties", rootProperties);
		root.put("required", List.of("findings"));
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
