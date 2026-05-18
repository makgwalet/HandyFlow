import { Outlet, useNavigate, NavLink } from 'react-router-dom'
import { Building2, Users, FileText, Package, ChevronLeft } from 'lucide-react'
import { useAuthStore } from '../../store/auth.store'

const NAV = [
  { to: '/customers', icon: Users, label: 'Customers' },
  { to: '/quotes', icon: FileText, label: 'Quotes' },
  { to: '/catalogue', icon: Package, label: 'Catalogue' },
]

export function ModuleLayout() {
  const navigate = useNavigate()
  const { user } = useAuthStore()

  return (
    <div style={{ minHeight: '100vh', background: '#F8FAFC', fontFamily: "'Inter', system-ui, sans-serif" }}>

      {/* Top Bar */}
      <header style={{
        background: '#1B3A6B',
        height: 56,
        display: 'flex', alignItems: 'center',
        padding: '0 24px', gap: 16,
        boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
        position: 'sticky', top: 0, zIndex: 50,
      }}>
        {/* Back to portal */}
        <button
          onClick={() => navigate('/dashboard')}
          style={{
            display: 'flex', alignItems: 'center', gap: 6,
            background: 'rgba(255,255,255,0.1)',
            border: '1px solid rgba(255,255,255,0.15)',
            borderRadius: 8, padding: '5px 10px',
            color: 'rgba(255,255,255,0.8)', fontSize: 12,
            cursor: 'pointer', transition: 'all 0.15s',
            fontWeight: 500,
          }}
          onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.18)'}
          onMouseLeave={e => e.currentTarget.style.background = 'rgba(255,255,255,0.1)'}
        >
          <ChevronLeft size={14} />
          Portal
        </button>

        {/* Logo */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ width: 28, height: 28, background: '#0D9488', borderRadius: 7, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Building2 size={15} color="white" strokeWidth={2.5} />
          </div>
          <span style={{ color: 'white', fontWeight: 700, fontSize: 15 }}>HandyFlow</span>
        </div>

        {/* Module Nav */}
        <nav style={{ display: 'flex', alignItems: 'center', gap: 2, marginLeft: 8 }}>
          {NAV.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              style={({ isActive }) => ({
                display: 'flex', alignItems: 'center', gap: 6,
                padding: '6px 12px', borderRadius: 8,
                fontSize: 13, fontWeight: 500, textDecoration: 'none',
                color: isActive ? 'white' : 'rgba(255,255,255,0.6)',
                background: isActive ? 'rgba(255,255,255,0.15)' : 'none',
                transition: 'all 0.15s',
              })}
            >
              <Icon size={15} />
              {label}
            </NavLink>
          ))}
        </nav>

        {/* Avatar */}
        <div style={{
          marginLeft: 'auto', width: 30, height: 30,
          borderRadius: '50%', background: '#0D9488',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'white', fontSize: 11, fontWeight: 700,
        }}>
          {user?.firstName?.[0]}{user?.lastName?.[0]}
        </div>
      </header>

      {/* Page Content */}
      <div style={{ padding: '28px 32px', maxWidth: 1200, margin: '0 auto' }}>
        <Outlet />
      </div>
    </div>
  )
}
