package dev.cleargrade.adapters.in.web.dto;

import java.util.List;
import java.util.Map;

import dev.cleargrade.application.model.ImportQualityContext;

/** Java-measured facts, always returned separately from model interpretation. */
public record MeasuredMetricsResponse(
		int rowsRead,
		int importedCount,
		int rejectedCount,
		int newCount,
		int changedCount,
		int unchangedCount,
		int errorCount,
		String importedPercentage,
		String rejectedPercentage,
		String errorsPerRejectedRow,
		Map<String, Integer> errorCountsByCode,
		List<String> schemaColumns) {

	public static MeasuredMetricsResponse from(ImportQualityContext context) {
		return new MeasuredMetricsResponse(context.rowsRead(), context.importedCount(), context.rejectedCount(),
				context.newCount(), context.changedCount(), context.unchangedCount(), context.errorCount(),
				context.importedPercentage(), context.rejectedPercentage(), context.errorsPerRejectedRow(),
				context.errorCounts(), context.schemaColumns());
	}
}
