import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, createApiClient } from './client';

afterEach(() => vi.unstubAllGlobals());

describe('api client', () => {
  it('parses successful JSON responses and builds query strings', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const client = createApiClient('/api');

    await expect(client.get('/students', { page: 2, size: 25, query: 'alice' })).resolves.toEqual({ ok: true });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/students?page=2&size=25&query=alice');
  });

  it('exposes the documented error code and details on a failed response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 409, code: 'BATCH_PROCESSING', message: 'This file is already being imported.',
      details: ['batchId: 12'], correlationId: null,
    }), { status: 409 })));
    const client = createApiClient('/api');

    const error = await client.get('/imports').catch((cause: unknown) => cause);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
    expect((error as ApiError).code).toBe('BATCH_PROCESSING');
    expect((error as ApiError).details).toEqual(['batchId: 12']);
  });

  it('maps a network failure to a safe NETWORK_ERROR', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const client = createApiClient('/api');

    const error = await client.get('/students').catch((cause: unknown) => cause);
    expect((error as ApiError).code).toBe('NETWORK_ERROR');
    expect((error as ApiError).message).toContain('Could not reach the server');
  });

  it('posts a multipart body for uploads', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 1 }), { status: 201 }));
    vi.stubGlobal('fetch', fetchMock);
    const client = createApiClient('/api');

    await client.upload('/imports', new File(['id'], 'marks.csv', { type: 'text/csv' }));
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(fetchMock.mock.calls[0][1].body).toBeInstanceOf(FormData);
  });
});
