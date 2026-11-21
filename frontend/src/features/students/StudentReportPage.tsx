import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ChevronLeft } from 'lucide-react';
import { ApiError } from '../../api/client';
import { getStudent, studentKeys } from '../../api/students';
import { ErrorState, SkeletonTable } from '../../components/ui/States';
import { ResultBadge } from '../../components/ui/ResultBadge';

const MARK_LABELS: Record<string, string> = {
  maths: 'Maths',
  science: 'Science',
  english: 'English',
  socialStudies: 'Social studies',
  secondLanguage: 'Second language',
};

export function StudentReportPage() {
  const { studentId = '' } = useParams<{ studentId: string }>();

  const report = useQuery({
    queryKey: studentKeys.report(studentId),
    queryFn: async ({ signal }) => {
      try {
        return await getStudent(studentId, signal);
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      }
    },
    enabled: Boolean(studentId),
    retry: false,
  });

  return (
    <section aria-labelledby="page-heading">
      <nav aria-label="Breadcrumb" className="no-print flex items-center text-xs font-medium text-ink-500">
        <Link to="/students" className="font-semibold text-lake-700 underline decoration-lake-500/50 underline-offset-4 transition-colors hover:text-navy-900">Students</Link>
        <span aria-hidden="true" className="mx-2 text-lake-500">/</span>
        <span aria-current="page" className="font-semibold text-navy-900">{studentId}</span>
      </nav>
      <Link to="/students" className="cg-btn-secondary no-print mt-5 !min-h-10 !px-3.5">
        <ChevronLeft aria-hidden="true" className="size-4" />
        Back to students
      </Link>
      <h2 id="page-heading" className="cg-page-title">Student report</h2>

      {report.isPending && <div className="mt-8"><SkeletonTable rows={4} /></div>}
      {report.isError && (
        <div className="mt-8">
          <ErrorState message="Could not load this student report." onRetry={() => report.refetch()} />
        </div>
      )}
      {report.data === null && (
        <div className="cg-card mt-8 p-10 text-center">
          <p className="text-sm text-ink-500">No student was found for this ID.</p>
          <Link to="/students" className="cg-btn-primary mt-5">Return to directory</Link>
        </div>
      )}
      {report.data && (
        <div className="mt-8 grid gap-6 lg:grid-cols-2">
          <section aria-label="Student identity" className="cg-card cg-rise-1 p-6 sm:p-7">
            <div className="flex items-center gap-4">
              <span aria-hidden="true" className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-lake-100 text-lg font-bold text-lake-700 ring-4 ring-white">
                {report.data.fullName.split(/\s+/).map((part) => part[0]).slice(0, 2).join('')}
              </span>
              <div className="min-w-0">
                <h3 className="truncate text-lg font-bold tracking-tight text-navy-900">{report.data.fullName}</h3>
                <p className="text-xs text-ink-500">{report.data.studentId} · Class {report.data.gradeLevel}, Section {report.data.section}</p>
              </div>
            </div>
            <dl className="mt-6 space-y-3 border-t border-line pt-5 text-sm">
              <div><dt className="inline text-ink-500">Email: </dt><dd className="inline font-medium text-navy-900">{report.data.email}</dd></div>
              <div><dt className="inline text-ink-500">Date of birth: </dt><dd className="inline font-medium text-navy-900">{new Date(`${report.data.dateOfBirth}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' })}</dd></div>
            </dl>
          </section>

          <section aria-label="Overall result" className="cg-card cg-rise-2 relative overflow-hidden p-6 sm:p-7">
            <span aria-hidden="true" className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-lake-500 via-sun-500 to-lake-500" />
            <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-ink-500">Overall result</p>
            <div className="mt-3 flex flex-wrap items-end gap-4">
              <p className="text-5xl font-bold tracking-tight tabular-nums text-navy-900">{report.data.percentage}%</p>
              <ResultBadge passed={report.data.passed} />
              <p className="ml-auto text-xl font-bold text-navy-900">{report.data.grade}</p>
            </div>
            <p className="mt-4 text-sm text-ink-500">Total: {report.data.total} / {report.data.maximum}</p>
          </section>

          <section aria-label="Marks by subject" className="cg-card cg-rise-3 p-6 sm:p-7 lg:col-span-2">
            <h3 className="text-lg font-bold tracking-tight text-navy-900">Marks by subject</h3>
            <ul className="mt-5 divide-y divide-line">
              {Object.entries(report.data.marks).map(([subject, mark]) => (
                <li key={subject} className="flex items-center justify-between gap-4 py-3.5 text-sm transition-colors hover:bg-lake-050/60">
                  <span className="font-medium text-ink-700">{MARK_LABELS[subject] ?? subject}</span>
                  <span className="font-semibold tabular-nums text-navy-900">{mark} / 100</span>
                </li>
              ))}
            </ul>
          </section>
        </div>
      )}
    </section>
  );
}
