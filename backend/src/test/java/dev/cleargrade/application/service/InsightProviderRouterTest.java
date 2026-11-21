package dev.cleargrade.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import dev.cleargrade.InsightFixtures;
import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.InsightTelemetry;
import dev.cleargrade.application.model.OllamaAvailability;
import dev.cleargrade.application.model.QualityAssessment;
import dev.cleargrade.application.port.out.ImportInsightProvider;
import org.junit.jupiter.api.Test;

class InsightProviderRouterTest {

	private static final QualityAssessment ASSESSMENT = InsightFixtures.syntheticBatch4();

	@Test
	void geminiIsPrimaryWhenConfigured() {
		InsightProperties properties = new InsightProperties();
		properties.getGemini().setApiKey("test-placeholder-not-a-secret");
		ImportInsightProvider gemini = provider(ImportInsightReport.Engine.GEMINI);
		ImportInsightProvider ollama = provider(ImportInsightReport.Engine.OLLAMA);
		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
				new DeterministicInsightService(), properties);

		ImportInsightReport report = router.analyse(ASSESSMENT);

		assertEquals(ImportInsightReport.Engine.GEMINI, report.engine());
		verify(ollama, never()).analyse(any());
	}

	@Test
	void quotaTimeoutAndServerErrorsFallBackToOllama() {
		for (int statusCase = 0; statusCase < 3; statusCase++) {
			InsightProperties properties = properties();
			String code = switch (statusCase) {
				case 0 -> "GEMINI_QUOTA";
				case 1 -> "GEMINI_NETWORK";
				default -> "GEMINI_SERVER";
			};
			ImportInsightProvider gemini = failing(ImportInsightReport.Engine.GEMINI, code, null);
			ImportInsightProvider ollama = provider(ImportInsightReport.Engine.OLLAMA);
			InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
					new DeterministicInsightService(), properties);

			ImportInsightReport report = router.analyse(ASSESSMENT);

			assertEquals(ImportInsightReport.Engine.OLLAMA, report.engine());
			assertEquals("GEMINI:" + code, report.fallbackReason());
			verify(ollama).analyse(any());
		}
	}

	@Test
	void bothAiProvidersUnavailableUsesDeterministicEngine() {
		InsightProperties properties = new InsightProperties();
		ImportInsightProvider gemini = provider(ImportInsightReport.Engine.GEMINI);
		ImportInsightProvider ollama = provider(ImportInsightReport.Engine.OLLAMA);
		when(ollama.available()).thenReturn(false);
		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
				new DeterministicInsightService(), properties);

		ImportInsightReport report = router.analyse(ASSESSMENT);

		assertEquals(ImportInsightReport.Engine.RULE_BASED, report.engine());
		assertEquals("NO_AI_PROVIDERS_AVAILABLE", report.fallbackReason());
	}

	@Test
	void configurationErrorsStayVisibleAndDoNotFallBack() {
		InsightProperties properties = properties();
		ImportInsightProvider gemini = failing(ImportInsightReport.Engine.GEMINI, "GEMINI_AUTH", null);
		org.mockito.Mockito.doThrow(new InsightProviderException("auth",
				InsightProviderException.Category.CONFIGURATION, "GEMINI_AUTH", null, null))
				.when(gemini).analyse(any());
		ImportInsightProvider ollama = provider(ImportInsightReport.Engine.OLLAMA);
		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
				new DeterministicInsightService(), properties);

		InsightProviderException thrown = org.junit.jupiter.api.Assertions.assertThrows(
				InsightProviderException.class, () -> router.analyse(ASSESSMENT));
		assertEquals(InsightProviderException.Category.CONFIGURATION, thrown.category());
		verify(ollama, never()).analyse(any());
	}

	@Test
	void successfulFallbackCarriesSafeReasonAndGeminiTelemetry() {
		InsightProperties properties = properties();
		ImportInsightProvider gemini = failing(ImportInsightReport.Engine.GEMINI, "GEMINI_SERVER",
				InsightTelemetry.unavailable("gemini-3.8-flash", "HTTP_503", null, 321L));
		ImportInsightProvider ollama = provider(ImportInsightReport.Engine.OLLAMA);
		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
				new DeterministicInsightService(), properties);

		ImportInsightReport report = router.analyse(ASSESSMENT);

		assertEquals(ImportInsightReport.Engine.OLLAMA, report.engine());
		assertEquals("GEMINI:GEMINI_SERVER", report.fallbackReason());
		assertEquals(1, report.providerFailures().size());
		assertEquals("GEMINI", report.providerFailures().get(0).engine());
		assertEquals("HTTP_503", report.providerFailures().get(0).telemetry().providerStatus());
		assertEquals(321L, report.providerFailures().get(0).telemetry().durationMs());
		assertEquals("HTTP_200", report.telemetry().providerStatus());
	}

	@Test
	void semanticRejectionAlsoContinuesTheSafeChain() {
		InsightProperties properties = properties();
		ImportInsightProvider gemini = failing(ImportInsightReport.Engine.GEMINI,
				"GEMINI_SEMANTIC_REJECTED", null);
		ImportInsightProvider ollama = failing(ImportInsightReport.Engine.OLLAMA,
				"OLLAMA_SEMANTIC_REJECTED", null);
		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
				new DeterministicInsightService(), properties);

		ImportInsightReport report = router.analyse(ASSESSMENT);

		assertEquals(ImportInsightReport.Engine.RULE_BASED, report.engine());
		assertEquals("GEMINI:GEMINI_SEMANTIC_REJECTED;OLLAMA:OLLAMA_SEMANTIC_REJECTED",
				report.fallbackReason());
		assertEquals(2, report.providerFailures().size());
		assertTrue(report.findings().stream().allMatch(
				finding -> finding.evidenceValue().equals(ASSESSMENT.context().evidenceValue(
						dev.cleargrade.application.model.InsightEvidenceMetric
								.valueOf(finding.evidenceMetric())))));
	}

	@Test
	void disabledAiUsesDeterministicEngineAndLabelsReason() {
		InsightProperties properties = new InsightProperties();
		properties.setEnabled(false);
		ImportInsightProvider gemini = provider(ImportInsightReport.Engine.GEMINI);
		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini),
				new DeterministicInsightService(), properties);

		ImportInsightReport report = router.analyse(ASSESSMENT);

		assertEquals(ImportInsightReport.Engine.RULE_BASED, report.engine());
		assertEquals("AI_DISABLED", report.fallbackReason());
		verify(gemini, never()).analyse(any());
	}

	@Test
	void deterministicEngineCanBeDisabledForControlled503() {
		InsightProperties properties = new InsightProperties();
		properties.setDeterministicEnabled(false);
		InsightProviderRouter router = new InsightProviderRouter(List.of(),
				new DeterministicInsightService(), properties);

		org.junit.jupiter.api.Assertions.assertThrows(dev.cleargrade.application.exception.InsightUnavailableException.class,
				() -> router.analyse(ASSESSMENT));
	}

	@Test
	void statusReportsSafeRoutingAndEngineVersion() {
		InsightProperties properties = properties();
		ImportInsightProvider gemini = provider(ImportInsightReport.Engine.GEMINI);
		ImportInsightProvider ollama = provider(ImportInsightReport.Engine.OLLAMA);
		when(ollama.ollamaAvailability()).thenReturn(new OllamaAvailability(true, true));
		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
				new DeterministicInsightService(), properties);

		assertEquals("GEMINI_THEN_OLLAMA_THEN_RULE_BASED", router.status().routing());
		assertFalse(router.status().inferenceTested());
		assertTrue(router.status().routingReasons().isEmpty());
		assertTrue(router.engineVersion().contains("GEMINI:test-model"));
		assertTrue(router.engineVersion().contains("RULE_BASED:deterministic-v1"));
	}

	private static InsightProperties properties() {
		InsightProperties properties = new InsightProperties();
		properties.getGemini().setApiKey("test-placeholder-not-a-secret");
		properties.getOllama().setBaseUrl("http://127.0.0.1:11434");
		properties.getOllama().setModel("test-model");
		return properties;
	}

	private static ImportInsightProvider provider(ImportInsightReport.Engine engine) {
		ImportInsightProvider provider = mock(ImportInsightProvider.class);
		when(provider.engine()).thenReturn(engine);
		when(provider.modelVersion()).thenReturn("test-model");
		when(provider.available()).thenReturn(true);
		when(provider.analyse(any())).thenReturn(new ImportInsightReport(engine, "test-model", null, false,
				ASSESSMENT.context(), "summary", "assessment", List.of(), List.of(), "limits",
				ASSESSMENT.context().fingerprint(),
				InsightTelemetry.unavailable("test-model", "HTTP_200", null, 5L)));
		return provider;
	}

	private static ImportInsightProvider failing(ImportInsightReport.Engine engine, String code,
			InsightTelemetry telemetry) {
		ImportInsightProvider provider = mock(ImportInsightProvider.class);
		when(provider.engine()).thenReturn(engine);
		when(provider.modelVersion()).thenReturn("test-model");
		when(provider.available()).thenReturn(true);
		when(provider.analyse(any())).thenThrow(new InsightProviderException("failure",
				InsightProviderException.Category.FALLBACK, code, null, telemetry));
		return provider;
	}
}
