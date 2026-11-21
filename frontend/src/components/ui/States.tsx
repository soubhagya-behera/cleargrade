import { Link } from 'react-router-dom';
import { CircleAlert, Compass, SearchX } from 'lucide-react';

interface EmptyStateProps {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function EmptyState({ message, actionLabel, onAction }: EmptyStateProps) {
  return (
    <div className="cg-card border-dashed p-10 text-center">
      <span className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-lake-100 text-lake-700">
        <SearchX aria-hidden="true" className="size-6" />
      </span>
      <p className="mt-4 text-sm text-ink-500">{message}</p>
      {actionLabel && onAction && (
        <button type="button" onClick={onAction} className="cg-btn-primary mt-5">
          {actionLabel}
        </button>
      )}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-wrap items-center gap-4 rounded-2xl border border-red-200 bg-red-50/80 p-5 text-sm text-danger">
      <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-white text-danger shadow-sm">
        <CircleAlert aria-hidden="true" className="size-5" />
      </span>
      <p className="min-w-0 flex-1 font-medium">{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className="cg-btn-secondary !min-h-10 !border-red-200 !bg-white !text-danger">
          Retry
        </button>
      )}
    </div>
  );
}

export function SkeletonTable({ rows = 5 }: { rows?: number }) {
  return (
    <div role="status" aria-label="Loading" className="cg-card space-y-2 p-4">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="h-12 animate-pulse rounded-xl bg-gradient-to-r from-lake-050 via-lake-100 to-lake-050" />
      ))}
    </div>
  );
}

export function NotFoundPanel({ message, backTo, backLabel }: { message: string; backTo: string; backLabel: string }) {
  return (
    <div className="cg-card p-10 text-center">
      <span className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-lake-100 text-lake-700">
        <Compass aria-hidden="true" className="size-6" />
      </span>
      <p className="mt-4 text-sm text-ink-500">{message}</p>
      <Link to={backTo} className="cg-btn-primary mt-5">{backLabel}</Link>
    </div>
  );
}
