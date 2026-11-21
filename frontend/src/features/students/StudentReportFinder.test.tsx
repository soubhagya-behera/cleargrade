import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { StudentReportFinder } from './StudentReportFinder';
import { StudentReportPage } from './StudentReportPage';
import { renderWithProviders } from '../../test/render';
import type { Page, StudentReport, StudentSummary } from '../../types/api';

vi.mock('../../api/students', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/students')>();
  return { ...actual, searchStudents: vi.fn(), getStudent: vi.fn() };
});

const { searchStudents, getStudent } = await import('../../api/students');

const rohan: StudentSummary = {
  studentId: 'STU00001', fullName: 'Rohan Kaur', gradeLevel: 9, section: 'D', percentage: '42.1', grade: 'F',
};
const snehaMalhotra: StudentSummary = {
  studentId: 'STU00002', fullName: 'Sneha Malhotra', gradeLevel: 11, section: 'F', percentage: '70.3', grade: 'B',
};
const snehaKapoor: StudentSummary = {
  studentId: 'STU00005', fullName: 'Sneha Kapoor', gradeLevel: 12, section: 'B', percentage: '49.6', grade: 'F',
};

const report: StudentReport = {
  ...rohan,
  email: 'rohan.kaur85@example.edu', dateOfBirth: '2012-04-20',
  marks: { maths: '43', science: '34', english: '42', socialStudies: '51.7', secondLanguage: '40' },
  total: '210.7', maximum: 500, passed: false,
};

function resultPage(content: StudentSummary[], totalElements = content.length): Page<StudentSummary> {
  return { content, page: 0, size: 8, totalElements, totalPages: Math.ceil(totalElements / 8) };
}

function renderFinder() {
  return renderWithProviders(
    <Routes>
      <Route path="/student-report" element={<StudentReportFinder />} />
      <Route path="/students/:studentId" element={<StudentReportPage />} />
    </Routes>,
    '/student-report',
  );
}

function typeSearch(value: string) {
  fireEvent.change(screen.getByLabelText('Student name or student ID'), { target: { value } });
}

beforeEach(() => {
  vi.mocked(searchStudents).mockReset();
  vi.mocked(getStudent).mockReset();
});

describe('StudentReportFinder', () => {
  it('shows the finder landing page without searching until input is entered', () => {
    renderFinder();
    expect(screen.getByRole('heading', { name: 'Find a student report' })).toBeInTheDocument();
    expect(screen.getByText(/Search by student name or student ID to open a full report/)).toBeInTheDocument();
    expect(screen.getByLabelText('Student name or student ID')).toBeInTheDocument();
    expect(searchStudents).not.toHaveBeenCalled();
  });

  it('searches by student name and shows matching result details', async () => {
    vi.mocked(searchStudents).mockResolvedValue(resultPage([rohan], 105));
    renderFinder();
    typeSearch('Rohan');

    const result = await screen.findByRole('link', { name: /Rohan Kaur/ });
    expect(result).toHaveAttribute('href', '/students/STU00001');
    expect(screen.getByText('STU00001 · Class 9, Section D')).toBeInTheDocument();
    expect(vi.mocked(searchStudents).mock.calls.at(-1)?.[0].query).toBe('Rohan');
    expect(screen.getByText(/105 students found/)).toBeInTheDocument();
  });

  it('searches by student ID', async () => {
    vi.mocked(searchStudents).mockResolvedValue(resultPage([rohan], 1));
    renderFinder();
    typeSearch('STU00001');

    expect(await screen.findByRole('link', { name: /Rohan Kaur/ })).toBeInTheDocument();
    expect(vi.mocked(searchStudents).mock.calls.at(-1)?.[0].query).toBe('STU00001');
  });

  it('lists multiple matching results', async () => {
    vi.mocked(searchStudents).mockResolvedValue(resultPage([snehaMalhotra, snehaKapoor], 87));
    renderFinder();
    typeSearch('Sneha');

    expect(await screen.findByRole('link', { name: /Sneha Malhotra/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Sneha Kapoor/ })).toBeInTheDocument();
    expect(screen.getByText(/Showing the first 2 of 87 matches/)).toBeInTheDocument();
  });

  it('shows a helpful no-results state with a clear action', async () => {
    vi.mocked(searchStudents).mockResolvedValue(resultPage([]));
    renderFinder();
    typeSearch('zzzznomatch');

    expect(await screen.findByText(/No students match “zzzznomatch”/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Clear search' }));
    await waitFor(() => expect(screen.getByLabelText('Student name or student ID')).toHaveValue(''));
  });

  it('shows a loading state while searching', async () => {
    let resolve!: (value: Page<StudentSummary>) => void;
    vi.mocked(searchStudents).mockReturnValue(new Promise((res) => { resolve = res; }));
    renderFinder();
    typeSearch('Rohan');

    expect(await screen.findByRole('status', { name: 'Loading' })).toBeInTheDocument();
    resolve(resultPage([rohan], 1));
    expect(await screen.findByRole('link', { name: /Rohan Kaur/ })).toBeInTheDocument();
  });

  it('shows an error state and can retry', async () => {
    vi.mocked(searchStudents)
      .mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', 'boom'))
      .mockResolvedValue(resultPage([rohan], 1));
    renderFinder();
    typeSearch('Rohan');

    expect(await screen.findByText('Could not search for students.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('link', { name: /Rohan Kaur/ })).toBeInTheDocument();
  });

  it('opens the correct individual report when a result is selected', async () => {
    vi.mocked(searchStudents).mockResolvedValue(resultPage([rohan], 1));
    vi.mocked(getStudent).mockResolvedValue(report);
    renderFinder();
    typeSearch('Rohan');

    fireEvent.click(await screen.findByRole('link', { name: /Rohan Kaur/ }));
    expect(await screen.findByRole('heading', { name: 'Student report' })).toBeInTheDocument();
    expect(await screen.findByText('Rohan Kaur')).toBeInTheDocument();
    expect(screen.getByText('42.1%')).toBeInTheDocument();
    expect(screen.getByText('FAIL')).toBeInTheDocument();
    expect(vi.mocked(getStudent)).toHaveBeenCalledWith('STU00001', expect.anything());
  });
});
