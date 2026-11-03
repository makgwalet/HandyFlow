import { Routes, Route, Navigate } from 'react-router-dom'
import { MainLayout } from '@/components/layout/MainLayout'
import { Login } from '@/pages/Login'
import { Dashboard } from '@/pages/Dashboard'
import { PatientList } from '@/pages/patients/PatientList'
import { PatientDetail } from '@/pages/patients/PatientDetail'
import { Schedule } from '@/pages/schedule/Schedule'
import { ConsultationWorkspace } from '@/pages/consultations/ConsultationWorkspace'
import { LabInbox } from '@/pages/lab/LabInbox'
import { Billing } from '@/pages/billing/Billing'
import { Settings } from '@/pages/Settings'
import { useAuthStore } from '@/stores/auth-store'

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuthStore()
  return isAuthenticated ? <MainLayout>{children}</MainLayout> : <Navigate to="/login" />
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
      <Route path="/patients" element={<ProtectedRoute><PatientList /></ProtectedRoute>} />
      <Route path="/patients/:id" element={<ProtectedRoute><PatientDetail /></ProtectedRoute>} />
      <Route path="/schedule" element={<ProtectedRoute><Schedule /></ProtectedRoute>} />
      <Route path="/consultations" element={<ProtectedRoute><ConsultationWorkspace /></ProtectedRoute>} />
      <Route path="/lab" element={<ProtectedRoute><LabInbox /></ProtectedRoute>} />
      <Route path="/billing" element={<ProtectedRoute><Billing /></ProtectedRoute>} />
      <Route path="/settings" element={<ProtectedRoute><Settings /></ProtectedRoute>} />
    </Routes>
  )
}