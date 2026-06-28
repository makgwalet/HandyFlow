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
import { FuelPage }                     from "./pages/fuel/FuelPage"
import { EarthMovingPage }              from "./pages/earthmoving/EarthmoingPage"
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
import { RecruiterPage }                from './pages/recruiter/RecruiterPage'
import { PosPage }                      from './pages/pos/PosPage'
import { AccountantPage }               from "./pages/accountant/AccountantPage"
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

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
})

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore(s => s.token)
  if (!token) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          {/* Public routes */}
          <Route path="/login"    element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />

          {/* Client portal — public, intentionally outside ModuleLayout (no nav bar) */}
          <Route path="/projects/portal/:token" element={<ClientPortalPage />} />

          {/* Token-secured routes — also outside ModuleLayout */}
          <Route path="/sign/:token"                   element={<SigningPage />} />
          <Route path="/creative/approve/:token"       element={<CreativeApprovePage />} />

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
