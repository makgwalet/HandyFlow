import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { LoginPage }                    from "./pages/auth/LoginPage"
import { RegisterPage }                 from "./pages/auth/RegisterPage"
import { DashboardPage }                from "./pages/dashboard/DashboardPage"
import { CustomersPage }                from "./pages/customers/CustomersPage"
import { InvoicingPage }                from "./pages/invoicing/InvoicingPage"
import { CreateQuotePage }              from "./pages/invoicing/CreateQuotePage"
import { QuoteDetailPage }              from "./pages/quotes/QuoteDetailPage"
import { CataloguePage }                from "./pages/catalogue/CataloguePage"
import { BillingPage }                  from "./pages/billing/BillingPage"
import { SecurityPage }                 from "./pages/security/SecurityPage"
import { ModuleLayout }                 from "./components/layout/ModuleLayout"
import { useAuthStore }                 from "./store/auth.store"
// NEW: closes "not added to App.tsx, redirects to saas".
import { usePortalAuthStore }           from "./store/portalAuth.store"
import { FuelPage }                     from "./pages/fuel/FuelPage"
import { PropertyPage }                 from "./pages/property/PropertyPage"
import { FleetPage }                    from "./pages/fleet/FleetPage"
import { SettingsPage }                 from './pages/settings/SettingsPage'
import { AccountingPage }               from './pages/accounting/AccountingPage'
import { BookingsPage }                 from './pages/bookings/BookingsPage'
import { HrPage }                       from './pages/hr/HrPage'
import { ClinicPage }                   from './pages/clinic/ClinicPage'
import { EventsPage }                   from './pages/events/EventsPage'
import ContractingPage                  from './pages/contracting/ContractingPage'
import SigningPage                      from './pages/contracting/SigningPage'
import { ExpensesPage }                 from './pages/expenses/ExpensesPage'
import { AcceptInvitePage }             from './pages/auth/AcceptInvitePage'
import { CreativePage }                 from './pages/creative/CreativePage'
import { DeskPage }                     from './pages/desk/DeskPage'
import { TasksPage }                    from './pages/tasks/TasksPage'
import { MarketingPage }                from './pages/marketing/MarketingPage'
import UnsubscribePage                  from './pages/marketing/UnsubscribePage'
import { RecruiterPage }                from './pages/recruiter/RecruiterPage'
import { PosPage }                      from './pages/pos/PosPage'
import { AccountantPage }               from "./pages/accountant/AccountantPage"
// NEW: closes "not added to App.tsx, redirects to saas" — the invite
// email's link (/accountant/portal/auth/accept-invite) was already
// hardcoded server-side; these are the pages it was always meant to
// point at.
import { PortalLoginPage }              from "./pages/accountant-portal/PortalLoginPage"
import { PortalAcceptInvitePage }       from "./pages/accountant-portal/PortalAcceptInvitePage"
import { PortalHomePage }               from "./pages/accountant-portal/PortalHomePage"
import { PortalClientDetailPage }       from "./pages/accountant-portal/PortalClientDetailPage"
import { CreativeApprovePage }          from "./pages/creative/CreativeApprovePage"
import { AccountsPayablePage }          from "./pages/ap/AccountsPayablePage"
import { ProfilePage }                  from "./pages/settings/ProfilePage"
import { CreateRecurringSchedulePage }  from "./pages/invoicing/CreateRecurringSchedulePage"
import { CreateRetainerPage }           from "./pages/invoicing/CreateRetainerPage"
import { SupplyChainPage }              from "./pages/supply-chain/SupplyChainPage"

// ── Payroll Bureau module ─────────────────────────────────────────────────────
import { PayrollBureauPage }                    from "./pages/payroll-bureau/PayrollBureauPage"
import { PayrollBureauPortalLoginPage }         from "./pages/payroll-bureau-portal/PayrollBureauPortalLoginPage"
import { PayrollBureauPortalAcceptInvitePage }  from "./pages/payroll-bureau-portal/PayrollBureauPortalAcceptInvitePage"
import { PayrollBureauPortalHomePage }          from "./pages/payroll-bureau-portal/PayrollBureauPortalHomePage"
import { PayrollBureauPortalClientDetailPage }  from "./pages/payroll-bureau-portal/PayrollBureauPortalClientDetailPage"

