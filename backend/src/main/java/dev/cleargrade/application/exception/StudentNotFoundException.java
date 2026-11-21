package dev.cleargrade.application.exception;

/** Unknown student id; mapped to 404 STUDENT_NOT_FOUND. */
public class StudentNotFoundException extends RuntimeException {

	public StudentNotFoundException(String studentId) {
		super("Student was not found");
	}
}
