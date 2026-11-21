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
    <section aria-labelledby="quality-heading" className="relative rounded-3xl border border-navy-900/18 bg-[#fdfcff] bg-gradient-to-b from-white via-[#fdfdff] to-[#f8fafd] shadow-[0_8px_26px_rgba(13,33,67,0.08)] sm:rounded-3xl">
      <span aria-hidden="true" className="absolute inset-y-4 left-0 w-[3px] rounded-r-full bg-gradient-to-b from-lake-600 to-lake-400" />
      <div className="relative p-6 sm:p-8">
        <p className="cg-eyebrow m-0 mb-3 inline-flex items-center gap-2.5 border-b border-line pb-3 pl-3 pt-0.5 text-[11px]">
          <span className="inline-block h-2.5 w-2.5 rounded bg-lake-400 shadow-[0_0_0_4px_rgba(128,180,227,0.13)]" />
          OUTCOME
        </p>
        <h3 className="mt-5 text-3xl font-bold tracking-tight text-navy-900 sm:text-[2.25rem]">Import summary</h3>
        <p className="cg-summary-strong mt-4 max-w-4xl text-[14.5px] leading-[1.8] sm:text-[16px]" aria-live="polite">
          We read <strong className="cg-summary-strong font-bold tabular-nums text-navy-950">{rowsRead}</strong> {rowsRead === 1 ? 'row' : 'rows'}.{' '}
          <strong className="cg-summary-strong font-bold tabular-nums text-navy-950">{imported}</strong> were imported
          {' '}({batch.newCount ?? 0} new, {batch.changedCount ?? 0} changed, {batch.unchangedCount ?? 0} unchanged).{' '}
          <strong className="cg-summary-strong font-bold tabular-nums text-navy-950">{rejected}</strong> {rejected === 1 ? 'row was' : 'rows were'} rejected because of{' '}
          <strong className="cg-summary-strong font-bold tabular-nums text-navy-950">{problems}</strong> {problems === 1 ? 'problem' : 'problems'}. Rejected rows and problems are
          different numbers: one row can have several problems.
        </p>
        {problems > rejected && (
          <p className="mt-3 text-[13px] font-medium text-ink-500">Extra problems come from rows with more than one issue.</p>
        )}
        {breakdown && (
          <ul className="mt-7 grid grid-cols-1 gap-2.5 min-[380px]:grid-cols-2 sm:flex sm:flex-wrap sm:gap-3">
            {CODES.filter((code) => (breakdown[code] ?? 0) > 0).map((code, index) => (
              <li key={code} className="flex min-w-0 items-start gap-2.5 self-start rounded-md border border-lake-300 bg-lake-050 px-4 py-3 text-[13.5px] font-bold leading-snug text-navy-900 transition-transform duration-200 sm:items-center sm:gap-3">
                <span aria-hidden="true" className={`mt-1 h-3 w-3 shrink-0 rounded-full ${index % 2 === 0 ? 'bg-lake-400' : 'bg-sun-400'} transition-all duration-200 sm:mt-0`} />
                <span className="min-w-0 tabular-nums">{CODE_LABELS[code]}: {breakdown[code]}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
