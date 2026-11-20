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

  return (
    <section aria-labelledby="quality-heading" className="rounded-xl border border-line bg-surface p-6 shadow-sm">
      <h3 id="quality-heading" className="text-lg font-semibold">Import summary</h3>
      <p className="mt-2 text-sm leading-relaxed" aria-live="polite">
        We read <strong>{rowsRead}</strong> {rowsRead === 1 ? 'row' : 'rows'}.{' '}
        <strong>{imported}</strong> were imported
        {' '}({batch.newCount ?? 0} new, {batch.changedCount ?? 0} changed, {batch.unchangedCount ?? 0} unchanged).{' '}
        <strong>{rejected}</strong> {rejected === 1 ? 'row was' : 'rows were'} rejected because of{' '}
        <strong>{problems}</strong> {problems === 1 ? 'problem' : 'problems'}. Rejected rows and problems are
        different numbers: one row can have several problems.
      </p>
      {problems > rejected && (
        <p className="mt-2 text-xs text-ink-500">Extra problems come from rows with more than one issue.</p>
      )}
      {breakdown && (
        <ul className="mt-5 flex flex-wrap gap-x-6 gap-y-2 text-xs text-ink-500">
          {CODES.filter((code) => (breakdown[code] ?? 0) > 0).map((code) => (
            <li key={code} className="tabular-nums">{CODE_LABELS[code]}: {breakdown[code]}</li>
          ))}
        </ul>
      )}
    </section>
  );
}
