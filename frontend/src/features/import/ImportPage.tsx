import { FileUp } from 'lucide-react';
import { PlaceholderPage } from '../../components/ui/PlaceholderPage';

export function ImportPage() {
  return (
    <PlaceholderPage
      eyebrow="Data quality"
      title="Import Quality Center"
      description="Upload a results CSV and review its import outcome. The real upload pipeline will report accepted rows, rejected rows and plain-language explanations for each problem."
      icon={FileUp}
      planned={[
        'CSV upload with validation and size limit',
        'Per-row error explanations with line numbers',
        'Import batch summary and repeat-upload handling',
        'Error report download (deferred bonus)',
      ]}
    />
  );
}
