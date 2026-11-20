import type { ApiErrorBody } from '../types/api';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: string[];
  readonly correlationId: string | null;

  constructor(status: number, code: string, message: string, details: string[] = [], correlationId: string | null = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
    this.correlationId = correlationId;
  }
}

export interface ApiClient {
  get<T>(path: string, params?: Record<string, string | number | undefined>, signal?: AbortSignal): Promise<T>;
  post<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T>;
  upload<T>(path: string, file: File, signal?: AbortSignal): Promise<T>;
}

export function createApiClient(baseUrl: string = import.meta.env.VITE_API_BASE_URL ?? '/api'): ApiClient {
  async function request<T>(path: string, init: RequestInit, signal?: AbortSignal): Promise<T> {
    let response: Response;
    try {
      response = await fetch(`${baseUrl}${path}`, { ...init, signal, headers: { Accept: 'application/json', ...init.headers } });
    } catch (cause) {
      if (signal?.aborted) throw cause;
      throw new ApiError(0, 'NETWORK_ERROR', 'Could not reach the server. Check your connection and retry.');
    }
    if (!response.ok) {
      let body: Partial<ApiErrorBody> = {};
      try {
        body = (await response.json()) as Partial<ApiErrorBody>;
      } catch {
        body = {};
      }
      throw new ApiError(
        response.status,
        body.code ?? `HTTP_${response.status}`,
        body.message ?? 'The request could not be completed.',
        body.details ?? [],
        body.correlationId ?? null,
      );
    }
    return (await response.json()) as T;
  }

  return {
    get<T>(path: string, params?: Record<string, string | number | undefined>, signal?: AbortSignal) {
      const query = new URLSearchParams();
      Object.entries(params ?? {}).forEach(([key, value]) => {
        if (value !== undefined && value !== '') query.set(key, String(value));
      });
      const suffix = query.toString();
      return request<T>(`${path}${suffix ? `?${suffix}` : ''}`, { method: 'GET' }, signal);
    },
    post<T>(path: string, body?: unknown, signal?: AbortSignal) {
      return request<T>(path, {
        method: 'POST',
        headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
      }, signal);
    },
    upload<T>(path: string, file: File, signal?: AbortSignal) {
      const form = new FormData();
      form.append('file', file);
      return request<T>(path, { method: 'POST', body: form }, signal);
    },
  };
}

export const apiClient = createApiClient();
