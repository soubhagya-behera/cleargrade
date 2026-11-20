package dev.cleargrade.adapters.out.persistence.repository;

import java.util.Optional;

import dev.cleargrade.adapters.out.persistence.entity.StudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lookup by the normalized upsert key (U-02). */
public interface StudentRepository extends JpaRepository<StudentEntity, Long> {

	Optional<StudentEntity> findByStudentId(String studentId);
}
