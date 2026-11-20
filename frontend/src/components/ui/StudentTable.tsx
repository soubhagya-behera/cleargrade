import { Link } from 'react-router-dom';
import type { StudentSummary } from '../../types/api';

interface StudentTableProps {
  students: StudentSummary[];
}

export function StudentTable({ students }: StudentTableProps) {
  if (students.length === 0) {
    return <p className="cg-card p-6 text-sm text-ink-500">No students on this page.</p>;
  }
  return (
    <div className="cg-card overflow-x-auto">
      <table className="min-w-full text-left text-sm">
        <caption className="sr-only">Imported students with derived percentage and grade</caption>
        <thead className="bg-canvas text-xs uppercase tracking-wider text-ink-500">
          <tr>
            <th scope="col" className="cg-table-head">Student ID</th>
            <th scope="col" className="cg-table-head">Name</th>
            <th scope="col" className="cg-table-head">Class</th>
            <th scope="col" className="cg-table-head">Section</th>
            <th scope="col" className="cg-table-head">Percentage</th>
            <th scope="col" className="cg-table-head">Grade</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {students.map((student) => (
            <tr key={student.studentId}>
              <td className="cg-table-cell font-medium tabular-nums">{student.studentId}</td>
              <td className="cg-table-cell">
                <Link className="font-medium text-brand-700 underline underline-offset-4 hover:text-brand-600" to={`/students/${student.studentId}`}>
                  {student.fullName}
                </Link>
              </td>
              <td className="cg-table-cell tabular-nums">{student.gradeLevel}</td>
              <td className="cg-table-cell">{student.section}</td>
              <td className="cg-table-cell tabular-nums">{student.percentage}%</td>
              <td className="cg-table-cell"><span className="cg-tag bg-brand-100 text-brand-700">{student.grade}</span></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
