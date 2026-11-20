import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { ApiError } from '../../api/client';
import { ImportPage } from './ImportPage';
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

const { uploadCsv, getBatchErrors } = await import('../../api/imports');
const { getAiStatus } = await import('../../api/ai');

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
  vi.mocked(uploadCsv).mockReset();
  vi.mocked(getBatchErrors).mockReset();
  vi.mocked(getBatchErrors).mockResolvedValue(errors);
  vi.mocked(getAiStatus).mockReset();
  vi.mocked(getAiStatus).mockResolvedValue({
    enabled: true, geminiConfigured: false, geminiAvailable: false, geminiModel: '', ollamaConfigured: false,
    ollamaAvailable: false, ollamaModel: '', deterministicAvailable: true,
    defaultEngine: 'RULE_BASED', routing: 'RULE_BASED_ONLY', routingReasons: [],
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
});
