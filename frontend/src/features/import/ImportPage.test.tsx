import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { ApiError } from '../../api/client';
import { ImportPage } from './ImportPage';
import { LAST_IMPORT_BATCH_ID_KEY } from './lastImportBatch';
import { renderWithProviders } from '../../test/render';
import type { ImportResponse, Page, RowError } from '../../types/api';

vi.mock('../../api/ai', () => ({
  aiKeys: { status: () => ['ai', 'status'], insights: (id: number) => ['ai', 'insights', id] },
  getAiStatus: vi.fn(),
  postInsights: vi.fn(),
}));

vi.mock('../../api/imports', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/imports')>();
  return { ...actual, uploadCsv: vi.fn(), getBatch: vi.fn(), getBatchErrors: vi.fn() };
});

const { uploadCsv, getBatch, getBatchErrors } = await import('../../api/imports');
const { getAiStatus } = await import('../../api/ai');

const STORAGE_KEY = LAST_IMPORT_BATCH_ID_KEY;

const batch: ImportResponse = {
  id: 7, filename: 'marks.csv', status: 'COMPLETED', rowsRead: 25, imported: 16,
  newCount: 16, changedCount: 0, unchangedCount: 0, rejected: 9, errorCount: 9,
  startedAt: '2026-11-19T00:00:00Z', finishedAt: '2026-11-19T00:00:01Z', durationMs: 1000,
  deduplicated: false,
};

const errors: Page<RowError> = {
  content: [{ lineNumber: 14, rawStudentId: 'STU001', field: 'email', errorCode: 'INVALID_FORMAT', message: 'Not a valid email address' }],
  page: 0, size: 50, totalElements: 1, totalPages: 1,
};

beforeEach(() => {
  window.sessionStorage.clear();
  vi.mocked(uploadCsv).mockReset();
  vi.mocked(getBatch).mockReset();
  vi.mocked(getBatch).mockResolvedValue(batch);
  vi.mocked(getBatchErrors).mockReset();
  vi.mocked(getBatchErrors).mockResolvedValue(errors);
  vi.mocked(getAiStatus).mockReset();
  vi.mocked(getAiStatus).mockResolvedValue({
    enabled: true, geminiConfigured: false, geminiModel: '', ollamaConfigured: false,
    ollamaReachable: false, ollamaModelPresent: false, ollamaModel: '', inferenceTested: false,
    deterministicAvailable: true, defaultEngine: 'RULE_BASED', routing: 'RULE_BASED_ONLY', routingReasons: [],
    lastAnalysisEngine: null, lastAnalysisReason: null,
    lastAnalysisUsage: { available: false, inputTokens: null, outputTokens: null, thinkingTokens: null,
      totalTokens: null, modelId: '', providerStatus: '', interactionStatus: '', durationMs: 0 },
  });
});

