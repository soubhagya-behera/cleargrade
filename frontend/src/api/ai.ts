import { apiClient } from './client';
import type { AiStatusResponse, InsightResponse } from '../types/api';

export const aiKeys = {
  status: () => ['ai', 'status'] as const,
  insights: (batchId: number) => ['ai', 'insights', batchId] as const,
};

export function getAiStatus(signal?: AbortSignal): Promise<AiStatusResponse> {
  return apiClient.get<AiStatusResponse>('/ai/status', undefined, signal);
}

export function postInsights(batchId: number, signal?: AbortSignal): Promise<InsightResponse> {
  return apiClient.post<InsightResponse>(`/imports/${batchId}/insights`, {}, signal);
}
