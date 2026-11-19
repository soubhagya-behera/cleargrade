package dev.cleargrade.configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Time source.
 *
 * All date-dependent decisions (e.g. student age validation, D-06) take
 * their "now" from an injected {@link Clock}. Production uses the system
 * clock; regression tests use the fixed benchmark clock documented in
 * DECISIONS.md D-06/I-04 (2026-11-19).
 */
@Configuration
public class ClockConfiguration {

	@Bean
	@Profile("!test")
	public Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	@Profile("test")
	public Clock testClock() {
		return Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);
	}
}
