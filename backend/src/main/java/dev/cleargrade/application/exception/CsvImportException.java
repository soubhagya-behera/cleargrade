package dev.cleargrade.application.exception;

/** Safe, administrator-readable import failure; never carries raw CSV content. */
public class CsvImportException extends RuntimeException {

	private final int physicalLine;
	private final CsvFailureCode failureCode;

	public CsvImportException(String safeMessage, int physicalLine, Throwable cause) {
		this(safeMessage, physicalLine, cause, CsvFailureCode.MALFORMED_CSV);
	}

	public CsvImportException(String safeMessage, int physicalLine, Throwable cause, CsvFailureCode failureCode) {
		super(safeMessage, cause);
		this.physicalLine = physicalLine;
		this.failureCode = failureCode;
	}

	public int physicalLine() {
		return this.physicalLine;
	}

	public CsvFailureCode failureCode() {
		return this.failureCode;
	}
}
