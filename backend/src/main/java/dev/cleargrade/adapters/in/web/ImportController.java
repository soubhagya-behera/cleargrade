package dev.cleargrade.adapters.in.web;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import dev.cleargrade.adapters.in.web.dto.ImportResponse;
import dev.cleargrade.adapters.in.web.dto.ImportHistoryResponse;
import dev.cleargrade.adapters.in.web.dto.PageResponse;
import dev.cleargrade.adapters.in.web.dto.RowErrorResponse;
import dev.cleargrade.application.exception.CsvFailureCode;
import dev.cleargrade.application.exception.CsvImportException;
import dev.cleargrade.application.exception.DigestConflictException;
import dev.cleargrade.application.exception.FileTooLargeException;
import dev.cleargrade.application.exception.InvalidEncodingException;
import dev.cleargrade.application.exception.InvalidFileTypeException;
import dev.cleargrade.application.model.BatchImportResult;
import dev.cleargrade.application.model.BatchSummary;
import dev.cleargrade.application.service.ImportApplicationService;
import dev.cleargrade.application.service.ImportCsvExportService;
import dev.cleargrade.application.service.ImportQueryService;
import dev.cleargrade.domain.batch.ImportStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Upload and batch-read API. Routes only to application services; no persistence types are exposed. */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
			"text/csv", "application/csv", "text/plain", "application/octet-stream", "application/vnd.ms-excel");
	private static final int MAX_FILENAME_LENGTH = 255;

	private final ImportApplicationService applicationService;
	private final ImportCsvExportService csvExportService;
	private final ImportQueryService queryService;
	private final long maxFileSizeBytes;

	public ImportController(ImportApplicationService applicationService, ImportCsvExportService csvExportService,
			ImportQueryService queryService,
			@Value("${cleargrade.upload.max-file-size}") DataSize maxFileSize) {
		this.applicationService = applicationService;
		this.csvExportService = csvExportService;
		this.queryService = queryService;
		this.maxFileSizeBytes = maxFileSize.toBytes();
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ImportResponse> upload(@RequestParam("file") MultipartFile file) {
		validate(file);
		byte[] content = readBytes(file);
		requireUtf8(content);
		try {
			BatchImportResult result = this.applicationService.importFile(content, sanitize(file.getOriginalFilename()));
			return ResponseEntity.created(URI.create("/api/imports/" + result.batchId()))
					.body(ImportResponse.from(this.queryService.getBatch(result.batchId()), false));
		}
		catch (DigestConflictException conflict) {
			if (conflict.existing().status() == ImportStatus.COMPLETED) {
				return ResponseEntity.ok(ImportResponse.from(
						this.queryService.getBatch(conflict.existing().batchId()), true));
			}
			throw conflict;
		}
	}

	@GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ImportResponse batch(@PathVariable long id) {
		return ImportResponse.from(this.queryService.getBatch(id), false);
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public PageResponse<ImportHistoryResponse> history(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return PageResponse.from(this.queryService.getHistory(page, size), ImportHistoryResponse::from);
	}

	@GetMapping(path = "/{id}/errors", produces = MediaType.APPLICATION_JSON_VALUE)
	public PageResponse<RowErrorResponse> errors(@PathVariable long id,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size,
			@RequestParam(required = false) String code) {
		return PageResponse.from(this.queryService.getErrors(id, page, size, code), RowErrorResponse::from);
	}

	@GetMapping(path = "/{id}/report", produces = "text/csv")
	public ResponseEntity<StreamingResponseBody> report(@PathVariable long id) {
		BatchSummary batch = this.queryService.getBatch(id);
		String filename = "cleargrade-import-" + batch.id() + "-report.csv";
		StreamingResponseBody body = output -> this.csvExportService.writeReport(batch, output);
		return csvDownload(filename, body);
	}

	@GetMapping(path = "/{id}/row-problems", produces = "text/csv")
	public ResponseEntity<StreamingResponseBody> rowProblems(@PathVariable long id) {
		BatchSummary batch = this.queryService.getBatch(id);
		String filename = "cleargrade-import-" + batch.id() + "-row-problems.csv";
		StreamingResponseBody body = output -> this.csvExportService.writeRowProblems(batch, output);
		return csvDownload(filename, body);
	}

	private static ResponseEntity<StreamingResponseBody> csvDownload(String filename, StreamingResponseBody body) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
				.body(body);
	}

	private void validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new CsvImportException("The uploaded file is empty", 1, null, CsvFailureCode.EMPTY_FILE);
		}
		if (file.getSize() > this.maxFileSizeBytes) {
			throw new FileTooLargeException();
		}
		String filename = file.getOriginalFilename();
		if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
			throw new InvalidFileTypeException();
		}
		String contentType = file.getContentType();
		if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
			throw new InvalidFileTypeException();
		}
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException error) {
			throw new IllegalStateException("The uploaded file could not be read", error);
		}
	}

	private static void requireUtf8(byte[] content) {
		try {
			StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(content));
		}
		catch (CharacterCodingException error) {
			throw new InvalidEncodingException();
		}
	}

	private static String sanitize(String originalFilename) {
		String name = originalFilename == null ? "upload.csv" : originalFilename.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
		if (name.isEmpty()) {
			name = "upload.csv";
		}
		return name.length() > MAX_FILENAME_LENGTH ? name.substring(name.length() - MAX_FILENAME_LENGTH) : name;
	}
}
