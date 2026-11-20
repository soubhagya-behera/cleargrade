package dev.cleargrade.adapters.in.web.dto;

import java.util.List;

import dev.cleargrade.application.model.PageResult;

/** Uniform paged envelope; page is 0-based (T-4). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, long totalPages) {

	public static <S, T> PageResponse<T> from(PageResult<S> source, java.util.function.Function<S, T> mapper) {
		return new PageResponse<>(source.content().stream().map(mapper).toList(),
				source.page(), source.size(), source.totalElements(), source.totalPages());
	}
}
