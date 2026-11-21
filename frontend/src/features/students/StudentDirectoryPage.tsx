import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ApiError } from '../../api/client';
import { searchStudents, studentKeys, type StudentQuery } from '../../api/students';
import { EmptyState, ErrorState, SkeletonTable } from '../../components/ui/States';
import { Pagination } from '../../components/ui/Pagination';
import { StudentTable } from '../../components/ui/StudentTable';

export function StudentDirectoryPage() {
  const [queryInput, setQueryInput] = useState('');
  const [query, setQuery] = useState('');
  const [grade, setGrade] = useState('');
  const [section, setSection] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [sort, setSort] = useState('studentId,asc');

  useEffect(() => {
    const timer = window.setTimeout(() => { setQuery(queryInput); setPage(0); }, 300);
    return () => window.clearTimeout(timer);
  }, [queryInput]);

  const criteria: StudentQuery = {
    query: query || undefined,
    grade: grade ? Number(grade) : undefined,
    section: section || undefined,
    page,
    size,
    sort,
  };
  const students = useQuery({
    queryKey: studentKeys.list(criteria),
    queryFn: ({ signal }) => searchStudents(criteria, signal),
  });

  return (
    <section aria-labelledby="page-heading">
      <header className="mb-7">
        <p className="cg-eyebrow">Students</p>
        <h2 id="page-heading" className="cg-page-title">Student directory</h2>
      </header>

      <div className="cg-card cg-rise-1 p-5 sm:p-6">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_repeat(3,minmax(0,160px))]">
          <div>
            <label htmlFor="student-search" className="cg-label">Search by name or student ID</label>
            <input
              id="student-search"
              value={queryInput}
              onChange={(event) => setQueryInput(event.target.value)}
              placeholder="e.g. Alice or STU001"
              className="cg-input mt-2"
            />
          </div>
          <label className="cg-label" htmlFor="grade-filter">
            Class
            <select id="grade-filter" value={grade} onChange={(event) => { setGrade(event.target.value); setPage(0); }} className="cg-input mt-2">
              <option value="">All</option>
              {Array.from({ length: 12 }, (_, index) => index + 1).map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <label className="cg-label" htmlFor="section-filter">
            Section
            <select id="section-filter" value={section} onChange={(event) => { setSection(event.target.value); setPage(0); }} className="cg-input mt-2">
              <option value="">All</option>
              {['A', 'B', 'C', 'D', 'E', 'F'].map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <label className="cg-label" htmlFor="sort-select">
            Sort
            <select id="sort-select" value={sort} onChange={(event) => { setSort(event.target.value); setPage(0); }} className="cg-input mt-2">
              <option value="studentId,asc">Student ID ↑</option>
              <option value="studentId,desc">Student ID ↓</option>
              <option value="fullName,asc">Name ↑</option>
              <option value="fullName,desc">Name ↓</option>
            </select>
          </label>
        </div>
      </div>

      <p className="mt-6 text-xs font-medium text-ink-500" aria-live="polite">
        {students.data
          ? students.data.totalElements === 0
            ? 'No students match the current filters.'
            : `Showing ${students.data.page * students.data.size + 1} to ${Math.min((students.data.page + 1) * students.data.size, students.data.totalElements)} of ${students.data.totalElements} students`
          : 'Loading students…'}
      </p>

      <div className="mt-3">
        {students.isPending && <SkeletonTable />}
        {students.isError && (
          <ErrorState
            message={students.error instanceof ApiError && students.error.code === 'INVALID_PARAMETER'
              ? 'Those filters are not valid. Adjust them and retry.'
              : 'Could not load students.'}
            onRetry={() => students.refetch()}
          />
        )}
        {students.data?.totalElements === 0 && (
          <EmptyState
            message={query ? `No students match “${query}”.` : 'No students imported yet.'}
            actionLabel={query ? 'Clear search' : undefined}
            onAction={query ? () => { setQueryInput(''); setQuery(''); } : undefined}
          />
        )}
        {students.data && students.data.totalElements > 0 && <StudentTable students={students.data.content} />}
        {students.data && (
          <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
            <Pagination page={students.data.page} totalPages={students.data.totalPages} onPageChange={setPage} label="Student pages" />
            <label className="text-xs font-medium text-ink-500" htmlFor="page-size">
              Rows per page
              <select id="page-size" value={size} onChange={(event) => { setSize(Number(event.target.value)); setPage(0); }} className="cg-input ml-2 !inline-flex !min-h-9 w-auto !py-1.5 align-middle">
                {[25, 50, 100].map((value) => <option key={value} value={value}>{value}</option>)}
              </select>
            </label>
          </div>
        )}
      </div>
    </section>
  );
}
