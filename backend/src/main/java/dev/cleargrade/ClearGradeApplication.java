package dev.cleargrade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ClearGrade application entry point.
 *
 * Defined in {@code dev.cleargrade}. Facades further modular packages as the
 * application matures: {@code domain} (framework-free), {@code application}
 * (use cases/ports), {@code adapters.in.web}, {@code adapters.in.csv},
 * {@code adapters.out.persistence}, {@code configuration} (ARCHITECTURE.md §2).
 */
@SpringBootApplication
public class ClearGradeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClearGradeApplication.class, args);
	}
}
