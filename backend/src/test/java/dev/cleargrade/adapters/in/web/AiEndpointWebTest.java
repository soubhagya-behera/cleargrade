package dev.cleargrade.adapters.in.web;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import dev.cleargrade.application.exception.BatchNotFoundException;
import dev.cleargrade.application.exception.InsightBatchNotCompletedException;
import dev.cleargrade.application.exception.InsightCooldownException;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.exception.InsightUnavailableException;
import dev.cleargrade.application.model.AiProviderStatus;
import dev.cleargrade.application.model.ImportInsightFinding;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.service.ImportInsightService;
import dev.cleargrade.application.service.InsightProviderRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP contract for both AI endpoints, including controlled error mapping. */
class AiEndpointWebTest {

	private static final String KEY_SENTINEL = "DUMMY_KEY_VALUE_SHOULD_NEVER_APPEAR";
	private static final String PROVIDER_PAYLOAD_SENTINEL = "RAW_PROVIDER_BODY_SHOULD_NEVER_APPEAR";
	private static final String PII_SENTINEL = "STU999,PII_SENTINEL_SHOULD_NEVER_APPEAR";

	private ImportInsightService service;
	private InsightProviderRouter router;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.service = mock(ImportInsightService.class);
		this.router = mock(InsightProviderRouter.class);
		this.mockMvc = MockMvcBuilders.standaloneSetup(new AiController(this.service, this.router))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void statusEndpointIsSafeAndReportsRouting() throws Exception {
		when(this.router.status()).thenReturn(new AiProviderStatus(true, true, "gemini-3.8-flash",
				false, false, false, "", false, true, ImportInsightReport.Engine.GEMINI,
				"GEMINI_THEN_RULE_BASED", List.of("OLLAMA_NOT_CONFIGURED")));

		String body = this.mockMvc.perform(get("/api/ai/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.geminiConfigured").value(true))
				.andExpect(jsonPath("$.inferenceTested").value(false))
				.andExpect(jsonPath("$.ollamaReachable").value(false))
				.andExpect(jsonPath("$.ollamaModelPresent").value(false))
				.andExpect(jsonPath("$.geminiModel").value("gemini-3.8-flash"))
				.andExpect(jsonPath("$.defaultEngine").value("GEMINI"))
				.andExpect(jsonPath("$.routingReasons[0]").value("OLLAMA_NOT_CONFIGURED"))
				.andReturn().getResponse().getContentAsString();

		org.junit.jupiter.api.Assertions.assertFalse(body.contains(KEY_SENTINEL));
		org.junit.jupiter.api.Assertions.assertFalse(body.toLowerCase().contains("apikey"));
	}

	@Test
	void refreshParameterIsForwardedToExplicitAnalysis() throws Exception {
		ImportQualityContext context = new ImportQualityContext(10, 8, 2, 8, 0, 0, 2, Map.of());
		when(this.service.analyse(7L, true)).thenReturn(new ImportInsightReport(
				ImportInsightReport.Engine.GEMINI, "gemini-3.8-flash", null, false, context,
				"summary", "assessment", List.of(), List.of(), "limits", context.fingerprint()));

		this.mockMvc.perform(post("/api/imports/7/insights?refresh=true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.engine").value("GEMINI"));

		org.mockito.Mockito.verify(this.service).analyse(7L, true);
	}

