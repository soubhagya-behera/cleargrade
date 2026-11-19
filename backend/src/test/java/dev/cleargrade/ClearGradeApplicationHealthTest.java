package dev.cleargrade;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Minimal application context/health verification (ARCHITECTURE.md §8,
 * DEVELOPMENT.md gates).
 *
 * Excludes auto-configuration that requires an external database so the
 * application context and health endpoint are verified without a running
 * PostgreSQL instance.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"spring.autoconfigure.exclude="
						+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
						+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
						+ "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
						+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
		}
)
class ClearGradeApplicationHealthTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void applicationContextStarts() {
		assertThat(this.applicationContext).isNotNull();
	}

	@Test
	void healthEndpointAnswers() {
		RestClient client = RestClient.create("http://localhost:" + this.port);
		ResponseEntity<String> health = client.get().uri("/actuator/health").retrieve().toEntity(String.class);
		assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(health.getBody()).isNotBlank();
	}
}
