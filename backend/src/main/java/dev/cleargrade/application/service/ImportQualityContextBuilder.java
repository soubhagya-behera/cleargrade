package dev.cleargrade.application.service;

import java.util.Map;

import dev.cleargrade.application.model.BatchSummary;
import dev.cleargrade.application.model.ImportQualityContext;
import org.springframework.stereotype.Component;

/** Builds the single privacy-safe aggregate context shared by every provider. */
@Component
public class ImportQualityContextBuilder {

	public ImportQualityContext build(BatchSummary batch, Map<String, Integer> errorCountsByCode) {
		return new ImportQualityContext(
				value(batch.rowsRead()), value(batch.imported()), value(batch.rejected()),
				value(batch.newCount()), value(batch.changedCount()), value(batch.unchangedCount()),
				value(batch.errorCount()), errorCountsByCode);
	}

	private static int value(Integer number) {
		return number == null ? 0 : number;
	}
}
