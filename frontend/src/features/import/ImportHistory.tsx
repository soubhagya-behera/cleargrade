import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Eye, Loader2 } from 'lucide-react';
import { getBatch, getImportHistory, importKeys } from '../../api/imports';
import { ErrorState, SkeletonTable } from '../../components/ui/States';
import { Pagination } from '../../components/ui/Pagination';
import type { ImportHistoryEntry } from '../../types/api';
import { BatchDownloadMenu } from './BatchDownloadMenu';
import { ImportBatchDetails } from './ImportBatchDetails';

const PAGE_SIZE = 10;

function value(count: number | null): number | string {
  return count ?? '—';
}

function statusStyle(status: ImportHistoryEntry['status']): string {
  if (status === 'COMPLETED') return 'bg-green-50 text-green-800';
  if (status === 'FAILED') return 'bg-red-50 text-red-800';
  return 'bg-lake-100 text-navy-900';
}

export function ImportHistory() {
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const history = useQuery({
    queryKey: [...importKeys.history(page, PAGE_SIZE)],
    queryFn: ({ signal }) => getImportHistory(page, PAGE_SIZE, signal),
    retry: false,
  });
  const detail = useQuery({
    queryKey: importKeys.batch(selectedId ?? 0),
    queryFn: ({ signal }) => getBatch(selectedId!, signal),
    enabled: selectedId !== null,
    retry: false,
  });

  if (selectedId !== null) {
    return (
      <section aria-labelledby="history-detail-heading" className="space-y-4">
        <button type="button" onClick={() => setSelectedId(null)} className="cg-btn-secondary">
          <ArrowLeft aria-hidden="true" className="size-4" /> Back to history
        </button>
        {detail.isPending && <p role="status" className="flex items-center gap-2 text-sm text-ink-500"><Loader2 className="size-4 animate-spin" />Loading batch details…</p>}
        {detail.isError && <ErrorState message="Could not load this import batch." onRetry={() => detail.refetch()} />}
        {detail.data && <>
          <div>
            <h3 id="history-detail-heading" className="text-xl font-bold tracking-tight text-navy-900">Batch {detail.data.id} · {detail.data.filename}</h3>
            <p className="mt-1 text-sm text-ink-500">Imported {new Date(detail.data.startedAt).toLocaleString()}</p>
          </div>
          <ImportBatchDetails batch={detail.data} />
        </>}
      </section>
    );
  }

  return (
    <section aria-labelledby="page-heading" className="space-y-4">
      <header>
        {history.data && <p className="text-sm font-semibold text-navy-900" aria-live="polite">
          {history.data.totalElements} {history.data.totalElements === 1 ? 'batch' : 'batches'}
        </p>}
      </header>
      {history.isPending && <SkeletonTable rows={5} />}
      {history.isError && <ErrorState message="Import history could not be loaded." onRetry={() => history.refetch()} />}
      {history.data?.content.length === 0 && <div className="cg-card border-dashed p-8 text-center">
        <p className="text-sm font-semibold text-navy-900">No imports yet</p>
        <p className="mt-1 text-sm text-ink-500">Completed and attempted CSV imports will appear here.</p>
      </div>}
      {!!history.data?.content.length && <>
        <div className="cg-card cg-scroll-cue overflow-x-auto">
          <table className="min-w-[1050px] w-full text-left text-sm">
            <caption className="sr-only">Previous CSV import batches, newest first</caption>
            <thead><tr>
              {['Batch ID', 'File Name', 'Imported At', 'Rows Read', 'New', 'Changed', 'Unchanged', 'Rejected', 'Status', 'Action'].map((heading) =>
                <th scope="col" key={heading} className="cg-table-head whitespace-nowrap">{heading}</th>)}
            </tr></thead>
            <tbody className="divide-y divide-line">
              {history.data.content.map((batch) => <tr key={batch.id} className="hover:bg-lake-050/50">
                <td className="cg-table-cell font-semibold tabular-nums text-navy-900">{batch.id}</td>
                <td className="cg-table-cell max-w-56 truncate font-medium" title={batch.filename}>{batch.filename}</td>
                <td className="cg-table-cell whitespace-nowrap">{new Date(batch.startedAt).toLocaleString()}</td>
                <td className="cg-table-cell tabular-nums">{value(batch.rowsRead)}</td>
                <td className="cg-table-cell tabular-nums">{value(batch.newCount)}</td>
                <td className="cg-table-cell tabular-nums">{value(batch.changedCount)}</td>
                <td className="cg-table-cell tabular-nums">{value(batch.unchangedCount)}</td>
                <td className="cg-table-cell tabular-nums">{value(batch.rejected)}</td>
                <td className="cg-table-cell"><span className={`cg-tag ${statusStyle(batch.status)}`}>{batch.status}</span></td>
                <td className="cg-table-cell"><div className="flex items-center gap-2">
                  <button type="button" onClick={() => setSelectedId(batch.id)}
                    className="cg-btn-secondary !min-h-9 !px-3" aria-label={`View details for batch ${batch.id}`}>
                    <Eye aria-hidden="true" className="size-4" /> View
                  </button>
                  <BatchDownloadMenu batch={batch} />
                </div></td>
              </tr>)}
            </tbody>
          </table>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-xs text-ink-500">Page {history.data.page + 1} of {Math.max(history.data.totalPages, 1)}</p>
          <Pagination page={history.data.page} totalPages={history.data.totalPages} onPageChange={setPage} label="Import history pages" />
        </div>
      </>}
    </section>
  );
}
