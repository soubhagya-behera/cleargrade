package dev.cleargrade.csv.spike;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** Isolated proof reader: Commons CSV parsing plus independent physical-line mapping. */
final class PhysicalLineCsvReader {

	private static final char BOM = '\ufeff';

	private final CSVFormat format;

	PhysicalLineCsvReader(CSVFormat format) {
		this.format = format;
	}

	List<TrackedRecord> read(Path fixture) throws IOException {
		String text = Files.readString(fixture, StandardCharsets.UTF_8);
		return read(stripBom(text));
	}

	List<TrackedRecord> read(String source) throws IOException {
		PhysicalLineMap lineMap = new PhysicalLineMap(source);
		List<TrackedRecord> records = new ArrayList<>();
		try (CSVParser parser = CSVParser.parse(source, this.format)) {
			for (CSVRecord record : parser) {
				long characterPosition = record.getCharacterPosition();
				records.add(new TrackedRecord(
						lineMap.startingLineOf(characterPosition),
						characterPosition,
						record.size(),
						false,
						List.copyOf(Arrays.asList(record.values()))));
			}
		}
		if (records.isEmpty()) {
			return records;
		}
		int headerFieldCount = records.get(0).fieldCount();
		return records.stream()
				.map(record -> new TrackedRecord(
						record.physicalLine(),
						record.characterPosition(),
						record.fieldCount(),
						record.fieldCount() == headerFieldCount,
						record.values()))
				.toList();
	}

	private static String stripBom(String text) {
		return !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
	}
}
