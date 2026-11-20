import { useRef, useState } from 'react';
import { FileUp } from 'lucide-react';

interface FileDropzoneProps {
  onFile: (file: File | null) => void;
  disabled?: boolean;
  maxBytes: number;
}

const REQUIRED_COLUMNS = 'student_id, full_name, email, date_of_birth, grade_level, section, and five subject marks';

export function FileDropzone({ onFile, disabled = false, maxBytes }: FileDropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  function select(file: File | undefined) {
    onFile(file ?? null);
  }

  return (
    <div>
      <div
        onDragOver={(event) => { event.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault();
          setDragging(false);
          if (!disabled) select(event.dataTransfer.files[0]);
        }}
        className={`rounded-xl border-2 border-dashed p-8 text-center transition-colors ${dragging ? 'border-brand-600 bg-brand-100/60' : 'border-line bg-surface hover:border-brand-600/50'}`}
      >
        <FileUp aria-hidden="true" className="mx-auto size-8 text-ink-500" />
        <p className="mt-3 text-sm font-medium">Drag and drop a CSV file here, or</p>
        <input
          ref={inputRef}
          type="file"
          accept=".csv,text/csv"
          aria-label="Choose CSV file"
          disabled={disabled}
          onChange={(event) => select(event.target.files?.[0])}
          className="sr-only"
        />
        <button
          type="button"
          disabled={disabled}
          onClick={() => inputRef.current?.click()}
          className="cg-btn-secondary mt-4"
        >
          Choose file
        </button>
        <p className="mt-4 text-xs text-ink-500">Required columns: {REQUIRED_COLUMNS}</p>
        <p className="mt-1 text-xs text-ink-500">Maximum file size: {Math.round(maxBytes / 1024)} KiB</p>
      </div>
    </div>
  );
}
