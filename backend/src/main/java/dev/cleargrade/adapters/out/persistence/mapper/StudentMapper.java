package dev.cleargrade.adapters.out.persistence.mapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import dev.cleargrade.adapters.out.persistence.entity.StudentEntity;
import dev.cleargrade.domain.student.DateOfBirth;
import dev.cleargrade.domain.student.EmailAddress;
import dev.cleargrade.domain.student.FullName;
import dev.cleargrade.domain.student.GradeLevel;
import dev.cleargrade.domain.student.Section;
import dev.cleargrade.domain.student.Student;
import dev.cleargrade.domain.student.StudentId;
import dev.cleargrade.domain.student.Subject;
import dev.cleargrade.domain.student.SubjectMarks;

/** Explicit, reflection-free mapping between student entities and the immutable aggregate. */
public final class StudentMapper {

	private StudentMapper() {
	}

	public static Student toDomain(StudentEntity entity, Clock clock) {
		return new Student(
				StudentId.of(entity.getStudentId()),
				FullName.of(entity.getFullName()),
				EmailAddress.of(entity.getEmail()),
				DateOfBirth.of(entity.getDateOfBirth(), clock),
				GradeLevel.of(entity.getGradeLevel()),
				Section.of(entity.getSection()),
				SubjectMarks.of(Map.of(
						Subject.MATHS, entity.getMaths(),
						Subject.SCIENCE, entity.getScience(),
						Subject.ENGLISH, entity.getEnglish(),
						Subject.SOCIAL_STUDIES, entity.getSocialStudies(),
						Subject.SECOND_LANGUAGE, entity.getSecondLanguage())));
	}

	public static StudentEntity toEntity(Student student, Long firstSeenBatchId, Long lastUpdatedBatchId,
			Instant createdAt, Instant updatedAt) {
		SubjectMarks marks = student.marks();
		return new StudentEntity(null,
				student.studentId().value(),
				student.fullName().value(),
				student.email().value(),
				student.dateOfBirth().value(),
				(short) student.gradeLevel().value(),
				String.valueOf(student.section().value()),
				marks.mark(Subject.MATHS),
				marks.mark(Subject.SCIENCE),
				marks.mark(Subject.ENGLISH),
				marks.mark(Subject.SOCIAL_STUDIES),
				marks.mark(Subject.SECOND_LANGUAGE),
				firstSeenBatchId,
				lastUpdatedBatchId,
				createdAt,
				updatedAt);
	}
}
