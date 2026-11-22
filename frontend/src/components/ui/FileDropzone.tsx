import { useRef, useState } from 'react';
import { FileCheck2, FileSpreadsheet, X } from 'lucide-react';

interface FileDropzoneProps {
  onFile: (file: File | null) => void;
  disabled?: boolean;
  maxBytes: number;
  file?: File | null;
  onRemove?: () => void;
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KiB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MiB`;
}

export function FileDropzone({ onFile, disabled = false, maxBytes, file = null, onRemove }: FileDropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const dragDepth = useRef(0);
  const [dragging, setDragging] = useState(false);

  function select(selected: File | undefined) {
    onFile(selected ?? null);
    if (inputRef.current) inputRef.current.value = '';
  }

  function clear() {
    if (inputRef.current) inputRef.current.value = '';
    if (onRemove) onRemove();
    else onFile(null);
  }

  const maxLabel = maxBytes >= 1024 * 1024
    ? `${Math.round(maxBytes / (1024 * 1024))} MiB`
    : `${Math.round(maxBytes / 1024)} KiB`;

  return (
    <div>
      <div
        onDragEnter={(event) => {
          event.preventDefault();
          if (disabled) return;
          dragDepth.current += 1;
          setDragging(true);
        }}
        onDragOver={(event) => { event.preventDefault(); if (!disabled) setDragging(true); }}
        onDragLeave={() => {
          dragDepth.current = Math.max(0, dragDepth.current - 1);
          if (dragDepth.current === 0) setDragging(false);
        }}
        onDrop={(event) => {
          event.preventDefault();
          dragDepth.current = 0;
          setDragging(false);
          if (!disabled) select(event.dataTransfer.files[0]);
        }}
        className={`cg-dropzone ${dragging && !disabled ? 'is-dragging' : ''} ${disabled ? 'is-disabled opacity-60' : ''} ${file ? 'is-selected' : ''}`}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".csv,text/csv"
          aria-label="Choose CSV file"
          disabled={disabled}
          onChange={(event) => select(event.target.files?.[0])}
          className="sr-only"
        />
        {file ? (
          <>
            <span className="cg-dropzone-icon">
              <FileCheck2 aria-hidden="true" className="size-6" />
            </span>
            <p className="mt-2 text-[11px] font-bold uppercase tracking-[0.14em] text-lake-700">File ready</p>
            <p className="mt-1.5 max-w-full truncate px-2 text-[15px] font-bold text-navy-950">{file.name}</p>
            <p className="mt-1 text-[12px] font-medium tabular-nums text-ink-500">{formatSize(file.size)}</p>
            <div className="mt-3 flex flex-wrap items-center justify-center gap-2">
              <button
                type="button"
                disabled={disabled}
                onClick={() => inputRef.current?.click()}
                className="cg-btn-secondary !min-h-10 !px-4 !py-2 text-[13px]"
              >
                Choose a different file
              </button>
              <button
                type="button"
                disabled={disabled}
                onClick={clear}
                aria-label="Remove selected file"
                className="cg-file-remove"
              >
                <X aria-hidden="true" className="size-4.5" />
              </button>
            </div>
          </>
        ) : (
          <>
            <span className="cg-dropzone-icon">
              <FileSpreadsheet aria-hidden="true" className="size-6" />
            </span>
            <p className="mt-2 text-[15px] font-semibold leading-snug text-navy-950">
              {dragging ? 'Release to import' : 'Drag and drop a CSV file here'}
            </p>
            <p className="mt-1 text-[13px] leading-relaxed text-ink-500">
              {dragging ? 'Your file will be checked against the CSV contract.' : 'or choose a file from your computer'}
            </p>
            <button
              type="button"
              disabled={disabled}
              onClick={() => inputRef.current?.click()}
              className="cg-btn-accent mt-3 px-5.5"
            >
              Choose file
            </button>
          </>
        )}
      </div>
      <p className="mt-2 text-center text-[11px] font-medium text-ink-500">
        CSV only · UTF-8 · up to {maxLabel}
      </p>
    </div>
  );
}
