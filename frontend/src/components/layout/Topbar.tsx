import { Menu } from 'lucide-react';
import { useBackendHealth } from '../../api/health';

interface TopbarProps {
  title: string;
  onOpenNavigation: () => void;
}

export function Topbar({ title, onOpenNavigation }: TopbarProps) {
  const health = useBackendHealth();
  const status = health.isPending
    ? 'Checking backend'
    : health.isSuccess && health.data?.status === 'UP'
      ? 'Backend online'
      : 'Backend unavailable';

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center justify-between gap-4 border-b border-line bg-surface px-4 sm:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <button
          type="button"
          onClick={onOpenNavigation}
          aria-label="Open navigation"
          aria-controls="cleargrade-sidebar"
          className="rounded-md p-2 text-ink-700 hover:bg-canvas lg:hidden"
        >
          <Menu aria-hidden="true" className="size-5" />
        </button>
        <h1 className="truncate text-lg font-semibold text-ink-950">{title}</h1>
      </div>

      <div className="flex items-center gap-2" aria-live="polite">
        <span
          aria-hidden="true"
          className={`size-2 rounded-full ${
            health.isSuccess && health.data?.status === 'UP' ? 'bg-emerald-500' : 'bg-slate-400'
          }`}
        />
        <span className="hidden text-sm text-ink-500 sm:inline">{status}</span>
      </div>
    </header>
  );
}
