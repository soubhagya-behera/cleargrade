interface StatCardProps {
  label: string;
  value: string | number;
  hint?: string;
  tone?: 'default' | 'success' | 'danger';
}

export function StatCard({ label, value, hint, tone = 'default' }: StatCardProps) {
  const valueTone = tone === 'success' ? 'text-emerald-700' : tone === 'danger' ? 'text-red-700' : 'text-ink-950';
  return (
    <div className="rounded-xl border border-line bg-surface p-5 shadow-sm">
      <p className="text-xs font-semibold uppercase tracking-wider text-ink-500">{label}</p>
      <p className={`mt-2 text-3xl font-semibold tabular-nums ${valueTone}`}>{value}</p>
      {hint && <p className="mt-1 text-xs text-ink-500">{hint}</p>}
    </div>
  );
}
