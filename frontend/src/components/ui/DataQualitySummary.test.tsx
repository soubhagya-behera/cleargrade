import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { DataQualitySummary } from './DataQualitySummary';
import type { ImportResponse, RowErrorCode } from '../../types/api';

const batch: ImportResponse = {
  id: 12,
  filename: 'results.csv',
  status: 'COMPLETED',
  rowsRead: 15,
  imported: 9,
  newCount: 7,
  changedCount: 2,
  unchangedCount: 0,
  rejected: 6,
  errorCount: 8,
  startedAt: '2026-11-22T00:00:00Z',
  finishedAt: '2026-11-22T00:00:01Z',
  durationMs: 1000,
  deduplicated: false,
};

const breakdown: Partial<Record<RowErrorCode, number>> = {
  MISSING_REQUIRED: 1,
  INVALID_FORMAT: 2,
  OUT_OF_RANGE: 3,
  DUPLICATE_ID: 0,
  WRONG_FIELD_COUNT: 2,
};

describe('DataQualitySummary', () => {
  it('shows batch-backed counts and every loaded problem category, including zero counts', () => {
    render(<DataQualitySummary batch={batch} breakdown={breakdown} />);

    expect(screen.getByRole('heading', { name: 'Import summary' })).toBeInTheDocument();
    const summary = screen.getByText((_, element) => element?.tagName === 'P' && element.getAttribute('aria-live') === 'polite');
    expect(summary.textContent?.replace(/\s+/g, ' ')).toContain('We read 15 rows. 9 were imported (7 new, 2 changed, 0 unchanged). 6 rows were rejected because of 8 problems.');
    expect(screen.getByText('Extra problems come from rows with more than one issue.')).toBeInTheDocument();

    const categories = screen.getByRole('list', { name: 'Problem counts by category' });
    expect(within(categories).getByText('Missing values')).toBeInTheDocument();
    expect(within(categories).getByText('Invalid formats')).toBeInTheDocument();
    expect(within(categories).getByText('Out-of-range values')).toBeInTheDocument();
    expect(within(categories).getByText('Duplicate IDs')).toBeInTheDocument();
    expect(within(categories).getByText('Wrong field counts')).toBeInTheDocument();
    expect(within(categories).getByText('0')).toBeInTheDocument();
  });
});
