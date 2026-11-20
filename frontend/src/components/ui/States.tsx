import { Link } from 'react-router-dom';

interface EmptyStateProps {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function EmptyState({ message, actionLabel, onAction }: EmptyStateProps) {
  return (
    <div className="rounded-xl border border-dashed border-line bg-surface p-10 text-center">
      <p className="text-sm text-ink-500">{message}</p>
      {actionLabel && onAction && (
        <button type="button" onClick={onAction} className="mt-4 rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white">
          {actionLabel}
        </button>
      )}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-800">
      <p>{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className="mt-3 rounded-md border border-red-300 px-3 py-2 text-sm font-medium">
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
    <div className="rounded-xl border border-line bg-surface p-10 text-center">
      <p className="text-sm text-ink-500">{message}</p>
      <Link to={backTo} className="mt-4 inline-block rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white">{backLabel}</Link>
    </div>
  );
}
