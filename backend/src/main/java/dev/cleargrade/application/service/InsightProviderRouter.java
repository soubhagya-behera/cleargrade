package dev.cleargrade.application.service;

import java.util.ArrayList;
import java.util.List;

import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.exception.InsightUnavailableException;
import dev.cleargrade.application.model.AiProviderStatus;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.port.out.ImportInsightProvider;

/** Provider routing: Gemini → Ollama → deterministic, with explicit failure categories. */
public class InsightProviderRouter {

	private final List<ImportInsightProvider> aiProviders;
	private final DeterministicInsightService deterministic;
	private final InsightProperties properties;

	public InsightProviderRouter(List<ImportInsightProvider> aiProviders,
			DeterministicInsightService deterministic, InsightProperties properties) {
		this.aiProviders = List.copyOf(aiProviders);
		this.deterministic = deterministic;
		this.properties = properties;
	}

	public ImportInsightReport analyse(ImportQualityContext context) {
		if (!this.properties.isEnabled()) return deterministicReport(context, "AI_DISABLED");

		String lastFallback = null;
		for (ImportInsightProvider provider : this.aiProviders) {
			if (!providerConfigured(provider) || !provider.available()) continue;
			try {
				ImportInsightReport report = provider.analyse(context);
				return lastFallback == null ? report : withReason(report, lastFallback);
			}
			catch (InsightProviderException error) {
				if (error.category() == InsightProviderException.Category.CONFIGURATION) throw error;
				lastFallback = provider.engine().name() + "_UNAVAILABLE";
			}
		}
		String reason = lastFallback == null ? "NO_AI_PROVIDERS_AVAILABLE" : lastFallback;
		return deterministicReport(context, reason);
	}

	private ImportInsightReport deterministicReport(ImportQualityContext context, String reason) {
		if (!this.properties.isDeterministicEnabled()) throw new InsightUnavailableException();
		ImportInsightReport fallback = this.deterministic.analyse(context);
		return withReason(fallback, reason);
	}

	private static ImportInsightReport withReason(ImportInsightReport report, String reason) {
		return new ImportInsightReport(report.engine(), report.engineModel(), reason, report.cached(),
				report.measuredFacts(), report.summary(), report.overallAssessment(), report.findings(),
				report.recommendedActions(), report.limitations(), report.contextFingerprint());
	}

	private boolean providerConfigured(ImportInsightProvider provider) {
		return switch (provider.engine()) {
			case GEMINI -> this.properties.getGemini().configured();
			case OLLAMA -> this.properties.getOllama().configured();
			case RULE_BASED -> false;
		};
	}

	public String engineVersion() {
		StringBuilder version = new StringBuilder();
		for (ImportInsightProvider provider : this.aiProviders) {
			version.append(provider.engine().name()).append(':').append(provider.modelVersion()).append('|');
		}
		return version.append(this.deterministic.engine().name()).append(':')
				.append(this.deterministic.modelVersion()).toString();
	}

	public AiProviderStatus status() {
		boolean geminiConfigured = this.properties.isEnabled() && this.properties.getGemini().configured();
		boolean ollamaConfigured = this.properties.isEnabled() && this.properties.getOllama().configured();
		boolean ollamaAvailable = ollamaConfigured && this.aiProviders.stream()
				.anyMatch(provider -> provider.engine() == ImportInsightReport.Engine.OLLAMA && provider.available());
		ImportInsightReport.Engine engine = geminiConfigured ? ImportInsightReport.Engine.GEMINI
				: ollamaAvailable ? ImportInsightReport.Engine.OLLAMA : ImportInsightReport.Engine.RULE_BASED;
		String routing;
		List<String> reasons = new ArrayList<>();
		if (!this.properties.isEnabled()) {
			routing = "AI_DISABLED_DETERMINISTIC_ONLY";
			reasons.add("AI_DISABLED");
		}
		else if (geminiConfigured && ollamaAvailable) {
			routing = "GEMINI_THEN_OLLAMA_THEN_RULE_BASED";
		}
		else if (geminiConfigured) {
			routing = "GEMINI_THEN_RULE_BASED";
			reasons.add("OLLAMA_NOT_AVAILABLE");
		}
		else if (ollamaAvailable) {
			routing = "OLLAMA_THEN_RULE_BASED";
			reasons.add("GEMINI_NOT_CONFIGURED");
		}
		else {
			routing = "RULE_BASED_ONLY";
			reasons.add("GEMINI_NOT_CONFIGURED");
			reasons.add("OLLAMA_NOT_AVAILABLE");
		}
		if (!this.properties.isDeterministicEnabled()) reasons.add("DETERMINISTIC_DISABLED");
		return new AiProviderStatus(this.properties.isEnabled(), geminiConfigured, geminiConfigured,
				geminiConfigured ? this.properties.getGemini().getModel() : "",
				ollamaConfigured, ollamaAvailable,
				ollamaAvailable ? this.properties.getOllama().getModel() : "",
				this.properties.isDeterministicEnabled(), engine, routing, List.copyOf(reasons));
	}
}