// ── Projects module ────────────────────────────────────────────────────────────
import { ProjectsPage }     from "./pages/projects/ProjectsPage"
import { ProjectDetailPage } from "./pages/projects/ProjectDetailPage"
import { ClientPortalPage }  from "./pages/projects/ClientPortalPage"
import { EarthMovingPage } from "./pages/earthmoving/EarthMovingPage"
import { ForgotPasswordPage } from "./pages/auth/ForgotPasswordPage"
import { ResetPasswordPage } from "./pages/auth/ResetPasswordPage"
import { VerifyEmailPage } from "./pages/auth/VerifyEmailPage"
import { AccountLockedPage } from "./pages/auth/AccountLockedPage"
import { SessionExpiryModal } from "./components/SessionExpiryModal"
import { CareersListPage } from "./pages/careers/CareersListPage"
import { JobApplyPage }    from "./pages/careers/JobApplyPage"
import { CreateVariableHoursContractPage } from "./pages/invoicing/CreateVariableHoursContractPage"
import { BookingAgencyPage } from "./pages/booking-agency/BookingAgencyPage"
import { RecruitmentAgencyPortalAcceptInvitePage } from "./pages/recruitment-agency-portal/RecruitmentAgencyPortalAcceptInvitePage"
import { RecruitmentAgencyPortalClientDetailPage } from "./pages/recruitment-agency-portal/RecruitmentAgencyPortalClientDetailPage"
import { RecruitmentAgencyPortalHomePage } from "./pages/recruitment-agency-portal/RecruitmentAgencyPortalHomePage"
import { RecruitmentAgencyPortalLoginPage } from "./pages/recruitment-agency-portal/RecruitmentAgencyPortalLoginPage"
import { RecruitmentAgencyPage } from "./pages/recruitment-agency/RecruitmentAgencyPage"
import { BookingAgencyPortalAcceptInvitePage } from "./pages/booking-agency-portal/BookingAgencyPortalAcceptInvitePage"
import { BookingAgencyPortalClientDetailPage } from "./pages/booking-agency-portal/BookingAgencyPortalClientDetailPage"
import { BookingAgencyPortalHomePage } from "./pages/booking-agency-portal/BookingAgencyPortalHomePage"
import { BookingAgencyPortalLoginPage } from "./pages/booking-agency-portal/BookingAgencyPortalLoginPage"
// NEW: the shared "needs attention" board — Stage 1 of the Financial
// Control & Assurance plan. Cross-module, tenant-wide, not scoped to
// any one module the way most routes below are — lives inside
// ModuleLayout like every other staff page (needs the nav bar and
// ProtectedRoute), but isn't itself a subscribable module.
import { ControlExceptionsPage } from "./pages/control-exceptions/ControlExceptionsPage"
// NEW: Stage 3 — external auditor portal. Same four-page shape as
// every other portal (login, accept-invite, home, detail), but
// tenant-scoped rather than client-scoped — see AuditorAccessGrant's
// own Javadoc for why.
import { AuditorPortalLoginPage } from "./pages/auditor-portal/AuditorPortalLoginPage"
import { AuditorPortalAcceptInvitePage } from "./pages/auditor-portal/AuditorPortalAcceptInvitePage"
import { AuditorPortalHomePage } from "./pages/auditor-portal/AuditorPortalHomePage"
import { AuditorPortalTenantDetailPage } from "./pages/auditor-portal/AuditorPortalTenantDetailPage"
import { LegalCompliancePage } from "./pages/legalcompliance/LegalCompliancePage"
import { DebtCollectionPage } from "./pages/debtcollection/DebtCollectionPage"
import { CollectionsAgencyPage } from "./pages/collectionsagency/CollectionsAgencyPage"
import { CollAgencyPortalAcceptInvitePage } from "./pages/collectionsagency-portal/CollAgencyPortalAcceptInvitePage"
import { CollAgencyPortalClientDetailPage } from "./pages/collectionsagency-portal/CollAgencyPortalClientDetailPage"
import { CollAgencyPortalHomePage } from "./pages/collectionsagency-portal/CollAgencyPortalHomePage"
import { CollAgencyPortalLoginPage } from "./pages/collectionsagency-portal/CollAgencyPortalLoginPage"
import WarehousingPage from "./pages/warehousing/WarehousingPage"
import { WhsePortalAcceptInvitePage } from "./pages/warehousing-portal/WhsePortalAcceptInvitePage"
import { WhsePortalClientDetailPage } from "./pages/warehousing-portal/WhsePortalClientDetailPage"
import { WhsePortalHomePage } from "./pages/warehousing-portal/WhsePortalHomePage"
import { WhsePortalLoginPage } from "./pages/warehousing-portal/WhsePortalLoginPage"
import TrainingPage from "./pages/training/TrainingPage"
import TrainProvPage from "./pages/trainingprovider/TrainProvPage"
import { TrainProvPortalAcceptInvitePage } from "./pages/trainingprovider-portal/TrainProvPortalAcceptInvitePage"
import { TrainProvPortalHomePage } from "./pages/trainingprovider-portal/TrainProvPortalHomePage"
import { TrainProvPortalLoginPage } from "./pages/trainingprovider-portal/TrainProvPortalLoginPage"
import AgriculturePage from "./pages/agriculture/AgriculturePage"

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
})


