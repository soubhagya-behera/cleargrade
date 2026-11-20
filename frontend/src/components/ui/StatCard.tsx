interface StatCardProps {
  label: string;
  value: string | number;
  hint?: string;
  tone?: 'default' | 'success' | 'danger';
}

export function StatCard({ label, value, hint, tone = 'default' }: StatCardProps) {
  const valueTone = tone === 'success' ? '!text-success' : tone === 'danger' ? '!text-danger' : '';
  return (
    <div className="cg-card p-5">
      <p className="text-xs font-semibold uppercase tracking-wider text-ink-500">{label}</p>
      <p className={`cg-stat-value ${valueTone}`}>{value}</p>
      {hint && <p className="mt-2 text-xs text-ink-500">{hint}</p>}
    </div>
  );
}
