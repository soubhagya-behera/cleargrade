package dev.cleargrade.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Optional AI configuration. No secret value is ever logged or returned by the API. */
@Component
@ConfigurationProperties(prefix = "cleargrade.ai")
public class InsightProperties {

	private boolean enabled = true;
	private boolean deterministicEnabled = true;
	private int cooldownSeconds;
	private int maxPromptChars = 8000;
	private int maxResponseChars = 16000;
	private final Gemini gemini = new Gemini();
	private final Ollama ollama = new Ollama();
	private final Cache cache = new Cache();

	public boolean isEnabled() { return this.enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public boolean isDeterministicEnabled() { return this.deterministicEnabled; }
	public void setDeterministicEnabled(boolean deterministicEnabled) { this.deterministicEnabled = deterministicEnabled; }
	public int getCooldownSeconds() { return this.cooldownSeconds; }
	public void setCooldownSeconds(int cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
	public int getMaxPromptChars() { return this.maxPromptChars; }
	public void setMaxPromptChars(int maxPromptChars) { this.maxPromptChars = maxPromptChars; }
	public int getMaxResponseChars() { return this.maxResponseChars; }
	public void setMaxResponseChars(int maxResponseChars) { this.maxResponseChars = maxResponseChars; }
	public Gemini getGemini() { return this.gemini; }
	public Ollama getOllama() { return this.ollama; }
	public Cache getCache() { return this.cache; }

	public static class Gemini {
		private String apiKey = "";
		private String model = "gemini-3.8-flash";
		private String endpoint = "https://generativelanguage.googleapis.com/v1beta/interactions";
		private String thinkingLevel = "low";
		private int maxOutputTokens = 2048;
		private int timeoutConnectMs = 10000;
		private int timeoutReadMs = 60000;

		public String getApiKey() { return this.apiKey; }
		public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }
		public String getModel() { return this.model; }
		public void setModel(String model) { this.model = model; }
		public String getEndpoint() { return this.endpoint; }
		public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
		public String getThinkingLevel() { return this.thinkingLevel; }
		public void setThinkingLevel(String thinkingLevel) { this.thinkingLevel = thinkingLevel == null ? "low" : thinkingLevel; }
		public int getMaxOutputTokens() { return this.maxOutputTokens; }
		public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
		public int getTimeoutConnectMs() { return this.timeoutConnectMs; }
		public void setTimeoutConnectMs(int timeoutConnectMs) { this.timeoutConnectMs = timeoutConnectMs; }
		public int getTimeoutReadMs() { return this.timeoutReadMs; }
		public void setTimeoutReadMs(int timeoutReadMs) { this.timeoutReadMs = timeoutReadMs; }
		public boolean configured() { return !this.apiKey.isBlank() && !this.model.isBlank(); }
	}

	public static class Ollama {
		private String baseUrl = "";
		private String model = "";
		private int timeoutConnectMs = 5000;
		private int timeoutReadMs = 60000;

		public String getBaseUrl() { return this.baseUrl; }
		public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl; }
		public String getModel() { return this.model; }
		public void setModel(String model) { this.model = model == null ? "" : model; }
		public int getTimeoutConnectMs() { return this.timeoutConnectMs; }
		public void setTimeoutConnectMs(int timeoutConnectMs) { this.timeoutConnectMs = timeoutConnectMs; }
		public int getTimeoutReadMs() { return this.timeoutReadMs; }
		public void setTimeoutReadMs(int timeoutReadMs) { this.timeoutReadMs = timeoutReadMs; }
		public boolean configured() { return !this.baseUrl.isBlank() && !this.model.isBlank(); }
	}

	public static class Cache {
		private int ttlSeconds = 600;
		private int maxEntries = 64;

		public int getTtlSeconds() { return this.ttlSeconds; }
		public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
		public int getMaxEntries() { return this.maxEntries; }
		public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
	}
}
