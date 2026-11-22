import { Menu } from 'lucide-react';
import { useBackendHealth } from '../../api/health';

interface TopbarProps {
  title: string;
  onOpenNavigation: () => void;
}

export function Topbar({ title, onOpenNavigation }: TopbarProps) {
  const health = useBackendHealth();
  const online = health.isSuccess && health.data?.status === 'UP';
  const status = health.isPending
    ? 'Checking backend'
    : online
      ? 'Backend online'
      : 'Backend unavailable';

  return (
    <header className="sticky top-0 z-20 border-b border-line/70 bg-surface/90 shadow-[0_1px_0_rgba(13,33,67,0.08)] backdrop-blur-xl backdrop-saturate-150">
      <div className="mx-auto flex h-16 w-full max-w-[1340px] items-center justify-between gap-4 px-4 sm:px-8">
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
              onClick={onOpenNavigation}
              aria-label="Open navigation"
              aria-controls="cleargrade-sidebar"
              className="cursor-pointer rounded-xl p-2.5 text-ink-700 transition-colors hover:bg-lake-050 hover:text-navy-900 focus-visible:bg-lake-050 lg:hidden"
            >
              <Menu aria-hidden="true" className="size-4.5" />
          </button>
          <h1 className="truncate text-lg font-bold tracking-tight text-navy-900 sm:text-xl">{title}</h1>
        </div>

        <div className="flex items-center gap-2.5" aria-live="polite">
          <span
            className={`inline-flex items-center gap-2.5 rounded-full border px-3.5 py-1.5 text-xs font-semibold transition-colors ${
              online
                ? 'border-lake-500/35 bg-lake-050 text-navy-900'
                : 'border-line bg-canvas text-ink-500'
            }`}
          >
            <span aria-hidden="true" className={`h-2 w-2 shrink-0 rounded-full ${online ? 'bg-success shadow-[0_0_0_3px_rgba(22,122,76,0.16)]' : 'bg-slate-400'}`} />
            <span className="hidden sm:inline">{status}</span>
          </span>
        </div>
      </div>
    </header>
  );
}
