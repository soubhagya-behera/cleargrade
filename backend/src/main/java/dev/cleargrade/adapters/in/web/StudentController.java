package dev.cleargrade.adapters.in.web;

import java.util.Locale;

import dev.cleargrade.adapters.in.web.dto.PageResponse;
import dev.cleargrade.adapters.in.web.dto.StudentReportResponse;
import dev.cleargrade.adapters.in.web.dto.StudentSummaryResponse;
import dev.cleargrade.application.exception.InvalidParameterException;
import dev.cleargrade.application.model.StudentSearchCriteria;
import dev.cleargrade.application.service.StudentQueryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Student directory and report API; returns DTOs with derived grading only. */
@RestController
@RequestMapping("/api/students")
public class StudentController {

	private final StudentQueryService queryService;

	public StudentController(StudentQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public PageResponse<StudentSummaryResponse> list(
			@RequestParam(required = false) String query,
			@RequestParam(required = false) Integer grade,
			@RequestParam(required = false) String section,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size,
			@RequestParam(defaultValue = "studentId,asc") String sort) {
		String[] sortParts = sort == null ? new String[] { "studentId", "asc" } : sort.split(",", -1);
		if (sortParts.length != 2 || sortParts[0].isBlank()
				|| !(sortParts[1].equalsIgnoreCase("asc") || sortParts[1].equalsIgnoreCase("desc"))) {
			throw new InvalidParameterException("sort must be property,asc|desc");
		}
		StudentSearchCriteria criteria = new StudentSearchCriteria(query, grade, section, page, size,
				sortParts[0].strip(), sortParts[1].equalsIgnoreCase("asc"));
		return PageResponse.from(this.queryService.search(criteria), StudentSummaryResponse::from);
	}

	@GetMapping(path = "/{studentId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public StudentReportResponse report(@PathVariable String studentId) {
		return StudentReportResponse.from(this.queryService.findReport(
				studentId == null ? "" : studentId.toUpperCase(Locale.ROOT)));
	}
}