	@Test
	void insightEndpointSeparatesMeasuredFactsFromInterpretation() throws Exception {
		ImportQualityContext context = new ImportQualityContext(100, 80, 20, 80, 0, 0, 22,
				Map.of("DUPLICATE_ID", 4));
		ImportInsightFinding finding = new ImportInsightFinding("Duplicates", "MEDIUM",
				"DUPLICATE_ID_COUNT", "4", "Four duplicate IDs were measured.", "Review the export.");
		when(this.service.analyse(7L, false)).thenReturn(new ImportInsightReport(
				ImportInsightReport.Engine.OLLAMA, "test-model", "GEMINI:GEMINI_SERVER", false, context,
				"Aggregate summary", "Aggregate assessment", List.of(finding), List.of("Review the export."),
				"Aggregates only", context.fingerprint()));

		this.mockMvc.perform(post("/api/imports/7/insights"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.engine").value("OLLAMA"))
				.andExpect(jsonPath("$.fallbackReason").value("GEMINI:GEMINI_SERVER"))
				.andExpect(jsonPath("$.cached").value(false))
				.andExpect(jsonPath("$.measured.rowsRead").value(100))
				.andExpect(jsonPath("$.measured.errorCountsByCode.DUPLICATE_ID").value(4))
				.andExpect(jsonPath("$.findings[0].evidenceMetric").value("DUPLICATE_ID_COUNT"))
				.andExpect(jsonPath("$.findings[0].evidenceValue").value("4"))
				.andExpect(jsonPath("$.summary").value("Aggregate summary"))
				.andExpect(jsonPath("$.advisoryOnly").value(true));
	}

	@Test
	void unknownAndUncompletedBatchesAreRejected() throws Exception {
		when(this.service.analyse(1L, false)).thenThrow(new BatchNotFoundException(1L));
		when(this.service.analyse(2L, false)).thenThrow(new InsightBatchNotCompletedException(2L));

		this.mockMvc.perform(post("/api/imports/1/insights"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BATCH_NOT_FOUND"));
		this.mockMvc.perform(post("/api/imports/2/insights"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BATCH_NOT_COMPLETED"));
	}

	@Test
	void cooldownConfigurationAndProviderFailuresAreDistinct() throws Exception {
		org.mockito.Mockito.doThrow(new InsightCooldownException()).when(this.service).analyse(anyLong(), anyBoolean());
		this.mockMvc.perform(post("/api/imports/7/insights"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("ANALYSIS_COOLDOWN"));

		org.mockito.Mockito.doThrow(new InsightProviderException("auth",
				InsightProviderException.Category.CONFIGURATION, null))
				.when(this.service).analyse(anyLong(), anyBoolean());
		this.mockMvc.perform(post("/api/imports/7/insights"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_CONFIGURATION_ERROR"));

		org.mockito.Mockito.doThrow(new InsightProviderException("provider",
				InsightProviderException.Category.FALLBACK, null))
				.when(this.service).analyse(anyLong(), anyBoolean());
		this.mockMvc.perform(post("/api/imports/7/insights"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("ANALYSIS_PROVIDER_FAILED"));
	}

	@Test
	void aiErrorResponsesSanitizeSensitiveContent() throws Exception {
		RuntimeException providerCause = new RuntimeException("raw=" + PROVIDER_PAYLOAD_SENTINEL
				+ " key=" + KEY_SENTINEL + " pii=" + PII_SENTINEL);

		org.mockito.Mockito.doThrow(new InsightCooldownException())
				.when(this.service).analyse(anyLong(), anyBoolean());
		String cooldownBody = this.mockMvc.perform(post("/api/imports/7/insights"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("ANALYSIS_COOLDOWN"))
				.andExpect(jsonPath("$.message").value("An analysis was requested too recently for this import"))
				.andExpect(jsonPath("$.details").isEmpty())
				.andExpect(jsonPath("$.correlationId").value(org.hamcrest.Matchers.nullValue()))
				.andReturn().getResponse().getContentAsString();

		org.mockito.Mockito.doThrow(new InsightProviderException(
				"A configured AI provider rejected the request",
				InsightProviderException.Category.CONFIGURATION, providerCause))
				.when(this.service).analyse(anyLong(), anyBoolean());
		String configurationBody = this.mockMvc.perform(post("/api/imports/7/insights"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_CONFIGURATION_ERROR"))
				.andExpect(jsonPath("$.message").value("A configured AI provider rejected the request"))
				.andExpect(jsonPath("$.details").isEmpty())
				.andExpect(jsonPath("$.correlationId").value(org.hamcrest.Matchers.nullValue()))
				.andReturn().getResponse().getContentAsString();

		for (String body : List.of(cooldownBody, configurationBody)) {
			org.junit.jupiter.api.Assertions.assertFalse(body.contains("stackTrace"), body);
			org.junit.jupiter.api.Assertions.assertFalse(body.contains("AiEndpointWebTest"), body);
			org.junit.jupiter.api.Assertions.assertFalse(body.contains(KEY_SENTINEL), body);
			org.junit.jupiter.api.Assertions.assertFalse(body.contains(PROVIDER_PAYLOAD_SENTINEL), body);
			org.junit.jupiter.api.Assertions.assertFalse(body.contains(PII_SENTINEL), body);
		}
	}

	@Test
	void unavailableEngineReturns503() throws Exception {
		when(this.service.analyse(7L, false)).thenThrow(new InsightUnavailableException());
		this.mockMvc.perform(post("/api/imports/7/insights"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("ANALYSIS_UNAVAILABLE"));
	}
}
