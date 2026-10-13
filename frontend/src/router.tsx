import {DataDeletionPage} from './pages/DataDeletionPage';
import {LegacyCareBoundary} from './components/LegacyCareBoundary';
import {CashManagementPage} from './pages/CashManagementPage';
import {CareVisitPage} from './pages/CareVisitPage';
import {AgendaEntryPage} from './pages/AgendaEntryPage';
import {CareTariffsPage} from './pages/CareTariffsPage';
import {CareEpisodeCreatePage,CareEpisodePage} from './pages/CareEpisodePage';
import {PatientIdentityCreatePage,PatientIdentityPage} from './pages/PatientIdentityPage';
import {MyTeamInvitationsPage} from './pages/MyTeamInvitationsPage';
import {LicensingPlansPage} from './pages/LicensingPlansPage';
import {OrganizationLicensePage} from './pages/OrganizationLicensePage';
import {MyPatientAccessPage} from './pages/MyPatientAccessPage';
import {ProfessionalOnboardingPage} from './pages/ProfessionalOnboardingPage';
import {ConsultationsPage} from './pages/ConsultationsPage';
import {RegistrationOptionsPage} from './pages/RegistrationOptionsPage';
import {ProfessionalReviewPage} from './pages/ProfessionalReviewPage';
import {ProfessionalInvitationPage} from './pages/ProfessionalInvitationPage';
import {createBrowserRouter} from 'react-router-dom';
import {lazy,Suspense} from 'react';

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
import {PrescriptionsPage} from './pages/PrescriptionsPage';
import {PrescriptionDetailPage} from './pages/PrescriptionDetailPage';
import {MedicationStocksPage} from './pages/MedicationStocksPage';
import {PatientAccessPage} from './pages/PatientAccessPage';
import {ConsultationStartPage} from './pages/ConsultationStartPage';
import {ConsultationPage} from './pages/ConsultationPage';
import {LaboratoryOrdersPage} from './pages/LaboratoryOrdersPage';
import {LaboratoryOrderPage} from './pages/LaboratoryOrderPage';
import {MaternalChildPage} from './pages/MaternalChildPage';
import {PregnancyDetailPage} from './pages/PregnancyDetailPage';
import {ChildHealthRecordPage} from './pages/ChildHealthRecordPage';
import {DocumentsPage} from './pages/DocumentsPage';
import {ChatPage} from './pages/ChatPage';
import {TeleconsultationsPage} from './pages/TeleconsultationsPage';
import {NotificationsPage} from './pages/NotificationsPage';
import {BillingPage} from './pages/BillingPage';
import {InvoiceDetailPage} from './pages/InvoiceDetailPage';
import {AdminPlatformPage} from './pages/AdminPlatformPage';
import {AuditSecurityDashboardPage} from './pages/AuditSecurityDashboardPage';

const TeleconsultationRoomPage=lazy(()=>import('./pages/TeleconsultationRoomPage').then(module=>({default:module.TeleconsultationRoomPage})));

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    element: <ProtectedRoute />,
        children: [
          {path: '/registration/complete', element: <RegistrationCompletePage />},
          {path: '/me/data-deletion', element: <DataDeletionPage />},
          {
        element: <MainLayout />,
        children: [
          {path: '/', element: <DashboardPage />},
          {path: '/me/team-invitations', element: <MyTeamInvitationsPage />},
          {path: '/me', element: <MePage />},
          {path: '/me/access', element: <MyPatientAccessPage />},
          {path: '/professional-onboarding', element: <ProfessionalOnboardingPage />},
          {path: '/professional-invitation', element: <ProfessionalInvitationPage />},
          {path: '/admin/registration-options', element: <RegistrationOptionsPage />},
          {path: '/admin/professional-requests', element: <ProfessionalReviewPage />},
          {path: '/organizations', element: <OrganizationsPage />},
          {path: '/organizations/new', element: <OrganizationFormPage />},
          {path: '/organizations/:id', element: <OrganizationDetailPage />},
          {path: '/organizations/:id/license', element: <OrganizationLicensePage />},
          {path: '/admin/licensing-plans', element: <LicensingPlansPage />},
          {path: '/organizations/:id/edit', element: <OrganizationFormPage />},
          {path: '/professionals', element: <ProfessionalsPage />},
          {path: '/professionals/new', element: <ProfessionalFormPage />},
          {path: '/professionals/:id', element: <ProfessionalDetailPage />},
          {path: '/professionals/:id/edit', element: <ProfessionalFormPage />},
          {path: '/patients', element: <PatientsPage />},
          {path: '/patients/new', element: <PatientIdentityCreatePage />},
          {path: '/patients/:id/identity', element: <PatientIdentityPage />},
          {path: '/patients/:id', element: <PatientDetailPage />},
          {path: '/patients/:id/edit', element: <PatientFormPage />},
          {path: '/patients/:id/access', element: <PatientAccessPage />},
          {path: '/care/episodes/new', element: <CareEpisodeCreatePage />},
          {path: '/care/episodes/:id', element: <CareEpisodePage />},
          {path: '/care/visits/:id', element: <CareVisitPage />},
          {path: '/agenda', element: <AgendaEntryPage />},
          {path: '/agenda/legacy', element: <LegacyCareBoundary>{readOnly=><AgendaPage readOnly={readOnly}/>}</LegacyCareBoundary>},
          {path: '/consultations', element: <LegacyCareBoundary>{readOnly=><ConsultationsPage readOnly={readOnly}/>}</LegacyCareBoundary>},
          {path: '/consultations/new', element: <LegacyCareBoundary writeOnly>{()=><ConsultationStartPage/>}</LegacyCareBoundary>},
          {path: '/consultations/:id', element: <LegacyCareBoundary>{readOnly=><ConsultationPage readOnly={readOnly}/>}</LegacyCareBoundary>},
          {path: '/laboratory/orders', element: <LaboratoryOrdersPage />},
          {path: '/laboratory/orders/:id', element: <LaboratoryOrderPage />},
          {path: '/maternal-child', element: <MaternalChildPage />},
          {path: '/maternal-child/pregnancies/:id', element: <PregnancyDetailPage />},
          {path: '/maternal-child/children/:childId', element: <ChildHealthRecordPage />},
          {path: '/documents', element: <DocumentsPage />},
          {path: '/chat', element: <ChatPage />},
          {path: '/teleconsultations', element: <TeleconsultationsPage />},
          {path: '/teleconsultations/:id', element: <Suspense fallback={null}><TeleconsultationRoomPage /></Suspense>},
          {path: '/notifications', element: <NotificationsPage />},
          {path: '/pharmacy/prescriptions', element: <PrescriptionsPage />},
          {path: '/pharmacy/prescriptions/:id', element: <PrescriptionDetailPage />},
          {path: '/pharmacy/stocks', element: <MedicationStocksPage />},
          {path: '/billing', element: <BillingPage />},
          {path: '/billing/tariffs', element: <CareTariffsPage />},
          {path: '/billing/cash', element: <CashManagementPage />},
          {path: '/billing/invoices/:id', element: <InvoiceDetailPage />},
          {path: '/admin', element: <AdminPlatformPage />},
          {path: '/admin/audit-security', element: <AuditSecurityDashboardPage />},
        ],
      },
    ],
  },
]);
