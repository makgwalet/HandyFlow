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

// ── Projects module ────────────────────────────────────────────────────────────
// ProjectsPage is the single-page shell (matches ClinicPage pattern)
// ProjectDetailPage keeps its own route so deep links work (but stays inside ModuleLayout)
// ClientPortalPage is public — outside ModuleLayout intentionally
import { ProjectsPage }     from "./pages/projects/ProjectsPage"
import { ProjectDetailPage } from "./pages/projects/ProjectDetailPage"
import { ClientPortalPage }  from "./pages/projects/ClientPortalPage"
import { EarthMovingPage } from "./pages/earthmoving/EarthMovingPage"
import { ForgotPasswordPage } from "./pages/auth/ForgotPasswordPage"
import { ResetPasswordPage } from "./pages/auth/ResetPasswordPage"
import { VerifyEmailPage } from "./pages/auth/VerifyEmailPage"
import { AccountLockedPage } from "./pages/auth/AccountLockedPage"
import { SessionExpiryModal } from "./components/SessionExpiryModal"
// Recruiter public careers/apply pages — no auth, no ModuleLayout, same
// shape as ClientPortalPage. NOTE: the "View posting" link inside
// RecruiterPage.tsx and the offer-letter/interview-scheduled emails
// already hardcode this exact URL scheme (/careers/:tenantSlug and
// /careers/:tenantSlug/:jobSlug) — these routes complete a contract that
// already existed in shipped code, not a fresh URL design.
import { CareersListPage } from "./pages/careers/CareersListPage"
import { JobApplyPage }    from "./pages/careers/JobApplyPage"
import { CreateVariableHoursContractPage } from "./pages/invoicing/CreateVariableHoursContractPage"

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
})

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore(s => s.token)
  if (!token) return <Navigate to="/login" replace />
  return <>{children}</>
}

// NEW: closes "not added to App.tsx, redirects to saas" — deliberately
// checks usePortalAuthStore, not useAuthStore, and redirects to the
// portal's own login page, not the staff one. A portal session and a
// staff session are genuinely separate; this guard must never
// accidentally accept one for the other.
function PortalProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = usePortalAuthStore(s => s.token)
  if (!token) return <Navigate to="/accountant/portal/login" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {/* NEW: mounted globally, outside Routes, so the warning can show
            regardless of which page is currently active — the whole
            point is catching a user sitting idle on any screen, not just
            specific ones. The component itself checks isAuthenticated
            internally and renders nothing when logged out, so this is
            safe to always render. */}
        <SessionExpiryModal />
        <Routes>
          {/* Public routes */}
          <Route path="/login"    element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
          <Route path="/forgot-password"           element={<ForgotPasswordPage />} />
          <Route path="/reset-password"            element={<ResetPasswordPage />} />
          <Route path="/verify-email"              element={<VerifyEmailPage />} />
          {/* NEW: was never wired into the router at all — confirmed via
              real testing this meant BOTH LoginPage.tsx's own redirect
              (added earlier when fixing subscriptionStatus) AND the
              client.ts 402 interceptor were both redirecting to a route
              that's never existed. Placed alongside the other
              auth-adjacent pages, outside ProtectedRoute, since a
              suspended tenant still holds a genuinely valid login token
              — they're authenticated but blocked, not logged out, so
              this needs to be reachable regardless of session state. */}
          <Route path="/account-locked"            element={<AccountLockedPage />} />

          {/* Client portal — public, intentionally outside ModuleLayout (no nav bar) */}
          <Route path="/projects/portal/:token" element={<ClientPortalPage />} />
          <Route path="/portal/:token" element={<ClientPortalPage />} />

          {/* NEW: Accountant client portal — closes "not added to
              App.tsx, redirects to saas". Path matches exactly what the
              invite email already sends (EmailTemplates.portalInvite),
              built server-side before this frontend existed — not
              changed to match a different frontend convention, since
              changing it now would break live invite links already
              sent. Genuinely outside ModuleLayout and outside the
              staff ProtectedRoute — a portal user is not staff and
              must never see the staff nav bar or be subject to staff
              auth. */}
          <Route path="/accountant/portal/login"              element={<PortalLoginPage />} />
          <Route path="/accountant/portal/auth/accept-invite" element={<PortalAcceptInvitePage />} />
          <Route path="/accountant/portal" element={<PortalProtectedRoute><PortalHomePage /></PortalProtectedRoute>} />
          <Route path="/accountant/portal/clients/:clientId" element={<PortalProtectedRoute><PortalClientDetailPage /></PortalProtectedRoute>} />

          {/* Token-secured routes — also outside ModuleLayout */}
          <Route path="/sign/:token"                   element={<SigningPage />} />
          <Route path="/creative/approve/:token"       element={<CreativeApprovePage />} />
          <Route path="/unsubscribe/:token"            element={<UnsubscribePage />} />

          {/* Recruiter public careers/apply — public, outside ModuleLayout,
              no login. NOTE: /careers/track/:token (the applicant portal
              already promised in offer-letter/interview emails via
              RecruiterService's hardcoded portalUrl) is NOT yet wired up —
              still a dead link until that page is built separately. */}
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
            <Route path="/profile"                element={<ProfilePage />} />
            <Route path="/invoices/retainer/new"  element={<CreateRetainerPage />} />
            <Route path="/recurring/variable-hours/new" element={<CreateVariableHoursContractPage />} />
            <Route path="/recurring/new"          element={<CreateRecurringSchedulePage />} />
            <Route path="/recurring"              element={<InvoicingPage />} />
            <Route path="/supply-chain"           element={<SupplyChainPage />} />
            
            {/* ── Projects ── */}
            {/*
             * /projects        → ProjectsPage (dashboard + list, single-page shell like ClinicPage)
             * /projects/:id    → ProjectDetailPage (deep-linkable, stays inside ModuleLayout = nav bar stays)
             *
             * IMPORTANT: /projects/:id must come AFTER /projects in this group.
             * React Router matches top-to-bottom so the exact /projects route won't
             * accidentally swallow /:id.
             */}
            <Route path="/projects"     element={<ProjectsPage />} />
            <Route path="/projects/:id" element={<ProjectDetailPage />} />
          </Route>

          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
