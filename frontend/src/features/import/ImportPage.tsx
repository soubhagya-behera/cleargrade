import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { CheckCircle2, Loader2, SearchCheck, UploadCloud } from 'lucide-react';
import { ApiError } from '../../api/client';
import { getBatch, importKeys, uploadCsv } from '../../api/imports';
import { ErrorState } from '../../components/ui/States';
import { FileDropzone } from '../../components/ui/FileDropzone';
import type { ImportResponse } from '../../types/api';
import { clearLastImportBatchId, readLastImportBatchId, saveLastImportBatchId } from './lastImportBatch';
import { ImportBatchDetails } from './ImportBatchDetails';
import { ImportHistory } from './ImportHistory';

const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
const CSV_COLUMNS = ['student_id', 'full_name', 'email', 'date_of_birth', 'grade_level', 'section'];

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
  const [tab, setTab] = useState<'upload' | 'history'>('upload');
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [lookupId, setLookupId] = useState('');
  const [searchedId, setSearchedId] = useState<number | null>(null);
  const [restoredBatchId, setRestoredBatchId] = useState<number | null>(() => readLastImportBatchId());
  const [restoreError, setRestoreError] = useState<string | null>(null);
  const uploadSucceeded = useRef(false);

  const upload = useMutation({
    mutationFn: (selected: File) => uploadCsv(selected),
    onSuccess: (batch) => {
      uploadSucceeded.current = true;
      setRestoredBatchId(null);
      setRestoreError(null);
      setResult(batch);
      setFile(null);
      setError(null);
      if (batch.status === 'COMPLETED') saveLastImportBatchId(batch.id);
    },
    onError: (cause: Error) => setError(friendlyError(cause)),
  });

  const restoration = useQuery({
    queryKey: importKeys.batch(restoredBatchId ?? 0),
    queryFn: ({ signal }) => getBatch(restoredBatchId!, signal),
    enabled: restoredBatchId !== null,
    retry: false,
  });

  useEffect(() => {
    if (!restoration.data || uploadSucceeded.current) return;
    setResult(restoration.data);
  }, [restoration.data]);

  useEffect(() => {
    if (uploadSucceeded.current) return;
    if (!restoration.isError) return;
    const cause = restoration.error;
    if (cause instanceof ApiError && cause.status === 404) {
      clearLastImportBatchId();
      setRestoredBatchId(null);
      setRestoreError(null);
      return;
    }
    setRestoreError('The last import could not be restored. You can still upload a new file.');
  }, [restoration.isError, restoration.error]);

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
      <header className="mb-5 sm:mb-6">
        <p className="cg-eyebrow text-[11px] font-bold uppercase text-lake-700">Data quality <span aria-hidden="true" className="px-1.5 text-line-strong">/</span> {tab === 'history' ? 'Import History' : 'File intake'}</p>
        <h2 id="page-heading" className="mt-2 text-[2rem] font-bold leading-[1.08] tracking-[-0.025em] text-navy-900 sm:text-[2.5rem]">
          {tab === 'history' ? 'Import History' : 'Import student results'}
        </h2>
        <p className="mt-2 max-w-2xl text-sm leading-relaxed text-ink-700 sm:text-[15px]">
          {tab === 'history'
            ? 'Review previous CSV imports and open a batch to see its summary and row problems.'
            : 'Bring in a CSV, validate every row, and review the results before moving on.'}
        </p>
        <span aria-hidden="true" className="mt-3 block h-1 w-10 bg-sun-500" />
      </header>

      <nav aria-label="Import Quality Center" className="mb-5 flex flex-wrap gap-2 border-b border-line pb-3">
        <button type="button" aria-current={tab === 'upload' ? 'page' : undefined} onClick={() => setTab('upload')}
          className={tab === 'upload' ? 'cg-btn-primary' : 'cg-btn-secondary'}>Import CSV</button>
        <button type="button" aria-current={tab === 'history' ? 'page' : undefined} onClick={() => setTab('history')}
          className={tab === 'history' ? 'cg-btn-primary' : 'cg-btn-secondary'}>Import History</button>
      </nav>

      {tab === 'history' ? <ImportHistory /> : <>

      <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-12 xl:items-stretch xl:gap-6">
        <section aria-labelledby="upload-heading" className="cg-card cg-rise-1 relative overflow-hidden p-4 pt-5 sm:p-5 sm:pt-6 xl:col-span-8">
          <span aria-hidden="true" className="absolute inset-x-0 top-0 h-1.5 bg-lake-500" />
          <div className="mb-3">
            <h3 id="upload-heading" className="text-lg font-bold tracking-tight text-navy-900">Upload a results file</h3>
            <p className="mt-1 text-[13px] leading-relaxed text-ink-500">Choose a CSV from your device or drop it into the upload area.</p>
          </div>

          <FileDropzone
            file={file}
            onFile={choose}
            onRemove={() => choose(null)}
            disabled={upload.isPending}
            maxBytes={MAX_UPLOAD_BYTES}
          />

          {restoredBatchId !== null && restoration.isFetching && (
            <p className="mt-3 text-[12px] font-medium text-ink-500" role="status">Restoring your last import…</p>
          )}
          {restoreError && (
            <p className="mt-3 text-[12px] font-semibold text-danger" role="status">{restoreError}</p>
          )}

          <div className="mt-3">
            <p className="cg-eyebrow text-[10px] font-bold uppercase text-lake-700">Required columns</p>
            <ul className="mt-1.5 flex flex-wrap gap-1" aria-label="Required CSV columns">
              {CSV_COLUMNS.map((column) => (
                <li key={column} className="rounded-md border border-line bg-canvas px-1.5 py-0.5 font-mono text-[11px] font-medium text-navy-800">
                  {column}
                </li>
              ))}
              <li className="rounded-md border border-line bg-canvas px-1.5 py-0.5 text-[11px] font-semibold text-navy-800">5 subject marks</li>
            </ul>
          </div>

          <div className="mt-4 flex flex-col gap-3 border-t border-line pt-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="min-w-0">
              <p className="text-[13px] leading-snug text-ink-500">Valid rows import; problem rows appear below.</p>
              {result && (
                result.status === 'COMPLETED' ? (
                  <p className="mt-1 inline-flex items-center gap-1.5 text-[12px] font-semibold text-success" aria-live="polite">
                    <CheckCircle2 aria-hidden="true" className="size-3.5" />
                    Import complete — review the summary below.
                  </p>
                ) : (
                  <p className="mt-1 text-[12px] font-semibold text-lake-700" aria-live="polite">
                    Batch {result.id} · {result.status}
                  </p>
                )
              )}
            </div>
            <button
              type="button"
              disabled={!file || upload.isPending}
              onClick={() => file && upload.mutate(file)}
              className="cg-btn-primary w-full shrink-0 sm:w-auto"
            >
              {upload.isPending ? (
                <>
                  <Loader2 aria-hidden="true" className="size-4 animate-spin" />
                  Uploading and importing…
                </>
              ) : (
                <>
                  <UploadCloud aria-hidden="true" className="size-4.5" />
                  Upload and import
                </>
              )}
            </button>
          </div>
          {error && <div className="mt-4"><ErrorState message={error} /></div>}
        </section>

        <div className="grid min-w-0 gap-4 xl:col-span-4 xl:grid-cols-1 xl:grid-rows-[auto_1fr]">
          <aside aria-labelledby="lookup-heading" className="cg-card cg-rise-2 min-w-0 p-4 sm:p-5">
            <div className="flex items-start gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-lake-100 text-lake-700">
                <SearchCheck aria-hidden="true" className="size-5" />
              </span>
              <div className="min-w-0">
                <p className="cg-eyebrow text-[10px] font-bold uppercase text-lake-700">Batch lookup</p>
                <h3 id="lookup-heading" className="mt-0.5 text-base font-bold tracking-tight text-navy-900">Check an earlier import</h3>
              </div>
            </div>
            <p className="mt-3 text-[13px] leading-relaxed text-ink-500">
              Enter a batch id to review a previous upload&apos;s status and row counts.
            </p>
            <label htmlFor="batch-id" className="cg-label mt-3">Batch id</label>
            <div className="mt-2 flex flex-col gap-2 sm:flex-row">
              <input
                id="batch-id"
                inputMode="numeric"
                value={lookupId}
                onChange={(event) => setLookupId(event.target.value)}
                placeholder="e.g. 42"
                className="cg-input min-w-0 flex-1"
              />
              <button
                type="button"
                onClick={() => setSearchedId(Number(lookupId))}
                className="cg-btn-accent w-full shrink-0 px-4 sm:w-auto"
              >
                Look up
              </button>
            </div>
            {lookup.isFetching && <p className="mt-3 text-[12px] font-medium text-ink-500">Looking up batch…</p>}
            {lookup.isError && <p className="mt-3 text-xs font-semibold text-danger">{friendlyError(lookup.error as Error)}</p>}
            {lookup.data && (
              <div className="mt-4 rounded-xl border border-lake-500/25 bg-lake-050 p-4" aria-live="polite">
                <div className="flex items-center justify-between gap-2">
                  <p className="text-sm font-bold text-navy-900">Batch {lookup.data.id}</p>
                  <span className="cg-tag">{lookup.data.status}</span>
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-3 text-[12px]">
                  <div>
                    <dt className="font-semibold text-ink-500">Rows read</dt>
                    <dd className="mt-0.5 text-sm font-bold tabular-nums text-navy-900">{lookup.data.rowsRead ?? 0}</dd>
                  </div>
                  <div>
                    <dt className="font-semibold text-ink-500">Imported</dt>
                    <dd className="mt-0.5 text-sm font-bold tabular-nums text-navy-900">{lookup.data.imported ?? 0}</dd>
                  </div>
                </dl>
              </div>
            )}
          </aside>

          <aside aria-labelledby="next-heading" className="cg-card cg-rise-3 p-4 sm:p-5">
            <p className="cg-eyebrow text-[10px] font-bold uppercase text-lake-700">Import workflow</p>
            <h3 id="next-heading" className="mt-1 text-base font-bold tracking-tight text-navy-900">What happens next</h3>
            <p className="mt-1 text-[13px] leading-relaxed text-ink-500">A clear, reviewable import process.</p>
            <ol className="mt-4 space-y-3">
              <li className="flex items-start gap-3">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-navy-900 text-[11px] font-bold tabular-nums text-white">01</span>
                <div>
                  <h4 className="text-[13px] font-bold text-navy-900">Validate the file</h4>
                  <p className="mt-0.5 text-[12px] leading-relaxed text-ink-500">Check its structure and each row.</p>
                </div>
              </li>
              <li className="flex items-start gap-3">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-lake-500 text-[11px] font-bold tabular-nums text-navy-950">02</span>
                <div>
                  <h4 className="text-[13px] font-bold text-navy-900">Import valid records</h4>
                  <p className="mt-0.5 text-[12px] leading-relaxed text-ink-500">Only accepted rows are saved.</p>
                </div>
              </li>
              <li className="flex items-start gap-3">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-sun-500 text-[11px] font-bold tabular-nums text-navy-950">03</span>
                <div>
                  <h4 className="text-[13px] font-bold text-navy-900">Review the outcome</h4>
                  <p className="mt-0.5 text-[12px] leading-relaxed text-ink-500">Inspect row issues and the import summary.</p>
                </div>
              </li>
            </ol>
          </aside>
        </div>
      </div>

      {result && <ImportBatchDetails batch={result} />}
      </>}
    </section>
  );
}
