package dev.cleargrade.adapters.in.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import dev.cleargrade.application.model.ParsedCsv;
import dev.cleargrade.application.port.in.CsvFileParser;
import org.junit.jupiter.api.Test;

class CommonsCsvFileParserTest {

	private static final String HEADER =
			"student_id,full_name,email,date_of_birth,grade_level,section,maths,science,english,social_studies,second_language";
	private final CsvFileParser parser = new CommonsCsvFileParser();

	@Test
	void headerIsPhysicalLineOneAndBlankLinesAreCounted() throws Exception {
		ParsedCsv parsed = parser.parse(resource("blank-lines-lf.csv"));
		assertTrue(parsed.fatalFailure().isEmpty());
		assertEquals(List.of(2, 5), parsed.rows().stream().map(ParsedCsv.Row::physicalLine).toList());
	}

	@Test
	void embeddedNewlinesAndMalformedRecordsKeepPhysicalLines() throws Exception {
		ParsedCsv parsed = parser.parse(resource("malformed-after-embedded-lf.csv"));
		assertEquals(List.of(2, 4, 6, 8, 9), parsed.rows().stream().map(ParsedCsv.Row::physicalLine).toList());
		ParsedCsv.Row malformed = parsed.rows().stream().filter(ParsedCsv.Row::wrongFieldCount).findFirst().orElseThrow();
		assertEquals(6, malformed.physicalLine());
		assertTrue(parsed.rows().stream().filter(row -> row.physicalLine() == 9).anyMatch(row -> !row.wrongFieldCount()));
	}

	@Test
	void embeddedNewlineValuesStayIntact() throws Exception {
		ParsedCsv parsed = parser.parse(resource("embedded-newline-lf.csv"));
		assertEquals("first\nsecond\nthird", parsed.rows().get(0).fields().get("note"));
	}

	@Test
	void crlfMixedAndLoneCrAreSupported() throws Exception {
		assertEquals(List.of(2, 3), lines("ordinary-crlf.csv"));
		assertEquals(List.of(2, 5), lines("embedded-newline-crlf.csv"));
		assertEquals(List.of(2, 4), lines("mixed-endings.csv"));
		assertEquals(List.of(2, 4, 6, 8), lines("lone-cr.csv"));
	}

	@Test
	void supplementaryCharactersArePreserved() throws Exception {
		ParsedCsv parsed = parser.parse(resource("unicode-supplementary.csv"));
		assertTrue(parsed.rows().stream().anyMatch(row -> row.fields().values().stream()
				.anyMatch(value -> value != null && value.indexOf('\ud83d') >= 0)));
	}

	@Test
	void bomIsStrippedBeforeMapping() {
		byte[] withBom = concatenate(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF },
				(HEADER + "\nSTU001,Alice Smith,alice@example.com,2001-01-01,9,A,80,80,80,80,80\n")
						.getBytes(StandardCharsets.UTF_8));
		ParsedCsv parsed = parser.parse(withBom);
		assertTrue(parsed.fatalFailure().isEmpty());
		assertEquals(1, parsed.rows().size());
		assertEquals("STU001", parsed.rows().get(0).fields().get("student_id"));
	}

	@Test
	void reorderedHeadersExtraColumnsAndSpacedNamesAreMapped() {
		String csv = " full_name , student_id ,extra, grade_level ,section,maths,science,english,social_studies,second_language,email,date_of_birth\n"
				+ "Alice Smith,STU001,ignore,9,a,80,80,80,80,80,alice@example.com,2001-01-01\n";
		ParsedCsv parsed = parser.parse(csv.getBytes(StandardCharsets.UTF_8));
		assertTrue(parsed.fatalFailure().isEmpty());
		assertEquals("STU001", parsed.rows().get(0).fields().get("student_id"));
		assertEquals("Alice Smith", parsed.rows().get(0).fields().get("full_name"));
	}

	@Test
	void duplicateHeadersAreFatal() {
		String duplicate = HEADER.replace("full_name", "student_id");
		assertTrue(parser.parse(duplicate.getBytes(StandardCharsets.UTF_8)).fatalFailure().isPresent());
	}

	@Test
	void unterminatedQuoteIsFatalWithSafeMessage() {
		String csv = HEADER + "\nSTU001,\"Alice,2001-01-01,9,A,80,80,80,80,80\n";
		ParsedCsv parsed = parser.parse(csv.getBytes(StandardCharsets.UTF_8));
		assertTrue(parsed.fatalFailure().isPresent());
		assertFalse(parsed.fatalFailure().get().safeMessage().contains("STU001"));
		assertFalse(parsed.fatalFailure().get().safeMessage().contains("Alice"));
	}

	@Test
	void headerIsAlwaysPhysicalLineOne() throws Exception {
		ParsedCsv parsed = parser.parse(resource("ordinary-lf.csv"));
		assertTrue(parsed.fatalFailure().isEmpty());
		assertEquals(2, parsed.rows().get(0).physicalLine());
	}

	@Test
	void emptyAndHeaderOnlyFilesFailWithDistinctSafeMessages() {
		ParsedCsv empty = parser.parse(new byte[0]);
		ParsedCsv headerOnly = parser.parse(HEADER.getBytes(StandardCharsets.UTF_8));

		assertTrue(empty.fatalFailure().isPresent());
		assertTrue(empty.fatalFailure().get().safeMessage().contains("empty"));
		assertTrue(headerOnly.fatalFailure().isPresent());
		assertTrue(headerOnly.fatalFailure().get().safeMessage().contains("no data rows"));
	}

	@Test
	void allEmptyRowsAreSkippedWithoutAffectingCountersOrLineAccuracy() {
		String csv = HEADER + "\n,,,,,,,,,,\n" + "STU001,Alice Smith,alice@example.com,2001-01-01,9,A,80,80,80,80,80\n";
		ParsedCsv parsed = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

		assertTrue(parsed.fatalFailure().isEmpty());
		assertEquals(1, parsed.rows().size());
		assertEquals(3, parsed.rows().get(0).physicalLine());
	}

	@Test
	void wrongFieldCountIsFlaggedAgainstActualHeaderWidth() {
		String csv = HEADER + ",extra\nSTU001,Alice,alice@example.com,2001-01-01,9,A,80,80,80,80,80\n";
		ParsedCsv parsed = parser.parse(csv.getBytes(StandardCharsets.UTF_8));
		assertTrue(parsed.rows().get(0).wrongFieldCount());
	}

	private List<Integer> lines(String name) throws Exception {
		return parser.parse(resource(name)).rows().stream().map(ParsedCsv.Row::physicalLine).toList();
	}

	private static byte[] resource(String name) throws Exception {
		return Files.readAllBytes(Path.of("src", "test", "resources", "csv-spike", name));
	}

	private static byte[] concatenate(byte[] first, byte[] second) {
		byte[] result = new byte[first.length + second.length];
		System.arraycopy(first, 0, result, 0, first.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}
}
