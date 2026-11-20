package dev.cleargrade.adapters.in.csv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.cleargrade.application.exception.CsvFailureCode;
import dev.cleargrade.application.model.ParsedCsv;
import dev.cleargrade.application.port.in.CsvFileParser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/** Production CSV reader: BOM-aware, header-mapped, with approved physical-line tracking (C-4). */
@Component
public class CommonsCsvFileParser implements CsvFileParser {

	private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
			.setDelimiter(',')
			.setQuote('"')
			.setCommentMarker((Character) null)
			.setIgnoreEmptyLines(true)
			.setIgnoreSurroundingSpaces(false)
			.setSkipHeaderRecord(false)
			.get();
	private static final char BOM = '\ufeff';
	private static final String FATAL_PARSE_MESSAGE =
			"The CSV file could not be parsed; it may contain an unterminated quoted field";
	private static final String FATAL_HEADER_MESSAGE = "The CSV header contains a duplicate column";
	private static final String FATAL_EMPTY_MESSAGE = "The CSV file is empty or has no header row";
	private static final String FATAL_NO_DATA_MESSAGE = "The CSV file has a header but no data rows";

	@Override
	public ParsedCsv parse(byte[] content) {
		String text = stripBom(new String(content, StandardCharsets.UTF_8));
		PhysicalLineMap lineMap = new PhysicalLineMap(text);
		List<ParsedCsv.Row> rows = new ArrayList<>();
		Map<String, Integer> header = null;
		int headerWidth = 0;
		int lastLine = 1;

		try (CSVParser parser = CSVParser.parse(text, FORMAT)) {
			for (CSVRecord record : parser) {
				lastLine = lineMap.startingLineOf(record.getCharacterPosition());
				List<String> values = Arrays.asList(record.values());
				if (header == null) {
					header = indexHeader(values);
					headerWidth = values.size();
					if (header == null) {
						return fatal(lastLine, FATAL_HEADER_MESSAGE, null, CsvFailureCode.MISSING_REQUIRED_COLUMNS);
					}
					continue;
				}
				if (values.stream().allMatch(value -> value == null || value.isBlank())) {
					continue;
				}
				boolean wrongFieldCount = values.size() != headerWidth;
				Map<String, String> fields = wrongFieldCount ? Map.of() : mapFields(header, values);
				rows.add(new ParsedCsv.Row(lastLine, fields, wrongFieldCount));
			}
		}
		catch (IOException | RuntimeException error) {
			return fatal(lastLine, FATAL_PARSE_MESSAGE + " (near physical line " + lastLine + ")",
					error, CsvFailureCode.MALFORMED_CSV);
		}
		if (header == null) {
			return fatal(lastLine, FATAL_EMPTY_MESSAGE, null, CsvFailureCode.EMPTY_FILE);
		}
		if (rows.isEmpty()) {
			return fatal(lastLine, FATAL_NO_DATA_MESSAGE, null, CsvFailureCode.EMPTY_FILE);
		}
		return new ParsedCsv(List.copyOf(rows), Optional.empty(), Set.copyOf(header.keySet()));
	}

	private static ParsedCsv fatal(int physicalLine, String safeMessage, Throwable cause, CsvFailureCode code) {
		return new ParsedCsv(List.of(),
				Optional.of(new ParsedCsv.FatalFailure(physicalLine, safeMessage, cause, code)), Set.of());
	}

	private static Map<String, Integer> indexHeader(List<String> values) {
		Map<String, Integer> header = new LinkedHashMap<>();
		for (int index = 0; index < values.size(); index++) {
			String name = normalizeHeader(values.get(index));
			if (!name.isEmpty() && header.putIfAbsent(name, index) != null) {
				return null;
			}
		}
		return header;
	}

	private static Map<String, String> mapFields(Map<String, Integer> header, List<String> values) {
		Map<String, String> fields = new HashMap<>();
		for (Map.Entry<String, Integer> entry : header.entrySet()) {
			int index = entry.getValue();
			fields.put(entry.getKey(), index < values.size() ? values.get(index) : "");
		}
		return fields;
	}

	private static String normalizeHeader(String raw) {
		return raw == null ? "" : raw.strip().toLowerCase(java.util.Locale.ROOT);
	}

	private static String stripBom(String text) {
		return !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
	}
}
