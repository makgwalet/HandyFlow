import { useState, useEffect, useRef } from 'react'
import { Outlet, useNavigate, NavLink } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Building2, Users, FileText, Package, ChevronLeft, CreditCard,
  Shield, Fuel, HardHat, Car, Settings, Calculator, CalendarCheck,
  HeartPulse, PartyPopper, FilePen, Wallet, Briefcase,
  Bell, User, Lock, LogOut, ChevronDown, X,
  Palette, Headphones, CheckSquare, Megaphone, UserCheck, ShoppingCart,
  Truck, Receipt, UserCog,
} from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuthStore } from '../../store/auth.store'

const MODULE_REGISTRY: Record<string, { icon: React.ElementType; label: string; route: string }> = {
  crm:          { icon: Users,         label: 'Customers',    route: '/customers'    },
  invoicing:    { icon: FileText,      label: 'Invoices',     route: '/invoices'     },
  catalogue:    { icon: Package,       label: 'Catalogue',    route: '/catalogue'    },
  security:     { icon: Shield,        label: 'Security',     route: '/security'     },
  fuel:         { icon: Fuel,          label: 'Fuel',         route: '/fuel'         },
  earthmoving:  { icon: HardHat,       label: 'Earthmoving',  route: '/earthmoving'  },
  property:     { icon: Building2,     label: 'Property',     route: '/property'     },
  fleet:        { icon: Car,           label: 'Fleet',        route: '/fleet'        },
  hr:           { icon: Briefcase,     label: 'HR & Payroll', route: '/hr'           },
  accounting:   { icon: Calculator,    label: 'Accounting',   route: '/accounting'   },
  bookings:     { icon: CalendarCheck, label: 'Bookings',     route: '/bookings'     },
  clinic:       { icon: HeartPulse,    label: 'Clinic',       route: '/clinic'       },
  events:       { icon: PartyPopper,   label: 'Events',       route: '/events'       },
  contracting:  { icon: FilePen,       label: 'Contracts',    route: '/contracts'    },
  expenses:     { icon: Wallet,        label: 'Expenses',     route: '/expenses'     },
  creative:     { icon: Palette,       label: 'Creative',     route: '/creative'     },
  desk:         { icon: Headphones,    label: 'Desk',         route: '/desk'         },
  tasks:        { icon: CheckSquare,   label: 'Tasks',        route: '/tasks'        },
  marketing:    { icon: Megaphone,     label: 'Marketing',    route: '/marketing'    },
  recruiter:    { icon: UserCheck,     label: 'Recruiter',    route: '/recruiter'    },
  pos:          { icon: ShoppingCart,  label: 'POS & Stock',  route: '/pos'          },
  supply_chain: { icon: Truck,         label: 'Supply Chain', route: '/supply-chain' },
  ap:           { icon: Receipt,       label: 'Payables',     route: '/ap'           },
  accountant:   { icon: UserCog,       label: 'Accountant',   route: '/accountant'   },
}

const STATIC_NAV = [
  { icon: FileText,   label: 'Quotes',   route: '/quotes'   },
  { icon: CreditCard, label: 'Billing',  route: '/billing'  },
  { icon: Settings,   label: 'Settings', route: '/settings' },
]

interface TenantModule { moduleKey: string; accessible: boolean }

