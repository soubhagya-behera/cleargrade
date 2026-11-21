import { FileText, FileUp, UsersRound, type LucideIcon } from 'lucide-react';

export interface NavigationItem {
  to: string;
  label: string;
  icon: LucideIcon;
  disabled?: boolean;
  hint?: string;
}

export const navigationItems: NavigationItem[] = [
  { to: '/import', label: 'Import Quality Center', icon: FileUp },
  { to: '/students', label: 'Student Directory', icon: UsersRound },
  {
    to: '/student-report',
    label: 'Student Report',
    icon: FileText,
    hint: 'Select a student from the directory',
  },
];

export function pageTitleFor(pathname: string): string {
  if (pathname === '/student-report') return 'Student Report';
  if (pathname.startsWith('/students/') && pathname !== '/students') return 'Student Report';
  if (pathname.startsWith('/students')) return 'Student Directory';
  if (pathname.startsWith('/import')) return 'Import Quality Center';
  return 'ClearGrade';
}
