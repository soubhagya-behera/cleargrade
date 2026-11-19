import { UsersRound } from 'lucide-react';
import { PlaceholderPage } from '../../components/ui/PlaceholderPage';

export function StudentDirectoryPage() {
  return (
    <PlaceholderPage
      eyebrow="Students"
      title="Student Directory"
      description="Browse the imported student snapshot. The directory will support server-side search and pagination once the API is available."
      icon={UsersRound}
      planned={[
        'Searchable, paginated student list',
        'Links to individual student reports',
        'Loading, empty and error states',
        'Keyboard-accessible table and filters',
      ]}
    />
  );
}
