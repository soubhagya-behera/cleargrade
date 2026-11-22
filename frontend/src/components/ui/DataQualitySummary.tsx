import type { ImportResponse, RowErrorCode } from '../../types/api';

interface DataQualitySummaryProps {
  batch: ImportResponse;
  breakdown?: Partial<Record<RowErrorCode, number>>;
}

const CODE_LABELS: Record<RowErrorCode, string> = {
  MISSING_REQUIRED: 'Missing values',
  INVALID_FORMAT: 'Invalid formats',
  OUT_OF_RANGE: 'Out-of-range values',
  DUPLICATE_ID: 'Duplicate IDs',
  WRONG_FIELD_COUNT: 'Wrong field counts',
};

const CODES: RowErrorCode[] = [
  'MISSING_REQUIRED', 'INVALID_FORMAT', 'OUT_OF_RANGE', 'DUPLICATE_ID', 'WRONG_FIELD_COUNT',
];

export function DataQualitySummary({ batch, breakdown }: DataQualitySummaryProps) {
  const rowsRead = batch.rowsRead ?? 0;
  const imported = batch.imported ?? 0;
  const rejected = batch.rejected ?? 0;
  const problems = batch.errorCount ?? 0;
  const breakdownItems = breakdown
    ? CODES.flatMap((code) => breakdown[code] === undefined ? [] : [{ code, count: breakdown[code]! }])
    : [];

  return (
    <section aria-labelledby="quality-heading" className="cg-card p-4 sm:p-5">
      <div>
        <h3 id="quality-heading" className="text-2xl font-bold tracking-tight text-navy-900">Import summary</h3>
        <p className="mt-2 max-w-5xl text-[14px] leading-relaxed text-ink-700 sm:text-[15px]" aria-live="polite">
          We read <strong className="font-bold tabular-nums text-navy-950">{rowsRead}</strong> {rowsRead === 1 ? 'row' : 'rows'}.{' '}
          <strong className="font-bold tabular-nums text-navy-950">{imported}</strong> were imported
          {' '}({batch.newCount ?? 0} new, {batch.changedCount ?? 0} changed, {batch.unchangedCount ?? 0} unchanged).{' '}
          <strong className="font-bold tabular-nums text-navy-950">{rejected}</strong> {rejected === 1 ? 'row was' : 'rows were'} rejected because of{' '}
          <strong className="font-bold tabular-nums text-navy-950">{problems}</strong> {problems === 1 ? 'problem' : 'problems'}. Rejected rows and problems are
          different numbers: one row can have several problems.
        </p>
        {problems > rejected && (
          <p className="mt-1.5 text-[12px] font-medium text-ink-500">Extra problems come from rows with more than one issue.</p>
        )}
        {breakdownItems.length > 0 && (
          <ul aria-label="Problem counts by category" className="mt-4 flex flex-wrap gap-2">
            {breakdownItems.map(({ code, count }, index) => (
              <li key={code} className="inline-flex min-w-0 items-center gap-2 rounded-md border border-line bg-canvas px-2.5 py-1.5 text-[12px] leading-snug text-ink-700">
                <span aria-hidden="true" className={`h-2 w-2 shrink-0 rounded-sm ${index % 2 === 0 ? 'bg-lake-500' : 'bg-sun-500'}`} />
                <span>{CODE_LABELS[code]}</span>
                <span className="font-bold tabular-nums text-navy-900">{count}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
