import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ProblemTable } from './ProblemTable';
import type { RowError } from '../../types/api';

const errors: RowError[] = [
  { lineNumber: 5, rawStudentId: null, field: 'student_id', errorCode: 'MISSING_REQUIRED', message: 'Student ID is required' },
  { lineNumber: 14, rawStudentId: 'STU-001', field: 'email', errorCode: 'INVALID_FORMAT', message: 'Not a valid email address' },
  { lineNumber: 22, rawStudentId: 'STU-014', field: '(whole row)', errorCode: 'WRONG_FIELD_COUNT', message: 'Row has too many columns' },
];

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ProblemTable responsive presentation', () => {
  it('shows labelled stacked records with every line, badge and blank id at mobile widths', () => {
    render(<ProblemTable errors={errors} />);

    expect(screen.getByText('Student ID is required')).toBeInTheDocument();
    expect(screen.getByText('Not a valid email address')).toBeInTheDocument();
    expect(screen.getByText('Row has too many columns')).toBeInTheDocument();
    expect(screen.getByText('(blank)')).toBeInTheDocument();
    expect(screen.getAllByText('INVALID_FORMAT')).toHaveLength(1);
    expect(screen.getAllByText('WRONG_FIELD_COUNT')).toHaveLength(1);
    expect(screen.getAllByText('MISSING_REQUIRED')).toHaveLength(1);
    for (const line of [5, 14, 22]) {
      expect(screen.getByText(String(line))).toBeInTheDocument();
    }
    expect(screen.getAllByText('Field')).toHaveLength(errors.length);
    expect(screen.getAllByText('Problem')).toHaveLength(errors.length);
    expect(document.querySelector('table')).toBeNull();
  });

  it('keeps the readable table with physical line numbers at larger widths', () => {
    vi.stubGlobal('matchMedia', () => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));

    render(<ProblemTable errors={errors} />);

    expect(document.querySelectorAll('tbody tr')).toHaveLength(errors.length);
    expect(screen.getByText('Not a valid email address')).toBeInTheDocument();
    expect(screen.getByText('INVALID_FORMAT')).toBeInTheDocument();
    expect(screen.getByText('(whole row)')).toBeInTheDocument();
  });

  it('shows a friendly empty state', () => {
    render(<ProblemTable errors={[]} />);
    expect(screen.getByText('No problems on this page.')).toBeInTheDocument();
  });
});
