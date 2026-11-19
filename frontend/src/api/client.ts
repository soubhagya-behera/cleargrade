export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export interface ApiClient {
  get<T>(path: string, signal?: AbortSignal): Promise<T>;
}

export function createApiClient(baseUrl: string = import.meta.env.VITE_API_BASE_URL ?? '/api'): ApiClient {
  async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
    const response = await fetch(`${baseUrl}${path}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal,
    });

    if (!response.ok) {
      throw new ApiError(response.status, `Request failed with status ${response.status}`);
    }

    return (await response.json()) as T;
  }

  return { get };
}

export const apiClient = createApiClient();
