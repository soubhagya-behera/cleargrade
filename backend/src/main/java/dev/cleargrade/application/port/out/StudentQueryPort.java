package dev.cleargrade.application.port.out;

import java.util.Optional;

import dev.cleargrade.application.model.PageResult;
import dev.cleargrade.application.model.StudentSearchCriteria;
import dev.cleargrade.domain.student.Student;

/** Read-side access to the accepted-student snapshot. */
public interface StudentQueryPort {

	PageResult<Student> search(StudentSearchCriteria criteria);

	Optional<Student> findByStudentId(String studentId);
}
