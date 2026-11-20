package dev.cleargrade.domain.student;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import dev.cleargrade.domain.DomainValidationException;

/** Exactly five marks, each in the inclusive range 0-100, with no rounding or precision loss. */
public final class SubjectMarks {

	private static final BigDecimal MIN = BigDecimal.ZERO;
	private static final BigDecimal MAX = BigDecimal.valueOf(100);

	private final Map<Subject, BigDecimal> marks;

	private SubjectMarks(Map<Subject, BigDecimal> marks) {
		this.marks = marks;
	}

	public static SubjectMarks of(Map<Subject, BigDecimal> values) {
		if (values == null || values.size() != Subject.values().length) {
			throw new DomainValidationException("Exactly five subject marks are required");
		}
		EnumMap<Subject, BigDecimal> copy = new EnumMap<>(Subject.class);
		for (Subject subject : Subject.values()) {
			BigDecimal mark = values.get(subject);
			if (mark == null) {
				throw new DomainValidationException("Missing mark for subject " + subject.columnName());
			}
			if (mark.compareTo(MIN) < 0 || mark.compareTo(MAX) > 0) {
				throw new DomainValidationException("Mark for " + subject.columnName() + " must be between 0 and 100");
			}
			copy.put(subject, mark);
		}
		return new SubjectMarks(Collections.unmodifiableMap(copy));
	}

	public BigDecimal mark(Subject subject) {
		return this.marks.get(subject);
	}

	public Map<Subject, BigDecimal> marks() {
		return this.marks;
	}

	public BigDecimal total() {
		return this.marks.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof SubjectMarks that && this.marks.equals(that.marks);
	}

	@Override
	public int hashCode() {
		return this.marks.hashCode();
	}

	@Override
	public String toString() {
		return "SubjectMarks" + this.marks;
	}
}
