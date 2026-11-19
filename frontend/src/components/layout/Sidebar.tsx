import { GraduationCap, X } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { navigationItems } from '../navigation/navigation';

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
          className="fixed inset-0 z-30 bg-ink-950/60 lg:hidden"
        />
      )}

      <aside
        id="cleargrade-sidebar"
        aria-label="Primary navigation"
        className={`fixed inset-y-0 left-0 z-40 flex w-72 flex-col bg-ink-950 text-slate-200 transition-transform duration-200 lg:translate-x-0 ${
          open ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex h-16 items-center justify-between border-b border-white/10 px-5">
          <div className="flex items-center gap-3">
            <span className="flex size-9 items-center justify-center rounded-lg bg-brand-600 text-white">
              <GraduationCap aria-hidden="true" className="size-5" />
            </span>
            <div className="leading-tight">
              <span className="block text-base font-semibold tracking-tight text-white">ClearGrade</span>
              <span className="block text-xs text-slate-400">Results &amp; Data Quality</span>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close navigation"
            className="rounded-md p-1.5 text-slate-400 hover:bg-white/10 hover:text-white lg:hidden"
          >
            <X aria-hidden="true" className="size-5" />
          </button>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-6">
          <p className="px-3 pb-2 text-xs font-semibold uppercase tracking-wider text-slate-500">Workspace</p>
          <ul className="space-y-1">
            {navigationItems.map((item) => {
              const Icon = item.icon;
              if (item.disabled) {
                return (
                  <li key={item.label}>
                    <span
                      aria-disabled="true"
                      title={item.hint}
                      className="flex cursor-not-allowed items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-slate-500"
                    >
                      <Icon aria-hidden="true" className="size-5 shrink-0" />
                      <span>
                        {item.label}
                        <span className="block text-xs text-slate-600">{item.hint}</span>
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
                      `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                        isActive
                          ? 'bg-brand-600 text-white'
                          : 'text-slate-300 hover:bg-white/10 hover:text-white'
                      }`
                    }
                  >
                    <Icon aria-hidden="true" className="size-5 shrink-0" />
                    {item.label}
                  </NavLink>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="border-t border-white/10 px-5 py-4 text-xs text-slate-500">
          Local / trusted evaluation only
        </div>
      </aside>
    </>
  );
}
