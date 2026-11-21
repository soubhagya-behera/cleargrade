import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { queryClient } from './api/queryClient';
import { AppShell } from './components/layout/AppShell';
import { NotFoundPage } from './features/app/NotFoundPage';
import { ImportPage } from './features/import/ImportPage';
import { StudentDirectoryPage } from './features/students/StudentDirectoryPage';
import { StudentReportPage } from './features/students/StudentReportPage';
import { StudentReportFinder } from './features/students/StudentReportFinder';
import './styles/index.css';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Root element #root is missing');
}

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<Navigate to="/import" replace />} />
            <Route path="/import" element={<ImportPage />} />
            <Route path="/students" element={<StudentDirectoryPage />} />
            <Route path="/student-report" element={<StudentReportFinder />} />
            <Route path="/students/:studentId" element={<StudentReportPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
