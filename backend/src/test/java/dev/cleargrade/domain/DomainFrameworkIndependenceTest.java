package dev.cleargrade.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DomainFrameworkIndependenceTest {

	private static final Pattern FORBIDDEN = Pattern.compile(
			"(?<![\\w.])(?:org\\.springframework|jakarta\\.[A-Za-z]|javax\\.persistence|"
					+ "org\\.apache\\.commons\\.csv|org\\.hibernate|org\\.flywaydb|"
					+ "dev\\.cleargrade\\.(?:adapters|application|configuration))");

	@Test
	void domainSourcesContainNoForbiddenFrameworkImportsOrReferences() throws Exception {
		List<Path> sources = domainSources();
		assertTrue(sources.size() >= 18, "Domain source scan must not be vacuous");

		for (Path source : sources) {
			String text = Files.readString(source, StandardCharsets.UTF_8);
			Matcher matcher = FORBIDDEN.matcher(text);
			assertFalse(matcher.find(),
					() -> "Forbidden framework reference in " + source + ": " + matcher.group());
		}
	}

	private static List<Path> domainSources() throws Exception {
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
		try (Stream<Path> paths = Files.walk(sourceRoot)) {
			return paths.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
		}
	}
}
