import { useQuery } from '@tanstack/react-query';
import { createApiClient } from './client';

interface HealthResponse {
  status: string;
}

const healthClient = createApiClient('');

export function useBackendHealth() {
  return useQuery({
    queryKey: ['backend-health'],
    queryFn: ({ signal }) => healthClient.get<HealthResponse>('/actuator/health', undefined, signal),
    retry: false,
    refetchInterval: 60_000,
  });
}
