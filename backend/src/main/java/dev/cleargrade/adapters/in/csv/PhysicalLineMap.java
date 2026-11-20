package dev.cleargrade.adapters.in.csv;

/** Maps decoded-source character offsets to physical lines (header = 1), including quoted newlines. */
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
