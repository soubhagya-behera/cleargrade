package dev.cleargrade.application.service;

import java.util.ArrayList;
import java.util.List;

import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.exception.InsightUnavailableException;
import dev.cleargrade.application.model.AiProviderStatus;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.ImportQualityContext;
import dev.cleargrade.application.model.OllamaAvailability;
import dev.cleargrade.application.port.out.ImportInsightProvider;

/** Provider routing: Gemini → Ollama → deterministic, with explicit safe failure codes. */
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

		List<String> failures = new ArrayList<>();
		for (ImportInsightProvider provider : this.aiProviders) {
			if (!providerConfigured(provider) || !provider.available()) continue;
			try {
				ImportInsightReport report = provider.analyse(context);
				return failures.isEmpty() ? report : withReason(report, String.join(";", failures));
			}
			catch (InsightProviderException error) {
				if (error.category() == InsightProviderException.Category.CONFIGURATION) throw error;
				failures.add(provider.engine().name() + ":" + error.code());
			}
		}
		String reason = failures.isEmpty() ? "NO_AI_PROVIDERS_AVAILABLE" : String.join(";", failures);
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
				report.recommendedActions(), report.limitations(), report.contextFingerprint(),
				report.telemetry());
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
		OllamaAvailability ollama = this.aiProviders.stream()
				.filter(provider -> provider.engine() == ImportInsightReport.Engine.OLLAMA)
				.findFirst()
				.map(ImportInsightProvider::ollamaAvailability)
				.orElse(OllamaAvailability.UNREACHABLE);
		String ollamaModel = ollama.modelPresent() ? this.properties.getOllama().getModel() : "";
		ImportInsightReport.Engine preferred = geminiConfigured ? ImportInsightReport.Engine.GEMINI
				: ollama.usable() ? ImportInsightReport.Engine.OLLAMA : ImportInsightReport.Engine.RULE_BASED;

		String routing;
		List<String> reasons = new ArrayList<>();
		if (!this.properties.isEnabled()) {
			routing = "AI_DISABLED_DETERMINISTIC_ONLY";
			reasons.add("AI_DISABLED");
		}
		else if (geminiConfigured && ollama.usable()) {
			routing = "GEMINI_THEN_OLLAMA_THEN_RULE_BASED";
		}
		else if (geminiConfigured) {
			routing = "GEMINI_THEN_RULE_BASED";
			if (!ollamaConfigured) reasons.add("OLLAMA_NOT_CONFIGURED");
			else if (!ollama.serviceReachable()) reasons.add("OLLAMA_UNREACHABLE");
			else reasons.add("OLLAMA_MODEL_MISSING");
		}
		else if (ollama.usable()) {
			routing = "OLLAMA_THEN_RULE_BASED";
			reasons.add("GEMINI_NOT_CONFIGURED");
		}
		else {
			routing = "RULE_BASED_ONLY";
			reasons.add("GEMINI_NOT_CONFIGURED");
			if (!ollamaConfigured) reasons.add("OLLAMA_NOT_CONFIGURED");
			else if (!ollama.serviceReachable()) reasons.add("OLLAMA_UNREACHABLE");
			else reasons.add("OLLAMA_MODEL_MISSING");
		}
		if (!this.properties.isDeterministicEnabled()) reasons.add("DETERMINISTIC_DISABLED");
		return new AiProviderStatus(this.properties.isEnabled(), geminiConfigured,
				geminiConfigured ? this.properties.getGemini().getModel() : "",
				ollamaConfigured, ollamaConfigured && ollama.serviceReachable(),
				ollamaConfigured && ollama.modelPresent(), ollamaModel, false,
				this.properties.isDeterministicEnabled(), preferred, routing, List.copyOf(reasons));
	}
}
