import { ShieldCheck, X } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { navigationItems } from '../navigation/navigation';
import { ClearGradeLogo } from './ClearGradeLogo';

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

export function Sidebar({ open, onClose }: SidebarProps) {
  return (
    <>
      {open && (
        <button
          type="button"
          aria-label="Close navigation"
          onClick={onClose}
          className="fixed inset-0 z-30 cursor-pointer bg-navy-950/50 backdrop-blur-[2px] lg:hidden"
        />
      )}

      <aside
        id="cleargrade-sidebar"
        aria-label="Primary navigation"
        className={`fixed inset-y-0 left-0 z-40 flex w-72 flex-col overflow-hidden bg-navy-900 text-slate-200 shadow-[0_0_60px_rgba(13,33,67,0.35)] transition-transform duration-300 ease-out lg:translate-x-0 ${
          open ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div aria-hidden="true" className="cg-sidebar-wave pointer-events-none absolute inset-0" />

        <div className="relative z-10 flex h-16 items-center justify-between border-b border-white/10 px-4.5">
          <div className="flex items-center gap-3">
            <ClearGradeLogo aria-label="ClearGrade" variant="onDark" className="h-10 w-10 drop-shadow-[0_1px_1px_rgba(0,0,0,0.3)]" />
            <div className="leading-tight">
              <span className="block font-bold tracking-[0.02em] text-white">ClearGrade</span>
              <span className="block text-[13px] font-medium tracking-[0.1em] text-lake-400/90">Results &amp; Data Quality</span>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close navigation"
            className="cursor-pointer rounded-xl p-2 text-slate-400 transition-colors hover:bg-white/10 hover:text-white lg:hidden"
          >
            <X aria-hidden="true" className="size-5" />
          </button>
        </div>

        <nav className="relative z-10 flex-1 overflow-y-auto px-3 py-6">
          <p className="cg-eyebrow mb-3.5 px-3 pb-2 pt-1.5 text-[11px] tracking-[0.14em] text-lake-500/70">Workspace</p>
          <ul className="space-y-1.5">
            {navigationItems.map((item) => {
              const Icon = item.icon;
              if (item.disabled) {
                return (
                  <li key={item.label}>
                    <span
                      aria-disabled="true"
                      title={item.hint}
                      className="flex cursor-not-allowed items-center gap-2.5 rounded-xl px-3 py-2.5 text-sm text-slate-500"
                    >
                      <Icon aria-hidden="true" className="size-5 shrink-0" />
                      <span>
                        {item.label}
                        <span className="mt-0.5 block text-xs text-slate-600">{item.hint}</span>
                      </span>
                    </span>
                  </li>
                );
              }

              return (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    onClick={onClose}
                    className={({ isActive }) =>
                      `group relative flex items-center gap-2.5 overflow-hidden rounded-[var(--radius-sidebar-item)] px-3 py-2.5 text-sm font-semibold transition-all duration-[var(--duration-transition-sidebar)] ${
                        isActive
                          ? 'bg-lake-500 text-navy-950 shadow-[0_6px_18px_rgba(128,180,227,0.22)]'
                          : 'text-slate-300 hover:bg-white/10 hover:text-white'
                      }`
                    }
                  >
                    {({ isActive }) => (
                      <>
                        {isActive && (
                          <span aria-hidden="true" className="absolute inset-y-2 left-0 w-1 rounded-r-full bg-sun-500" />
                        )}
                        <Icon
                          aria-hidden="true"
                          className={`size-5 shrink-0 transition-transform duration-200 ${
                            isActive ? 'text-navy-950' : 'text-lake-500/80 group-hover:text-white'
                          }`}
                        />
                        {item.label}
                      </>
                    )}
                  </NavLink>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="relative z-10 flex items-center gap-2 border-t border-white/10 px-4.5 py-4 text-[12px] tracking-[0.06em] text-slate-400">
          <ShieldCheck aria-hidden="true" className="size-4 shrink-0 text-lake-500/90" />
          <span>Local / trusted evaluation only</span>
        </div>
      </aside>
    </>
  );
}
