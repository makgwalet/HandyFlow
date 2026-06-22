// src/components/AdminLayout.tsx
import { useState } from 'react'
import { useNavigate, useLocation, Outlet } from 'react-router-dom'
import { authStore } from '../store/auth'
import {
  LayoutDashboard, Building2, CreditCard, AlertTriangle,
  FileText, BarChart2, LogOut, Shield, ChevronDown,
  Bell, Package, ScrollText, Menu, X,
  Database,
  UserPlus,
} from 'lucide-react'
import { AdminNotificationBell } from './AdminNotificationBell'

const NAV = [
  { path: '/dashboard',  label: 'Dashboard',    icon: LayoutDashboard },
  { path: '/tenants',    label: 'Tenants',       icon: Building2       },
  { path: '/billing',    label: 'Billing',       icon: CreditCard      },
  { path: '/incidents',  label: 'Incidents',     icon: AlertTriangle   },
  { path: '/invoices',   label: 'Invoices',      icon: FileText        },
  { path: '/modules',    label: 'Modules',       icon: Package         },
  { path: '/reports',    label: 'Reports',       icon: BarChart2       },
  { path: '/audit',      label: 'Audit Log',     icon: ScrollText      },
  { path: '/lookups', label: 'Lookups',           icon: Database },
  { path: '/onboarding', label: 'Onboarding', icon: UserPlus },
]

export function AdminLayout() {
  const navigate  = useNavigate()
  const location  = useLocation()
  const user      = authStore.get()
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [userMenuOpen, setUserMenuOpen] = useState(false)

  const handleLogout = () => {
    authStore.clear()
    navigate('/login')
  }

  const initials = user?.fullName
    ? user.fullName.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)
    : 'SA'

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: '#0F1117', fontFamily: "'Inter', system-ui, sans-serif" }}>

      {/* Sidebar */}
      <aside style={{
        width: sidebarOpen ? 240 : 64, flexShrink: 0,
        background: '#13161E', borderRight: '1px solid #1E2532',
        display: 'flex', flexDirection: 'column',
        transition: 'width 0.2s ease', overflow: 'hidden',
        position: 'sticky' as const, top: 0, height: '100vh',
      }}>
        {/* Logo */}
        <div style={{ padding: '20px 16px', borderBottom: '1px solid #1E2532', display: 'flex', alignItems: 'center', gap: 10, flexShrink: 0 }}>
          <div style={{ width: 32, height: 32, borderRadius: 9, background: 'linear-gradient(135deg, #1B3A6B, #0D9488)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <Shield size={16} color="#fff" />
          </div>
          {sidebarOpen && (
            <div>
              <div style={{ fontSize: 13, fontWeight: 800, color: '#F7FAFC', letterSpacing: '-0.3px' }}>HandyFlow</div>
              <div style={{ fontSize: 10, color: '#4A5568', fontWeight: 600, letterSpacing: '0.08em' }}>ADMIN PORTAL</div>
            </div>
          )}
          <button onClick={() => setSidebarOpen(p => !p)}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#4A5568', display: 'flex', marginLeft: 'auto', flexShrink: 0 }}>
            {sidebarOpen ? <X size={15} /> : <Menu size={15} />}
          </button>
        </div>

        {/* Nav */}
        <nav style={{ flex: 1, overflowY: 'auto', padding: '12px 8px' }}>
          {NAV.map(item => {
            const active = location.pathname.startsWith(item.path)
            const Icon   = item.icon
            return (
              <button key={item.path} onClick={() => navigate(item.path)}
                style={{
                  width: '100%', display: 'flex', alignItems: 'center', gap: 10,
                  padding: '9px 10px', borderRadius: 9, border: 'none', cursor: 'pointer',
                  marginBottom: 2,
                  background: active ? 'rgba(13,148,136,0.12)' : 'transparent',
                  color: active ? '#0D9488' : '#718096',
                  fontWeight: active ? 700 : 400, fontSize: 13,
                  textAlign: 'left' as const, transition: 'all 0.12s',
                }}
                onMouseEnter={e => { if (!active) (e.currentTarget as HTMLElement).style.background = 'rgba(255,255,255,0.04)' }}
                onMouseLeave={e => { if (!active) (e.currentTarget as HTMLElement).style.background = 'transparent' }}>
                <Icon size={16} style={{ flexShrink: 0 }} />
                {sidebarOpen && <span style={{ whiteSpace: 'nowrap' as const }}>{item.label}</span>}
                {active && sidebarOpen && <div style={{ width: 4, height: 4, borderRadius: '50%', background: '#0D9488', marginLeft: 'auto' }} />}
              </button>
            )
          })}
        </nav>

        {/* User */}
        <div style={{ padding: '12px 8px', borderTop: '1px solid #1E2532', flexShrink: 0 }}>
          <button onClick={() => setUserMenuOpen(p => !p)}
            style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 10, padding: '9px 10px', borderRadius: 9, border: 'none', cursor: 'pointer', background: userMenuOpen ? 'rgba(255,255,255,0.06)' : 'transparent' }}
            onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = 'rgba(255,255,255,0.04)'}
            onMouseLeave={e => { if (!userMenuOpen) (e.currentTarget as HTMLElement).style.background = 'transparent' }}>
            <div style={{ width: 28, height: 28, borderRadius: '50%', background: 'linear-gradient(135deg, #1B3A6B, #0D9488)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 700, color: '#fff', flexShrink: 0 }}>
              {initials}
            </div>
            {sidebarOpen && (
              <>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 12, fontWeight: 700, color: '#F7FAFC', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' as const }}>{user?.fullName ?? 'Admin'}</div>
                  <div style={{ fontSize: 10, color: '#4A5568', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' as const }}>{user?.role ?? 'SUPERADMIN'}</div>
                </div>
                <ChevronDown size={13} color="#4A5568" />
              </>
            )}
          </button>
          {userMenuOpen && sidebarOpen && (
            <div style={{ marginTop: 6, background: '#1A202C', border: '1px solid #2D3748', borderRadius: 9, overflow: 'hidden' }}>
              <button onClick={handleLogout}
                style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 8, padding: '9px 12px', border: 'none', background: 'none', cursor: 'pointer', fontSize: 13, color: '#FC8181' }}
                onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#742A2A22'}
                onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'none'}>
                <LogOut size={14} /> Sign out
              </button>
            </div>
          )}
        </div>
      </aside>

      {/* Main */}
      <main style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        {/* Top bar */}
        <header style={{ height: 56, background: '#13161E', borderBottom: '1px solid #1E2532', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px', flexShrink: 0, position: 'sticky' as const, top: 0, zIndex: 100 }}>
          <div style={{ fontSize: 14, color: '#718096', fontWeight: 500 }}>
            {NAV.find(n => location.pathname.startsWith(n.path))?.label ?? 'Admin Portal'}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ fontSize: 11, color: '#4A5568', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 6, padding: '3px 10px' }}>
              All actions logged
            </div>
            <AdminNotificationBell />
          </div>
        </header>

        {/* Page content */}
        <div style={{ flex: 1, padding: 28, overflowY: 'auto' }}>
          <Outlet />
        </div>
      </main>
    </div>
  )
}
