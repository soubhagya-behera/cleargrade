import type { RowError } from '../../types/api';

interface ProblemTableProps {
  errors: RowError[];
}

export function ProblemTable({ errors }: ProblemTableProps) {
  if (errors.length === 0) {
    return <p className="rounded-lg border border-line bg-surface p-6 text-sm text-ink-500">No problems on this page.</p>;
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-line bg-surface shadow-sm">
      <table className="min-w-full text-left text-sm">
        <thead className="bg-canvas text-xs uppercase tracking-wider text-ink-500">
          <tr>
            <th scope="col" className="px-4 py-3">Row</th>
            <th scope="col" className="px-4 py-3">Student ID</th>
            <th scope="col" className="px-4 py-3">Field</th>
            <th scope="col" className="px-4 py-3">Problem</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {errors.map((error, index) => (
            <tr key={`${error.lineNumber}-${error.field}-${error.errorCode}-${index}`}>
              <td className="px-4 py-3 tabular-nums">{error.lineNumber}</td>
              <td className="px-4 py-3 tabular-nums">{error.rawStudentId ?? '(blank)'}</td>
              <td className="px-4 py-3">{error.field === '(whole row)' ? '(whole row)' : error.field.replaceAll('_', ' ')}</td>
              <td className="px-4 py-3">
                <span>{error.message}</span>
                <span className="ml-2 rounded bg-canvas px-2 py-0.5 text-xs text-ink-500">{error.errorCode}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
