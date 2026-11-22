import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { ApiError } from '../../api/client';
import { renderWithProviders } from '../../test/render';
import type { ImportHistoryEntry, ImportResponse, Page, RowError } from '../../types/api';
import { ImportHistory } from './ImportHistory';

vi.mock('../../api/imports', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/imports')>();
  return { ...actual, getImportHistory: vi.fn(), getBatch: vi.fn(), getBatchErrors: vi.fn() };
});

const { getImportHistory, getBatch, getBatchErrors } = await import('../../api/imports');

const makeBatch = (id: number, filename: string): ImportResponse => ({
  id, filename, status: 'COMPLETED', rowsRead: 4, imported: 3, newCount: 1, changedCount: 1,
  unchangedCount: 1, rejected: 1, errorCount: 1, startedAt: `2026-11-2${id}T10:00:00Z`,
  finishedAt: `2026-11-2${id}T10:00:01Z`, durationMs: 1000, deduplicated: false,
});
const first = makeBatch(10, 'first.csv');
const second = makeBatch(9, 'second.csv');
const emptyErrors: Page<RowError> = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 };
const historyPage = (content: ImportHistoryEntry[], page = 0, totalElements = content.length): Page<ImportHistoryEntry> => ({
  content, page, size: 10, totalElements, totalPages: Math.ceil(totalElements / 10),
});

beforeEach(() => {
  vi.mocked(getImportHistory).mockReset();
  vi.mocked(getBatch).mockReset();
  vi.mocked(getBatchErrors).mockReset();
  vi.mocked(getBatchErrors).mockResolvedValue(emptyErrors);
  vi.mocked(getBatch).mockImplementation(async (id) => id === first.id ? first : second);
});

describe('ImportHistory', () => {
  it('renders real API values and the server total', async () => {
    vi.mocked(getImportHistory).mockResolvedValue(historyPage([first, second], 0, 12));
    renderWithProviders(<ImportHistory />);
    expect(await screen.findByText('12 batches')).toBeInTheDocument();
    expect(screen.getByText('first.csv')).toBeInTheDocument();
    expect(screen.getByText('second.csv')).toBeInTheDocument();
    expect(screen.getAllByText('COMPLETED')).toHaveLength(2);
    expect(screen.getByRole('button', { name: 'View details for batch 10' })).toBeInTheDocument();
  });

  it('renders an empty state from an empty server page', async () => {
    vi.mocked(getImportHistory).mockResolvedValue(historyPage([], 0, 0));
    renderWithProviders(<ImportHistory />);
    expect(await screen.findByText('No imports yet')).toBeInTheDocument();
    expect(screen.getByText('0 batches')).toBeInTheDocument();
  });

  it('shows loading and retries a failed history request', async () => {
    vi.mocked(getImportHistory).mockRejectedValueOnce(new ApiError(0, 'NETWORK_ERROR', 'offline'))
      .mockResolvedValueOnce(historyPage([first]));
    renderWithProviders(<ImportHistory />);
    expect(screen.getByRole('status', { name: 'Loading' })).toBeInTheDocument();
    fireEvent.click(await screen.findByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('first.csv')).toBeInTheDocument();
    expect(getImportHistory).toHaveBeenCalledTimes(2);
  });

  it('paginates with the requested server page', async () => {
    vi.mocked(getImportHistory).mockImplementation(async (page) => page === 0
      ? historyPage([first], 0, 11)
      : historyPage([second], 1, 11));
    renderWithProviders(<ImportHistory />);
    expect(await screen.findByText('first.csv')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('second.csv')).toBeInTheDocument();
    await waitFor(() => expect(getImportHistory).toHaveBeenLastCalledWith(1, 10, expect.anything()));
  });

  it('loads shared batch details and switches without retaining stale details', async () => {
    vi.mocked(getImportHistory).mockResolvedValue(historyPage([first, second]));
    let resolveSecond!: (batch: ImportResponse) => void;
    vi.mocked(getBatch).mockImplementation((id) => id === first.id
      ? Promise.resolve(first)
      : new Promise((resolve) => { resolveSecond = resolve; }));
    renderWithProviders(<ImportHistory />);

    fireEvent.click(await screen.findByRole('button', { name: 'View details for batch 10' }));
    expect(await screen.findByText((_, element) => element?.tagName === 'P'
      && (element.textContent ?? '').includes('1 new, 1 changed, 1 unchanged'))).toBeInTheDocument();
    expect(await screen.findByText('No problems on this page.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Back to history' }));
    fireEvent.click(await screen.findByRole('button', { name: 'View details for batch 9' }));
    expect(screen.queryByText('Batch 10 · first.csv')).not.toBeInTheDocument();
    resolveSecond(second);
    expect(await screen.findByText('Batch 9 · second.csv')).toBeInTheDocument();
  });
});
