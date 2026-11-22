import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { ApiError } from '../../api/client';
import { renderWithProviders } from '../../test/render';
import type { ImportHistoryEntry, ImportResponse, Page, RowError } from '../../types/api';
import { ImportHistory } from './ImportHistory';

vi.mock('../../api/imports', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/imports')>();
  return { ...actual, getImportHistory: vi.fn(), getBatch: vi.fn(), getBatchErrors: vi.fn(), getBatchReportCsv: vi.fn(), getBatchRowProblemsCsv: vi.fn() };
});

vi.mock('./saveCsvDownload', () => ({ saveCsvDownload: vi.fn() }));

const { getImportHistory, getBatch, getBatchErrors, getBatchReportCsv, getBatchRowProblemsCsv } = await import('../../api/imports');
const { saveCsvDownload } = await import('./saveCsvDownload');

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
  vi.mocked(getBatchReportCsv).mockReset();
  vi.mocked(getBatchRowProblemsCsv).mockReset();
  vi.mocked(getBatchReportCsv).mockResolvedValue({ blob: new Blob(['report']), filename: 'report.csv' });
  vi.mocked(getBatchRowProblemsCsv).mockResolvedValue({ blob: new Blob(['issues']), filename: 'issues.csv' });
  vi.mocked(saveCsvDownload).mockReset();
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
    expect(screen.getByRole('button', { name: 'CSV download options for batch 10' })).toHaveAttribute('aria-expanded', 'false');
  });

  it('downloads the report and row problems for the selected batch', async () => {
    vi.mocked(getImportHistory).mockResolvedValue(historyPage([first]));
    renderWithProviders(<ImportHistory />);
    fireEvent.click(await screen.findByRole('button', { name: 'CSV download options for batch 10' }));
    const reportItem = await screen.findByRole('menuitem', { name: /Download Import Report \(CSV\)/ });
    expect(reportItem).toHaveTextContent('Summary of this import and its outcome counts');
    fireEvent.click(reportItem);
    await waitFor(() => expect(getBatchReportCsv).toHaveBeenCalledWith(10));
    expect(saveCsvDownload).toHaveBeenCalledWith(expect.objectContaining({ filename: 'report.csv' }), 'cleargrade-import-10-report.csv');

    fireEvent.click(screen.getByRole('button', { name: 'CSV download options for batch 10' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: /Download Row Problems \(CSV\)/ }));
    await waitFor(() => expect(getBatchRowProblemsCsv).toHaveBeenCalledWith(10));
    expect(saveCsvDownload).toHaveBeenCalledWith(expect.objectContaining({ filename: 'issues.csv' }), 'cleargrade-import-10-row-problems.csv');
  });

  it('disables row problems for a batch that has not completed', async () => {
    vi.mocked(getImportHistory).mockResolvedValue(historyPage([{ ...first, status: 'PROCESSING' }]));
    renderWithProviders(<ImportHistory />);
    fireEvent.click(await screen.findByRole('button', { name: 'CSV download options for batch 10' }));
    expect(await screen.findByRole('menuitem', { name: /Download Row Problems \(CSV\)/ })).toBeDisabled();
    expect(screen.getByText('Available after the batch completes.')).toBeInTheDocument();
    expect(getBatchRowProblemsCsv).not.toHaveBeenCalled();
  });

  it('closes the menu on Escape and outside pointer interaction', async () => {
    vi.mocked(getImportHistory).mockResolvedValue(historyPage([first]));
    renderWithProviders(<ImportHistory />);
    const trigger = await screen.findByRole('button', { name: 'CSV download options for batch 10' });
    fireEvent.click(trigger);
    const menu = await screen.findByRole('menu');
    const menuItems = screen.getAllByRole('menuitem');
    expect(menu).toBeInTheDocument();
    await waitFor(() => expect(document.activeElement).toBe(menuItems[0]));
    fireEvent.keyDown(menuItems[0], { key: 'End' });
    expect(document.activeElement).toBe(menuItems[1]);
    fireEvent.keyDown(menuItems[1], { key: 'Home' });
    expect(document.activeElement).toBe(menuItems[0]);
    fireEvent.keyDown(document, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
    fireEvent.click(trigger);
    expect(await screen.findByRole('menu')).toBeInTheDocument();
    fireEvent.pointerDown(document.body);
    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
  });

  it('shows a busy state, prevents duplicate downloads, and offers retry after failure', async () => {
    vi.mocked(getImportHistory).mockResolvedValue(historyPage([first]));
    vi.mocked(getBatchReportCsv).mockRejectedValueOnce(new ApiError(500, 'EXPORT_FAILED', 'failed'))
      .mockResolvedValueOnce({ blob: new Blob(['report']), filename: null });
    renderWithProviders(<ImportHistory />);
    const trigger = await screen.findByRole('button', { name: 'CSV download options for batch 10' });
    fireEvent.click(trigger);
    fireEvent.click(await screen.findByRole('menuitem', { name: /Download Import Report \(CSV\)/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('CSV download failed.');
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(saveCsvDownload).toHaveBeenCalledWith(expect.objectContaining({ filename: null }), 'cleargrade-import-10-report.csv'));
    expect(getBatchReportCsv).toHaveBeenCalledTimes(2);
  });

  it('disables the trigger while a download is pending', async () => {
    vi.mocked(getImportHistory).mockResolvedValue(historyPage([first]));
    let finish!: (download: { blob: Blob; filename: string | null }) => void;
    vi.mocked(getBatchReportCsv).mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    renderWithProviders(<ImportHistory />);
    const trigger = await screen.findByRole('button', { name: 'CSV download options for batch 10' });
    fireEvent.click(trigger);
    fireEvent.click(await screen.findByRole('menuitem', { name: /Download Import Report \(CSV\)/ }));
    await waitFor(() => expect(getBatchReportCsv).toHaveBeenCalledTimes(1));
    expect(trigger).toBeDisabled();
    expect(screen.getByText('Preparing CSV download…')).toBeInTheDocument();
    fireEvent.click(trigger);
    expect(getBatchReportCsv).toHaveBeenCalledTimes(1);
    finish({ blob: new Blob(['report']), filename: null });
    await waitFor(() => expect(saveCsvDownload).toHaveBeenCalledTimes(1));
    expect(trigger).toBeEnabled();
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
