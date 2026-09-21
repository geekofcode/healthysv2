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
import {ProfessionalsPage} from './pages/ProfessionalsPage';
import {ProfessionalFormPage} from './pages/ProfessionalFormPage';
import {ProfessionalDetailPage} from './pages/ProfessionalDetailPage';
import {PatientsPage} from './pages/PatientsPage';
import {PatientFormPage} from './pages/PatientFormPage';
import {PatientDetailPage} from './pages/PatientDetailPage';
import {AgendaPage} from './pages/AgendaPage';

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
          {path: '/professionals', element: <ProfessionalsPage />},
          {path: '/professionals/new', element: <ProfessionalFormPage />},
          {path: '/professionals/:id', element: <ProfessionalDetailPage />},
          {path: '/patients', element: <PatientsPage />},
          {path: '/patients/new', element: <PatientFormPage />},
          {path: '/patients/:id', element: <PatientDetailPage />},
          {path: '/agenda', element: <AgendaPage />},
        ],
      },
    ],
  },
]);
