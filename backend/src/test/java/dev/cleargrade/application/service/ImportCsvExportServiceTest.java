package dev.cleargrade.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.cleargrade.application.model.BatchSummary;
import dev.cleargrade.application.model.PageResult;
import dev.cleargrade.application.model.RowErrorView;
import dev.cleargrade.domain.batch.ImportStatus;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

class ImportCsvExportServiceTest {

	@Test
	void reportEscapesFormulaLikeFilenameAndLeavesUnavailableValuesEmpty() throws Exception {
		ImportCsvExportService service = new ImportCsvExportService(mock(ImportQueryService.class));
		BatchSummary batch = new BatchSummary(17L, "  =cmd.csv", ImportStatus.FAILED,
				null, null, null, null, null, null, null, null, null, null);
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		service.writeReport(batch, output);

		try (CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader(output.toString(StandardCharsets.UTF_8)))) {
			List<CSVRecord> records = parser.getRecords();
			assertEquals(2, records.size());
			assertEquals("Batch ID", records.get(0).get(0));
			assertEquals("'  =cmd.csv", records.get(1).get(1));
			assertEquals("17", records.get(1).get(0));
			for (int column = 3; column < records.get(1).size(); column++) {
				assertEquals("", records.get(1).get(column));
			}
		}
	}

	@Test
	void rowProblemsQuoteCommasQuotesAndNewlinesAndProtectFormulaText() throws Exception {
		ImportQueryService imports = mock(ImportQueryService.class);
		when(imports.getErrors(18L, 0, 100, null)).thenReturn(PageResult.of(
				List.of(new RowErrorView(2, " \t@SUM(A1:A2)", "field,\"name\"\nnext", "=BAD",
						"=HYPERLINK(\"example\")\r\ncontinued")), 0, 100, 1));
		ImportCsvExportService service = new ImportCsvExportService(imports);
		BatchSummary batch = new BatchSummary(18L, "marks,\"sample.csv", ImportStatus.COMPLETED,
				1, 0, 0, 0, 0, 1, 1, null, null, null);
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		service.writeRowProblems(batch, output);

		try (CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader(output.toString(StandardCharsets.UTF_8)))) {
			List<CSVRecord> records = parser.getRecords();
			assertEquals(2, records.size());
			CSVRecord issue = records.get(1);
			assertEquals("marks,\"sample.csv", issue.get(1));
			assertEquals("2", issue.get(2));
			assertEquals("' \t@SUM(A1:A2)", issue.get(3));
			assertEquals("field,\"name\"\nnext", issue.get(4));
			assertEquals("'=BAD", issue.get(5));
			assertEquals("'=HYPERLINK(\"example\")\r\ncontinued", issue.get(6));
		}
	}
}
