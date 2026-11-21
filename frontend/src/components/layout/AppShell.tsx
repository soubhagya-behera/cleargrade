import { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { pageTitleFor } from '../navigation/navigation';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

export function AppShell() {
  const [navigationOpen, setNavigationOpen] = useState(false);
  const { pathname } = useLocation();

  return (
    <div className="cg-shell-glow min-h-screen bg-canvas">
      <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-navy-900 focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-white">
        Skip to content
      </a>
      <div className="no-print">
        <Sidebar open={navigationOpen} onClose={() => setNavigationOpen(false)} />
      </div>
      <div className="lg:pl-72">
        <div className="no-print">
          <Topbar title={pageTitleFor(pathname)} onOpenNavigation={() => setNavigationOpen(true)} />
        </div>
        <main id="main-content" className="mx-auto w-full max-w-[1340px] px-4 py-6 sm:px-8 sm:py-10 lg:py-14">
          <div key={pathname} className="cg-page-enter">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
