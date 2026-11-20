package dev.cleargrade.adapters.in.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.cleargrade.adapters.in.web.dto.ErrorResponse;
import dev.cleargrade.application.exception.BatchNotFoundException;
import dev.cleargrade.application.exception.CsvImportException;
import dev.cleargrade.application.exception.DigestConflictException;
import dev.cleargrade.application.exception.FileTooLargeException;
import dev.cleargrade.application.exception.InvalidEncodingException;
import dev.cleargrade.application.exception.InvalidFileTypeException;
import dev.cleargrade.application.exception.InvalidParameterException;
import dev.cleargrade.application.exception.StudentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Maps application and web failures to the documented /api error envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(CsvImportException.class)
	public ResponseEntity<ErrorResponse> csvFailure(CsvImportException error, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, error.failureCode().name(), error.getMessage(), List.of(), request);
	}

	@ExceptionHandler(InvalidEncodingException.class)
	public ResponseEntity<ErrorResponse> invalidEncoding(InvalidEncodingException error, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_ENCODING", error.getMessage(), List.of(), request);
	}

	@ExceptionHandler({ FileTooLargeException.class, MaxUploadSizeExceededException.class })
	public ResponseEntity<ErrorResponse> tooLarge(Exception error, HttpServletRequest request) {
		return response(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
				"The uploaded file exceeds the configured size limit", List.of(), request);
	}

	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<ErrorResponse> malformedMultipart(MultipartException error, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "The multipart request is invalid",
				List.of(), request);
	}

	@ExceptionHandler({ InvalidFileTypeException.class, HttpMediaTypeNotSupportedException.class })
	public ResponseEntity<ErrorResponse> invalidType(Exception error, HttpServletRequest request) {
		return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_FILE_TYPE",
				"Only multipart CSV uploads are accepted", List.of(), request);
	}

	@ExceptionHandler(DigestConflictException.class)
	public ResponseEntity<ErrorResponse> duplicateDigest(DigestConflictException error, HttpServletRequest request) {
		String batchId = Long.toString(error.existing().batchId());
		return response(HttpStatus.CONFLICT, "BATCH_PROCESSING", "This file is already being imported.",
				List.of("batchId: " + batchId), request);
	}

	@ExceptionHandler({ InvalidParameterException.class, MethodArgumentNotValidException.class,
			MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
			MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ErrorResponse> invalidRequest(Exception error, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "The request parameters are invalid",
				List.of(), request);
	}

	@ExceptionHandler(BatchNotFoundException.class)
	public ResponseEntity<ErrorResponse> batchNotFound(BatchNotFoundException error, HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", error.getMessage(), List.of(), request);
	}

	@ExceptionHandler(StudentNotFoundException.class)
	public ResponseEntity<ErrorResponse> studentNotFound(StudentNotFoundException error, HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", error.getMessage(), List.of(), request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> unexpected(Exception error, HttpServletRequest request) {
		String correlationId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		LOG.error("correlationId={} type={} path={}", correlationId, error.getClass().getSimpleName(),
				request.getRequestURI());
		return new ResponseEntity<>(new ErrorResponse(Instant.now(), 500, "INTERNAL_ERROR",
				"An unexpected error occurred. Quote the correlation id when reporting this problem.",
				List.of(), correlationId, request.getRequestURI()), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private static ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message,
			List<String> details, HttpServletRequest request) {
		return new ResponseEntity<>(new ErrorResponse(Instant.now(), status.value(), code, message,
				details, null, request.getRequestURI()), status);
	}
}
