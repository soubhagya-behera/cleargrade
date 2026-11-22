import { useState } from 'react';
import { useQueries, useQuery } from '@tanstack/react-query';
import { getBatchErrors, importKeys } from '../../api/imports';
import { DataQualitySummary } from '../../components/ui/DataQualitySummary';
import { ErrorState } from '../../components/ui/States';
import { IntelligencePanel } from '../../components/ui/IntelligencePanel';
import { Pagination } from '../../components/ui/Pagination';
import { ProblemTable } from '../../components/ui/ProblemTable';
import { StatCard } from '../../components/ui/StatCard';
import type { ImportResponse, RowErrorCode } from '../../types/api';

const PAGE_SIZE = 50;
const CODES: RowErrorCode[] = ['MISSING_REQUIRED', 'INVALID_FORMAT', 'OUT_OF_RANGE', 'DUPLICATE_ID', 'WRONG_FIELD_COUNT'];

export function ImportBatchDetails({ batch }: { batch: ImportResponse }) {
  const [page, setPage] = useState(0);
  const [code, setCode] = useState<RowErrorCode | ''>('');
  const problems = useQuery({
    queryKey: importKeys.errors(batch.id, page, PAGE_SIZE, code || undefined),
    queryFn: ({ signal }) => getBatchErrors(batch.id, page, PAGE_SIZE, code || undefined, signal),
    enabled: batch.status === 'COMPLETED',
  });
  const breakdownQueries = useQueries({
    queries: CODES.map((item) => ({
      queryKey: importKeys.errors(batch.id, 0, 1, item),
      queryFn: ({ signal }: { signal: AbortSignal }) => getBatchErrors(batch.id, 0, 1, item, signal),
      enabled: batch.status === 'COMPLETED',
    })),
  });
  const breakdown: Partial<Record<RowErrorCode, number>> = {};
  CODES.forEach((item, index) => {
    const entry = breakdownQueries[index];
    if (entry.data) breakdown[item] = entry.data.totalElements;
  });

  return (
    <div className="mt-6 space-y-5" aria-live="polite">
      <DataQualitySummary batch={batch} breakdown={breakdown} />
      <div className="cg-stat-row">
        <StatCard label="Rows read" value={batch.rowsRead ?? 0} />
        <StatCard label="Imported" value={batch.imported ?? 0} hint={`${batch.newCount ?? 0} new · ${batch.changedCount ?? 0} changed · ${batch.unchangedCount ?? 0} unchanged`} tone="success" />
        <StatCard label="Rows rejected" value={batch.rejected ?? 0} hint={`${batch.errorCount ?? 0} individual problems`} tone="danger" />
      </div>
      {batch.status === 'COMPLETED' && (
        <section aria-labelledby={`problems-heading-${batch.id}`} className="border-t border-line pt-5">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-3 border-b border-line pb-3">
            <h3 id={`problems-heading-${batch.id}`} className="text-xl font-bold tracking-tight text-navy-900">Row problems</h3>
            <label className="flex items-center gap-2 text-xs font-medium text-ink-500" htmlFor={`code-filter-${batch.id}`}>
              Filter by code
              <select id={`code-filter-${batch.id}`} value={code}
                onChange={(event) => { setCode(event.target.value as RowErrorCode | ''); setPage(0); }}
                className="cg-input !min-h-9 w-auto border-line/80 !py-1.5">
                <option value="">All problems</option>
                {CODES.map((item) => <option key={item} value={item}>{item}</option>)}
              </select>
            </label>
          </div>
          {problems.isPending && <p className="text-sm text-ink-500" role="status">Loading problems…</p>}
          {problems.isError && <ErrorState message="Could not load row problems." onRetry={() => problems.refetch()} />}
          {problems.data && <ProblemTable errors={problems.data.content} />}
          {problems.data && <Pagination page={problems.data.page} totalPages={problems.data.totalPages}
            onPageChange={setPage} label="Row problem pages" />}
        </section>
      )}
      {batch.status === 'COMPLETED' && <IntelligencePanel batchId={batch.id} />}
    </div>
  );
}
