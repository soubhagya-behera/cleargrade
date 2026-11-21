import { Check, X } from 'lucide-react';

interface ResultBadgeProps {
  passed: boolean;
}

export function ResultBadge({ passed }: ResultBadgeProps) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-full border px-3 py-1 text-xs font-extrabold tracking-wide ${
      passed ? 'border-emerald-200 bg-emerald-50 text-success' : 'border-red-200 bg-red-50 text-danger'
    }`}>
      {passed ? <Check aria-hidden="true" className="size-3.5" /> : <X aria-hidden="true" className="size-3.5" />}
      {passed ? 'PASS' : 'FAIL'}
    </span>
  );
}