function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore(s => s.token)
  if (!token) return <Navigate to="/login" replace />
  return <>{children}</>
}

function RecruitmentAgencyPortalProtectedRoute({ children }: { children: React.ReactNode }) {
      const token = usePortalAuthStore(s => s.token)
      if (!token) return <Navigate to="/recruitment-agency/portal/login" replace />
      return <>{children}</>
    }

function BookingAgencyPortalProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = usePortalAuthStore(s => s.token)
  if (!token) return <Navigate to="/booking-agency/portal/login" replace />
  return <>{children}</>
}

function PortalProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = usePortalAuthStore(s => s.token)
  if (!token) return <Navigate to="/accountant/portal/login" replace />
  return <>{children}</>
}

function PayrollBureauPortalProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = usePortalAuthStore(s => s.token)
  if (!token) return <Navigate to="/payroll-bureau/portal/login" replace />
  return <>{children}</>
}

function CollAgencyPortalProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = usePortalAuthStore(s => s.token)
  if (!token) return <Navigate to="/collections-agency/portal/login" replace />
  return <>{children}</>
}

function WhsePortalProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = usePortalAuthStore(s => s.token)
  if (!token) return <Navigate to="/warehousing/portal/login" replace />
  return <>{children}</>
}

// NEW: Stage 3. Uses the same shared usePortalAuthStore every other
// portal uses — same pre-existing caveat as those: logging into two
// different portal types in the same browser session would share one
// token slot. Not a new risk introduced here, just inherited.
function AuditorPortalProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = usePortalAuthStore(s => s.token)
  if (!token) return <Navigate to="/auditor/portal/login" replace />
  return <>{children}</>
}


