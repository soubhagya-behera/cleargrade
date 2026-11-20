package dev.cleargrade.domain.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.cleargrade.domain.DomainValidationException;

class EmailAddressTest {

	@Test
	void normalizesToLowercase() {
		assertEquals("student.one+tag@example.co.in",
				EmailAddress.of("Student.One+Tag@Example.CO.IN").value());
	}

	@Test
	void supportsPlusAddressingAndSubdomains() {
		assertEquals("a+b@sub.example.school.in", EmailAddress.of("a+b@sub.example.school.in").value());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"student.example.com", "a@@example.com", "@example.com", "a@",
			"a b@example.com", "a@example", "a..b@example.com", "a@-example.com",
			"a@example..com", "a@example.c", "a@exam_ple.com", "plainaddress"
	})
	void rejectsObviousMalformedAddresses(String raw) {
		assertThrows(DomainValidationException.class, () -> EmailAddress.of(raw));
	}

	@Test
	void rejectsNullEmptyAndOverlongInput() {
		assertThrows(DomainValidationException.class, () -> EmailAddress.of(null));
		assertThrows(DomainValidationException.class, () -> EmailAddress.of(""));
		assertThrows(DomainValidationException.class, () -> EmailAddress.of("a@b.co".repeat(60)));
	}

	@Test
	void neverExposesTheAddressInToStringOrValidationMessages() {
		EmailAddress email = EmailAddress.of("Private.Person+secret@example.com");
		assertFalse(email.toString().contains("Private"));
		assertFalse(email.toString().contains("example.com"));
		for (String raw : List.of("not-an-email", "a@@b.com")) {
			DomainValidationException error = assertThrows(DomainValidationException.class,
					() -> EmailAddress.of(raw));
			assertFalse(error.getMessage().contains(raw));
		}
	}

	@Test
	void equalityIsCaseInsensitiveAfterNormalization() {
		assertEquals(EmailAddress.of("Student@Example.com"), EmailAddress.of("student@example.COM"));
	}
}
