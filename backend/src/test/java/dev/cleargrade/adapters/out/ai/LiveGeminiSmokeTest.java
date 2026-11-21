package dev.cleargrade.adapters.out.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;

import dev.cleargrade.InsightFixtures;
import dev.cleargrade.adapters.out.ai.gemini.GeminiInteractionsAdapter;
import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Opt-in live smoke check. Runs only with CLEARGRADE_LIVE_GEMINI_SMOKE=true and a
 * configured GEMINI_API_KEY. The key is read from the environment and never logged.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "CLEARGRADE_LIVE_GEMINI_SMOKE", matches = "true")
class LiveGeminiSmokeTest {

	@Test
	void configuredGeminiCanReturnValidatedStructuredOutput() {
		String key = System.getenv("GEMINI_API_KEY");
		assumeTrue(key != null && !key.isBlank(), "GEMINI_API_KEY is not configured");

		InsightProperties properties = new InsightProperties();
		properties.getGemini().setApiKey(key);
		String model = System.getenv("GEMINI_MODEL");
		properties.getGemini().setModel(model == null || model.isBlank() ? "gemini-3.8-flash" : model);
		ImportQualityContext context = new ImportQualityContext(100, 80, 20, 80, 0, 0, 20,
				Map.of("MISSING_REQUIRED", 5, "INVALID_FORMAT", 5, "OUT_OF_RANGE", 5,
						"DUPLICATE_ID", 5, "WRONG_FIELD_COUNT", 0));

		ImportInsightReport report = new GeminiInteractionsAdapter(properties)
				.analyse(InsightFixtures.assessment(context));

		assertNotNull(report);
		assertFalse(report.summary().isBlank());
		assertFalse(report.measuredFacts().fingerprint().isBlank());
	}
}
