package dev.cleargrade.adapters.out.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Internal JPA mapping of the {@code student} table (V1). */
@Entity
@Table(name = "student", uniqueConstraints = @UniqueConstraint(name = "uq_student_student_id", columnNames = "student_id"))
public class StudentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "student_id", length = 20, nullable = false)
	private String studentId;

	@Column(name = "full_name", length = 100, nullable = false)
	private String fullName;

	@Column(name = "email", length = 320, nullable = false)
	private String email;

	@Column(name = "date_of_birth", nullable = false)
	private LocalDate dateOfBirth;

	@Column(name = "grade_level", nullable = false)
	private short gradeLevel;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "section", length = 1, nullable = false)
	private String section;

	@Column(name = "maths", nullable = false)
	private BigDecimal maths;

	@Column(name = "science", nullable = false)
	private BigDecimal science;

	@Column(name = "english", nullable = false)
	private BigDecimal english;

	@Column(name = "social_studies", nullable = false)
	private BigDecimal socialStudies;

	@Column(name = "second_language", nullable = false)
	private BigDecimal secondLanguage;

	@Column(name = "first_seen_batch_id", nullable = false)
	private Long firstSeenBatchId;

	@Column(name = "last_updated_batch_id", nullable = false)
	private Long lastUpdatedBatchId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected StudentEntity() {
	}

	public StudentEntity(Long id, String studentId, String fullName, String email, LocalDate dateOfBirth,
			short gradeLevel, String section, BigDecimal maths, BigDecimal science, BigDecimal english,
			BigDecimal socialStudies, BigDecimal secondLanguage, Long firstSeenBatchId, Long lastUpdatedBatchId,
			Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.studentId = studentId;
		this.fullName = fullName;
		this.email = email;
		this.dateOfBirth = dateOfBirth;
		this.gradeLevel = gradeLevel;
		this.section = section;
		this.maths = maths;
		this.science = science;
		this.english = english;
		this.socialStudies = socialStudies;
		this.secondLanguage = secondLanguage;
		this.firstSeenBatchId = firstSeenBatchId;
		this.lastUpdatedBatchId = lastUpdatedBatchId;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getStudentId() {
		return this.studentId;
	}

	public void setStudentId(String studentId) {
		this.studentId = studentId;
	}

	public String getFullName() {
		return this.fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getEmail() {
		return this.email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public LocalDate getDateOfBirth() {
		return this.dateOfBirth;
	}

	public void setDateOfBirth(LocalDate dateOfBirth) {
		this.dateOfBirth = dateOfBirth;
	}

	public short getGradeLevel() {
		return this.gradeLevel;
	}

	public void setGradeLevel(short gradeLevel) {
		this.gradeLevel = gradeLevel;
	}

	public String getSection() {
		return this.section;
	}

	public void setSection(String section) {
		this.section = section;
	}

	public BigDecimal getMaths() {
		return this.maths;
	}

	public void setMaths(BigDecimal maths) {
		this.maths = maths;
	}

	public BigDecimal getScience() {
		return this.science;
	}

	public void setScience(BigDecimal science) {
		this.science = science;
	}

	public BigDecimal getEnglish() {
		return this.english;
	}

	public void setEnglish(BigDecimal english) {
		this.english = english;
	}

	public BigDecimal getSocialStudies() {
		return this.socialStudies;
	}

	public void setSocialStudies(BigDecimal socialStudies) {
		this.socialStudies = socialStudies;
	}

	public BigDecimal getSecondLanguage() {
		return this.secondLanguage;
	}

	public void setSecondLanguage(BigDecimal secondLanguage) {
		this.secondLanguage = secondLanguage;
	}

	public Long getFirstSeenBatchId() {
		return this.firstSeenBatchId;
	}

	public void setFirstSeenBatchId(Long firstSeenBatchId) {
		this.firstSeenBatchId = firstSeenBatchId;
	}

	public Long getLastUpdatedBatchId() {
		return this.lastUpdatedBatchId;
	}

	public void setLastUpdatedBatchId(Long lastUpdatedBatchId) {
		this.lastUpdatedBatchId = lastUpdatedBatchId;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
