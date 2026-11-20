package dev.cleargrade.adapters.out.persistence.repository;

import java.util.List;

import dev.cleargrade.adapters.out.persistence.entity.ImportRowErrorEntity;
import dev.cleargrade.domain.batch.RowErrorCode;
import org.springframework.data.jpa.repository.JpaRepository;

/** Paged diagnostics in file order and by problem code (DATA_MODEL.md §3, D-14). */
public interface ImportRowErrorRepository extends JpaRepository<ImportRowErrorEntity, Long> {

	List<ImportRowErrorEntity> findAllByBatchIdOrderByLineNumberAsc(Long batchId);

	List<ImportRowErrorEntity> findAllByBatchIdAndErrorCodeOrderByLineNumberAsc(Long batchId, RowErrorCode errorCode);
}
