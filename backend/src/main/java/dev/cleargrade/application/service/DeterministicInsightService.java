package dev.cleargrade.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.cleargrade.application.model.ImportInsightFinding;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.InsightEvidenceMetric;
import dev.cleargrade.application.port.out.ImportInsightProvider;
import dev.cleargrade.domain.batch.RowErrorCode;
import org.springframework.stereotype.Component;

/** Deterministic, network-free rule engine. This is explicitly NOT AI-generated. */
@Component
public class DeterministicInsightService implements ImportInsightProvider {

	private static final String MODEL_VERSION = "deterministic-v1";
	private static final int SEVERITY_HIGH = 3;
	private static final int SEVERITY_MEDIUM = 2;
	private static final int SEVERITY_LOW = 1;

	@Override
	public ImportInsightReport.Engine engine() {
		return ImportInsightReport.Engine.RULE_BASED;
	}

	@Override
	public String modelVersion() {
		return MODEL_VERSION;
	}

	@Override
	public ImportInsightReport analyse(ImportQualityContext context) {
		List<ImportInsightFinding> findings = new ArrayList<>();
		addIf(context, findings, InsightEvidenceMetric.REJECTED_PERCENTAGE,
				percent(context.rejectedPercentage()) >= 20, SEVERITY_HIGH,
				"Elevated rejected-row share",
				"A substantial share of the file was rejected. Review the problem table before publishing results.",
				"Review the rejected rows by error code and correct the source export.");
		addIf(context, findings, InsightEvidenceMetric.ERRORS_PER_REJECTED_ROW,
				decimal(context.errorsPerRejectedRow()) > 1.0, SEVERITY_MEDIUM,
				"Multiple problems per rejected row",
				"Some rejected rows carry more than one problem, so problem occurrences exceed rejected rows.",
				"Fix field-level problems together to recover rows in one pass.");
		addIf(context, findings, InsightEvidenceMetric.WRONG_FIELD_COUNT_COUNT,
				context.errorCount(RowErrorCode.WRONG_FIELD_COUNT) > 0, SEVERITY_MEDIUM,
				"Rows with wrong field count",
				"Rows whose width differs from the header cannot be mapped to columns.",
				"Check delimiters, quoting and trailing columns in the source export.");
		addIf(context, findings, InsightEvidenceMetric.DUPLICATE_ID_COUNT,
				context.errorCount(RowErrorCode.DUPLICATE_ID) > 0, SEVERITY_MEDIUM,
				"Duplicate student identifiers",
				"Later occurrences of an already reserved student ID were rejected.",
				"De-duplicate student IDs before the next upload.");
		addIf(context, findings, InsightEvidenceMetric.MISSING_REQUIRED_COUNT,
				context.errorCount(RowErrorCode.MISSING_REQUIRED) > 0, SEVERITY_MEDIUM,
				"Missing required values",
				"Required columns were empty in at least one data row.",
				"Populate all required columns in the source export.");
		addIf(context, findings, InsightEvidenceMetric.INVALID_FORMAT_COUNT,
				context.errorCount(RowErrorCode.INVALID_FORMAT) > 0, SEVERITY_LOW,
				"Invalid value formats",
				"At least one value did not match its documented format.",
				"Normalise dates, identifiers and email formatting before export.");
		addIf(context, findings, InsightEvidenceMetric.OUT_OF_RANGE_COUNT,
				context.errorCount(RowErrorCode.OUT_OF_RANGE) > 0, SEVERITY_LOW,
				"Out-of-range values",
				"At least one value was outside its allowed range.",
				"Verify marks and age bounds in the source system.");
		findings.sort(Comparator
				.comparingInt((ImportInsightFinding f) -> severityRank(f.severity())).reversed()
				.thenComparing(ImportInsightFinding::evidenceMetric));

		List<String> actions = findings.stream()
				.map(finding -> finding.recommendedAction() + " Evidence: " + finding.evidenceMetric()
						+ " = " + finding.evidenceValue())
				.toList();

		String summary = context.rowsRead() == 0
				? "No data rows were read from this import."
				: "Read " + context.rowsRead() + " rows; imported " + context.importedCount()
						+ " (" + context.importedPercentage() + "%) and rejected " + context.rejectedCount()
						+ " (" + context.rejectedPercentage() + "%).";
		String assessment = findings.isEmpty()
				? "No rule-based quality concerns were detected in the measured aggregates."
				: "Rule-based checks detected " + findings.size()
						+ " quality concern(s) grounded in the measured import counters.";
		String limitations = "Rule-based analysis uses aggregate counters only. It cannot inspect row content, "
				+ "verify student eligibility beyond the import validator, or guarantee source-system correctness.";

		return new ImportInsightReport(engine(), MODEL_VERSION, null, false, context,
				summary, assessment, List.copyOf(findings), actions, limitations, context.fingerprint());
	}

	private void addIf(ImportQualityContext context, List<ImportInsightFinding> findings,
			InsightEvidenceMetric metric, boolean condition, int severity, String title,
			String explanation, String action) {
		if (!condition) return;
		String value = context.evidenceValue(metric);
		findings.add(new ImportInsightFinding(title, severityLabel(severity), metric.name(), value,
				explanation + " Measured " + metric.name().toLowerCase() + ": " + value + ".", action));
	}

	private static String severityLabel(int rank) {
		return switch (rank) {
			case SEVERITY_HIGH -> "HIGH";
			case SEVERITY_MEDIUM -> "MEDIUM";
			default -> "LOW";
		};
	}

	private static int severityRank(String severity) {
		return switch (severity) {
			case "HIGH" -> SEVERITY_HIGH;
			case "MEDIUM" -> SEVERITY_MEDIUM;
			case "LOW" -> SEVERITY_LOW;
			default -> 0;
		};
	}

	private static double percent(String value) {
		try { return Double.parseDouble(value); } catch (NumberFormatException error) { return 0; }
	}

	private static double decimal(String value) {
		try { return Double.parseDouble(value); } catch (NumberFormatException error) { return 0; }
	}
}
