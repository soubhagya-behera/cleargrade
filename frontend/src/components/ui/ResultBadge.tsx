interface ResultBadgeProps {
  passed: boolean;
}

export function ResultBadge({ passed }: ResultBadgeProps) {
  return (
    <span className={`rounded px-2 py-1 text-xs font-semibold ${passed ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}`}>
      {passed ? 'PASS' : 'FAIL'}
    </span>
  );
}
