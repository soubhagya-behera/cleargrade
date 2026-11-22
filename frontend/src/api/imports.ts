import { apiClient } from './client';
import type { ImportHistoryEntry, ImportResponse, Page, RowError, RowErrorCode } from '../types/api';

export const importKeys = {
  all: ['import'] as const,
  batch: (id: number) => ['import', 'batch', id] as const,
  errors: (id: number, page: number, size: number, code?: RowErrorCode) =>
    ['import', 'errors', id, page, size, code ?? 'ALL'] as const,
  history: (page: number, size: number) => ['import', 'history', page, size] as const,
};

export function uploadCsv(file: File, signal?: AbortSignal): Promise<ImportResponse> {
  return apiClient.upload<ImportResponse>('/imports', file, signal);
}

export function getBatch(id: number, signal?: AbortSignal): Promise<ImportResponse> {
  return apiClient.get<ImportResponse>(`/imports/${id}`, undefined, signal);
}

export function getImportHistory(page: number, size: number, signal?: AbortSignal): Promise<Page<ImportHistoryEntry>> {
  return apiClient.get<Page<ImportHistoryEntry>>('/imports', { page, size }, signal);
}

export function getBatchReportCsv(id: number, signal?: AbortSignal) {
  return apiClient.download(`/imports/${id}/report`, signal);
}

export function getBatchRowProblemsCsv(id: number, signal?: AbortSignal) {
  return apiClient.download(`/imports/${id}/row-problems`, signal);
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
