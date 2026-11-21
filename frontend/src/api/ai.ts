import { apiClient } from './client';
import type { AiStatusResponse, InsightResponse } from '../types/api';

export const aiKeys = {
  status: () => ['ai', 'status'] as const,
  insights: (batchId: number, refresh = false, run = 0) =>
    ['ai', 'insights', batchId, refresh, run] as const,
};

export function getAiStatus(signal?: AbortSignal): Promise<AiStatusResponse> {
  return apiClient.get<AiStatusResponse>('/ai/status', undefined, signal);
}

export function postInsights(batchId: number, refresh = false, signal?: AbortSignal): Promise<InsightResponse> {
  return apiClient.post<InsightResponse>(
    `/imports/${batchId}/insights${refresh ? '?refresh=true' : ''}`,
    {},
    signal,
  );
}
