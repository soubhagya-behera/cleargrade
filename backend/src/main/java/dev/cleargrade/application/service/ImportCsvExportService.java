package dev.cleargrade.application.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import dev.cleargrade.application.model.BatchSummary;
import dev.cleargrade.application.model.PageResult;
import dev.cleargrade.application.model.RowErrorView;
import dev.cleargrade.domain.batch.ImportStatus;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

/** Writes per-batch CSV exports from persisted application read models. */
@Service
public class ImportCsvExportService {

	private static final int ERROR_PAGE_SIZE = 100;
	private static final String[] REPORT_HEADERS = {
			"Batch ID", "Original Filename", "Status", "Imported At", "Finished At", "Duration (ms)",
			"Rows Read", "Total Imported", "New Records", "Changed Records", "Unchanged Records",
			"Rejected Rows", "Validation Problem Count" };
	private static final String[] ROW_PROBLEM_HEADERS = {
			"Batch ID", "Filename", "Line Number", "Student ID", "Field", "Error Code", "Message" };
	private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
			.setRecordSeparator("\r\n")
			.get();

	private final ImportQueryService imports;

	public ImportCsvExportService(ImportQueryService imports) {
		this.imports = imports;
	}

	public void writeReport(BatchSummary batch, OutputStream output) throws IOException {
		try (CSVPrinter csv = printer(output)) {
			csv.printRecord((Object[]) REPORT_HEADERS);
			csv.printRecord(batch.id(), safeText(batch.filename()), batch.status().name(),
					instant(batch.startedAt()), instant(batch.finishedAt()), batch.durationMs(), batch.rowsRead(),
					batch.imported(), batch.newCount(), batch.changedCount(), batch.unchangedCount(),
					batch.rejected(), batch.errorCount());
		}
	}

	public void writeRowProblems(BatchSummary batch, OutputStream output) throws IOException {
		try (CSVPrinter csv = printer(output)) {
			csv.printRecord((Object[]) ROW_PROBLEM_HEADERS);
			if (batch.status() != ImportStatus.COMPLETED) {
				return;
			}

			PageResult<RowErrorView> page = this.imports.getErrors(batch.id(), 0, ERROR_PAGE_SIZE, null);
			while (true) {
				for (RowErrorView problem : page.content()) {
					csv.printRecord(batch.id(), safeText(batch.filename()), problem.lineNumber(),
							safeText(problem.rawStudentId()), safeText(problem.field()), safeText(problem.errorCode()),
							safeText(problem.message()));
				}
				if (page.page() + 1 >= page.totalPages()) {
					break;
				}
				page = this.imports.getErrors(batch.id(), page.page() + 1, ERROR_PAGE_SIZE, null);
			}
		}
	}

	private static CSVPrinter printer(OutputStream output) throws IOException {
		return new CSVPrinter(new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8)), CSV_FORMAT);
	}

	private static String instant(Instant value) {
		return value == null ? "" : value.toString();
	}

	/** Prefixes spreadsheet formula-like text while preserving the original value for CSV consumers. */
	static String safeText(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		int index = 0;
		while (index < value.length()) {
			int codePoint = value.codePointAt(index);
			if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
				break;
			}
			index += Character.charCount(codePoint);
		}
		if (index < value.length()) {
			int first = value.codePointAt(index);
			if (first == '=' || first == '+' || first == '-' || first == '@') {
				return "'" + value;
			}
		}
		return value;
	}
}
