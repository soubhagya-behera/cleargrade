package dev.cleargrade.application.model;

import java.util.List;

/** Application-owned page result; never exposes a Spring Data or persistence type. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, long totalPages) {

	public PageResult {
		content = List.copyOf(content);
	}

	public static <T> PageResult<T> of(List<T> content, int page, int size, long totalElements) {
		long totalPages = size == 0 ? 0 : (totalElements + size - 1) / size;
		return new PageResult<>(content, page, size, totalElements, totalPages);
	}
}
