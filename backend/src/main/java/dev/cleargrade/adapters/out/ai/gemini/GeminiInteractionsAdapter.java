package dev.cleargrade.adapters.out.ai.gemini;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.cleargrade.adapters.out.ai.InsightPromptFactory;
import dev.cleargrade.adapters.out.ai.InsightResponseParser;
import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.port.out.ImportInsightProvider;
import org.springframework.stereotype.Component;

/** Gemini Interactions REST adapter. Stateless, store=false, single request, no retries. */
@Component
public class GeminiInteractionsAdapter implements ImportInsightProvider {

	private static final String API_REVISION = "2026-05-20";
	private static final ObjectMapper MAPPER = new ObjectMapper()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	private final InsightProperties properties;

	public GeminiInteractionsAdapter(InsightProperties properties) {
		this.properties = properties;
	}

	@Override
	public ImportInsightReport.Engine engine() {
		return ImportInsightReport.Engine.GEMINI;
	}

	@Override
	public String modelVersion() {
		return this.properties.getGemini().getModel();
	}

	@Override
	public ImportInsightReport analyse(ImportQualityContext context) throws InsightProviderException {
		InsightProperties.Gemini config = this.properties.getGemini();
		if (!config.configured()) {
			throw new InsightProviderException("Gemini is not configured",
					InsightProviderException.Category.CONFIGURATION, null);
		}
		String prompt = InsightPromptFactory.prompt(context, this.properties.getMaxPromptChars());
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("model", config.getModel());
		request.put("input", prompt);
		request.put("store", false);
		Map<String, Object> responseFormat = new LinkedHashMap<>();
		responseFormat.put("type", "text");
		responseFormat.put("mime_type", "application/json");
		responseFormat.put("schema", InsightPromptFactory.responseSchema());
		request.put("response_format", responseFormat);

		try {
			String body = MAPPER.writeValueAsString(request);
			HttpClient client = HttpClient.newBuilder()
					.connectTimeout(Duration.ofMillis(config.getTimeoutConnectMs()))
					.build();
			HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.getEndpoint()))
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.header("x-goog-api-key", config.getApiKey())
					.header("Api-Revision", API_REVISION)
					.timeout(Duration.ofMillis(config.getTimeoutReadMs()))
					.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
					.build();
			HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
			int status = response.statusCode();
			if (status == 401 || status == 403) {
				throw new InsightProviderException("Gemini rejected the configured credentials",
						InsightProviderException.Category.CONFIGURATION, null);
			}
			if (status == 429 || status >= 500) {
				throw new InsightProviderException("Gemini is temporarily unavailable",
						InsightProviderException.Category.FALLBACK, null);
			}
			if (status < 200 || status >= 300) {
				throw new InsightProviderException("Gemini rejected the analysis request",
						InsightProviderException.Category.CONFIGURATION, null);
			}
			byte[] bytes = response.body();
			int limit = this.properties.getMaxResponseChars() * 4;
			if (bytes == null || bytes.length == 0 || bytes.length > limit) {
				throw new InsightProviderException("Gemini response size was unusable",
						InsightProviderException.Category.FALLBACK, null);
			}
			String text = extractOutputText(new String(bytes, StandardCharsets.UTF_8));
			return InsightResponseParser.parse(text, context, engine(), modelVersion(), null);
		}
		catch (IOException | InterruptedException error) {
			if (error instanceof InterruptedException) Thread.currentThread().interrupt();
			throw new InsightProviderException("Gemini could not be reached",
					InsightProviderException.Category.FALLBACK, error);
		}
	}

	private static String extractOutputText(String json) {
		try {
			JsonNode root = MAPPER.readTree(json);
			String interactionStatus = root.path("status").asText("");
			if (!interactionStatus.isBlank() && !"completed".equals(interactionStatus)) {
				throw new InsightProviderException("Gemini interaction did not complete",
						InsightProviderException.Category.FALLBACK, null);
			}
			JsonNode steps = root.get("steps");
			if (steps == null || !steps.isArray()) {
				throw new InsightProviderException("Gemini response had no steps",
						InsightProviderException.Category.FALLBACK, null);
			}
			StringBuilder text = new StringBuilder();
			for (JsonNode step : steps) {
				if (!"model_output".equals(step.path("type").asText())) continue;
				JsonNode content = step.get("content");
				if (content == null || !content.isArray()) continue;
				for (JsonNode part : content) {
					if ("text".equals(part.path("type").asText("text")) && part.hasNonNull("text")) {
						text.append(part.get("text").asText());
					}
				}
			}
			if (text.isEmpty()) {
				throw new InsightProviderException("Gemini response had no text output",
						InsightProviderException.Category.FALLBACK, null);
			}
			return text.toString();
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException error) {
			throw new InsightProviderException("Gemini response was not JSON",
					InsightProviderException.Category.FALLBACK, error);
		}
	}
}