export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <SessionExpiryModal />
        <Routes>
{/* Public routes */}
          <Route path="/login"    element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
          <Route path="/forgot-password"           element={<ForgotPasswordPage />} />
          <Route path="/reset-password"            element={<ResetPasswordPage />} />
          <Route path="/verify-email"              element={<VerifyEmailPage />} />
          <Route path="/account-locked"            element={<AccountLockedPage />} />

          <Route path="/projects/portal/:token" element={<ClientPortalPage />} />
          <Route path="/portal/:token" element={<ClientPortalPage />} />

          <Route path="/accountant/portal/login"              element={<PortalLoginPage />} />
          <Route path="/accountant/portal/auth/accept-invite" element={<PortalAcceptInvitePage />} />
          <Route path="/accountant/portal" element={<PortalProtectedRoute><PortalHomePage /></PortalProtectedRoute>} />
          <Route path="/accountant/portal/clients/:clientId" element={<PortalProtectedRoute><PortalClientDetailPage /></PortalProtectedRoute>} />

          <Route path="/recruitment-agency/portal/login"              element={<RecruitmentAgencyPortalLoginPage />} />
            <Route path="/recruitment-agency/portal/auth/accept-invite" element={<RecruitmentAgencyPortalAcceptInvitePage />} />
            <Route path="/recruitment-agency/portal" element={<RecruitmentAgencyPortalProtectedRoute><RecruitmentAgencyPortalHomePage /></RecruitmentAgencyPortalProtectedRoute>} />
            <Route path="/recruitment-agency/portal/clients/:clientId" element={<RecruitmentAgencyPortalProtectedRoute><RecruitmentAgencyPortalClientDetailPage /></RecruitmentAgencyPortalProtectedRoute>} />

          <Route path="/payroll-bureau/portal/login"              element={<PayrollBureauPortalLoginPage />} />
          <Route path="/payroll-bureau/portal/auth/accept-invite" element={<PayrollBureauPortalAcceptInvitePage />} />
          <Route path="/payroll-bureau/portal" element={<PayrollBureauPortalProtectedRoute><PayrollBureauPortalHomePage /></PayrollBureauPortalProtectedRoute>} />
          <Route path="/payroll-bureau/portal/clients/:clientId" element={<PayrollBureauPortalProtectedRoute><PayrollBureauPortalClientDetailPage /></PayrollBureauPortalProtectedRoute>} />

          <Route path="/booking-agency/portal/login"              element={<BookingAgencyPortalLoginPage />} />
          <Route path="/booking-agency/portal/auth/accept-invite" element={<BookingAgencyPortalAcceptInvitePage />} />
          <Route path="/booking-agency/portal" element={<BookingAgencyPortalProtectedRoute><BookingAgencyPortalHomePage /></BookingAgencyPortalProtectedRoute>} />
          <Route path="/booking-agency/portal/clients/:clientId" element={<BookingAgencyPortalProtectedRoute><BookingAgencyPortalClientDetailPage /></BookingAgencyPortalProtectedRoute>} />

          <Route path="/collections-agency/portal/login"              element={<CollAgencyPortalLoginPage />} />
          <Route path="/collections-agency/portal/auth/accept-invite" element={<CollAgencyPortalAcceptInvitePage />} />
          <Route path="/collections-agency/portal" element={<CollAgencyPortalProtectedRoute><CollAgencyPortalHomePage /></CollAgencyPortalProtectedRoute>} />
          <Route path="/collections-agency/portal/clients/:clientId" element={<CollAgencyPortalProtectedRoute><CollAgencyPortalClientDetailPage /></CollAgencyPortalProtectedRoute>} />

          // Client portal (public auth routes + an authenticated portal route,
          // matching the exact pattern already used for /warehousing/portal/*):
          <Route path="/training-provider/portal/login" element={<TrainProvPortalLoginPage />} />
          <Route path="/training-provider/portal/auth/accept-invite" element={<TrainProvPortalAcceptInvitePage />} />
          <Route path="/training-provider/portal" element={<TrainProvPortalHomePage />} /> {/* wrap with the same portal-auth guard used for Warehousing/Collections Agency */}

          <Route path="/warehousing/portal/login" element={<WhsePortalLoginPage />} />
          <Route path="/warehousing/portal/auth/accept-invite" element={<WhsePortalAcceptInvitePage />} />
          <Route path="/warehousing/portal" element={
              <WhsePortalProtectedRoute><WhsePortalHomePage /></WhsePortalProtectedRoute>
          } />
          <Route path="/warehousing/portal/clients/:clientId" element={
            <WhsePortalProtectedRoute><WhsePortalClientDetailPage /></WhsePortalProtectedRoute>
          } />

          {/* NEW: Stage 3 — external auditor portal. Tenant-scoped, not
              client-scoped — routes reflect that: /tenants/:tenantId,
              not /clients/:clientId. */}
          <Route path="/auditor/portal/login"              element={<AuditorPortalLoginPage />} />
          <Route path="/auditor/portal/auth/accept-invite" element={<AuditorPortalAcceptInvitePage />} />
          <Route path="/auditor/portal" element={<AuditorPortalProtectedRoute><AuditorPortalHomePage /></AuditorPortalProtectedRoute>} />
          <Route path="/auditor/portal/tenants/:tenantId" element={<AuditorPortalProtectedRoute><AuditorPortalTenantDetailPage /></AuditorPortalProtectedRoute>} />

          <Route path="/sign/:token"                   element={<SigningPage />} />
          <Route path="/creative/approve/:token"       element={<CreativeApprovePage />} />
          <Route path="/unsubscribe/:token"            element={<UnsubscribePage />} />

          <Route path="/careers/:tenantSlug"                element={<CareersListPage />} />
          <Route path="/careers/:tenantSlug/:jobSlug"       element={<JobApplyPage />} />

          {/* All module pages — inside ModuleLayout so the nav bar appears */}
          <Route element={<ProtectedRoute><ModuleLayout /></ProtectedRoute>}>
            <Route path="/customers"   element={<CustomersPage />} />
            <Route path="/quotes"      element={<InvoicingPage />} />
            <Route path="/invoices"    element={<InvoicingPage />} />
            <Route path="/quotes/new"  element={<CreateQuotePage />} />
            <Route path="/quotes/:id"  element={<QuoteDetailPage />} />
            <Route path="/catalogue"   element={<CataloguePage />} />
            <Route path="/billing"     element={<BillingPage />} />
            <Route path="/security"    element={<SecurityPage />} />
            <Route path="/fuel"        element={<FuelPage />} />
            <Route path="/earthmoving" element={<EarthMovingPage />} />
            <Route path="/property"    element={<PropertyPage />} />
 <Route path="/fleet"       element={<FleetPage />} />
            <Route path="/bookings"    element={<BookingsPage />} />
            <Route path="/accounting"  element={<AccountingPage />} />
            <Route path="/settings"    element={<SettingsPage />} />
            <Route path="/hr"          element={<HrPage />} />
            <Route path="/clinic"      element={<ClinicPage />} />
            <Route path="/events"      element={<EventsPage />} />
            <Route path="/contracts"   element={<ContractingPage />} />
            <Route path="/expenses"    element={<ExpensesPage />} />
            <Route path="/invite/accept"          element={<AcceptInvitePage />} />
            <Route path="/creative"               element={<CreativePage />} />
            <Route path="/desk"                   element={<DeskPage />} />
            <Route path="/tasks"                  element={<TasksPage />} />
            <Route path="/marketing"              element={<MarketingPage />} />
            <Route path="/recruiter"              element={<RecruiterPage />} />
            <Route path="/pos"                    element={<PosPage />} />
            <Route path="/accountant"             element={<AccountantPage />} />
            <Route path="/ap"                     element={<AccountsPayablePage />} />
            <Route path="/booking-agency"       element={<BookingAgencyPage />} />
            <Route path="/payroll-bureau"         element={<PayrollBureauPage />} />
            <Route path="/profile"                element={<ProfilePage />} />
            <Route path="/invoices/retainer/new"  element={<CreateRetainerPage />} />
            <Route path="/recurring/variable-hours/new" element={<CreateVariableHoursContractPage />} />
            <Route path="/recurring/new"          element={<CreateRecurringSchedulePage />} />
            <Route path="/recurring"              element={<InvoicingPage />} />
            <Route path="/supply-chain"           element={<SupplyChainPage />} />
            <Route path="/legalcompliance"        element={<LegalCompliancePage />} />
            <Route path="/debtcollection" element={<DebtCollectionPage />} />
            <Route path="/recruitment-agency" element={<RecruitmentAgencyPage />} />
            <Route path="/collections-agency" element={<CollectionsAgencyPage />} />
            <Route path="/warehousing" element={<WarehousingPage />} />
            <Route path="/training" element={<TrainingPage />} />
            <Route path="/training-provider" element={<TrainProvPage />} />
            <Route path="/agriculture" element={<AgriculturePage />} />
            {/* NEW: shared "needs attention" board — see import comment above. */}
            <Route path="/control-exceptions"     element={<ControlExceptionsPage />} />

            {/* ── Projects ── */}
            <Route path="/projects"     element={<ProjectsPage />} />
            <Route path="/projects/:id" element={<ProjectDetailPage />} />
          </Route>

          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
