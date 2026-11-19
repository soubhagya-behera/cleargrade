package dev.cleargrade.csv.spike;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

/** Evidence that parser-native record numbers and current line are NOT physical starting lines. */
class NaiveMetadataEvidenceTest {

	private static final Path FIXTURE = Path.of("src", "test", "resources", "csv-spike", "malformed-after-embedded-crlf.csv");

	@Test
	void recordNumbersAreSequentialAndDivergeFromPhysicalLines() throws IOException {
		assertThat(recordNumbers()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
		assertThat(new PhysicalLineCsvReader(CSVFormat.DEFAULT).read(FIXTURE))
				.extracting(TrackedRecord::physicalLine)
				.containsExactly(1, 2, 4, 6, 8, 9);
	}

	@Test
	void parserCurrentLineIsNotTheRecordsStartingLine() throws IOException {
		assertThat(currentLines()).containsExactly(1L, 2L, 5L, 6L, 8L, 9L);
	}

	private static List<Long> recordNumbers() throws IOException {
		List<Long> values = new ArrayList<>();
		try (CSVParser parser = CSVParser.parse(Files.readString(FIXTURE, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
			for (CSVRecord record : parser) {
				values.add(record.getRecordNumber());
			}
		}
		return values;
	}

	private static List<Long> currentLines() throws IOException {
		List<Long> values = new ArrayList<>();
		try (CSVParser parser = CSVParser.parse(Files.readString(FIXTURE, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
			for (CSVRecord record : parser) {
				values.add(parser.getCurrentLineNumber());
			}
		}
		return values;
	}
}
