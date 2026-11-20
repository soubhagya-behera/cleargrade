package dev.cleargrade.adapters.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.cleargrade.adapters.in.web.dto.AiStatusResponse;
import dev.cleargrade.adapters.in.web.dto.InsightResponse;
import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.model.AiProviderStatus;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.service.ImportInsightService;
import dev.cleargrade.application.service.InsightProviderRouter;
import org.junit.jupiter.api.Test;

class AiControllerTest {

	private static final String DUMMY_KEY = "DUMMY_TEST_KEY_NOT_A_SECRET";

	@Test
	void statusNeverExposesKeyMaterial() throws Exception {
		InsightProperties properties = new InsightProperties();
		properties.getGemini().setApiKey(DUMMY_KEY);
		InsightProviderRouter router = mock(InsightProviderRouter.class);
		when(router.status()).thenReturn(new AiProviderStatus(true, true, true, "gemini-3.8-flash", false, false,
				"", true, ImportInsightReport.Engine.GEMINI, "GEMINI_THEN_RULE_BASED", java.util.List.of()));
		AiController controller = new AiController(mock(ImportInsightService.class), router);

		AiStatusResponse status = controller.status();
		String json = new ObjectMapper().writeValueAsString(status);

		assertEquals("GEMINI", status.defaultEngine());
		assertTrue(status.geminiConfigured());
		assertFalse(json.contains(DUMMY_KEY));
		assertFalse(json.contains("apiKey"));
	}

	@Test
	void insightsEndpointDelegatesToExplicitService() {
		ImportInsightService service = mock(ImportInsightService.class);
		ImportQualityContext context = new ImportQualityContext(10, 8, 2, 8, 0, 0, 2, Map.of());
		ImportInsightReport report = new ImportInsightReport(ImportInsightReport.Engine.RULE_BASED,
				"deterministic-v1", "NO_AI_PROVIDERS_AVAILABLE", false, context, "summary", "assessment",
				List.of(), List.of(), "limits", context.fingerprint());
		when(service.analyse(7L)).thenReturn(report);
		AiController controller = new AiController(service, mock(InsightProviderRouter.class));

		InsightResponse response = controller.analyse(7L);

		assertEquals("RULE_BASED", response.engine());
		assertEquals("NO_AI_PROVIDERS_AVAILABLE", response.fallbackReason());
		assertEquals(10, response.measured().rowsRead());
		assertTrue(response.advisoryOnly());
		verify(service).analyse(7L);
	}
}
