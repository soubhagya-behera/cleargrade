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
        className={`cg-dropzone ${dragging && !disabled ? 'is-dragging' : ''} ${disabled ? 'is-disabled opacity-60' : ''}`}
      >
        <span className="cg-dropzone-icon">
          <FileUp aria-hidden="true" className="size-6" />
        </span>
        <p className="mt-3 text-[15px] font-semibold leading-snug text-navy-950">
          Drag and drop a CSV file here, or
        </p>
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
          className="cg-btn-accent mt-3 px-5.5"
        >
          Choose file
        </button>
      </div>
      <div className="mt-3.5 space-y-1 text-[13px] leading-relaxed text-ink-500">
        <p>Required columns: {REQUIRED_COLUMNS}</p>
        <p>Maximum file size: {Math.round(maxBytes / 1024)} KiB</p>
      </div>
    </div>
  );
}
