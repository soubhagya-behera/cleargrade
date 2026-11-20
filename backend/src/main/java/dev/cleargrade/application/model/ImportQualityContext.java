package dev.cleargrade.application.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import dev.cleargrade.domain.batch.RowErrorCode;

/**
 * Privacy-safe aggregate context. Contains counts and backend-calculated ratios only —
 * never student names, IDs, emails, dates of birth, marks, lines or row messages.
 */
public final class ImportQualityContext {

	private static final List<String> STATIC_SCHEMA_COLUMNS = List.of(
			"student_id", "full_name", "email", "date_of_birth", "grade_level", "section",
			"maths", "science", "english", "social_studies", "second_language");

	private final int rowsRead;
	private final int importedCount;
	private final int rejectedCount;
	private final int newCount;
	private final int changedCount;
	private final int unchangedCount;
	private final int errorCount;
	private final Map<String, Integer> errorCounts;
	private final List<String> schemaColumns;

	public ImportQualityContext(int rowsRead, int importedCount, int rejectedCount, int newCount,
			int changedCount, int unchangedCount, int errorCount, Map<String, Integer> errorCounts) {
		this.rowsRead = rowsRead;
		this.importedCount = importedCount;
		this.rejectedCount = rejectedCount;
		this.newCount = newCount;
		this.changedCount = changedCount;
		this.unchangedCount = unchangedCount;
		this.errorCount = errorCount;
		Map<String, Integer> safe = new LinkedHashMap<>();
		for (RowErrorCode code : RowErrorCode.values()) {
			safe.put(code.name(), errorCounts.getOrDefault(code.name(), 0));
		}
		this.errorCounts = Map.copyOf(safe);
		this.schemaColumns = STATIC_SCHEMA_COLUMNS;
	}

	public int rowsRead() { return this.rowsRead; }
	public int importedCount() { return this.importedCount; }
	public int rejectedCount() { return this.rejectedCount; }
	public int newCount() { return this.newCount; }
	public int changedCount() { return this.changedCount; }
	public int unchangedCount() { return this.unchangedCount; }
	public int errorCount() { return this.errorCount; }
	public Map<String, Integer> errorCounts() { return this.errorCounts; }
	public List<String> schemaColumns() { return this.schemaColumns; }

	public String percentage(int numerator, int denominator) {
		if (denominator == 0) return "0.0";
		return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP).toPlainString();
	}

	public String importedPercentage() { return percentage(this.importedCount, this.rowsRead); }
	public String rejectedPercentage() { return percentage(this.rejectedCount, this.rowsRead); }
	public String newPercentage() { return percentage(this.newCount, this.importedCount); }
	public String changedPercentage() { return percentage(this.changedCount, this.importedCount); }
	public String unchangedPercentage() { return percentage(this.unchangedCount, this.importedCount); }
	public String duplicatePercentage() { return percentage(errorCount(RowErrorCode.DUPLICATE_ID), this.errorCount); }
	public String wrongFieldCountPercentage() {
		return percentage(errorCount(RowErrorCode.WRONG_FIELD_COUNT), this.errorCount);
	}

	public String errorsPerRejectedRow() {
		if (this.rejectedCount == 0) return "0.00";
		return BigDecimal.valueOf(this.errorCount)
				.divide(BigDecimal.valueOf(this.rejectedCount), 2, RoundingMode.HALF_UP).toPlainString();
	}

	public int errorCount(RowErrorCode code) {
		return this.errorCounts.getOrDefault(code.name(), 0);
	}

	/** Canonical evidence value for an allowlisted metric, computed only by the backend. */
	public String evidenceValue(InsightEvidenceMetric metric) {
		if (metric == null) return null;
		return switch (metric) {
			case ROWS_READ -> String.valueOf(this.rowsRead);
			case IMPORTED_COUNT -> String.valueOf(this.importedCount);
			case REJECTED_COUNT -> String.valueOf(this.rejectedCount);
			case IMPORTED_PERCENTAGE -> importedPercentage();
			case REJECTED_PERCENTAGE -> rejectedPercentage();
			case NEW_COUNT -> String.valueOf(this.newCount);
			case CHANGED_COUNT -> String.valueOf(this.changedCount);
			case UNCHANGED_COUNT -> String.valueOf(this.unchangedCount);
			case NEW_PERCENTAGE -> newPercentage();
			case CHANGED_PERCENTAGE -> changedPercentage();
			case UNCHANGED_PERCENTAGE -> unchangedPercentage();
			case ERROR_COUNT -> String.valueOf(this.errorCount);
			case ERRORS_PER_REJECTED_ROW -> errorsPerRejectedRow();
			case MISSING_REQUIRED_COUNT -> String.valueOf(errorCount(RowErrorCode.MISSING_REQUIRED));
			case INVALID_FORMAT_COUNT -> String.valueOf(errorCount(RowErrorCode.INVALID_FORMAT));
			case OUT_OF_RANGE_COUNT -> String.valueOf(errorCount(RowErrorCode.OUT_OF_RANGE));
			case DUPLICATE_ID_COUNT -> String.valueOf(errorCount(RowErrorCode.DUPLICATE_ID));
			case WRONG_FIELD_COUNT_COUNT -> String.valueOf(errorCount(RowErrorCode.WRONG_FIELD_COUNT));
			case DUPLICATE_PERCENTAGE -> duplicatePercentage();
			case WRONG_FIELD_COUNT_PERCENTAGE -> wrongFieldCountPercentage();
		};
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof ImportQualityContext that)) return false;
		return this.rowsRead == that.rowsRead && this.importedCount == that.importedCount
				&& this.rejectedCount == that.rejectedCount && this.newCount == that.newCount
				&& this.changedCount == that.changedCount && this.unchangedCount == that.unchangedCount
				&& this.errorCount == that.errorCount && this.errorCounts.equals(that.errorCounts)
				&& this.schemaColumns.equals(that.schemaColumns);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.rowsRead, this.importedCount, this.rejectedCount, this.newCount,
				this.changedCount, this.unchangedCount, this.errorCount, this.errorCounts, this.schemaColumns);
	}

	/** Deterministic fingerprint for cache keying; contains no raw row data. */
	public String fingerprint() {
		String canonical = String.join("|",
				String.valueOf(this.rowsRead), String.valueOf(this.importedCount),
				String.valueOf(this.rejectedCount), String.valueOf(this.newCount),
				String.valueOf(this.changedCount), String.valueOf(this.unchangedCount),
				String.valueOf(this.errorCount),
				String.join(",", this.errorCounts.entrySet().stream()
						.sorted(Map.Entry.comparingByKey())
						.map(entry -> entry.getKey() + "=" + entry.getValue()).toList()));
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(canonical.getBytes(StandardCharsets.UTF_8));
			StringBuilder result = new StringBuilder(digest.length * 2);
			for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value));
			return result.toString();
		}
		catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 unavailable", error);
		}
	}
}
