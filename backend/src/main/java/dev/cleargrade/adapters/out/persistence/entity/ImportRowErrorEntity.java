package dev.cleargrade.adapters.out.persistence.entity;

import dev.cleargrade.domain.batch.RowErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Internal JPA mapping of the {@code import_row_error} table (V1). */
@Entity
@Table(name = "import_row_error")
public class ImportRowErrorEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "batch_id", nullable = false)
	private Long batchId;

	@Column(name = "line_number", nullable = false)
	private int lineNumber;

	@Column(name = "raw_student_id")
	private String rawStudentId;

	@Column(name = "field_name", length = 32, nullable = false)
	private String fieldName;

	@Enumerated(EnumType.STRING)
	@Column(name = "error_code", length = 24, nullable = false)
	private RowErrorCode errorCode;

	@Column(name = "message", nullable = false)
	private String message;

	protected ImportRowErrorEntity() {
	}

	public ImportRowErrorEntity(Long id, Long batchId, int lineNumber, String rawStudentId, String fieldName,
			RowErrorCode errorCode, String message) {
		this.id = id;
		this.batchId = batchId;
		this.lineNumber = lineNumber;
		this.rawStudentId = rawStudentId;
		this.fieldName = fieldName;
		this.errorCode = errorCode;
		this.message = message;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getBatchId() {
		return this.batchId;
	}

	public void setBatchId(Long batchId) {
		this.batchId = batchId;
	}

	public int getLineNumber() {
		return this.lineNumber;
	}

	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}

	public String getRawStudentId() {
		return this.rawStudentId;
	}

	public void setRawStudentId(String rawStudentId) {
		this.rawStudentId = rawStudentId;
	}

	public String getFieldName() {
		return this.fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public RowErrorCode getErrorCode() {
		return this.errorCode;
	}

	public void setErrorCode(RowErrorCode errorCode) {
		this.errorCode = errorCode;
	}

	public String getMessage() {
		return this.message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
