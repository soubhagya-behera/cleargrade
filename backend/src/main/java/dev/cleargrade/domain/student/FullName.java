package dev.cleargrade.domain.student;

import java.util.Objects;

import dev.cleargrade.domain.DomainValidationException;

/** Normalized human name. Length is measured in Unicode code points, not UTF-16 units. */
public final class FullName {

	private static final int MIN_CODE_POINTS = 2;
	private static final int MAX_CODE_POINTS = 100;

	private final String value;

	private FullName(String value) {
		this.value = value;
	}

	public static FullName of(String raw) {
		if (raw == null) {
			throw new DomainValidationException("Full name is required");
		}
		String normalized = collapseWhitespace(raw.strip());
		int codePoints = normalized.codePointCount(0, normalized.length());
		if (codePoints < MIN_CODE_POINTS || codePoints > MAX_CODE_POINTS) {
			throw new DomainValidationException("Full name must contain 2 to 100 Unicode code points");
		}
		normalized.codePoints().forEach(FullName::requireAllowedCodePoint);
		return new FullName(normalized);
	}

	private static String collapseWhitespace(String text) {
		StringBuilder result = new StringBuilder(text.length());
		boolean pendingSpace = false;
		for (int i = 0; i < text.length();) {
			int codePoint = text.codePointAt(i);
			i += Character.charCount(codePoint);
			if (Character.isWhitespace(codePoint)) {
				pendingSpace = result.length() > 0;
				continue;
			}
			if (pendingSpace) {
				result.append(' ');
				pendingSpace = false;
			}
			result.appendCodePoint(codePoint);
		}
		return result.toString();
	}

	private static void requireAllowedCodePoint(int codePoint) {
		int type = Character.getType(codePoint);
		boolean mark = type == Character.NON_SPACING_MARK
				|| type == Character.COMBINING_SPACING_MARK
				|| type == Character.ENCLOSING_MARK;
		boolean allowed = Character.isLetter(codePoint)
				|| mark
				|| codePoint == ' '
				|| codePoint == '\''
				|| codePoint == '\u2019'
				|| codePoint == ','
				|| codePoint == '-'
				|| codePoint == '\u2013'
				|| codePoint == '\u2014';
		if (!allowed) {
			throw new DomainValidationException("Full name contains a character that is not a letter, apostrophe, comma or hyphen");
		}
	}

	public String value() {
		return this.value;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof FullName that && this.value.equals(that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.value);
	}

	@Override
	public String toString() {
		return this.value;
	}
}
