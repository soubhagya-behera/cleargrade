import { apiClient } from './client';
import type { ImportResponse, Page, RowError, RowErrorCode } from '../types/api';

export const importKeys = {
  batch: (id: number) => ['import', 'batch', id] as const,
  errors: (id: number, page: number, size: number, code?: RowErrorCode) =>
    ['import', 'errors', id, page, size, code ?? 'ALL'] as const,
};

export function uploadCsv(file: File, signal?: AbortSignal): Promise<ImportResponse> {
  return apiClient.upload<ImportResponse>('/imports', file, signal);
}

export function getBatch(id: number, signal?: AbortSignal): Promise<ImportResponse> {
  return apiClient.get<ImportResponse>(`/imports/${id}`, undefined, signal);
}

export function getBatchErrors(
  id: number,
  page: number,
  size: number,
  code?: RowErrorCode,
  signal?: AbortSignal,
): Promise<Page<RowError>> {
  return apiClient.get<Page<RowError>>(
    `/imports/${id}/errors`,
    { page, size, code },
    signal,
  );
}
