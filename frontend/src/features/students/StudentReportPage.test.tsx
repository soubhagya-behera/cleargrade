import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { StudentReportPage } from './StudentReportPage';
import type { StudentReport } from '../../types/api';

vi.mock('../../api/students', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/students')>();
  return { ...actual, getStudent: vi.fn() };
});

const { getStudent } = await import('../../api/students');

const report: StudentReport = {
  studentId: 'STU001', fullName: 'Alice Smith', email: 'alice@example.com', dateOfBirth: '2001-01-01',
  gradeLevel: 9, section: 'A', percentage: '82.0', grade: 'A',
  marks: { maths: '82.0', science: '82.0', english: '82.0', socialStudies: '82.0', secondLanguage: '82.0' },
  total: '410.0', maximum: 500, passed: true,
};

function renderReport() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
    queryCache: new QueryCache({ onError: () => {} }),
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/students/stu001']}>
        <Routes><Route path="/students/:studentId" element={<StudentReportPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => vi.mocked(getStudent).mockReset());

describe('StudentReportPage', () => {
  it('renders the real report with derived grade and PASS badge', async () => {
    vi.mocked(getStudent).mockResolvedValue(report);
    renderReport();
    expect(await screen.findByText('Alice Smith')).toBeInTheDocument();
    expect(screen.getByText('82.0%')).toBeInTheDocument();
    expect(screen.getByText('PASS')).toBeInTheDocument();
    expect(screen.getByText('Total: 410.0 / 500')).toBeInTheDocument();
    expect(screen.getByText('Second language')).toBeInTheDocument();
    expect(vi.mocked(getStudent)).toHaveBeenCalledWith('stu001', expect.anything());
  });

  it('renders the not-found state for an unknown student', async () => {
    vi.mocked(getStudent).mockResolvedValue(null as unknown as Awaited<ReturnType<typeof getStudent>>);
    renderReport();
    expect(await screen.findByText(/No student was found for this ID/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to students' })).toBeInTheDocument();
  });
});
