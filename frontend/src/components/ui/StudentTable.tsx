import { Link } from 'react-router-dom';
import type { StudentSummary } from '../../types/api';

interface StudentTableProps {
  students: StudentSummary[];
}

export function StudentTable({ students }: StudentTableProps) {
  if (students.length === 0) {
    return <p className="rounded-lg border border-line bg-surface p-6 text-sm text-ink-500">No students on this page.</p>;
  }
  return (
    <div className="overflow-x-auto rounded-xl border border-line bg-surface shadow-sm">
      <table className="min-w-full text-left text-sm">
        <thead className="bg-canvas text-xs uppercase tracking-wider text-ink-500">
          <tr>
            <th scope="col" className="px-4 py-3">Student ID</th>
            <th scope="col" className="px-4 py-3">Name</th>
            <th scope="col" className="px-4 py-3">Class</th>
            <th scope="col" className="px-4 py-3">Section</th>
            <th scope="col" className="px-4 py-3">Percentage</th>
            <th scope="col" className="px-4 py-3">Grade</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {students.map((student) => (
            <tr key={student.studentId}>
              <td className="px-4 py-3 font-medium tabular-nums">{student.studentId}</td>
              <td className="px-4 py-3">
                <Link className="text-brand-600 underline underline-offset-4" to={`/students/${student.studentId}`}>
                  {student.fullName}
                </Link>
              </td>
              <td className="px-4 py-3 tabular-nums">{student.gradeLevel}</td>
              <td className="px-4 py-3">{student.section}</td>
              <td className="px-4 py-3 tabular-nums">{student.percentage}%</td>
              <td className="px-4 py-3">{student.grade}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
