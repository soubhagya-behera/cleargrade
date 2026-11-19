import { FileText } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { PlaceholderPage } from '../../components/ui/PlaceholderPage';

export function StudentReportPage() {
  const { studentId } = useParams<{ studentId: string }>();

  return (
    <section aria-labelledby="page-heading">
      <PlaceholderPage
        eyebrow="Students"
        title="Student Report"
        description={`Individual report for student ${studentId}. The report will show marks, total, percentage, grade and pass/fail as defined by the ratified grading scheme.`}
        icon={FileText}
        planned={[
          'Marks, total, percentage and grade',
          'Pass/fail result using the approved scheme',
          'Print-friendly layout (deferred bonus)',
          'Return link to the student directory',
        ]}
      />
      <p className="mt-6 text-sm">
        <Link to="/students" className="font-medium text-brand-700 underline underline-offset-4 hover:text-brand-600">
          Back to Student Directory
        </Link>
      </p>
    </section>
  );
}
