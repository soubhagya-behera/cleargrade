package dev.cleargrade.application.exception;

/** Safe, administrator-readable import failure; never carries raw CSV content. */
public class CsvImportException extends RuntimeException {

	private final int physicalLine;

	public CsvImportException(String safeMessage, int physicalLine, Throwable cause) {
		super(safeMessage, cause);
		this.physicalLine = physicalLine;
	}

	public int physicalLine() {
		return this.physicalLine;
	}
}
