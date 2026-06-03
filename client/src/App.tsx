import * as Sentry from '@sentry/react';
import { Outlet } from 'react-router-dom';

import { ErrorPage } from '@/features/Error/pages/ErrorPage';

import { ToastProvider } from './shared/components/Toast/ToastContext';
import { usePageTrack } from './shared/hooks/usePageTrack';

export const App = () => {
  usePageTrack();

  return (
    <Sentry.ErrorBoundary fallback={<ErrorPage />}>
      <ToastProvider>
        <Outlet />
      </ToastProvider>
    </Sentry.ErrorBoundary>
  );
};
