import {createBrowserRouter} from 'react-router-dom';

import {ProtectedRoute} from './auth/ProtectedRoute';
import {MainLayout} from './layout/MainLayout';
import {DashboardPage} from './pages/DashboardPage';
import {LoginPage} from './pages/LoginPage';
import {MePage} from './pages/MePage';
import {OrganizationsPage} from './pages/OrganizationsPage';
import {OrganizationFormPage} from './pages/OrganizationFormPage';
import {OrganizationDetailPage} from './pages/OrganizationDetailPage';
import {RegistrationCompletePage} from './pages/RegistrationCompletePage';

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    element: <ProtectedRoute />,
        children: [
          {path: '/registration/complete', element: <RegistrationCompletePage />},
          {
        element: <MainLayout />,
        children: [
          {path: '/', element: <DashboardPage />},
          {path: '/me', element: <MePage />},
          {path: '/organizations', element: <OrganizationsPage />},
          {path: '/organizations/new', element: <OrganizationFormPage />},
          {path: '/organizations/:id', element: <OrganizationDetailPage />},
          {path: '/organizations/:id/edit', element: <OrganizationFormPage />},
        ],
      },
    ],
  },
]);
