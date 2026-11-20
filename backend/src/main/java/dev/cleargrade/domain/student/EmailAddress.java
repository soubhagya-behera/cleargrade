package dev.cleargrade.domain.student;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import dev.cleargrade.domain.DomainValidationException;

/**
 * Pragmatic email address validation (U-01): plus-addressing and subdomains are supported,
 * obvious malformed values are rejected. This is not a claim of full RFC compliance.
 *
 * <p>The raw value is never included in messages or {@link #toString()}.</p>
 */
public final class EmailAddress {

	private static final int MAX_LENGTH = 320;
	private static final Pattern LOCAL = Pattern.compile("[A-Za-z0-9_%+-]+(?:\\.[A-Za-z0-9_%+-]+)*");
	private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
	private static final Pattern TLD = Pattern.compile("[A-Za-z]{2,}");

	private final String value;

	private EmailAddress(String value) {
		this.value = value;
	}

	public static EmailAddress of(String raw) {
		if (raw == null) {
			throw new DomainValidationException("Email address is required");
		}
		String candidate = raw.strip();
		if (candidate.isEmpty() || candidate.length() > MAX_LENGTH) {
			throw new DomainValidationException("Invalid email address");
		}
		if (candidate.chars().anyMatch(Character::isWhitespace)) {
			throw new DomainValidationException("Invalid email address");
		}
		int at = candidate.indexOf('@');
		if (at <= 0 || at != candidate.lastIndexOf('@') || at == candidate.length() - 1) {
			throw new DomainValidationException("Invalid email address");
		}

		String local = candidate.substring(0, at);
		String domain = candidate.substring(at + 1);
		if (!LOCAL.matcher(local).matches()) {
			throw new DomainValidationException("Invalid email address");
		}

		String[] labels = domain.split("\\.", -1);
		if (labels.length < 2) {
			throw new DomainValidationException("Invalid email address");
		}
		for (String label : labels) {
			if (!LABEL.matcher(label).matches()) {
				throw new DomainValidationException("Invalid email address");
			}
		}
		if (!TLD.matcher(labels[labels.length - 1]).matches()) {
			throw new DomainValidationException("Invalid email address");
		}
		return new EmailAddress(candidate.toLowerCase(Locale.ROOT));
	}

	public String value() {
		return this.value;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof EmailAddress that && this.value.equals(that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.value);
	}

	@Override
	public String toString() {
		return "EmailAddress[***]";
	}
}
