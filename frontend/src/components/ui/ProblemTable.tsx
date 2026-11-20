import type { RowError } from '../../types/api';

interface ProblemTableProps {
  errors: RowError[];
}

export function ProblemTable({ errors }: ProblemTableProps) {
  if (errors.length === 0) {
    return <p className="cg-card p-6 text-sm text-ink-500">No problems on this page.</p>;
  }

  return (
    <div className="cg-card overflow-x-auto">
      <table className="min-w-full text-left text-sm">
        <caption className="sr-only">Rejected rows with physical line numbers and error codes</caption>
        <thead className="bg-canvas text-xs uppercase tracking-wider text-ink-500">
          <tr>
            <th scope="col" className="cg-table-head">Row</th>
            <th scope="col" className="cg-table-head">Student ID</th>
            <th scope="col" className="cg-table-head">Field</th>
            <th scope="col" className="cg-table-head">Problem</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {errors.map((error, index) => (
            <tr key={`${error.lineNumber}-${error.field}-${error.errorCode}-${index}`}>
              <td className="cg-table-cell tabular-nums">{error.lineNumber}</td>
              <td className="cg-table-cell tabular-nums">{error.rawStudentId ?? '(blank)'}</td>
              <td className="cg-table-cell">{error.field === '(whole row)' ? '(whole row)' : error.field.replaceAll('_', ' ')}</td>
              <td className="cg-table-cell">
                <span>{error.message}</span>
                <span className="cg-tag ml-2 bg-canvas">{error.errorCode}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
