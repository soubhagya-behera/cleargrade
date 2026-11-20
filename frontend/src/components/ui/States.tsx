import { Link } from 'react-router-dom';

interface EmptyStateProps {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function EmptyState({ message, actionLabel, onAction }: EmptyStateProps) {
  return (
    <div className="cg-card border-dashed p-10 text-center">
      <p className="text-sm text-ink-500">{message}</p>
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
    <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-danger">
      <p>{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className="cg-btn-secondary mt-4 !border-red-300 !text-danger">
          Retry
        </button>
      )}
    </div>
  );
}

export function SkeletonTable({ rows = 5 }: { rows?: number }) {
  return (
    <div role="status" aria-label="Loading" className="space-y-2">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="h-12 animate-pulse rounded-lg bg-line/60" />
      ))}
    </div>
  );
}

export function NotFoundPanel({ message, backTo, backLabel }: { message: string; backTo: string; backLabel: string }) {
  return (
    <div className="cg-card p-10 text-center">
      <p className="text-sm text-ink-500">{message}</p>
      <Link to={backTo} className="cg-btn-primary mt-5">{backLabel}</Link>
    </div>
  );
}
