// src/components/layout/AppLayout.tsx

import { Outlet } from 'react-router-dom'

export function AppLayout() {
  return (
    <div style={{ minHeight: '100vh', background: '#F8FAFC' }}>
      <Outlet />
    </div>
  )
}