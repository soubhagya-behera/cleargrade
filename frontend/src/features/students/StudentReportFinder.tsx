import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ChevronRight, Search } from 'lucide-react';
import { searchStudents, studentKeys, type StudentQuery } from '../../api/students';
import { EmptyState, ErrorState, SkeletonTable } from '../../components/ui/States';

const RESULT_LIMIT = 8;

export function StudentReportFinder() {
  const [input, setInput] = useState('');
  const [query, setQuery] = useState('');

  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(input.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [input]);

  const criteria: StudentQuery = {
    query: query || undefined,
    page: 0,
    size: RESULT_LIMIT,
    sort: 'studentId,asc',
  };

  const search = useQuery({
    queryKey: studentKeys.list(criteria),
    queryFn: ({ signal }) => searchStudents(criteria, signal),
    enabled: Boolean(query),
    retry: false,
  });

  return (
    <section aria-labelledby="page-heading">
      <header className="mb-7">
        <p className="cg-eyebrow">Student reports</p>
        <h2 id="page-heading" className="cg-page-title">Find a student report</h2>
        <p className="mt-4 max-w-2xl text-sm text-ink-500">
          Search by student name or student ID to open a full report with marks, overall result and grade.
        </p>
      </header>

      <div className="cg-card cg-rise-1 p-5 sm:p-6">
        <label htmlFor="report-search" className="cg-label">Student name or student ID</label>
        <div className="relative mt-2">
          <Search aria-hidden="true" className="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-ink-500" />
          <input
            id="report-search"
            type="search"
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="e.g. Rohan or STU00001"
            autoComplete="off"
            className="cg-input !pl-11"
          />
        </div>
        <p className="mt-3 text-xs text-ink-500" aria-live="polite">
          {!query
            ? 'Search by name or student ID to see matching reports.'
            : search.isFetching
              ? 'Searching…'
              : search.data
                ? `${search.data.totalElements} student${search.data.totalElements === 1 ? '' : 's'} found for “${query}”.`
                : ''}
        </p>
      </div>

      {query && search.isPending && <div className="mt-6"><SkeletonTable rows={4} /></div>}
      {query && search.isError && (
        <div className="mt-6">
          <ErrorState message="Could not search for students." onRetry={() => search.refetch()} />
        </div>
      )}
      {query && search.data?.totalElements === 0 && (
        <div className="mt-6">
          <EmptyState
            message={`No students match “${query}”. Check the spelling or try a student ID.`}
            actionLabel="Clear search"
            onAction={() => { setInput(''); setQuery(''); }}
          />
        </div>
      )}
      {query && search.data && search.data.totalElements > 0 && (
        <div className="cg-card mt-6 overflow-hidden">
          <ul className="divide-y divide-line">
            {search.data.content.map((student) => (
              <li key={student.studentId}>
                <Link
                  to={`/students/${student.studentId}`}
                  className="group flex flex-wrap items-center gap-3 px-4 py-4 transition-colors hover:bg-lake-050/60 sm:flex-nowrap sm:px-5"
                >
                  <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-lake-100 text-sm font-bold text-lake-700">
                    {student.fullName.split(/\s+/).map((part) => part[0]).slice(0, 2).join('')}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-bold text-navy-900 group-hover:text-lake-700">{student.fullName}</span>
                    <span className="mt-0.5 block text-xs text-ink-500">
                      {student.studentId} · Class {student.gradeLevel}, Section {student.section}
                    </span>
                  </span>
                  <ChevronRight aria-hidden="true" className="size-5 shrink-0 text-lake-600 transition-transform group-hover:translate-x-0.5" />
                </Link>
              </li>
            ))}
          </ul>
          {search.data.totalElements > search.data.content.length && (
            <p className="border-t border-line bg-lake-050/60 px-4 py-3 text-xs text-ink-500 sm:px-5">
              Showing the first {search.data.content.length} of {search.data.totalElements} matches. Narrow your search to find a specific student.
            </p>
          )}
        </div>
      )}
    </section>
  );
}
