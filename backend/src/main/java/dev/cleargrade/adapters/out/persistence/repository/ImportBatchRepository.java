package dev.cleargrade.adapters.out.persistence.repository;

import java.util.List;

import dev.cleargrade.adapters.out.persistence.entity.ImportBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Digest lookup for the documented identical-file reservation (DATA_MODEL.md §5). */
public interface ImportBatchRepository extends JpaRepository<ImportBatchEntity, Long> {

	List<ImportBatchEntity> findAllBySha256(String sha256);
}
