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
      <Sidebar open={navigationOpen} onClose={() => setNavigationOpen(false)} />
      <div className="lg:pl-72">
        <Topbar title={pageTitleFor(pathname)} onOpenNavigation={() => setNavigationOpen(true)} />
        <main className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 sm:py-10">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
