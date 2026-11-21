package dev.cleargrade.application.model;

/** Safe Ollama snapshot from /api/tags; no inference is performed to build it. */
public record OllamaAvailability(boolean serviceReachable, boolean modelPresent) {

	public static final OllamaAvailability UNREACHABLE = new OllamaAvailability(false, false);

	public boolean usable() {
		return this.serviceReachable && this.modelPresent;
	}
}
