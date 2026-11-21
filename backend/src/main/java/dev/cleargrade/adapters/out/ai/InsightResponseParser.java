package dev.cleargrade.adapters.out.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ImportInsightFinding;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.InsightEvidenceMetric;
import dev.cleargrade.application.model.InsightTelemetry;

/** Strict server-side validation of provider JSON; no model output can alter measured facts. */
public final class InsightResponseParser {

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
	private static final Set<String> SEVERITIES = Set.of("INFO", "LOW", "MEDIUM", "HIGH");
	private static final int MAX_FINDINGS = 5;
	private static final int MAX_ACTIONS = 5;

	private InsightResponseParser() {
	}

	public static ImportInsightReport parse(String json, ImportQualityContext context,
			ImportInsightReport.Engine engine, String modelVersion, String fallbackReason) {
		return parse(json, context, engine, modelVersion, fallbackReason,
				InsightTelemetry.unavailable(modelVersion, "NOT_RECORDED", null, 0L));
	}

	public static ImportInsightReport parse(String json, ImportQualityContext context,
			ImportInsightReport.Engine engine, String modelVersion, String fallbackReason,
			InsightTelemetry telemetry) {
		try {
			JsonNode root = MAPPER.readTree(json);
			if (root == null || !root.isObject()) return reject("root is not an object");
			requireExactKeys(root, Set.of("summary", "overallAssessment", "findings",
					"recommendedActions", "limitations"));

			String summary = text(root, "summary", 400);
			String assessment = text(root, "overallAssessment", 600);
			String limitations = text(root, "limitations", 400);
			rejectBlockedText(summary);
			rejectBlockedText(assessment);
			rejectBlockedText(limitations);

			JsonNode findingsNode = root.get("findings");
			if (findingsNode == null || !findingsNode.isArray() || findingsNode.size() > MAX_FINDINGS) {
				return reject("findings array is missing or too large");
			}
			List<ImportInsightFinding> findings = new ArrayList<>();
			for (JsonNode node : findingsNode) {
				findings.add(parseFinding(node, context));
			}

			JsonNode actionsNode = root.get("recommendedActions");
			if (actionsNode == null || !actionsNode.isArray() || actionsNode.size() > MAX_ACTIONS) {
				return reject("recommendedActions array is missing or too large");
			}
			List<String> actions = new ArrayList<>();
			for (JsonNode node : actionsNode) {
				if (node == null || !node.isTextual() || node.asText().isBlank()
						|| node.asText().length() > 300) {
					return reject("invalid recommended action");
				}
				rejectBlockedText(node.asText());
				actions.add(node.asText());
			}

			return new ImportInsightReport(engine, modelVersion, fallbackReason, false, context,
					summary, assessment, List.copyOf(findings), List.copyOf(actions),
					limitations, context.fingerprint(), telemetry);
		}
		catch (JsonProcessingException error) {
			throw fallback("provider JSON was malformed", error);
		}
	}

	private static ImportInsightFinding parseFinding(JsonNode node, ImportQualityContext context) {
		if (node == null || !node.isObject()) throw fallback("finding is not an object", null);
		requireExactKeys(node, Set.of("title", "severity", "evidenceMetric", "evidenceValue",
				"explanation", "recommendedAction"));
		String title = text(node, "title", 120);
		rejectBlockedText(title);
		String severity = text(node, "severity", 16).toUpperCase(Locale.ROOT);
		if (!SEVERITIES.contains(severity)) throw fallback("unsupported severity", null);
		String metricName = text(node, "evidenceMetric", 80).toUpperCase(Locale.ROOT);
		InsightEvidenceMetric metric;
		try {
			metric = InsightEvidenceMetric.valueOf(metricName);
		}
		catch (IllegalArgumentException error) {
			throw fallback("unsupported evidence metric", error);
		}
		String evidenceValue = text(node, "evidenceValue", 80);
		String expected = context.evidenceValue(metric);
		if (!expected.equals(evidenceValue)) throw fallback("evidence value does not match measured facts", null);
		String explanation = text(node, "explanation", 400);
		rejectBlockedText(explanation);
		String action = text(node, "recommendedAction", 300);
		rejectBlockedText(action);
		return new ImportInsightFinding(title, severity, metric.name(), expected, explanation, action);
	}

	private static void requireExactKeys(JsonNode node, Set<String> expected) {
		java.util.Iterator<String> names = node.fieldNames();
		while (names.hasNext()) {
			if (!expected.contains(names.next())) throw fallback("unexpected field in provider JSON", null);
		}
		for (String field : expected) {
			if (!node.has(field)) throw fallback("missing field in provider JSON: " + field, null);
		}
	}

	private static void rejectBlockedText(String value) {
		for (String sentinel : InsightPromptFactory.blockedSentinels()) {
			if (value.toLowerCase(Locale.ROOT).contains(sentinel.toLowerCase(Locale.ROOT))) {
				throw fallback("provider text contained a blocked identifier marker", null);
			}
		}
	}

	private static String text(JsonNode node, String field, int maxLength) {
		JsonNode value = node.get(field);
		if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) {
			throw fallback("invalid or missing text field: " + field, null);
		}
		return value.asText();
	}

	private static ImportInsightReport reject(String reason) {
		throw fallback(reason, null);
	}

	private static InsightProviderException fallback(String safeReason, Throwable cause) {
		String code = switch (safeReason) {
			case "evidence value does not match measured facts" -> "EVIDENCE_VALUE_MISMATCH";
			case "unsupported severity" -> "UNSUPPORTED_SEVERITY";
			case "unsupported evidence metric" -> "UNSUPPORTED_EVIDENCE_METRIC";
			case "provider JSON was malformed" -> "MALFORMED_JSON";
			default -> "PROVIDER_VALIDATION";
		};
		return new InsightProviderException("Provider response failed validation: " + safeReason,
				InsightProviderException.Category.FALLBACK, code, cause, null);
	}
}
