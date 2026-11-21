package dev.cleargrade.application.service;

import java.util.ArrayList;
import java.util.List;

import dev.cleargrade.application.config.InsightProperties;
import dev.cleargrade.application.exception.InsightProviderException;
import dev.cleargrade.application.exception.InsightUnavailableException;
import dev.cleargrade.application.model.AiProviderStatus;
import dev.cleargrade.application.model.ImportInsightReport;
import dev.cleargrade.application.model.OllamaAvailability;
import dev.cleargrade.application.model.ProviderFailure;
import dev.cleargrade.application.model.QualityAssessment;
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

	public ImportInsightReport analyse(QualityAssessment assessment) {
		if (!this.properties.isEnabled()) return deterministicReport(assessment, "AI_DISABLED");

		List<ProviderFailure> failures = new ArrayList<>();
		for (ImportInsightProvider provider : this.aiProviders) {
			if (!providerConfigured(provider) || !provider.available()) continue;
			try {
				ImportInsightReport report = provider.analyse(assessment);
				ImportInsightReport attributed = withFailures(report, failures);
				return failures.isEmpty() ? attributed
						: withReason(attributed, reasons(failures));
			}
			catch (InsightProviderException error) {
				if (error.category() == InsightProviderException.Category.CONFIGURATION) throw error;
				failures.add(new ProviderFailure(provider.engine().name(), error.code(), error.telemetry()));
			}
		}
		String reason = failures.isEmpty() ? "NO_AI_PROVIDERS_AVAILABLE" : reasons(failures);
		return deterministicReport(assessment, reason, failures);
	}

	private ImportInsightReport deterministicReport(QualityAssessment assessment, String reason) {
		return deterministicReport(assessment, reason, List.of());
	}

	private ImportInsightReport deterministicReport(QualityAssessment assessment, String reason,
			List<ProviderFailure> failures) {
		if (!this.properties.isDeterministicEnabled()) throw new InsightUnavailableException();
		ImportInsightReport fallback = this.deterministic.analyse(assessment);
		return withFailures(withReason(fallback, reason), failures);
	}

	private static String reasons(List<ProviderFailure> failures) {
		return String.join(";", failures.stream()
				.map(failure -> failure.engine() + ":" + failure.code()).toList());
	}

	private static ImportInsightReport withFailures(ImportInsightReport report,
			List<ProviderFailure> failures) {
		if (failures.isEmpty()) return report;
		return new ImportInsightReport(report.engine(), report.engineModel(), report.fallbackReason(),
				report.cached(), report.measuredFacts(), report.summary(), report.overallAssessment(),
				report.findings(), report.recommendedActions(), report.limitations(),
				report.contextFingerprint(), report.telemetry(), List.copyOf(failures));
	}

	private static ImportInsightReport withReason(ImportInsightReport report, String reason) {
		return new ImportInsightReport(report.engine(), report.engineModel(), reason, report.cached(),
				report.measuredFacts(), report.summary(), report.overallAssessment(), report.findings(),
				report.recommendedActions(), report.limitations(), report.contextFingerprint(),
				report.telemetry(), List.copyOf(report.providerFailures()));
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
