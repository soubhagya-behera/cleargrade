package dev.cleargrade.adapters.in.web.dto;

import java.util.List;

import dev.cleargrade.application.model.AiProviderStatus;
import dev.cleargrade.application.model.LastAnalysis;

/** Safe status for the UI: configuration, reachability and last result are deliberately distinct. */
public record AiStatusResponse(
		boolean enabled,
		boolean geminiConfigured,
		String geminiModel,
		boolean ollamaConfigured,
		boolean ollamaReachable,
		boolean ollamaModelPresent,
		String ollamaModel,
		boolean inferenceTested,
		boolean deterministicAvailable,
		String defaultEngine,
		String routing,
		List<String> routingReasons,
		String lastAnalysisEngine,
		String lastAnalysisReason,
		InsightUsageResponse lastAnalysisUsage,
		List<InsightFailureResponse> lastAnalysisFailures) {

	public static AiStatusResponse from(AiProviderStatus status, LastAnalysis lastAnalysis) {
		return new AiStatusResponse(status.featureEnabled(), status.geminiConfigured(),
				status.geminiModel(), status.ollamaConfigured(), status.ollamaReachable(),
				status.ollamaModelPresent(), status.ollamaModel(), status.inferenceTested(),
				status.deterministicAvailable(), status.defaultEngine().name(), status.routing(),
				List.copyOf(status.routingReasons()),
				lastAnalysis == null ? null : lastAnalysis.engine(),
				lastAnalysis == null ? null : lastAnalysis.reason(),
				InsightUsageResponse.from(lastAnalysis == null ? null : lastAnalysis.telemetry()),
				lastAnalysis == null ? List.of() : failures(lastAnalysis));
	}

	private static List<InsightFailureResponse> failures(LastAnalysis lastAnalysis) {
		return lastAnalysis.providerFailures().stream()
				.map(failure -> new InsightFailureResponse(failure.engine(), failure.code(),
						failure.telemetry() == null ? "" : nullToEmpty(failure.telemetry().providerStatus()),
						failure.telemetry() == null ? 0L : failure.telemetry().durationMs(),
						lastAnalysis.engine() != null && !failure.engine().equals(lastAnalysis.engine())))
				.toList();
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