describe('ImportPage', () => {
  it('uploads a CSV and renders the real summary and row problems with physical lines', async () => {
    let resolveUpload!: (value: ImportResponse) => void;
    vi.mocked(uploadCsv).mockImplementation(() => new Promise<ImportResponse>((resolve) => { resolveUpload = resolve; }));
    renderWithProviders(<ImportPage />);

    const input = screen.getByLabelText('Choose CSV file');
    fireEvent.change(input, { target: { files: [new File(['csv'], 'marks.csv', { type: 'text/csv' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Uploading and importing…' })).toBeDisabled());
    resolveUpload(batch);
    expect(await screen.findByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('We read 25 rows'))).toBeInTheDocument();
    expect(screen.getByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('16 were imported'))).toBeInTheDocument();
    expect(screen.getByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('9 rows were rejected'))).toBeInTheDocument();
    expect(await screen.findByText('Not a valid email address')).toBeInTheDocument();
    expect(screen.getByText('14')).toBeInTheDocument();
    expect(screen.getAllByText('INVALID_FORMAT').length).toBeGreaterThan(0);
  });

  it('shows friendly messages for duplicate and oversized uploads', async () => {
    renderWithProviders(<ImportPage />);
    const input = screen.getByLabelText('Choose CSV file');
    const upload = async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));
    };

    vi.mocked(uploadCsv).mockRejectedValueOnce(new ApiError(409, 'BATCH_PROCESSING', 'conflict', ['batchId: 12']));
    fireEvent.change(input, { target: { files: [new File(['csv'], 'marks.csv', { type: 'text/csv' })] } });
    await upload();
    expect(await screen.findByText(/already being imported \(batchId: 12\)/)).toBeInTheDocument();

    vi.mocked(uploadCsv).mockRejectedValueOnce(new ApiError(413, 'FILE_TOO_LARGE', 'too large'));
    fireEvent.change(input, { target: { files: [new File(['csv'], 'large.csv', { type: 'text/csv' })] } });
    await upload();
    expect(await screen.findByText(/larger than the 10 MiB limit/)).toBeInTheDocument();
  });

  it('rejects an oversized file before requesting an upload', async () => {
    vi.mocked(uploadCsv).mockResolvedValue(batch);
    renderWithProviders(<ImportPage />);
    const large = new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.csv', { type: 'text/csv' });
    fireEvent.change(screen.getByLabelText('Choose CSV file'), { target: { files: [large] } });
    expect(screen.getByText(/larger than the 10 MiB limit/)).toBeInTheDocument();
    expect(uploadCsv).not.toHaveBeenCalled();
  });

  it('renders failure state when a batch lookup returns not found', async () => {
    vi.mocked(getBatchErrors).mockResolvedValue(errors);
    const { getBatch } = await import('../../api/imports');
    vi.mocked(getBatch).mockRejectedValue(new ApiError(404, 'BATCH_NOT_FOUND', 'missing'));
    renderWithProviders(<ImportPage />);
    fireEvent.change(screen.getByLabelText('Batch id'), { target: { value: '999' } });
    fireEvent.click(screen.getByRole('button', { name: 'Look up' }));
    await waitFor(() => expect(screen.getByText(/import batch was not found/i)).toBeInTheDocument());
  });

  it('saves only the completed batch id after a successful upload', async () => {
    vi.mocked(uploadCsv).mockResolvedValue(batch);
    renderWithProviders(<ImportPage />);
    fireEvent.change(screen.getByLabelText('Choose CSV file'), { target: { files: [new File(['csv'], 'marks.csv', { type: 'text/csv' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));

    expect(await screen.findByText(/Import complete/)).toBeInTheDocument();
    expect(window.sessionStorage.getItem(STORAGE_KEY)).toBe('7');
  });

  it('clears the selected file after a successful upload', async () => {
    vi.mocked(uploadCsv).mockResolvedValue(batch);
    renderWithProviders(<ImportPage />);
    fireEvent.change(screen.getByLabelText('Choose CSV file'), { target: { files: [new File(['csv'], 'marks.csv', { type: 'text/csv' })] } });
    expect(screen.getByText('marks.csv')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));

    expect(await screen.findByText(/Import complete/)).toBeInTheDocument();
    expect(screen.queryByText('File ready')).not.toBeInTheDocument();
    expect(screen.queryByText('marks.csv')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Upload and import' })).toBeDisabled();
  });

  it('restores the previous batch after navigating away and returning', async () => {
    vi.mocked(uploadCsv).mockResolvedValue(batch);
    const first = renderWithProviders(<ImportPage />);
    fireEvent.change(screen.getByLabelText('Choose CSV file'), { target: { files: [new File(['csv'], 'marks.csv', { type: 'text/csv' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));
    await screen.findByText(/Import complete/);
    first.unmount();

    renderWithProviders(<ImportPage />);

    expect(await screen.findByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('We read 25 rows'))).toBeInTheDocument();
    expect(getBatch).toHaveBeenCalledWith(7, expect.anything());
  });

  it('restores the stored batch through the existing batch API on remount', async () => {
    window.sessionStorage.setItem(STORAGE_KEY, '7');
    renderWithProviders(<ImportPage />);

    expect(await screen.findByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('We read 25 rows'))).toBeInTheDocument();
    expect(getBatch).toHaveBeenCalledWith(7, expect.anything());
  });

  it('restores the summary, stat cards, row problems and intelligence panel', async () => {
    window.sessionStorage.setItem(STORAGE_KEY, '7');
    renderWithProviders(<ImportPage />);

    expect(await screen.findByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('We read 25 rows'))).toBeInTheDocument();
    expect(screen.getByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('16 were imported'))).toBeInTheDocument();
    expect(screen.getByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('9 rows were rejected'))).toBeInTheDocument();
    expect(await screen.findByText('Not a valid email address')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Row problems' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Import quality report' })).toBeInTheDocument();
  });

  it('ignores an invalid stored batch id without requesting the API', async () => {
    window.sessionStorage.setItem(STORAGE_KEY, 'not-a-number');
    renderWithProviders(<ImportPage />);

    await waitFor(() => expect(getBatch).not.toHaveBeenCalled());
    expect(window.sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('removes a stale stored batch id when the API returns not found', async () => {
    window.sessionStorage.setItem(STORAGE_KEY, '999');
    vi.mocked(getBatch).mockRejectedValueOnce(new ApiError(404, 'BATCH_NOT_FOUND', 'missing'));
    renderWithProviders(<ImportPage />);

    await waitFor(() => expect(window.sessionStorage.getItem(STORAGE_KEY)).toBeNull());
    expect(screen.queryByText(/could not be restored/)).not.toBeInTheDocument();
  });

  it('keeps the stored batch id after a temporary network failure', async () => {
    window.sessionStorage.setItem(STORAGE_KEY, '7');
    vi.mocked(getBatch).mockRejectedValueOnce(new ApiError(0, 'NETWORK_ERROR', 'offline'));
    renderWithProviders(<ImportPage />);

    expect(await screen.findByText(/could not be restored/)).toBeInTheDocument();
    expect(window.sessionStorage.getItem(STORAGE_KEY)).toBe('7');
  });

  it('keeps the previous completed result when a new upload fails', async () => {
    vi.mocked(uploadCsv).mockResolvedValueOnce(batch);
    renderWithProviders(<ImportPage />);
    const input = screen.getByLabelText('Choose CSV file');
    fireEvent.change(input, { target: { files: [new File(['csv'], 'marks.csv', { type: 'text/csv' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));
    await screen.findByText(/Import complete/);

    vi.mocked(uploadCsv).mockRejectedValueOnce(new ApiError(413, 'FILE_TOO_LARGE', 'too large'));
    fireEvent.change(input, { target: { files: [new File(['csv'], 'next.csv', { type: 'text/csv' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));

    expect(await screen.findByText(/larger than the 10 MiB limit/)).toBeInTheDocument();
    expect(screen.getByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('We read 25 rows'))).toBeInTheDocument();
    expect(window.sessionStorage.getItem(STORAGE_KEY)).toBe('7');
  });

  it('does not let a slow restoration overwrite a newer successful upload', async () => {
    window.sessionStorage.setItem(STORAGE_KEY, '7');
    let resolveRestore!: (value: ImportResponse) => void;
    vi.mocked(getBatch).mockImplementation(() => new Promise<ImportResponse>((resolve) => { resolveRestore = resolve; }));
    const newer: ImportResponse = { ...batch, id: 8, rowsRead: 30, imported: 20, rejected: 10 };
    vi.mocked(uploadCsv).mockResolvedValue(newer);
    renderWithProviders(<ImportPage />);
    await waitFor(() => expect(getBatch).toHaveBeenCalled());

    fireEvent.change(screen.getByLabelText('Choose CSV file'), { target: { files: [new File(['csv'], 'marks.csv', { type: 'text/csv' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));
    expect(await screen.findByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('We read 30 rows'))).toBeInTheDocument();

    resolveRestore(batch);
    await waitFor(() => expect(screen.queryByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('We read 25 rows'))).not.toBeInTheDocument());
    expect(window.sessionStorage.getItem(STORAGE_KEY)).toBe('8');
  });

  it('never stores file contents or student details in session storage', async () => {
    vi.mocked(uploadCsv).mockResolvedValue(batch);
    renderWithProviders(<ImportPage />);
    const csv = 'student_id,full_name\nSTU001,Jane Doe';
    fireEvent.change(screen.getByLabelText('Choose CSV file'), { target: { files: [new File([csv], 'marks.csv', { type: 'text/csv' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));
    await screen.findByText(/Import complete/);

    const values = Array.from({ length: window.sessionStorage.length }, (_, index) =>
      window.sessionStorage.getItem(window.sessionStorage.key(index)!));
    expect(values).toEqual(['7']);
    expect(values.join(' ')).not.toContain('STU001');
    expect(values.join(' ')).not.toContain('Jane Doe');
    expect(values.join(' ')).not.toContain('marks.csv');
  });
});
