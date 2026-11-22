package dev.cleargrade.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

	@Test
	void rowProblemsExportIncludesEveryIssueAcrossPageBoundaries() throws Exception {
		ImportQueryService imports = mock(ImportQueryService.class);
		when(imports.getErrors(20L, 0, 100, null)).thenReturn(PageResult.of(rowErrors(1, 100), 0, 100, 250));
		when(imports.getErrors(20L, 1, 100, null)).thenReturn(PageResult.of(rowErrors(101, 100), 1, 100, 250));
		when(imports.getErrors(20L, 2, 100, null)).thenReturn(PageResult.of(rowErrors(201, 50), 2, 100, 250));
		ImportCsvExportService service = new ImportCsvExportService(imports);
		BatchSummary batch = new BatchSummary(20L, "marks.csv", ImportStatus.COMPLETED,
				250, 0, 0, 0, 0, 250, 250, null, null, null);
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		service.writeRowProblems(batch, output);

		try (CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader(output.toString(StandardCharsets.UTF_8)))) {
			List<CSVRecord> records = parser.getRecords();
			assertEquals(251, records.size());
			Set<String> lines = records.stream().skip(1).map(record -> record.get(2)).collect(Collectors.toSet());
			assertEquals(250, lines.size());
			for (int line = 1; line <= 250; line++) {
				assertTrue(lines.contains(Integer.toString(line)), "line " + line + " is missing from the export");
			}
			assertEquals("1", records.get(1).get(2));
			assertEquals("250", records.get(250).get(2));
		}
		verify(imports).getErrors(20L, 0, 100, null);
		verify(imports).getErrors(20L, 1, 100, null);
		verify(imports).getErrors(20L, 2, 100, null);
		verifyNoMoreInteractions(imports);
	}

	@Test
	void rowProblemsExportStopsAtExactPageBoundary() throws Exception {
		ImportQueryService imports = mock(ImportQueryService.class);
		when(imports.getErrors(21L, 0, 100, null)).thenReturn(PageResult.of(rowErrors(1, 100), 0, 100, 200));
		when(imports.getErrors(21L, 1, 100, null)).thenReturn(PageResult.of(rowErrors(101, 100), 1, 100, 200));
		ImportCsvExportService service = new ImportCsvExportService(imports);
		BatchSummary batch = new BatchSummary(21L, "marks.csv", ImportStatus.COMPLETED,
				200, 0, 0, 0, 0, 200, 200, null, null, null);
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		service.writeRowProblems(batch, output);

		try (CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader(output.toString(StandardCharsets.UTF_8)))) {
			assertEquals(201, parser.getRecords().size());
		}
		verify(imports).getErrors(21L, 0, 100, null);
		verify(imports).getErrors(21L, 1, 100, null);
		verifyNoMoreInteractions(imports);
	}

	private static List<RowErrorView> rowErrors(int firstLine, int count) {
		List<RowErrorView> errors = new ArrayList<>();
		for (int line = firstLine; line < firstLine + count; line++) {
			errors.add(new RowErrorView(line, "STU" + line, "field", "INVALID_FORMAT", "problem"));
		}
		return errors;
	}
}