export function ModuleLayout() {
  const navigate = useNavigate()
  const { user, logout } = useAuthStore()
  const [profileOpen, setProfileOpen] = useState(false)
  const [notifOpen, setNotifOpen]     = useState(false)
  const profileRef = useRef<HTMLDivElement>(null)
  const notifRef   = useRef<HTMLDivElement>(null)

  const { data: tenantModules = [] } = useQuery<TenantModule[]>({
    queryKey: ['tenant-modules-nav'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/billing/modules/mine')
      return r.data || []
    },
    staleTime: 5 * 60 * 1000,
  })

  const CORE_ALWAYS = ['crm', 'catalogue']
  const apiKeys = new Set(tenantModules.filter(m => m.accessible).map(m => m.moduleKey))
  const allActiveKeys = [...CORE_ALWAYS, ...Array.from(apiKeys)]
  const moduleNav = allActiveKeys
    .filter(key => MODULE_REGISTRY[key])
    .map(key => ({ ...MODULE_REGISTRY[key], key }))

  const moduleRoutes = new Set(moduleNav.map(m => m.route))
  const allNav = [
    ...moduleNav,
    ...STATIC_NAV.filter(s => !moduleRoutes.has(s.route)).map(s => ({ ...s, key: s.route })),
  ]

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) setProfileOpen(false)
      if (notifRef.current   && !notifRef.current.contains(e.target as Node))   setNotifOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const initials = `${user?.firstName?.[0] ?? ''}${user?.lastName?.[0] ?? ''}`

  return (
    <div style={{ minHeight: '100vh', background: '#F8FAFC', fontFamily: "'Inter', system-ui, sans-serif" }}>
      <header style={{
        background: '#1B3A6B', height: 56,
        display: 'flex', alignItems: 'center',
        padding: '0 16px', gap: 10,
        boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
        position: 'sticky', top: 0, zIndex: 100,
      }}>
        <button onClick={() => navigate('/dashboard')}
          style={{ display: 'flex', alignItems: 'center', gap: 5, background: 'rgba(255,255,255,0.1)', border: '1px solid rgba(255,255,255,0.15)', borderRadius: 8, padding: '5px 10px', color: 'rgba(255,255,255,0.8)', fontSize: 12, cursor: 'pointer', fontWeight: 600, flexShrink: 0 }}>
          <ChevronLeft size={13} /> Portal
        </button>

        <div style={{ display: 'flex', alignItems: 'center', gap: 7, flexShrink: 0 }}>
          <div style={{ width: 26, height: 26, background: '#0D9488', borderRadius: 6, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Building2 size={14} color="white" strokeWidth={2.5} />
          </div>
          <span style={{ color: 'white', fontWeight: 700, fontSize: 14 }}>HandyFlow</span>
        </div>

        <nav style={{ display: 'flex', alignItems: 'center', gap: 1, flex: 1, overflowX: 'auto', msOverflowStyle: 'none' as any }}>
          <style>{`nav::-webkit-scrollbar{display:none}`}</style>
          {allNav.map(({ icon: Icon, label, route, key }) => (
            <NavLink key={key} to={route}
              style={({ isActive }) => ({
                display: 'flex', alignItems: 'center', gap: 5,
                padding: '5px 9px', borderRadius: 7,
                fontSize: 12, fontWeight: isActive ? 600 : 500,
                textDecoration: 'none', whiteSpace: 'nowrap',
                color: isActive ? 'white' : 'rgba(255,255,255,0.6)',
                background: isActive ? 'rgba(255,255,255,0.15)' : 'transparent',
                transition: 'all 0.12s', flexShrink: 0,
              })}>
              <Icon size={13} />{label}
            </NavLink>
          ))}
        </nav>

        <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexShrink: 0 }}>
          {/* Notifications */}
          <div ref={notifRef} style={{ position: 'relative' }}>
            <button onClick={() => { setNotifOpen(o => !o); setProfileOpen(false) }}
              style={{ position: 'relative', background: notifOpen ? 'rgba(255,255,255,0.12)' : 'none', border: 'none', cursor: 'pointer', padding: '7px', borderRadius: 8, display: 'flex', alignItems: 'center', color: 'rgba(255,255,255,0.7)' }}>
              <Bell size={17} />
              <span style={{ position: 'absolute', top: 5, right: 5, width: 6, height: 6, background: '#EF4444', borderRadius: '50%', border: '1.5px solid #1B3A6B' }} />
            </button>
            {notifOpen && (
              <div style={{ position: 'absolute', top: 'calc(100% + 8px)', right: 0, width: 300, background: 'white', border: '1px solid #E2E8F0', borderRadius: 12, boxShadow: '0 8px 32px rgba(0,0,0,0.12)', zIndex: 200, overflow: 'hidden' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', borderBottom: '1px solid #F1F5F9' }}>
                  <span style={{ fontWeight: 600, fontSize: 14, color: '#0F172A' }}>Notifications</span>
                  <button onClick={() => setNotifOpen(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={15} /></button>
                </div>
                <div style={{ padding: '24px 16px', fontSize: 13, color: '#94A3B8', textAlign: 'center' }}>No new notifications</div>
              </div>
            )}
          </div>

          <div style={{ width: 1, height: 20, background: 'rgba(255,255,255,0.15)' }} />

          {/* Profile */}
          <div ref={profileRef} style={{ position: 'relative' }}>
            <button onClick={() => { setProfileOpen(o => !o); setNotifOpen(false) }}
              style={{ display: 'flex', alignItems: 'center', gap: 7, background: profileOpen ? 'rgba(255,255,255,0.12)' : 'none', border: 'none', cursor: 'pointer', padding: '4px 8px 4px 4px', borderRadius: 8 }}>
              <div style={{ width: 28, height: 28, borderRadius: '50%', background: '#0D9488', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white', fontSize: 11, fontWeight: 700 }}>
                {initials}
              </div>
              <span style={{ color: 'rgba(255,255,255,0.85)', fontSize: 13, fontWeight: 500 }}>{user?.firstName}</span>
              <ChevronDown size={12} color="rgba(255,255,255,0.4)" />
            </button>

            {profileOpen && (
              <div style={{ position: 'absolute', top: 'calc(100% + 8px)', right: 0, width: 230, background: 'white', border: '1px solid #E2E8F0', borderRadius: 12, boxShadow: '0 8px 32px rgba(0,0,0,0.12)', zIndex: 200, overflow: 'hidden' }}>
                <div style={{ padding: '12px 16px', borderBottom: '1px solid #F1F5F9' }}>
                  <div style={{ fontWeight: 600, fontSize: 14, color: '#0F172A' }}>{user?.firstName} {user?.lastName}</div>
                  <div style={{ fontSize: 12, color: '#94A3B8', marginTop: 2 }}>{user?.email}</div>
                </div>
                <div style={{ padding: '4px 0' }}>
                  {[
                    { icon: User,       label: 'Update profile',  fn: () => { navigate('/profile');  setProfileOpen(false) } },
                    { icon: Lock,       label: 'Change password', fn: () => { navigate('/profile');  setProfileOpen(false) } },
                    { icon: CreditCard, label: 'Billing & plan',  fn: () => { navigate('/billing');  setProfileOpen(false) } },
                    { icon: Settings,   label: 'Settings',        fn: () => { navigate('/settings'); setProfileOpen(false) } },
                  ].map(item => (
                    <button key={item.label} onClick={item.fn}
                      style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 10, padding: '9px 16px', border: 'none', background: 'none', cursor: 'pointer', fontSize: 13, color: '#374151', textAlign: 'left' }}
                      onMouseEnter={e => { e.currentTarget.style.background = '#F8FAFC' }}
                      onMouseLeave={e => { e.currentTarget.style.background = 'none' }}>
                      <item.icon size={15} color="#94A3B8" />
                      {item.label}
                    </button>
                  ))}
                  <div style={{ height: 1, background: '#F1F5F9', margin: '4px 0' }} />
                  <button onClick={() => { logout(); navigate('/login') }}
                    style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 10, padding: '9px 16px', border: 'none', background: 'none', cursor: 'pointer', fontSize: 13, color: '#DC2626', textAlign: 'left' }}
                    onMouseEnter={e => { e.currentTarget.style.background = '#FEF2F2' }}
                    onMouseLeave={e => { e.currentTarget.style.background = 'none' }}>
                    <LogOut size={15} color="#DC2626" /> Sign out
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </header>

      <div style={{ padding: '28px 32px', maxWidth: 1200, margin: '0 auto' }}>
        <Outlet />
      </div>
    </div>
  )
}
