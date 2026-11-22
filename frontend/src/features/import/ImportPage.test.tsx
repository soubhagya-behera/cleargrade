import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { ImportPage } from './ImportPage';
import { LAST_IMPORT_BATCH_ID_KEY } from './lastImportBatch';
import { renderWithProviders } from '../../test/render';
import type { ImportHistoryEntry, ImportResponse, Page, RowError } from '../../types/api';

vi.mock('../../api/ai', () => ({
  aiKeys: { status: () => ['ai', 'status'], insights: (id: number) => ['ai', 'insights', id] },
  getAiStatus: vi.fn(),
  postInsights: vi.fn(),
}));

vi.mock('../../api/imports', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/imports')>();
  return { ...actual, uploadCsv: vi.fn(), getBatch: vi.fn(), getBatchErrors: vi.fn(), getImportHistory: vi.fn() };
});

const { uploadCsv, getBatch, getBatchErrors, getImportHistory } = await import('../../api/imports');
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
  vi.mocked(getImportHistory).mockReset();
  vi.mocked(getImportHistory).mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
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
  it('uses a contextual page heading without duplicating Import History', async () => {
    renderWithProviders(<ImportPage />);

    expect(screen.getByRole('heading', { level: 2, name: 'Import student results' })).toBeInTheDocument();
    expect(screen.getByText('Bring in a CSV, validate every row, and review the results before moving on.')).toBeInTheDocument();
    expect(screen.getByText((_, element) => element?.tagName === 'P'
      && (element.textContent ?? '').includes('Data quality')
      && (element.textContent ?? '').includes('File intake'))).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Import History' }));
    expect(await screen.findByText('No imports yet')).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { name: 'Import History' })).toHaveLength(1);
    expect(screen.getByText('Review previous CSV imports and open a batch to see its summary and row problems.')).toBeInTheDocument();
    expect(screen.getByText((_, element) => element?.tagName === 'P'
      && (element.textContent ?? '').includes('Data quality')
      && (element.textContent ?? '').includes('Import History'))).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Import CSV' }));
    expect(screen.getByRole('heading', { level: 2, name: 'Import student results' })).toBeInTheDocument();
    expect(screen.getByText('Upload a results file')).toBeInTheDocument();
  });

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

  it('does not clear a newer batch id when a stale restoration fails with not found', async () => {
    window.sessionStorage.setItem(STORAGE_KEY, '7');
    let rejectRestore!: (reason: unknown) => void;
    vi.mocked(getBatch).mockImplementation(() => new Promise<ImportResponse>((_, reject) => { rejectRestore = reject; }));
    const newer: ImportResponse = { ...batch, id: 8, rowsRead: 30, imported: 20, rejected: 10 };
    vi.mocked(uploadCsv).mockResolvedValue(newer);
    renderWithProviders(<ImportPage />);
    await waitFor(() => expect(getBatch).toHaveBeenCalled());

    fireEvent.change(screen.getByLabelText('Choose CSV file'), { target: { files: [new File(['csv'], 'marks.csv', { type: 'text/csv' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));
    expect(await screen.findByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('We read 30 rows'))).toBeInTheDocument();

    rejectRestore(new ApiError(404, 'BATCH_NOT_FOUND', 'missing'));
    await waitFor(() => expect(window.sessionStorage.getItem(STORAGE_KEY)).toBe('8'));
    expect(screen.getByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('We read 30 rows'))).toBeInTheDocument();
    expect(screen.queryByText((_, element) =>
      element?.tagName === 'P' && (element.textContent ?? '').includes('We read 25 rows'))).not.toBeInTheDocument();
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

  it('rejects malformed batch lookups without requesting the API', async () => {
    renderWithProviders(<ImportPage />);
    const input = screen.getByLabelText('Batch id');
    for (const value of ['', '   ', 'abc', '0', '-3', '1.5', '1e2', '9007199254740993']) {
      fireEvent.change(input, { target: { value } });
      fireEvent.click(screen.getByRole('button', { name: 'Look up' }));
      expect(await screen.findByText('Enter a positive whole-number batch id.')).toBeInTheDocument();
    }
    expect(getBatch).not.toHaveBeenCalled();
  });

  it('looks up a valid batch id after correcting a malformed entry', async () => {
    renderWithProviders(<ImportPage />);
    const input = screen.getByLabelText('Batch id');
    fireEvent.change(input, { target: { value: 'abc' } });
    fireEvent.click(screen.getByRole('button', { name: 'Look up' }));
    expect(await screen.findByText('Enter a positive whole-number batch id.')).toBeInTheDocument();

    fireEvent.change(input, { target: { value: ' 42 ' } });
    expect(screen.queryByText('Enter a positive whole-number batch id.')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Look up' }));
    await waitFor(() => expect(getBatch).toHaveBeenCalledWith(42, expect.anything()));
  });

  it('refetches cached import history and invalidates student data after a successful upload', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: 30_000, gcTime: 60_000 } },
    });
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const previous: ImportHistoryEntry = {
      id: 6, filename: 'older.csv', status: 'COMPLETED', rowsRead: 1, imported: 1,
      newCount: 1, changedCount: 0, unchangedCount: 0, rejected: 0, errorCount: 0,
      startedAt: '2026-11-18T00:00:00Z', finishedAt: '2026-11-18T00:00:01Z', durationMs: 500,
    };
    const historyPage = (content: ImportHistoryEntry[], totalElements: number): Page<ImportHistoryEntry> => ({
      content, page: 0, size: 10, totalElements, totalPages: Math.ceil(totalElements / 10),
    });
    vi.mocked(getImportHistory).mockResolvedValueOnce(historyPage([previous], 1))
      .mockResolvedValue(historyPage([previous, { ...previous, id: batch.id, filename: batch.filename }], 2));
    vi.mocked(uploadCsv).mockResolvedValue(batch);

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter><ImportPage /></MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Import History' }));
    expect(await screen.findByText('older.csv')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Import CSV' }));

    fireEvent.change(screen.getByLabelText('Choose CSV file'), {
      target: { files: [new File(['csv'], 'marks.csv', { type: 'text/csv' })] },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));
    expect(await screen.findByText(/Import complete/)).toBeInTheDocument();

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['import'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['students'] });

    fireEvent.click(screen.getByRole('button', { name: 'Import History' }));
    expect(await screen.findByText('marks.csv')).toBeInTheDocument();
    await waitFor(() => expect(getImportHistory).toHaveBeenCalledTimes(2));
  });

  it('resets row problem pagination when a different batch replaces the result', async () => {
    const many: ImportResponse = { ...batch, id: 20, rejected: 60, errorCount: 60 };
    const few: ImportResponse = { ...batch, id: 21, rejected: 1, errorCount: 1 };
    vi.mocked(uploadCsv).mockResolvedValueOnce(many).mockResolvedValueOnce(few);
    vi.mocked(getBatchErrors).mockImplementation(async (id, page, size) => ({
      content: size === 1 ? [] : id === many.id && page === 0
        ? [{ lineNumber: 2, rawStudentId: 'STU001', field: 'email', errorCode: 'INVALID_FORMAT', message: 'First page problem' }]
        : id === many.id && page === 1
          ? [{ lineNumber: 40, rawStudentId: 'STU040', field: 'email', errorCode: 'INVALID_FORMAT', message: 'Second page problem' }]
          : id === few.id && page === 0
            ? [{ lineNumber: 2, rawStudentId: 'STU002', field: 'email', errorCode: 'INVALID_FORMAT', message: 'New batch problem' }]
            : [],
      page, size,
      totalElements: id === many.id ? 60 : 1,
      totalPages: id === many.id ? 2 : 1,
    }));

    renderWithProviders(<ImportPage />);
    const input = screen.getByLabelText('Choose CSV file');
    fireEvent.change(input, { target: { files: [new File(['csv'], 'many.csv', { type: 'text/csv' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));
    expect(await screen.findByText('First page problem')).toBeInTheDocument();

    fireEvent.click(within(screen.getByRole('navigation', { name: 'Row problem pages' })).getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('Second page problem')).toBeInTheDocument();

    fireEvent.change(input, { target: { files: [new File(['csv'], 'few.csv', { type: 'text/csv' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload and import' }));
    expect(await screen.findByText('New batch problem')).toBeInTheDocument();
    expect(screen.queryByText('No problems on this page.')).not.toBeInTheDocument();
    await waitFor(() => expect(getBatchErrors).toHaveBeenCalledWith(21, 0, 50, undefined, expect.anything()));
  });
});
