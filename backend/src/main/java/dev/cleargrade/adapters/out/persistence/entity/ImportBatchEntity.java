package dev.cleargrade.adapters.out.persistence.entity;

import java.time.Instant;

import dev.cleargrade.domain.batch.ImportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Internal JPA mapping of the {@code import_batch} table (V1). */
@Entity
@Table(name = "import_batch")
public class ImportBatchEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "filename", length = 255, nullable = false)
	private String filename;

	@Column(name = "file_size_bytes", nullable = false)
	private long fileSizeBytes;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "sha256", length = 64, nullable = false)
	private String sha256;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 16, nullable = false)
	private ImportStatus status;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "rows_read")
	private Integer rowsRead;

	@Column(name = "imported_count")
	private Integer importedCount;

	@Column(name = "new_count")
	private Integer newCount;

	@Column(name = "changed_count")
	private Integer changedCount;

	@Column(name = "unchanged_count")
	private Integer unchangedCount;

	@Column(name = "rejected_count")
	private Integer rejectedCount;

	@Column(name = "error_count")
	private Integer errorCount;

	protected ImportBatchEntity() {
	}

	public ImportBatchEntity(Long id, String filename, long fileSizeBytes, String sha256, ImportStatus status,
			Instant startedAt, Instant finishedAt, Long durationMs, Integer rowsRead, Integer importedCount,
			Integer newCount, Integer changedCount, Integer unchangedCount, Integer rejectedCount, Integer errorCount) {
		this.id = id;
		this.filename = filename;
		this.fileSizeBytes = fileSizeBytes;
		this.sha256 = sha256;
		this.status = status;
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
		this.durationMs = durationMs;
		this.rowsRead = rowsRead;
		this.importedCount = importedCount;
		this.newCount = newCount;
		this.changedCount = changedCount;
		this.unchangedCount = unchangedCount;
		this.rejectedCount = rejectedCount;
		this.errorCount = errorCount;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getFilename() {
		return this.filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public long getFileSizeBytes() {
		return this.fileSizeBytes;
	}

	public void setFileSizeBytes(long fileSizeBytes) {
		this.fileSizeBytes = fileSizeBytes;
	}

	public String getSha256() {
		return this.sha256;
	}

	public void setSha256(String sha256) {
		this.sha256 = sha256;
	}

	public ImportStatus getStatus() {
		return this.status;
	}

	public void setStatus(ImportStatus status) {
		this.status = status;
	}

	public Instant getStartedAt() {
		return this.startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getFinishedAt() {
		return this.finishedAt;
	}

	public void setFinishedAt(Instant finishedAt) {
		this.finishedAt = finishedAt;
	}

	public Long getDurationMs() {
		return this.durationMs;
	}

	public void setDurationMs(Long durationMs) {
		this.durationMs = durationMs;
	}

	public Integer getRowsRead() {
		return this.rowsRead;
	}

	public void setRowsRead(Integer rowsRead) {
		this.rowsRead = rowsRead;
	}

	public Integer getImportedCount() {
		return this.importedCount;
	}

	public void setImportedCount(Integer importedCount) {
		this.importedCount = importedCount;
	}

	public Integer getNewCount() {
		return this.newCount;
	}

	public void setNewCount(Integer newCount) {
		this.newCount = newCount;
	}

	public Integer getChangedCount() {
		return this.changedCount;
	}

	public void setChangedCount(Integer changedCount) {
		this.changedCount = changedCount;
	}

	public Integer getUnchangedCount() {
		return this.unchangedCount;
	}

	public void setUnchangedCount(Integer unchangedCount) {
		this.unchangedCount = unchangedCount;
	}

	public Integer getRejectedCount() {
		return this.rejectedCount;
	}

	public void setRejectedCount(Integer rejectedCount) {
		this.rejectedCount = rejectedCount;
	}

	public Integer getErrorCount() {
		return this.errorCount;
	}

	public void setErrorCount(Integer errorCount) {
		this.errorCount = errorCount;
	}
}
