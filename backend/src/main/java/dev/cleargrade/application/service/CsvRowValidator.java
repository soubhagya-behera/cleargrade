package dev.cleargrade.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.cleargrade.application.exception.CsvFailureCode;
import dev.cleargrade.application.exception.CsvImportException;
import dev.cleargrade.application.model.ParsedCsv;
import dev.cleargrade.application.model.ValidatedRows;
import dev.cleargrade.domain.DomainValidationException;
import dev.cleargrade.domain.batch.PhysicalLineNumber;
import dev.cleargrade.domain.batch.RowError;
import dev.cleargrade.domain.batch.RowErrorCode;
import dev.cleargrade.domain.student.DateOfBirth;
import dev.cleargrade.domain.student.EmailAddress;
import dev.cleargrade.domain.student.FullName;
import dev.cleargrade.domain.student.GradeLevel;
import dev.cleargrade.domain.student.Section;
import dev.cleargrade.domain.student.Student;
import dev.cleargrade.domain.student.StudentId;
import dev.cleargrade.domain.student.Subject;
import dev.cleargrade.domain.student.SubjectMarks;
import org.springframework.stereotype.Component;

/** Applies existing domain validation to parsed rows and collects every error occurrence. */
@Component
public class CsvRowValidator {

	private static final DateTimeFormatter DD_MM_YYYY =
			DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
	private static final String REQUIRED_MESSAGE = "Value is required";
	private static final String FORMAT_MESSAGE = "Value has an invalid format";
	private static final String RANGE_MESSAGE = "Value is outside the allowed range";
	private static final String DUPLICATE_MESSAGE = "Student ID is duplicated within this file";
	private static final String FIELD_COUNT_MESSAGE = "Row field count differs from the CSV header";
	private static final String MISSING_HEADER_MESSAGE = "The CSV header is missing a required column";
	private static final Set<String> REQUIRED_COLUMNS = Set.of(
			"student_id", "full_name", "email", "date_of_birth", "grade_level", "section",
			"maths", "science", "english", "social_studies", "second_language");

	private final Clock clock;

	public CsvRowValidator(Clock clock) {
		this.clock = clock;
	}

	public ValidatedRows validate(ParsedCsv parsed) {
		if (parsed.fatalFailure().isPresent()) {
			ParsedCsv.FatalFailure failure = parsed.fatalFailure().get();
			throw new CsvImportException(failure.safeMessage(), failure.physicalLine(), failure.cause(),
					failure.failureCode());
		}
		if (!parsed.headers().containsAll(REQUIRED_COLUMNS)) {
			throw new CsvImportException(MISSING_HEADER_MESSAGE, 1, null,
					CsvFailureCode.MISSING_REQUIRED_COLUMNS);
		}

		List<Student> accepted = new ArrayList<>();
		List<RowError> errors = new ArrayList<>();
		Set<String> reservedIds = new HashSet<>();
		int rejectedRows = 0;

		for (ParsedCsv.Row row : parsed.rows()) {
			PhysicalLineNumber line = PhysicalLineNumber.ofDataRow(row.physicalLine());
			String rawId = row.fields().get("student_id");
			ErrorCollector collector = new ErrorCollector(line, rawId, errors);

			if (row.wrongFieldCount()) {
				errors.add(RowError.wholeRow(line, RowErrorCode.WRONG_FIELD_COUNT, FIELD_COUNT_MESSAGE));
				rejectedRows++;
				continue;
			}

			StudentId studentId = parseStudentId(rawId, collector, reservedIds);
			FullName fullName = parseFullName(row.fields().get("full_name"), collector);
			EmailAddress email = parseEmail(row.fields().get("email"), collector);
			DateOfBirth dateOfBirth = parseDateOfBirth(row.fields().get("date_of_birth"), collector);
			GradeLevel gradeLevel = parseGradeLevel(row.fields().get("grade_level"), collector);
			Section section = parseSection(row.fields().get("section"), collector);
			Map<Subject, BigDecimal> marks = parseMarks(row.fields(), collector);

			if (collector.hasErrors()) {
				rejectedRows++;
				continue;
			}
			accepted.add(new Student(studentId, fullName, email, dateOfBirth, gradeLevel, section,
					SubjectMarks.of(marks)));
		}
		return new ValidatedRows(List.copyOf(accepted), List.copyOf(errors), rejectedRows);
	}

	private StudentId parseStudentId(String raw, ErrorCollector collector, Set<String> reservedIds) {
		if (isBlank(raw)) {
			collector.add("student_id", RowErrorCode.MISSING_REQUIRED, REQUIRED_MESSAGE);
			return null;
		}
		try {
			StudentId parsed = StudentId.of(raw);
			if (!reservedIds.add(parsed.value())) {
				collector.add("student_id", RowErrorCode.DUPLICATE_ID, DUPLICATE_MESSAGE);
			}
			return parsed;
		}
		catch (DomainValidationException error) {
			collector.add("student_id", RowErrorCode.INVALID_FORMAT, FORMAT_MESSAGE);
			return null;
		}
	}

