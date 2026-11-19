package dev.cleargrade.csv.spike;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

/** C-4 proof: every record's physical starting line is recovered correctly. */
class PhysicalLineTrackingSpikeTest {

	private static final Path FIXTURES = Path.of("src", "test", "resources", "csv-spike");
	private static final CSVFormat FORMAT = CSVFormat.DEFAULT;

	@Test
	void headerIsAlwaysPhysicalLineOne() throws IOException {
		for (String fixture : fixtureNames()) {
			List<TrackedRecord> records = read(fixture);
			assertThat(records).as(fixture).isNotEmpty();
			assertThat(records.get(0).physicalLine()).as(fixture).isEqualTo(1);
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("expectedLines")
	void reportsActualPhysicalStartingLines(String fixture, int[] expected) throws IOException {
		assertThat(physicalLines(fixture))
				.as("physical starting lines for %s", fixture)
				.containsExactly(boxed(expected));
	}

	@Test
	void blankPhysicalLinesAreCounted() throws IOException {
		assertThat(physicalLines("blank-lines-lf.csv")).containsExactly(1, 2, 5);
	}

	@Test
	void embeddedNewlinesAreCountedAndValuesStayIntact() throws IOException {
		List<TrackedRecord> records = read("embedded-newline-lf.csv");
		assertThat(records).extracting(TrackedRecord::physicalLine).containsExactly(1, 2, 5);
		assertThat(records.get(1).values().get(1)).isEqualTo("first\nsecond\nthird");
	}

	@Test
	void wrongFieldCountAfterEmbeddedNewlineGetsCorrectLineAndParsingContinues() throws IOException {
		List<TrackedRecord> records = read("malformed-after-embedded-lf.csv");
		assertThat(records).extracting(TrackedRecord::physicalLine).containsExactly(1, 2, 4, 6, 8, 9);

		TrackedRecord malformed = records.get(3);
		assertThat(malformed.physicalLine()).isEqualTo(6);
		assertThat(malformed.fieldCount()).isEqualTo(2);
		assertThat(malformed.consistent()).isFalse();
		assertThat(malformed.values()).containsExactly("3", "Short");

		TrackedRecord afterMalformed = records.get(5);
		assertThat(afterMalformed.physicalLine()).isEqualTo(9);
		assertThat(afterMalformed.values()).containsExactly("5", "Eve", "final");
	}

	@Test
	void crlfAndMixedEndingsProduceTheSamePhysicalLines() throws IOException {
		assertThat(physicalLines("ordinary-crlf.csv")).containsExactly(1, 2, 3);
		assertThat(physicalLines("embedded-newline-crlf.csv")).containsExactly(1, 2, 5);
		assertThat(physicalLines("malformed-after-embedded-crlf.csv")).containsExactly(1, 2, 4, 6, 8, 9);
		assertThat(physicalLines("mixed-endings.csv")).containsExactly(1, 2, 4);
	}

	@Test
	void repeatedExecutionIsDeterministic() throws IOException {
		int[] expected = { 1, 2, 4, 6, 8, 9 };
		List<List<Integer>> observed = new ArrayList<>();
		for (int attempt = 0; attempt < 100; attempt++) {
			observed.add(physicalLines("malformed-after-embedded-crlf.csv"));
		}
		assertThat(observed).hasSize(100).allSatisfy(lines -> assertThat(lines).containsExactly(boxed(expected)));
	}

	private static List<Integer> physicalLines(String fixture) throws IOException {
		return read(fixture).stream().map(TrackedRecord::physicalLine).toList();
	}

	private static List<TrackedRecord> read(String fixture) throws IOException {
		return new PhysicalLineCsvReader(FORMAT).read(FIXTURES.resolve(fixture));
	}

	private static Integer[] boxed(int[] values) {
		return Arrays.stream(values).boxed().toArray(Integer[]::new);
	}

	private static List<String> fixtureNames() {
		return expectedLines().map(arguments -> (String) arguments.get()[0]).toList();
	}

	private static Stream<Arguments> expectedLines() {
		return Stream.of(
				Arguments.of("ordinary-lf.csv", new int[] { 1, 2, 3 }),
				Arguments.of("ordinary-crlf.csv", new int[] { 1, 2, 3 }),
				Arguments.of("blank-lines-lf.csv", new int[] { 1, 2, 5 }),
				Arguments.of("embedded-newline-lf.csv", new int[] { 1, 2, 5 }),
				Arguments.of("embedded-newline-crlf.csv", new int[] { 1, 2, 5 }),
				Arguments.of("malformed-after-embedded-lf.csv", new int[] { 1, 2, 4, 6, 8, 9 }),
				Arguments.of("malformed-after-embedded-crlf.csv", new int[] { 1, 2, 4, 6, 8, 9 }),
				Arguments.of("mixed-endings.csv", new int[] { 1, 2, 4 }));
	}
}
