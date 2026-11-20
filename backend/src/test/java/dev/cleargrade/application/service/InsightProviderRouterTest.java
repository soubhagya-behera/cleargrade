package dev.cleargrade.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.port.out.ImportInsightProvider;
import org.junit.jupiter.api.Test;

class InsightProviderRouterTest {

	private static final ImportQualityContext CONTEXT = new ImportQualityContext(10, 8, 2, 8, 0, 0, 2, java.util.Map.of());

	@Test
	void geminiIsPrimaryWhenConfigured() {
		InsightProperties properties = new InsightProperties();
		properties.getGemini().setApiKey("test-placeholder-not-a-secret");
		ImportInsightProvider gemini = provider(ImportInsightReport.Engine.GEMINI);
		ImportInsightProvider ollama = provider(ImportInsightReport.Engine.OLLAMA);
		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
				new DeterministicInsightService(), properties);

		ImportInsightReport report = router.analyse(CONTEXT);

		assertEquals(ImportInsightReport.Engine.GEMINI, report.engine());
		verify(ollama, never()).analyse(any());
	}

	@Test
	void quotaConnectionTimeoutAndServerErrorsFallBackToOllama() {
		for (int statusCase = 0; statusCase < 3; statusCase++) {
			InsightProperties properties = new InsightProperties();
			properties.getGemini().setApiKey("test-placeholder-not-a-secret");
			properties.getOllama().setBaseUrl("http://127.0.0.1:11434");
			properties.getOllama().setModel("test-model");
			ImportInsightProvider gemini = mock(ImportInsightProvider.class);
			when(gemini.engine()).thenReturn(ImportInsightReport.Engine.GEMINI);
			when(gemini.modelVersion()).thenReturn("gemini-test");
			String reason = switch (statusCase) {
				case 0 -> "quota";
				case 1 -> "timeout";
				default -> "server error";
			};
			when(gemini.analyse(any())).thenThrow(new InsightProviderException(reason,
					InsightProviderException.Category.FALLBACK, null));
			ImportInsightProvider ollama = provider(ImportInsightReport.Engine.OLLAMA);
			when(ollama.available()).thenReturn(true);
			InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
					new DeterministicInsightService(), properties);

			ImportInsightReport report = router.analyse(CONTEXT);

			assertEquals(ImportInsightReport.Engine.OLLAMA, report.engine());
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

		ImportInsightReport report = router.analyse(CONTEXT);

		assertEquals(ImportInsightReport.Engine.RULE_BASED, report.engine());
		assertEquals("NO_AI_PROVIDERS_AVAILABLE", report.fallbackReason());
	}

	@Test
	void configurationErrorsStayVisibleAndDoNotFallBack() {
		InsightProperties properties = new InsightProperties();
		properties.getGemini().setApiKey("test-placeholder-not-a-secret");
		ImportInsightProvider gemini = mock(ImportInsightProvider.class);
		when(gemini.engine()).thenReturn(ImportInsightReport.Engine.GEMINI);
		when(gemini.available()).thenReturn(true);
		when(gemini.analyse(any())).thenThrow(new InsightProviderException("auth",
				InsightProviderException.Category.CONFIGURATION, null));
		ImportInsightProvider ollama = provider(ImportInsightReport.Engine.OLLAMA);
		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
				new DeterministicInsightService(), properties);

		InsightProviderException thrown = org.junit.jupiter.api.Assertions.assertThrows(
				InsightProviderException.class, () -> router.analyse(CONTEXT));
		assertEquals(InsightProviderException.Category.CONFIGURATION, thrown.category());
		verify(ollama, never()).analyse(any());
	}

	@Test
	void successfulFallbackCarriesSafeReason() {
		InsightProperties properties = new InsightProperties();
		properties.getGemini().setApiKey("test-placeholder-not-a-secret");
		properties.getOllama().setBaseUrl("http://127.0.0.1:11434");
		properties.getOllama().setModel("test-model");
		ImportInsightProvider gemini = mock(ImportInsightProvider.class);
		when(gemini.engine()).thenReturn(ImportInsightReport.Engine.GEMINI);
		when(gemini.available()).thenReturn(true);
		when(gemini.analyse(any())).thenThrow(new InsightProviderException("quota",
				InsightProviderException.Category.FALLBACK, null));
		ImportInsightProvider ollama = provider(ImportInsightReport.Engine.OLLAMA);
		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
				new DeterministicInsightService(), properties);

		ImportInsightReport report = router.analyse(CONTEXT);

		assertEquals(ImportInsightReport.Engine.OLLAMA, report.engine());
		assertEquals("GEMINI_UNAVAILABLE", report.fallbackReason());
	}

	@Test
	void disabledAiUsesDeterministicEngineAndLabelsReason() {
		InsightProperties properties = new InsightProperties();
		properties.setEnabled(false);
		ImportInsightProvider gemini = provider(ImportInsightReport.Engine.GEMINI);
		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini),
				new DeterministicInsightService(), properties);

		ImportInsightReport report = router.analyse(CONTEXT);

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
				() -> router.analyse(CONTEXT));
	}

	@Test
	void statusReportsSafeRoutingAndEngineVersion() {
		InsightProperties properties = new InsightProperties();
		properties.getGemini().setApiKey("test-placeholder-not-a-secret");
		properties.getOllama().setBaseUrl("http://127.0.0.1:11434");
		properties.getOllama().setModel("test-model");
		ImportInsightProvider gemini = provider(ImportInsightReport.Engine.GEMINI);
		ImportInsightProvider ollama = provider(ImportInsightReport.Engine.OLLAMA);
		when(ollama.available()).thenReturn(true);
		InsightProviderRouter router = new InsightProviderRouter(List.of(gemini, ollama),
				new DeterministicInsightService(), properties);

		assertEquals("GEMINI_THEN_OLLAMA_THEN_RULE_BASED", router.status().routing());
		assertTrue(router.status().routingReasons().isEmpty());
		assertTrue(router.engineVersion().contains("GEMINI:test-model"));
		assertTrue(router.engineVersion().contains("OLLAMA:test-model"));
		assertTrue(router.engineVersion().contains("RULE_BASED:deterministic-v1"));
		properties.getGemini().setApiKey("");
		properties.getOllama().setBaseUrl("");
		assertEquals("RULE_BASED_ONLY", router.status().routing());
		assertTrue(router.status().routingReasons().contains("GEMINI_NOT_CONFIGURED"));
	}

	private static ImportInsightProvider provider(ImportInsightReport.Engine engine) {
		ImportInsightProvider provider = mock(ImportInsightProvider.class);
		when(provider.engine()).thenReturn(engine);
		when(provider.modelVersion()).thenReturn("test-model");
		when(provider.available()).thenReturn(true);
		when(provider.analyse(any())).thenReturn(new ImportInsightReport(engine, "test-model", null, false,
				CONTEXT, "summary", "assessment", List.of(), List.of(), "limitations", CONTEXT.fingerprint()));
		return provider;
	}
}
