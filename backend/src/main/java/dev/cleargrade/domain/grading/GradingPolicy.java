package dev.cleargrade.domain.grading;

import java.math.BigDecimal;
import java.math.RoundingMode;

import dev.cleargrade.domain.DomainValidationException;
import dev.cleargrade.domain.student.Student;
import dev.cleargrade.domain.student.SubjectMarks;

/** Pure grading rules (D-07): exact percentage drives grade and pass; only display is rounded. */
public final class GradingPolicy {

	private static final BigDecimal SUBJECT_COUNT = BigDecimal.valueOf(5);
	private static final BigDecimal PASS_MARK = BigDecimal.valueOf(50);

	public BigDecimal exactPercentage(SubjectMarks marks) {
		if (marks == null) {
			throw new DomainValidationException("Subject marks are required");
		}
		return exactPercentage(marks.total());
	}

	public BigDecimal exactPercentage(BigDecimal total) {
		if (total == null) {
			throw new DomainValidationException("Total is required");
		}
		return total.divide(SUBJECT_COUNT);
	}

	public Grade grade(BigDecimal exactPercentage) {
		return Grade.fromPercentage(exactPercentage);
	}

	public boolean passed(BigDecimal exactPercentage) {
		if (exactPercentage == null) {
			throw new DomainValidationException("Exact percentage is required");
		}
		return exactPercentage.compareTo(PASS_MARK) >= 0;
	}

	public BigDecimal displayPercentage(BigDecimal exactPercentage) {
		if (exactPercentage == null) {
			throw new DomainValidationException("Exact percentage is required");
		}
		return exactPercentage.setScale(1, RoundingMode.HALF_UP);
	}

	public StudentOutcome outcome(Student student) {
		if (student == null) {
			throw new DomainValidationException("Student is required");
		}
		BigDecimal total = student.marks().total();
		BigDecimal exact = exactPercentage(total);
		return new StudentOutcome(student, total, exact, displayPercentage(exact), grade(exact), passed(exact));
	}
}