	private FullName parseFullName(String raw, ErrorCollector collector) {
		if (isBlank(raw)) {
			collector.add("full_name", RowErrorCode.MISSING_REQUIRED, REQUIRED_MESSAGE);
			return null;
		}
		try {
			return FullName.of(raw);
		}
		catch (DomainValidationException error) {
			collector.add("full_name", RowErrorCode.INVALID_FORMAT, FORMAT_MESSAGE);
			return null;
		}
	}

	private EmailAddress parseEmail(String raw, ErrorCollector collector) {
		if (isBlank(raw)) {
			collector.add("email", RowErrorCode.MISSING_REQUIRED, REQUIRED_MESSAGE);
			return null;
		}
		try {
			return EmailAddress.of(raw);
		}
		catch (DomainValidationException error) {
			collector.add("email", RowErrorCode.INVALID_FORMAT, FORMAT_MESSAGE);
			return null;
		}
	}

	private DateOfBirth parseDateOfBirth(String raw, ErrorCollector collector) {
		if (isBlank(raw)) {
			collector.add("date_of_birth", RowErrorCode.MISSING_REQUIRED, REQUIRED_MESSAGE);
			return null;
		}
		LocalDate parsed = parseDate(raw);
		if (parsed == null) {
			collector.add("date_of_birth", RowErrorCode.INVALID_FORMAT, FORMAT_MESSAGE);
			return null;
		}
		try {
			return DateOfBirth.of(parsed, this.clock);
		}
		catch (DomainValidationException error) {
			collector.add("date_of_birth", RowErrorCode.OUT_OF_RANGE, RANGE_MESSAGE);
			return null;
		}
	}

	private GradeLevel parseGradeLevel(String raw, ErrorCollector collector) {
		if (isBlank(raw)) {
			collector.add("grade_level", RowErrorCode.MISSING_REQUIRED, REQUIRED_MESSAGE);
			return null;
		}
		int value;
		try {
			value = Integer.parseInt(raw.strip());
		}
		catch (NumberFormatException error) {
			collector.add("grade_level", RowErrorCode.INVALID_FORMAT, FORMAT_MESSAGE);
			return null;
		}
		try {
			return GradeLevel.of(value);
		}
		catch (DomainValidationException error) {
			collector.add("grade_level", RowErrorCode.OUT_OF_RANGE, RANGE_MESSAGE);
			return null;
		}
	}

	private Section parseSection(String raw, ErrorCollector collector) {
		if (isBlank(raw)) {
			collector.add("section", RowErrorCode.MISSING_REQUIRED, REQUIRED_MESSAGE);
			return null;
		}
		try {
			return Section.of(raw);
		}
		catch (DomainValidationException error) {
			collector.add("section", RowErrorCode.INVALID_FORMAT, FORMAT_MESSAGE);
			return null;
		}
	}

	private Map<Subject, BigDecimal> parseMarks(Map<String, String> fields, ErrorCollector collector) {
		Map<Subject, BigDecimal> marks = new EnumMap<>(Subject.class);
		for (Subject subject : Subject.values()) {
			String column = subject.columnName();
			String raw = fields.get(column);
			if (isBlank(raw)) {
				collector.add(column, RowErrorCode.MISSING_REQUIRED, REQUIRED_MESSAGE);
				continue;
			}
			BigDecimal value;
			try {
				value = new BigDecimal(raw.strip());
			}
			catch (NumberFormatException error) {
				collector.add(column, RowErrorCode.INVALID_FORMAT, FORMAT_MESSAGE);
				continue;
			}
			if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
				collector.add(column, RowErrorCode.OUT_OF_RANGE, RANGE_MESSAGE);
				continue;
			}
			marks.put(subject, value);
		}
		return marks;
	}

	private static LocalDate parseDate(String raw) {
		String value = raw.strip();
		try {
			return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
		}
		catch (DateTimeParseException ignored) {
			try {
				return LocalDate.parse(value, DD_MM_YYYY);
			}
			catch (DateTimeParseException other) {
				return null;
			}
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static final class ErrorCollector {

		private final PhysicalLineNumber line;
		private final String rawStudentId;
		private final List<RowError> errors;
		private int count;

		private ErrorCollector(PhysicalLineNumber line, String rawStudentId, List<RowError> errors) {
			this.line = line;
			this.rawStudentId = rawStudentId;
			this.errors = errors;
		}

		private void add(String field, RowErrorCode code, String message) {
			this.errors.add(RowError.forField(this.line, code, field, this.rawStudentId, message));
			this.count++;
		}

		private boolean hasErrors() {
			return this.count > 0;
		}
	}
}
