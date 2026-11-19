package dev.cleargrade.csv.spike;

/**
 * Maps source character offsets to physical line numbers (header = 1).
 *
 * <p>The map is built independently of the CSV parser by counting physical
 * line breaks, including those inside quoted fields. LF, CRLF and lone CR are
 * handled. A leading UTF-8 BOM, when stripped before parsing, is not present.</p>
 */
final class PhysicalLineMap {

	private final String source;
	private final int[] lineByCharacter;

	PhysicalLineMap(String source) {
		this.source = source;
		this.lineByCharacter = new int[source.length()];
		int line = 1;
		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			this.lineByCharacter[i] = line;
			if (c == '\n') {
				line++;
			}
			else if (c == '\r' && (i + 1 >= source.length() || source.charAt(i + 1) != '\n')) {
				line++;
			}
		}
	}

	/**
	 * Returns the physical line on which the record actually begins.
	 *
	 * <p>Commons CSV reports a record's character position at the point where
	 * the lexer starts looking for it, which can be the first blank physical
	 * line preceding the record. Leading CR/LF characters are therefore
	 * skipped before the line is looked up.</p>
	 */
	int startingLineOf(long characterPosition) {
		if (this.source.isEmpty()) {
			return 1;
		}
		int index = (int) Math.max(0, Math.min(characterPosition, this.source.length() - 1));
		while (index < this.source.length() && isLineBreak(this.source.charAt(index))) {
			index++;
		}
		if (index >= this.source.length()) {
			return this.lineByCharacter[this.lineByCharacter.length - 1];
		}
		return this.lineByCharacter[index];
	}

	private static boolean isLineBreak(char c) {
		return c == '\r' || c == '\n';
	}
}
