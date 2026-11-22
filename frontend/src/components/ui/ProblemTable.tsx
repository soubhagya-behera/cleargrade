import { useSyncExternalStore } from 'react';
import type { RowError } from '../../types/api';

interface ProblemTableProps {
  errors: RowError[];
}

const MOBILE_QUERY = '(max-width: 767px)';

function subscribe(callback: () => void): () => void {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return () => {};
  const query = window.matchMedia(MOBILE_QUERY);
  query.addEventListener('change', callback);
  return () => query.removeEventListener('change', callback);
}

function getSnapshot(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return true;
  return window.matchMedia(MOBILE_QUERY).matches;
}

function getServerSnapshot(): boolean {
  return true;
}

function fieldLabel(field: string): string {
  return field === '(whole row)' ? '(whole row)' : field.replaceAll('_', ' ');
}

function ProblemCards({ errors }: ProblemTableProps) {
  return (
    <ul className="divide-y divide-line">
      {errors.map((error, index) => (
        <li key={`${error.lineNumber}-${error.field}-${error.errorCode}-${index}`} className="p-4">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="cg-caps text-[11px] font-bold text-ink-500">Row</p>
              <p className="mt-0.5 text-sm font-semibold tabular-nums text-navy-900">{error.lineNumber}</p>
            </div>
          </div>
          <dl className="mt-3 space-y-2.5">
            <div className="min-w-0">
              <dt className="cg-caps text-[11px] font-bold text-ink-500">Student ID</dt>
              <dd className={`mt-0.5 break-words text-sm tabular-nums ${error.rawStudentId ? 'font-medium text-navy-900' : 'italic text-ink-500'}`}>
                {error.rawStudentId ?? '(blank)'}
              </dd>
            </div>
            <div className="min-w-0">
              <dt className="cg-caps text-[11px] font-bold text-ink-500">Field</dt>
              <dd className="mt-0.5 break-words text-sm text-ink-700">{fieldLabel(error.field)}</dd>
            </div>
            <div className="min-w-0">
              <dt className="cg-caps text-[11px] font-bold text-ink-500">Problem</dt>
              <dd className="mt-1 flex min-w-0 flex-wrap items-start gap-2 text-sm leading-relaxed text-ink-700">
                <span className="min-w-0 flex-1 [overflow-wrap:anywhere]">{error.message}</span>
                <span className="cg-tag shrink-0 whitespace-nowrap bg-lake-100 !px-2 !py-1 text-[10px] font-bold leading-none">{error.errorCode}</span>
              </dd>
            </div>
          </dl>
        </li>
      ))}
    </ul>
  );
}

function ProblemRowsTable({ errors }: ProblemTableProps) {
  return (
    <div className="cg-scroll-cue overflow-x-auto">
      <table className="min-w-full table-fixed text-left text-sm">
        <caption className="sr-only">Rejected rows with physical line numbers and error codes</caption>
        <colgroup>
          <col className="w-[10%]" />
          <col className="w-[19%]" />
          <col className="w-[19%]" />
          <col className="w-[52%]" />
        </colgroup>
        <thead>
          <tr>
            <th scope="col" className="cg-table-head">Row</th>
            <th scope="col" className="cg-table-head">Student ID</th>
            <th scope="col" className="cg-table-head">Field</th>
            <th scope="col" className="cg-table-head">Problem</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {errors.map((error, index) => (
            <tr key={`${error.lineNumber}-${error.field}-${error.errorCode}-${index}`} className="transition-colors hover:bg-lake-050/60">
              <td className="cg-table-cell align-top font-semibold tabular-nums text-navy-900">{error.lineNumber}</td>
              <td className="cg-table-cell align-top font-medium tabular-nums text-navy-900 [overflow-wrap:anywhere]">{error.rawStudentId ?? '(blank)'}</td>
              <td className="cg-table-cell align-top [overflow-wrap:anywhere]">{fieldLabel(error.field)}</td>
              <td className="cg-table-cell align-top">
                <div className="flex min-w-0 flex-wrap items-start gap-x-2 gap-y-1.5">
                  <span className="min-w-0 flex-1 leading-relaxed [overflow-wrap:anywhere]">{error.message}</span>
                  <span className="cg-tag shrink-0 whitespace-nowrap bg-lake-100 !px-2 !py-1 text-[10px] font-bold leading-none">{error.errorCode}</span>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function ProblemTable({ errors }: ProblemTableProps) {
  const stacked = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  if (errors.length === 0) {
    return <p className="cg-card p-6 text-sm text-ink-500">No problems on this page.</p>;
  }

  return (
    <div className="cg-card overflow-hidden">
      {stacked ? <ProblemCards errors={errors} /> : <ProblemRowsTable errors={errors} />}
    </div>
  );
}
