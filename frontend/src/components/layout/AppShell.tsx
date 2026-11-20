import { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { pageTitleFor } from '../navigation/navigation';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

export function AppShell() {
  const [navigationOpen, setNavigationOpen] = useState(false);
  const { pathname } = useLocation();

  return (
    <div className="min-h-screen bg-canvas">
      <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-brand-600 focus:px-4 focus:py-2 focus:text-white">
        Skip to content
      </a>
      <div className="no-print">
        <Sidebar open={navigationOpen} onClose={() => setNavigationOpen(false)} />
      </div>
      <div className="lg:pl-72">
        <div className="no-print">
          <Topbar title={pageTitleFor(pathname)} onOpenNavigation={() => setNavigationOpen(true)} />
        </div>
        <main id="main-content" className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 sm:py-10">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
