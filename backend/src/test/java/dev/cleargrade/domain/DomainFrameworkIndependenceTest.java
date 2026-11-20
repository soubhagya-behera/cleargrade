package dev.cleargrade.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DomainFrameworkIndependenceTest {

	private static final Pattern FORBIDDEN = Pattern.compile(
			"(?<![\\w.])(?:org\\.springframework|jakarta\\.[A-Za-z]|javax\\.(?:persistence|sql)|"
					+ "java\\.sql\\.|org\\.apache\\.commons\\.csv|org\\.hibernate|org\\.flywaydb|"
					+ "dev\\.cleargrade\\.(?:adapters|application|configuration))");

	private static final Set<String> EXPECTED_SOURCES = Set.of(
			"DomainValidationException.java",
			"package-info.java",
			"batch/ImportBatch.java",
			"batch/ImportCounts.java",
			"batch/ImportStatus.java",
			"batch/PhysicalLineNumber.java",
			"batch/RowError.java",
			"batch/RowErrorCode.java",
			"grading/Grade.java",
			"grading/GradingPolicy.java",
			"grading/StudentOutcome.java",
			"student/DateOfBirth.java",
			"student/EmailAddress.java",
			"student/FullName.java",
			"student/GradeLevel.java",
			"student/Section.java",
			"student/Student.java",
			"student/StudentId.java",
			"student/Subject.java",
			"student/SubjectMarks.java");

	@Test
	void domainSourcesContainNoForbiddenFrameworkImportsOrReferences() throws Exception {
		Path root = domainSourceRoot();
		List<Path> sources = domainSources(root);
		Set<String> actual = sources.stream()
				.map(path -> root.relativize(path).toString().replace('\\', '/'))
				.collect(Collectors.toSet());
		assertEquals(EXPECTED_SOURCES, actual);
		assertEquals(EXPECTED_SOURCES.size(), sources.size());

		for (Path source : sources) {
			String text = Files.readString(source, StandardCharsets.UTF_8);
			Matcher matcher = FORBIDDEN.matcher(text);
			assertFalse(matcher.find(),
					() -> "Forbidden framework reference in " + source + ": " + matcher.group());
		}
	}

	private static Path domainSourceRoot() throws Exception {
		Path sourceRoot = Paths.get("src/main/java/dev/cleargrade/domain").toAbsolutePath().normalize();
		if (!Files.isDirectory(sourceRoot)) {
			URL resource = DomainFrameworkIndependenceTest.class.getResource("/dev/cleargrade/domain");
			if (resource != null && "file".equals(resource.getProtocol())) {
				Path classesDir = Paths.get(resource.toURI());
				Path target = classesDir.getParent().getParent().getParent().getParent();
				sourceRoot = target.resolveSibling("src/main/java/dev/cleargrade/domain");
			}
		}
		assertTrue(Files.isDirectory(sourceRoot), "Domain source root not found: " + sourceRoot);
		return sourceRoot;
	}

	private static List<Path> domainSources(Path root) throws Exception {
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
		}
	}
}
