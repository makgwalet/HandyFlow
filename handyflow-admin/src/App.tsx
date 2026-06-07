// src/App.tsx
import { useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminLoginPage }     from './pages/login/AdminLoginPage'
import { AdminLayout }        from './components/AdminLayout'
import { AdminDashboardPage } from './pages/dashboard/AdminDashboardPage'
import { AdminTenantsPage }   from './pages/tenants/AdminTenantsPage'
import { AdminTenantDetail }  from './pages/tenants/AdminTenantDetail'
import { AdminBillingPage }   from './pages/billing/AdminBillingPage'
import { AdminIncidentsPage } from './pages/incidents/AdminIncidentsPage'
import { AdminInvoicesPage }  from './pages/invoices/AdminInvoicesPage'
import { AdminModulesPage, AdminNewModulePage }   from './pages/modules/AdminModulesPage'
import { AdminReportsPage }   from './pages/reports/AdminReportsPage'
import { AdminAuditPage }     from './pages/audit/AdminAuditPage'
import { authStore }          from './store/auth'
import { AdminLookupsPage } from './pages/lookups/AdminLookupsPage'

const qc = new QueryClient({ defaultOptions: { queries: { retry: 1, staleTime: 30_000 } } })

function RequireAuth({ children }: { children: JSX.Element }) {
  if (!authStore.isLoggedIn()) return <Navigate to="/login" replace />
  return children
}

export default function App() {
  const [authed, setAuthed] = useState(authStore.isLoggedIn())

  useEffect(() => {
    // Check token expiry every minute
    const id = setInterval(() => setAuthed(authStore.isLoggedIn()), 60_000)
    return () => clearInterval(id)
  }, [])

  return (
    <QueryClientProvider client={qc}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={
            authed
              ? <Navigate to="/dashboard" replace />
              : <AdminLoginPage onLogin={() => setAuthed(true)} />
          } />
          <Route element={<RequireAuth><AdminLayout /></RequireAuth>}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard"     element={<AdminDashboardPage />} />
            <Route path="/tenants"       element={<AdminTenantsPage />} />
            <Route path="/tenants/:slug" element={<AdminTenantDetail />} />
            <Route path="/billing"       element={<AdminBillingPage />} />
            <Route path="/incidents"     element={<AdminIncidentsPage />} />
            <Route path="/invoices"      element={<AdminInvoicesPage />} />
            <Route path="/modules"       element={<AdminModulesPage />} />
            <Route path="/reports"       element={<AdminReportsPage />} />
            <Route path="/audit"         element={<AdminAuditPage />} />
            <Route path="/lookups"       element={<AdminLookupsPage />} />
            <Route path="/modules/new"   element={<AdminNewModulePage />} />
          </Route>
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
