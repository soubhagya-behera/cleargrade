interface StatCardProps {
  label: string;
  value: string | number;
  hint?: string;
  tone?: 'default' | 'success' | 'danger';
  reveal?: '1' | '2' | '3';
}

const REVEAL_CLASSES: Record<NonNullable<StatCardProps['reveal']>, string> = {
  '1': 'cg-rise-1',
  '2': 'cg-rise-2',
  '3': 'cg-rise-3',
};

export function StatCard({ label, value, hint, tone = 'default', reveal = '1' }: StatCardProps) {
  const valueTone = tone === 'success' ? '!text-success' : tone === 'danger' ? '!text-danger' : '';
  const markerTone = tone === 'success' ? 'bg-success' : tone === 'danger' ? 'bg-danger' : 'bg-lake-500';
  return (
    <div className={`cg-card cg-stat-card relative min-w-0 overflow-hidden p-5 sm:p-6 ${REVEAL_CLASSES[reveal]}`}>
      <div className="flex min-w-0 items-start justify-between gap-3">
        <p className="cg-caps mb-2 min-w-0 text-[11px] font-bold text-ink-500">{label}</p>
        <span aria-hidden="true" className={`mt-1 h-2.5 w-2.5 shrink-0 rounded-full ${markerTone}`} />
      </div>
      <p className={`cg-stat-value ${valueTone}`}>{value}</p>
      {hint && <p className="mt-1.5 break-words text-xs leading-relaxed text-ink-500">{hint}</p>}
      <span aria-hidden="true" className={`absolute inset-x-5 bottom-0 h-1 rounded-t-full ${markerTone}`} />
    </div>
  );
}
