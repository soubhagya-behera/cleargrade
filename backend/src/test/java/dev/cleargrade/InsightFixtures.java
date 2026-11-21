package dev.cleargrade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.cleargrade.application.model.ApprovedInsightFinding;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.InsightFindingId;
import dev.cleargrade.application.model.QualityAssessment;
import dev.cleargrade.application.service.DeterministicInsightService;

/** Synthetic fixtures shared by AI tests. No live provider or real student data is involved. */
public final class InsightFixtures {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private InsightFixtures() {
	}

	/** Exact counters and error-code distribution observed in the real batch-4 browser test. */
	public static ImportQualityContext realBatch4Context() {
		return new ImportQualityContext(25, 16, 9, 16, 0, 0, 9, Map.of(
				"MISSING_REQUIRED", 2, "INVALID_FORMAT", 3, "OUT_OF_RANGE", 2,
				"DUPLICATE_ID", 1, "WRONG_FIELD_COUNT", 1));
	}

	public static QualityAssessment realBatch4() {
		return assessment(realBatch4Context());
	}

	/** Alternative synthetic fixture with all nine errors in one code; useful for single-finding paths. */
	public static ImportQualityContext syntheticBatch4Context() {
		return new ImportQualityContext(25, 16, 9, 16, 0, 0, 9, Map.of("MISSING_REQUIRED", 9));
	}

	public static QualityAssessment syntheticBatch4() {
		return assessment(syntheticBatch4Context());
	}

	public static QualityAssessment mixed() {
		return assessment(new ImportQualityContext(100, 80, 20, 80, 0, 0, 22, Map.of(
				"MISSING_REQUIRED", 5, "INVALID_FORMAT", 4, "OUT_OF_RANGE", 3,
				"DUPLICATE_ID", 5, "WRONG_FIELD_COUNT", 5)));
	}

	public static QualityAssessment assessment(ImportQualityContext context) {
		return new DeterministicInsightService().assess(context);
	}

	public static String validAiJson(QualityAssessment assessment) {
		Map<String, Object> root = new LinkedHashMap<>();
		List<Map<String, Object>> findings = new ArrayList<>();
		for (ApprovedInsightFinding finding : assessment.approvedFindings()) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("findingId", finding.id().name());
			entry.put("explanation", plainExplanation(finding, assessment.context()));
			findings.add(entry);
		}
		root.put("findings", findings);
		try {
			return MAPPER.writeValueAsString(root);
		}
		catch (Exception error) {
			throw new IllegalStateException(error);
		}
	}

	private static String plainExplanation(ApprovedInsightFinding finding, ImportQualityContext context) {
		String value = finding.evidenceValue();
		return switch (finding.id()) {
			case REJECTED_SHARE_ELEVATED -> context.rejectedCount() + " of " + context.rowsRead()
					+ " rows were rejected (" + value + "%), so more than one third of the file needs correction.";
			case MULTIPLE_ERRORS_PER_REJECTED_ROW -> "The " + value
					+ " problems per rejected row mean several rows have more than one issue.";
			case DUPLICATE_ID -> "A student ID appears more than once, so " + value
					+ " row was rejected as a duplicate.";
			case MISSING_REQUIRED -> value + " required values were left blank in the source file.";
			case WRONG_FIELD_COUNT -> value + " row has the wrong number of columns and could not be mapped.";
			case INVALID_FORMAT -> value + " values do not match the required format.";
			case OUT_OF_RANGE -> value + " values fall outside the permitted range.";
		};
	}

	/** Reproduces the malformed content seen in the browser screenshots. */
	public static String malformedExplanationJson(QualityAssessment assessment) {
		return withFindingField(validAiJson(assessment),
				InsightFindingId.REJECTED_SHARE_ELEVATED.name(), "explanation",
				"A substantial share of the file was rejected... Measured rejected_percentage: 36.0.\"\"\""
						+ "javaExplanation: A substantial share of the file was rejected. "
						+ "javaRecommendedAction: Review the rejected rows by error code and correct the source export.");
	}

	public static String withFindingField(String json, String findingId, String field, String value) {
		try {
			var root = MAPPER.readTree(json);
			for (var node : root.get("findings")) {
				if (node.get("findingId").asText().equals(findingId)) {
					((com.fasterxml.jackson.databind.node.ObjectNode) node).put(field, value);
					return MAPPER.writeValueAsString(root);
				}
			}
			throw new IllegalArgumentException("finding not present: " + findingId);
		}
		catch (Exception error) {
			throw new IllegalStateException(error);
		}
	}
}
