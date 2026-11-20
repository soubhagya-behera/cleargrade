import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
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
    retry: false,
  });

  return (
    <section aria-labelledby="page-heading">
      <Link to="/students" className="text-sm font-medium text-brand-600 underline underline-offset-4">‹ Back to students</Link>
      <h2 id="page-heading" className="mt-4 text-2xl font-semibold tracking-tight">Student report</h2>

      {report.isPending && <div className="mt-8"><SkeletonTable rows={4} /></div>}
      {report.isError && (
        <div className="mt-8">
          <ErrorState message="Could not load this student report." onRetry={() => report.refetch()} />
        </div>
      )}
      {report.data === null && (
        <div className="mt-8 rounded-xl border border-line bg-surface p-10 text-center">
          <p className="text-sm text-ink-500">No student was found for this ID.</p>
          <Link to="/students" className="mt-4 inline-block rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white">Back to students</Link>
        </div>
      )}
      {report.data && (
        <div className="mt-8 grid gap-6 lg:grid-cols-2">
          <section aria-label="Student identity" className="rounded-xl border border-line bg-surface p-6 shadow-sm">
            <div className="flex items-center gap-4">
              <span aria-hidden="true" className="flex size-12 items-center justify-center rounded-full bg-brand-100 text-lg font-semibold text-brand-700">
                {report.data.fullName.split(/\s+/).map((part) => part[0]).slice(0, 2).join('')}
              </span>
              <div>
                <h3 className="text-lg font-semibold">{report.data.fullName}</h3>
                <p className="text-xs text-ink-500">{report.data.studentId} · Class {report.data.gradeLevel}, Section {report.data.section}</p>
              </div>
            </div>
            <dl className="mt-6 space-y-2 text-sm">
              <div><dt className="inline text-ink-500">Email: </dt><dd className="inline">{report.data.email}</dd></div>
              <div><dt className="inline text-ink-500">Date of birth: </dt><dd className="inline">{new Date(`${report.data.dateOfBirth}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' })}</dd></div>
            </dl>
          </section>

          <section aria-label="Overall result" className="rounded-xl border border-line bg-surface p-6 shadow-sm">
            <p className="text-xs font-semibold uppercase tracking-wider text-ink-500">Overall result</p>
            <div className="mt-2 flex items-end gap-4">
              <p className="text-5xl font-semibold tabular-nums text-ink-950">{report.data.percentage}%</p>
              <ResultBadge passed={report.data.passed} />
              <p className="ml-auto text-lg font-semibold">{report.data.grade}</p>
            </div>
            <p className="mt-3 text-sm text-ink-500">Total: {report.data.total} / {report.data.maximum}</p>
          </section>

          <section aria-label="Marks by subject" className="rounded-xl border border-line bg-surface p-6 shadow-sm lg:col-span-2">
            <h3 className="text-lg font-semibold">Marks by subject</h3>
            <ul className="mt-4 divide-y divide-line">
              {Object.entries(report.data.marks).map(([subject, mark]) => (
                <li key={subject} className="flex items-center justify-between py-3 text-sm">
                  <span>{MARK_LABELS[subject] ?? subject}</span>
                  <span className="tabular-nums">{mark} / 100</span>
                </li>
              ))}
            </ul>
          </section>
        </div>
      )}
    </section>
  );
}
