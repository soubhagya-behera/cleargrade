package dev.cleargrade.application.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import dev.cleargrade.application.exception.InvalidParameterException;
import dev.cleargrade.application.exception.StudentNotFoundException;
import dev.cleargrade.application.model.PageResult;
import dev.cleargrade.application.model.StudentReportView;
import dev.cleargrade.application.model.StudentSearchCriteria;
import dev.cleargrade.application.model.StudentSummaryView;
import dev.cleargrade.application.port.out.StudentQueryPort;
import dev.cleargrade.domain.grading.GradingPolicy;
import dev.cleargrade.domain.grading.StudentOutcome;
import dev.cleargrade.domain.student.Student;
import dev.cleargrade.domain.student.StudentId;
import dev.cleargrade.domain.student.Subject;
import org.springframework.stereotype.Service;

/** Read-side directory/report orchestration; grading is derived, never stored. */
@Service
public class StudentQueryService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final GradingPolicy POLICY = new GradingPolicy();
	private static final Map<String, String> SORT_COLUMNS = Map.of(
			"studentId", "student_id",
			"fullName", "full_name",
			"gradeLevel", "grade_level",
			"section", "section");

	private final StudentQueryPort students;

	public StudentQueryService(StudentQueryPort students) {
		this.students = students;
	}

	public PageResult<StudentSummaryView> search(StudentSearchCriteria criteria) {
		validate(criteria);
		PageResult<Student> found = this.students.search(criteria);
		return PageResult.of(found.content().stream().map(this::summary).toList(),
				found.page(), found.size(), found.totalElements());
	}

	public StudentReportView findReport(String rawStudentId) {
		StudentId studentId;
		try {
			studentId = StudentId.of(rawStudentId);
		}
		catch (RuntimeException invalid) {
			throw new StudentNotFoundException(rawStudentId);
		}
		Student student = this.students.findByStudentId(studentId.value())
				.orElseThrow(() -> new StudentNotFoundException(rawStudentId));
		StudentOutcome outcome = POLICY.outcome(student);
		Map<String, BigDecimal> marks = new LinkedHashMap<>();
		marks.put("maths", student.marks().mark(Subject.MATHS));
		marks.put("science", student.marks().mark(Subject.SCIENCE));
		marks.put("english", student.marks().mark(Subject.ENGLISH));
		marks.put("socialStudies", student.marks().mark(Subject.SOCIAL_STUDIES));
		marks.put("secondLanguage", student.marks().mark(Subject.SECOND_LANGUAGE));
		return new StudentReportView(
				student.studentId().value(),
				student.fullName().value(),
				student.email().value(),
				student.dateOfBirth().value(),
				student.gradeLevel().value(),
				String.valueOf(student.section().value()),
				Map.copyOf(marks),
				outcome.total(),
				Subject.values().length * 100,
				outcome.displayPercentage(),
				outcome.grade(),
				outcome.passed());
	}

	private StudentSummaryView summary(Student student) {
		StudentOutcome outcome = POLICY.outcome(student);
		return new StudentSummaryView(
				student.studentId().value(),
				student.fullName().value(),
				student.gradeLevel().value(),
				String.valueOf(student.section().value()),
				outcome.displayPercentage(),
				outcome.grade());
	}

	private void validate(StudentSearchCriteria criteria) {
		if (criteria.page() < 0 || criteria.size() < 1 || criteria.size() > MAX_PAGE_SIZE) {
			throw new InvalidParameterException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
		}
		if (criteria.grade() != null && (criteria.grade() < 1 || criteria.grade() > 12)) {
			throw new InvalidParameterException("grade must be between 1 and 12");
		}
		if (criteria.section() != null && !criteria.section().isBlank()
				&& (criteria.section().length() != 1
						|| "ABCDEF".indexOf(criteria.section().toUpperCase(Locale.ROOT).charAt(0)) < 0)) {
			throw new InvalidParameterException("section must be one letter A to F");
		}
		if (!SORT_COLUMNS.containsKey(criteria.sortProperty())) {
			throw new InvalidParameterException("Unknown sort property");
		}
	}
}
