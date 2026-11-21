import { useState } from 'react';
import { useMutation, useQueries, useQuery } from '@tanstack/react-query';
import { ListFilter, SearchCheck } from 'lucide-react';
import { ApiError } from '../../api/client';
import { getBatch, getBatchErrors, importKeys, uploadCsv } from '../../api/imports';
import { DataQualitySummary } from '../../components/ui/DataQualitySummary';
import { ErrorState } from '../../components/ui/States';
import { FileDropzone } from '../../components/ui/FileDropzone';
import { IntelligencePanel } from '../../components/ui/IntelligencePanel';
import { Pagination } from '../../components/ui/Pagination';
import { ProblemTable } from '../../components/ui/ProblemTable';
import { StatCard } from '../../components/ui/StatCard';
import type { ImportResponse, RowErrorCode } from '../../types/api';

const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
const PAGE_SIZE = 50;
const CODES: RowErrorCode[] = ['MISSING_REQUIRED', 'INVALID_FORMAT', 'OUT_OF_RANGE', 'DUPLICATE_ID', 'WRONG_FIELD_COUNT'];

function friendlyError(error: Error): string {
  if (!(error instanceof ApiError)) return 'The import could not be completed. Please retry.';
  switch (error.code) {
    case 'FILE_TOO_LARGE': return 'This file is larger than the 10 MiB limit. Choose a smaller file.';
    case 'INVALID_FILE_TYPE': return 'Only .csv files can be uploaded.';
    case 'BATCH_PROCESSING': return `This file is already being imported${error.details[0] ? ` (${error.details[0]})` : ''}.`;
    case 'EMPTY_FILE': return 'This file is empty or has no data rows. Choose another file.';
    case 'MISSING_REQUIRED_COLUMNS': return 'The file is missing one or more required columns. Choose another file.';
    case 'INVALID_ENCODING': return 'The file is not valid UTF-8 text. Choose another file.';
    case 'MALFORMED_CSV': return 'The file could not be parsed; it may contain an unterminated quoted field.';
    case 'BATCH_NOT_FOUND': return 'That import batch was not found. Check the id and try again.';
    case 'NETWORK_ERROR': return error.message;
    default: return 'The import failed. Please retry, or contact support with the correlation id.';
  }
}

