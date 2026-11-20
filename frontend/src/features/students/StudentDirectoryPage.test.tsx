import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { ApiError } from '../../api/client';
import { StudentDirectoryPage } from './StudentDirectoryPage';
import { renderWithProviders } from '../../test/render';
import type { Page, StudentSummary } from '../../types/api';

vi.mock('../../api/students', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/students')>();
  return { ...actual, searchStudents: vi.fn() };
});

const { searchStudents } = await import('../../api/students');

const student: StudentSummary = {
  studentId: 'STU001', fullName: 'Alice Smith', gradeLevel: 9, section: 'A', percentage: '82.0', grade: 'A',
};
const page: Page<StudentSummary> = {
  content: [student], page: 0, size: 25, totalElements: 51, totalPages: 3,
};

beforeEach(() => {
  vi.mocked(searchStudents).mockReset();
  vi.mocked(searchStudents).mockResolvedValue(page);
});

describe('StudentDirectoryPage', () => {
  it('shows a loading state then real student data and pagination', async () => {
    renderWithProviders(<StudentDirectoryPage />);
    expect(screen.getByRole('status', { name: 'Loading' })).toBeInTheDocument();

    expect(await screen.findByText('Alice Smith')).toBeInTheDocument();
    expect(screen.getByText(/Showing 1 to 25 of 51 students/)).toBeInTheDocument();
    expect(screen.getByText('82.0%')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => expect(vi.mocked(searchStudents).mock.calls.at(-1)?.[0].page).toBe(1));
  });

  it('applies server-side search, grade and section filters', async () => {
    renderWithProviders(<StudentDirectoryPage />);
    fireEvent.change(screen.getByLabelText('Search by name or student ID'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByLabelText('Class'), { target: { value: '9' } });
    fireEvent.change(screen.getByLabelText('Section'), { target: { value: 'A' } });

    await waitFor(() => {
      const criteria = vi.mocked(searchStudents).mock.calls.at(-1)?.[0];
      expect(criteria?.query).toBe('alice');
      expect(criteria?.grade).toBe(9);
      expect(criteria?.section).toBe('A');
    });
  });

  it('renders an empty state with a clear-search action', async () => {
    vi.mocked(searchStudents).mockResolvedValue({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    renderWithProviders(<StudentDirectoryPage />);
    fireEvent.change(screen.getByLabelText('Search by name or student ID'), { target: { value: 'nobody' } });
    expect(await screen.findByRole('button', { name: 'Clear search' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Clear search' }));
    await waitFor(() => expect(screen.getByLabelText('Search by name or student ID')).toHaveValue(''));
  });

  it('renders an error state and retries', async () => {
    vi.mocked(searchStudents).mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', 'boom'));
    renderWithProviders(<StudentDirectoryPage />);
    expect(await screen.findByText('Could not load students.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(vi.mocked(searchStudents).mock.calls.length).toBeGreaterThan(1));
  });
});
