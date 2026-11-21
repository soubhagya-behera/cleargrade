package dev.cleargrade.adapters.out.ai;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ApprovedInsightFinding;
import dev.cleargrade.application.model.ImportInsightFinding;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.InsightEvidenceMetric;
import dev.cleargrade.application.model.InsightFindingId;
import dev.cleargrade.application.model.InsightTelemetry;
import dev.cleargrade.application.model.QualityAssessment;

/**
 * Strict validation of provider JSON. Java owns every finding, severity, metric, value, explanation
 * baseline, summary, assessment, recommendation and limitation; providers supply explanation wording only.
 */
public final class InsightResponseParser {

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

	private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}_])(\\d+(?:\\.\\d+)?)\\s*(%?)");

	private static final List<Pattern> DENOMINATORS = List.of(
			Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s+out\\s+of\\s+(\\d+(?:\\.\\d+)?)"),
			Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s+of\\s+(\\d+(?:\\.\\d+)?)"),
			Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)"),
			Pattern.compile("(?i)\\bout\\s+of\\s+(?:the\\s+)?(\\d+(?:\\.\\d+)?)\\s+rows?\\b"),
			Pattern.compile("(?i)\\b(?:of|from|among)\\s+(?:the\\s+)?(\\d+(?:\\.\\d+)?)\\s+rows?\\b"));

	private static final Pattern BASELINE_CLAIMS = Pattern.compile(
			"(?i)(\\bexpected\\b|(?:acceptable|recommended)\\s+range|within\\s+(?:the\\s+)?range"
					+ "|within\\s+the\\s+allowed|\\btarget\\b|\\bbenchmark\\b|\\bthreshold\\b"
					+ "|should\\s+be|\\bnormally\\b|on\\s+average|\\btypical\\b"
					+ "|industry\\s+standard|acceptable\\s+level|\\bideal\\b)");
	private static final Pattern STRUCTURAL_CONTENT = Pattern.compile(
			"(?i)(javaExplanation|javaRecommendedAction|findingId|evidenceMetric|evidenceValue"
					+ "|recommendedAction|overallAssessment|canonicalEvidenceValues|approvedFindings"
					+ "|schemaColumns|\"\"\")");
	private static final Pattern MEASURED_FRAGMENT = Pattern.compile("(?i)\\bmeasured\\s+[a-z_]+:");
	private static final Pattern DISALLOWED_CHARACTERS = Pattern.compile("[\"{}\\[\\]<>\\\\|~^=`]");
	private static final Set<String> STOP_WORDS = Set.of("measured", "this", "that", "with", "from", "were",
			"was", "have", "has", "been", "their", "there", "before", "review", "results", "table",
			"problem", "after", "some", "than", "into", "more", "each", "which", "about");

	private static final Set<String> GENERIC_FILLER = Set.of(
			"verify data integrity", "review and adjust import settings", "verify the import process",
			"ensure accuracy and efficiency", "accurate and efficient", "monitor regularly",
			"take appropriate action", "please review", "check for issues", "investigate further",
			"improve data quality", "ensure data quality", "verify data quality", "review the situation");
	private static final Set<String> FALSE_CLEAN_CLAIMS = Set.of(
			"no issues", "no concerns", "no quality concerns", "all rows imported",
			"clean import", "no problems detected");

	private InsightResponseParser() {
	}

	public static ImportInsightReport parse(String json, QualityAssessment assessment,
			ImportInsightReport.Engine engine, String modelVersion, String fallbackReason) {
		return parse(json, assessment, engine, modelVersion, fallbackReason,
				InsightTelemetry.unavailable(modelVersion, "NOT_RECORDED", null, 0L));
	}

	public static ImportInsightReport parse(String json, QualityAssessment assessment,
			ImportInsightReport.Engine engine, String modelVersion, String fallbackReason,
			InsightTelemetry telemetry) {
		try {
			JsonNode root = MAPPER.readTree(json);
			if (root == null || !root.isObject()) structural("root is not an object");
			requireExactKeys(root, Set.of("findings"));

			JsonNode findingsNode = root.get("findings");
			List<ApprovedInsightFinding> approved = assessment.approvedFindings();
			if (findingsNode == null || !findingsNode.isArray() || findingsNode.size() != approved.size()) {
				throw semantic("findings must match the approved findings exactly", engine);
			}
			Map<String, JsonNode> supplied = new LinkedHashMap<>();
			for (JsonNode node : findingsNode) {
				requireExactKeys(node, Set.of("findingId", "explanation"));
				String id = text(node, "findingId", 80).toUpperCase(Locale.ROOT);
				InsightFindingId findingId;
				try {
					findingId = InsightFindingId.valueOf(id);
				}
				catch (IllegalArgumentException error) {
					throw semantic("unapproved finding identifier", engine);
				}
				if (supplied.put(findingId.name(), node) != null) {
					throw semantic("duplicate finding identifier", engine);
				}
			}
			if (!supplied.keySet().equals(new HashSet<>(approved.stream()
					.map(finding -> finding.id().name()).toList()))) {
				throw semantic("approved findings must be present exactly once", engine);
			}

			List<ImportInsightFinding> findings = new ArrayList<>();
			for (ApprovedInsightFinding finding : approved) {
				String explanation = findingExplanation(supplied.get(finding.id().name()), finding, assessment,
						engine);
				findings.add(new ImportInsightFinding(finding.title(), finding.severity(),
						finding.evidenceMetric().name(), finding.evidenceValue(), explanation,
						finding.recommendedAction()));
			}

			return new ImportInsightReport(engine, modelVersion, fallbackReason, false, assessment.context(),
					assessment.javaSummary(), assessment.javaOverallAssessment(), List.copyOf(findings),
					assessment.javaRecommendedActions(), assessment.javaLimitations(),
					assessment.context().fingerprint(), telemetry, List.of());
		}
		catch (JsonProcessingException error) {
			throw structuralWithCause("provider JSON was malformed", error);
		}
	}

	private static String findingExplanation(JsonNode node, ApprovedInsightFinding finding,
			QualityAssessment assessment, ImportInsightReport.Engine engine) {
		String value = text(node, "explanation", 400);
		rejectBlockedText(value);
		rejectBannedPhrases(value, assessment, engine);
		rejectNonNarrative(value, finding, engine);
		if (!containsCanonicalNumber(value, finding.evidenceValue())) {
			throw semantic("explanation must include the canonical evidence value", engine);
		}
		for (InsightEvidenceMetric metric : InsightEvidenceMetric.values()) {
			if (metric == finding.evidenceMetric()) continue;
			if (value.toUpperCase(Locale.ROOT).contains(metric.name())) {
				throw semantic("explanation referenced an unapproved evidence metric", engine);
			}
		}
		checkNumbers(value, canonicalValues(assessment), canonicalPercentages(assessment), assessment, engine);
		return value;
	}

	private static void rejectNonNarrative(String value, ApprovedInsightFinding finding,
			ImportInsightReport.Engine engine) {
		if (STRUCTURAL_CONTENT.matcher(value).find()) {
			throw semantic("explanation contained prompt or field labels", engine);
		}
		if (MEASURED_FRAGMENT.matcher(value).find()) {
			throw semantic("explanation duplicated Java baseline content", engine);
		}
		if (DISALLOWED_CHARACTERS.matcher(value).find()) {
			throw semantic("explanation was not plain text", engine);
		}
		if (value.chars().anyMatch(Character::isISOControl)) {
			throw semantic("explanation contained control characters", engine);
		}
		if (value.trim().length() < 12 || value.trim().split("\\s+").length < 3) {
			throw semantic("explanation was too short to be meaningful", engine);
		}
		if (!relevantToFinding(value, finding)) {
			throw semantic("explanation was not relevant to the approved finding", engine);
		}
	}

	private static boolean relevantToFinding(String value, ApprovedInsightFinding finding) {
		String source = (finding.title() + " " + finding.explanation()).toLowerCase(Locale.ROOT);
		Set<String> words = new HashSet<>(Arrays.asList(source.split("[^\\p{L}]+")));
		String lower = value.toLowerCase(Locale.ROOT);
		return words.stream().anyMatch(word -> word.length() >= 4 && !STOP_WORDS.contains(word)
				&& lower.contains(word));
	}

	private static void rejectBannedPhrases(String value, QualityAssessment assessment,
			ImportInsightReport.Engine engine) {
		if (BASELINE_CLAIMS.matcher(value).find()) {
			throw semantic("unsupported expected baseline or range", engine);
		}
		String lower = value.toLowerCase(Locale.ROOT);
		for (String phrase : GENERIC_FILLER) {
			if (lower.contains(phrase)) throw semantic("generic filler text", engine);
		}
		for (String phrase : FALSE_CLEAN_CLAIMS) {
			if (lower.contains(phrase)) throw semantic("text contradicts approved findings", engine);
		}
	}

	private static void checkNumbers(String value, Set<String> canonical, Set<String> percentages,
			QualityAssessment assessment, ImportInsightReport.Engine engine) {
		Matcher matcher = NUMBER.matcher(value);
		while (matcher.find()) {
			String token = matcher.group(1);
			if (canonical.stream().noneMatch(item -> equivalent(token, item))) {
				throw semantic("unsupported numeric claim", engine);
			}
			if (!matcher.group(2).isEmpty() && percentages.stream()
					.noneMatch(item -> equivalent(token, item))) {
				throw semantic("unsupported percentage", engine);
			}
		}
		String rowsRead = String.valueOf(assessment.context().rowsRead());
		for (Pattern pattern : DENOMINATORS) {
			Matcher denominators = pattern.matcher(value);
			while (denominators.find()) {
				String denominator = denominators.group(denominators.groupCount());
				if (!equivalent(denominator, rowsRead)) {
					throw semantic("wrong denominator", engine);
				}
			}
		}
	}

	private static boolean equivalent(String token, String canonical) {
		try {
			return new BigDecimal(token).compareTo(new BigDecimal(canonical)) == 0;
		}
		catch (NumberFormatException error) {
			return false;
		}
	}

	private static boolean containsCanonicalNumber(String value, String canonical) {
		if (value.contains(canonical)) return true;
		Matcher matcher = NUMBER.matcher(value);
		while (matcher.find()) {
			if (equivalent(matcher.group(1), canonical)) return true;
		}
		return false;
	}

	private static Set<String> canonicalValues(QualityAssessment assessment) {
		Set<String> values = new HashSet<>();
		for (InsightEvidenceMetric metric : InsightEvidenceMetric.values()) {
			values.add(assessment.context().evidenceValue(metric));
		}
		return values;
	}

	private static Set<String> canonicalPercentages(QualityAssessment assessment) {
		Set<String> values = new HashSet<>();
		for (InsightEvidenceMetric metric : InsightEvidenceMetric.values()) {
			if (metric.name().endsWith("_PERCENTAGE")) {
				values.add(assessment.context().evidenceValue(metric));
			}
		}
		return values;
	}

	private static void requireExactKeys(JsonNode node, Set<String> expected) {
		Iterator<String> names = node.fieldNames();
		while (names.hasNext()) {
			if (!expected.contains(names.next())) structural("unexpected field in provider JSON");
		}
		for (String field : expected) {
			if (!node.has(field)) structural("missing field in provider JSON: " + field);
		}
	}

	private static void rejectBlockedText(String value) {
		for (String sentinel : InsightPromptFactory.blockedSentinels()) {
			if (value.toLowerCase(Locale.ROOT).contains(sentinel.toLowerCase(Locale.ROOT))) {
				structural("provider text contained a blocked identifier marker");
			}
		}
	}

	private static String text(JsonNode node, String field, int maxLength) {
		JsonNode value = node.get(field);
		if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) {
			structural("invalid or missing text field: " + field);
		}
		return value.asText();
	}

	private static void structural(String safeReason) {
		throw new InsightProviderException("Provider response failed validation: " + safeReason,
				InsightProviderException.Category.FALLBACK, "PROVIDER_VALIDATION", null, null);
	}

	private static InsightProviderException structuralWithCause(String safeReason, Throwable cause) {
		return new InsightProviderException("Provider response failed validation: " + safeReason,
				InsightProviderException.Category.FALLBACK, "MALFORMED_JSON", cause, null);
	}

	private static InsightProviderException semantic(String safeReason, ImportInsightReport.Engine engine) {
		String code = switch (engine) {
			case GEMINI -> "GEMINI_SEMANTIC_REJECTED";
			case OLLAMA -> "OLLAMA_SEMANTIC_REJECTED";
			default -> "AI_SEMANTIC_REJECTED";
		};
		return new InsightProviderException("Provider response failed semantic validation: " + safeReason,
				InsightProviderException.Category.FALLBACK, code, null, null);
	}
}
