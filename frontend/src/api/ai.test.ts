import { afterEach, describe, expect, it, vi } from 'vitest';
import aiSource from './ai.ts?raw';
import { getAiStatus, postInsights } from './ai';

afterEach(() => vi.unstubAllGlobals());

describe('intelligence API client', () => {
  it('reads provider status from the documented endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      enabled: true, geminiConfigured: true, geminiAvailable: true, geminiModel: 'gemini-3.8-flash',
      ollamaConfigured: false, ollamaAvailable: false, ollamaModel: '',
      deterministicAvailable: true, defaultEngine: 'GEMINI', routing: 'GEMINI_THEN_RULE_BASED',
      routingReasons: [],
    }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const status = await getAiStatus();

    expect(status.defaultEngine).toBe('GEMINI');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/ai/status');
    expect(fetchMock.mock.calls[0][1].method).toBe('GET');
  });

  it('triggers analysis explicitly for one batch with a JSON body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ batchId: 7, engine: 'RULE_BASED' }),
      { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const report = await postInsights(7);

    expect(report.batchId).toBe(7);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/imports/7/insights');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(fetchMock.mock.calls[0][1].body).toBe('{}');
    expect(JSON.stringify(fetchMock.mock.calls[0][1].headers)).not.toContain('key');
  });

  it('contains no frontend API key or secret configuration', () => {
    expect(aiSource).not.toContain('GEMINI_API_KEY');
    expect(aiSource).not.toContain('apiKey');
    expect(aiSource).not.toContain('import.meta.env.VITE_');
  });
});
