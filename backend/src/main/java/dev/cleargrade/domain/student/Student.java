package dev.cleargrade.domain.student;

import java.util.Objects;

import dev.cleargrade.domain.DomainValidationException;

/** Immutable student aggregate for the current-result snapshot (D-11). */
public final class Student {

	private final StudentId studentId;
	private final FullName fullName;
	private final EmailAddress email;
	private final DateOfBirth dateOfBirth;
	private final GradeLevel gradeLevel;
	private final Section section;
	private final SubjectMarks marks;

	public Student(StudentId studentId, FullName fullName, EmailAddress email, DateOfBirth dateOfBirth,
			GradeLevel gradeLevel, Section section, SubjectMarks marks) {
		this.studentId = required(studentId, "studentId");
		this.fullName = required(fullName, "fullName");
		this.email = required(email, "email");
		this.dateOfBirth = required(dateOfBirth, "dateOfBirth");
		this.gradeLevel = required(gradeLevel, "gradeLevel");
		this.section = required(section, "section");
		this.marks = required(marks, "marks");
	}

	private static <T> T required(T value, String name) {
		if (value == null) {
			throw new DomainValidationException(name + " is required");
		}
		return value;
	}

	public StudentId studentId() {
		return this.studentId;
	}

	public FullName fullName() {
		return this.fullName;
	}

	public EmailAddress email() {
		return this.email;
	}

	public DateOfBirth dateOfBirth() {
		return this.dateOfBirth;
	}

	public GradeLevel gradeLevel() {
		return this.gradeLevel;
	}

	public Section section() {
		return this.section;
	}

	public SubjectMarks marks() {
		return this.marks;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Student that
				&& this.studentId.equals(that.studentId)
				&& this.fullName.equals(that.fullName)
				&& this.email.equals(that.email)
				&& this.dateOfBirth.equals(that.dateOfBirth)
				&& this.gradeLevel.equals(that.gradeLevel)
				&& this.section.equals(that.section)
				&& this.marks.equals(that.marks);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.studentId, this.fullName, this.email, this.dateOfBirth, this.gradeLevel, this.section, this.marks);
	}

	@Override
	public String toString() {
		return "Student[" + this.studentId + "]";
	}
}