export function ImportPage() {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [code, setCode] = useState<RowErrorCode | ''>('');
  const [lookupId, setLookupId] = useState('');
  const [searchedId, setSearchedId] = useState<number | null>(null);

  const upload = useMutation({
    mutationFn: (selected: File) => uploadCsv(selected),
    onSuccess: (batch) => {
      setResult(batch);
      setError(null);
      setPage(0);
      setCode('');
    },
    onError: (cause: Error) => setError(friendlyError(cause)),
  });

  const problems = useQuery({
    queryKey: importKeys.errors(result?.id ?? 0, page, PAGE_SIZE, code || undefined),
    queryFn: ({ signal }) => getBatchErrors(result!.id, page, PAGE_SIZE, code || undefined, signal),
    enabled: result !== null && result.status === 'COMPLETED',
  });

  const breakdownQueries = useQueries({
    queries: CODES.map((item) => ({
      queryKey: importKeys.errors(result?.id ?? 0, 0, 1, item),
      queryFn: ({ signal }: { signal: AbortSignal }) => getBatchErrors(result!.id, 0, 1, item, signal),
      enabled: result !== null,
    })),
  });
  const breakdown: Partial<Record<RowErrorCode, number>> = {};
  CODES.forEach((item, index) => {
    const entry = breakdownQueries[index];
    if (entry.data) breakdown[item] = entry.data.totalElements;
  });

  const lookup = useQuery({
    queryKey: importKeys.batch(searchedId ?? 0),
    queryFn: ({ signal }) => getBatch(searchedId!, signal),
    enabled: searchedId !== null,
    retry: false,
  });

  function choose(selected: File | null) {
    setFile(selected);
    setError(null);
    if (selected && selected.size > MAX_UPLOAD_BYTES) {
      setError('This file is larger than the 10 MiB limit. Choose a smaller file.');
    }
  }

  return (
    <section aria-labelledby="page-heading">
      <header className="mb-6">
        <p className="cg-eyebrow text-[11px] font-bold uppercase text-lake-700">Data quality</p>
        <h2 id="page-heading" className="cg-page-title cg-page-title-plain">Import student results</h2>
        <p className="mt-2 max-w-2xl text-sm leading-relaxed text-ink-700 sm:text-[15px]">
          Valid rows are imported; problem rows are listed below with their physical line numbers.
        </p>
      </header>

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <section className="cg-card cg-rise-1 p-4 sm:p-6">
          <FileDropzone onFile={choose} disabled={upload.isPending} maxBytes={MAX_UPLOAD_BYTES} />
          <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-3 border-t border-line pt-4">
            <button
              type="button"
              disabled={!file || upload.isPending}
              onClick={() => file && upload.mutate(file)}
              className="cg-btn-primary"
            >
              {upload.isPending ? 'Uploading and importing…' : 'Upload and import'}
            </button>
            {file && (
              <span className="min-w-0 max-w-full truncate rounded-lg bg-lake-050 px-3 py-1.5 text-[13px] font-semibold text-navy-900 ring-1 ring-inset ring-lake-500/30">
                {file.name} · {Math.round(file.size / 1024)} KiB
              </span>
            )}
            <p className="text-[13px] leading-snug text-ink-500">
              Valid rows import; problem rows appear below.
            </p>
          </div>
          {error && <div className="mt-4"><ErrorState message={error} /></div>}
        </section>

        <aside className="cg-card cg-rise-2 p-4 text-sm sm:p-6">
          <div className="flex items-center gap-3">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-lake-100 text-lake-700">
              <SearchCheck aria-hidden="true" className="size-5" />
            </span>
            <h3 className="text-base font-bold tracking-tight text-navy-900">Check an earlier import</h3>
          </div>
          <label htmlFor="batch-id" className="cg-label mt-5">Batch id</label>
          <div className="mt-2 flex gap-2">
            <input
              id="batch-id"
              inputMode="numeric"
              value={lookupId}
              onChange={(event) => setLookupId(event.target.value)}
              className="cg-input flex-1"
            />
            <button
              type="button"
              onClick={() => setSearchedId(Number(lookupId))}
              className="cg-btn-accent px-4.5"
            >
              Look up
            </button>
          </div>
          {lookup.isError && <p className="mt-3 text-xs font-semibold text-danger">{friendlyError(lookup.error as Error)}</p>}
          {lookup.data && (
            <p className="mt-4 rounded-xl bg-lake-050 px-4 py-3 text-sm leading-relaxed text-navy-900" aria-live="polite">
              Batch {lookup.data.id}: {lookup.data.status} · {lookup.data.rowsRead ?? 0} rows read · {lookup.data.imported ?? 0} imported
            </p>
          )}
        </aside>
      </div>

      {result && (
        <div className="mt-8 space-y-5" aria-live="polite">
          <DataQualitySummary batch={result} breakdown={breakdown} />
          <div className="cg-stat-row">
            <StatCard label="Rows read" value={result.rowsRead ?? 0} reveal="1" />
            <StatCard label="Imported" value={result.imported ?? 0} hint={`${result.newCount ?? 0} new · ${result.changedCount ?? 0} changed · ${result.unchangedCount ?? 0} unchanged`} tone="success" reveal="2" />
            <StatCard label="Rows rejected" value={result.rejected ?? 0} tone="danger" reveal="3" />
          </div>

          <section aria-labelledby="problems-heading">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-4">
              <div className="flex items-center gap-4">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-lake-100 text-lake-700 shadow-[0_0_0_4px_rgba(128,180,227,0.16)]">
                  <ListFilter aria-hidden="true" className="size-5" />
                </span>
                <h3 id="problems-heading" className="text-lg font-bold tracking-tight text-navy-900">Row problems</h3>
              </div>
              <label className="flex items-center gap-2 text-xs font-medium text-ink-500" htmlFor="code-filter">
                Filter by code
                <select
                  id="code-filter"
                  value={code}
                  onChange={(event) => { setCode(event.target.value as RowErrorCode | ''); setPage(0); }}
                  className="cg-input !min-h-9 w-auto border-line/80 !py-1.5"
                >
                  <option value="">All problems</option>
                  {CODES.map((item) => <option key={item} value={item}>{item}</option>)}
                </select>
              </label>
            </div>
            {problems.isPending && <p className="text-sm text-ink-500">Loading problems…</p>}
            {problems.isError && <ErrorState message="Could not load row problems." onRetry={() => problems.refetch()} />}
            {problems.data && <ProblemTable errors={problems.data.content} />}
            {problems.data && (
              <Pagination
                page={problems.data.page}
                totalPages={problems.data.totalPages}
                onPageChange={setPage}
                label="Row problem pages"
              />
            )}
          </section>

          {result.status === 'COMPLETED' && <IntelligencePanel batchId={result.id} />}
        </div>
      )}
    </section>
  );
}
