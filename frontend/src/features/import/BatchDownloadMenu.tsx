import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ChevronDown, Download, Loader2 } from 'lucide-react';
import { getBatchReportCsv, getBatchRowProblemsCsv } from '../../api/imports';
import type { ImportHistoryEntry } from '../../types/api';
import { saveCsvDownload } from './saveCsvDownload';

type ExportKind = 'report' | 'row-problems';

export function BatchDownloadMenu({ batch }: { batch: ImportHistoryEntry }) {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState({ top: 0, left: 0, width: 304 });
  const [pending, setPending] = useState<ExportKind | null>(null);
  const [failed, setFailed] = useState<ExportKind | null>(null);
  const busy = useRef(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const menuId = `batch-download-menu-${batch.id}`;

  useLayoutEffect(() => {
    if (!open) return;
    const updatePosition = () => {
      const trigger = triggerRef.current;
      if (!trigger) return;
      const rect = trigger.getBoundingClientRect();
      const width = Math.min(320, Math.max(220, window.innerWidth - 16));
      const height = menuRef.current?.offsetHeight ?? 164;
      const left = Math.max(8, Math.min(rect.right - width, window.innerWidth - width - 8));
      const below = rect.bottom + 8;
      const top = below + height <= window.innerHeight - 8
        ? below
        : Math.max(8, rect.top - height - 8);
      setPosition({ top, left, width });
    };
    updatePosition();
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);
    return () => {
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const firstItem = menuRef.current?.querySelector<HTMLButtonElement>('[role="menuitem"]:not(:disabled)');
    firstItem?.focus();
    const onPointerDown = (event: PointerEvent) => {
      const target = event.target as Node;
      if (!menuRef.current?.contains(target) && !triggerRef.current?.contains(target)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        setOpen(false);
        triggerRef.current?.focus();
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  function handleMenuKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
    const items = Array.from(menuRef.current?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]:not(:disabled)') ?? []);
    if (items.length === 0) return;
    event.preventDefault();
    const index = items.indexOf(document.activeElement as HTMLButtonElement);
    const next = event.key === 'Home' ? 0
      : event.key === 'End' ? items.length - 1
        : event.key === 'ArrowDown' ? (index + 1 + items.length) % items.length
          : (index - 1 + items.length) % items.length;
    items[next].focus();
  }

  async function startDownload(kind: ExportKind) {
    if (busy.current || (kind === 'row-problems' && batch.status !== 'COMPLETED')) return;
    busy.current = true;
    setPending(kind);
    setFailed(null);
    setOpen(false);
    triggerRef.current?.focus();
    const fallback = kind === 'report'
      ? `cleargrade-import-${batch.id}-report.csv`
      : `cleargrade-import-${batch.id}-row-problems.csv`;
    try {
      const download = kind === 'report'
        ? await getBatchReportCsv(batch.id)
        : await getBatchRowProblemsCsv(batch.id);
      saveCsvDownload(download, fallback);
    } catch {
      setFailed(kind);
    } finally {
      busy.current = false;
      setPending(null);
    }
  }

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        aria-label={`CSV download options for batch ${batch.id}`}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        aria-busy={pending !== null}
        disabled={pending !== null}
        onClick={() => setOpen((current) => !current)}
        className="cg-btn-secondary !min-h-9 !gap-1.5 !px-2.5"
      >
        {pending ? <Loader2 aria-hidden="true" className="size-4 animate-spin" /> : <Download aria-hidden="true" className="size-4" />}
        <ChevronDown aria-hidden="true" className="size-3.5" />
      </button>
      {pending && <span role="status" className="sr-only">Preparing CSV download…</span>}
      {failed && <div role="alert" className="mt-1 max-w-56 rounded-lg border border-red-200 bg-red-50 px-2 py-1.5 text-xs text-danger">
        <span>CSV download failed.</span>{' '}
        <button type="button" className="font-bold underline focus-visible:outline focus-visible:outline-2" onClick={() => void startDownload(failed)}>Retry</button>
      </div>}
      {open && createPortal(
        <div
          id={menuId}
          ref={menuRef}
          role="menu"
          aria-label={`CSV downloads for batch ${batch.id}`}
          onKeyDown={handleMenuKeyDown}
          className="z-[100] rounded-xl border border-line bg-surface p-1.5 shadow-xl shadow-navy-900/15"
          style={{ position: 'fixed', top: position.top, left: position.left, width: position.width }}
        >
          <button type="button" role="menuitem" onClick={() => void startDownload('report')}
            className="flex w-full items-start gap-3 rounded-lg p-3 text-left hover:bg-lake-050 focus-visible:bg-lake-050 focus-visible:outline focus-visible:outline-2 focus-visible:outline-lake-500">
            <Download aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-navy-800" />
            <span className="min-w-0">
              <span className="block text-sm font-semibold text-navy-900">Download Import Report (CSV)</span>
              <span className="mt-0.5 block text-xs text-ink-500">Summary of this import and its outcome counts</span>
            </span>
          </button>
          <button type="button" role="menuitem" disabled={batch.status !== 'COMPLETED'}
            title={batch.status !== 'COMPLETED' ? 'Row problems are available after the batch completes.' : undefined}
            onClick={() => void startDownload('row-problems')}
            className="flex w-full items-start gap-3 rounded-lg p-3 text-left hover:bg-lake-050 focus-visible:bg-lake-050 focus-visible:outline focus-visible:outline-2 focus-visible:outline-lake-500 disabled:cursor-not-allowed disabled:opacity-50">
            <Download aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-navy-800" />
            <span className="min-w-0">
              <span className="block text-sm font-semibold text-navy-900">Download Row Problems (CSV)</span>
              <span className="mt-0.5 block text-xs text-ink-500">Validation issues recorded for this batch</span>
              {batch.status !== 'COMPLETED' && <span className="mt-1 block text-[11px] text-ink-500">Available after the batch completes.</span>}
            </span>
          </button>
        </div>,
        document.body,
      )}
    </>
  );
}
