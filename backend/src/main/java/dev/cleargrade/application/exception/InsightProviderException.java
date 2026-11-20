package dev.cleargrade.application.exception;

/** Provider failure with an explicit routing category; never carries response bodies or keys. */
public class InsightProviderException extends RuntimeException {

	public enum Category { FALLBACK, CONFIGURATION }

	private final Category category;

	public InsightProviderException(String safeMessage, Category category, Throwable cause) {
		super(safeMessage, cause);
		this.category = category;
	}

	public Category category() {
		return this.category;
	}
}
