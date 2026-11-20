package dev.cleargrade.application.port.out;

import dev.cleargrade.application.model.PageResult;
import dev.cleargrade.application.model.RowErrorView;

/** Read-side access to stored row-problem occurrences. */
public interface ImportRowErrorQueryPort {

	PageResult<RowErrorView> findErrors(long batchId, int page, int size, String code);
}
