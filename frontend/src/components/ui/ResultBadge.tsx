interface ResultBadgeProps {
  passed: boolean;
}

export function ResultBadge({ passed }: ResultBadgeProps) {
  return (
    <span className={`inline-flex items-center rounded-md px-3 py-1 text-xs font-bold tracking-wide ${passed ? 'bg-emerald-50 text-success' : 'bg-red-50 text-danger'}`}>
      {passed ? 'PASS' : 'FAIL'}
    </span>
  );
}
