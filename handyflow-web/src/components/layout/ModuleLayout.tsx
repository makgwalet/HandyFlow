import { useState, useEffect, useRef } from 'react'
import { Outlet, useNavigate, useLocation, NavLink } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Building2, Users, FileText, Package, ChevronLeft, CreditCard,
  Shield, Fuel, HardHat, Car, Settings, Calculator, CalendarCheck,
  HeartPulse, PartyPopper, FilePen, Wallet, Briefcase,
  Bell, User, Lock, LogOut, ChevronDown,
  Palette, Headphones, CheckSquare, Megaphone, UserCheck, ShoppingCart,
  Truck, Receipt, UserCog,
} from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuthStore } from '../../store/auth.store'
import { ModuleSwitcher } from './ModuleSwitcher'
import type { ModuleNavItem } from './ModuleSwitcher'
import { NotificationDrawer } from './NotificationDrawer'

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

// Always reachable regardless of subscription — not part of the pin/switcher
// system, since Settings especially shouldn't ever be one extra click away
// behind a search box.
const STATIC_NAV = [
  { icon: FileText,   label: 'Quotes',   route: '/quotes'   },
  { icon: CreditCard, label: 'Billing',  route: '/billing'  },
  { icon: Settings,   label: 'Settings', route: '/settings' },
]

const PINNED_MODULES_KEY = 'handyflow-pinned-modules'

interface TenantModule { moduleKey: string; accessible: boolean }

export function ModuleLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout } = useAuthStore()
  const [profileOpen, setProfileOpen] = useState(false)
  const [notifDrawerOpen, setNotifDrawerOpen] = useState(false)

  // FIX: "implement the notification APIs" — the bell previously always
  // showed a hardcoded red dot regardless of actual state. Wired to the
  // real GET /api/v1/notifications/unread-count, independent of whether
  // the drawer is open, so the badge is accurate on page load. The
  // drawer's own mutations (markRead/markAllRead) invalidate this same
  // query key so the badge updates the moment something's read, without
  // waiting for the 60s poll.
  const { data: unreadData } = useQuery<{ unreadCount: number }>({
    queryKey: ['notifications-unread-count'],
    queryFn: async () => (await apiClient.get('/api/v1/notifications/unread-count')).data,
    refetchInterval: 60_000,
  })
  const unreadCount = unreadData?.unreadCount ?? 0
  const profileRef = useRef<HTMLDivElement>(null)

  // FIX: "navbar cluttered with so many modules" — previously every
  // subscribed module rendered as an inline pill with no cap, becoming an
  // unusable horizontal-scroll strip once past ~8 modules (confirmed via
  // screenshot showing 20+ crammed in). Pinning is client-side only for
  // now (localStorage) — there's no confirmed backend endpoint for
  // per-user layout preferences, so this won't follow the user across
  // devices until one exists. Worth adding a real
  // GET/PUT /api/v1/users/me/preferences endpoint if that matters; this
  // is the honest interim.
  const [pinnedKeys, setPinnedKeys] = useState<string[]>(() => {
    try {
      const raw = localStorage.getItem(PINNED_MODULES_KEY)
      return raw ? JSON.parse(raw) : []
    } catch { return [] }
  })

  useEffect(() => {
    try { localStorage.setItem(PINNED_MODULES_KEY, JSON.stringify(pinnedKeys)) } catch { /* storage unavailable — pinning just won't persist */ }
  }, [pinnedKeys])

  const togglePin = (key: string) => {
    setPinnedKeys(prev => prev.includes(key) ? prev.filter(k => k !== key) : [...prev, key])
  }

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
  const moduleNav: ModuleNavItem[] = allActiveKeys
    .filter((key, i, arr) => MODULE_REGISTRY[key] && arr.indexOf(key) === i)
    .map(key => ({ ...MODULE_REGISTRY[key], key }))

  // Pinned modules that are still actually subscribed — a module could be
  // pinned, then the subscription lapses; don't render a dead pill for it.
  const validPinnedKeys = pinnedKeys.filter(k => moduleNav.some(m => m.key === k))
  const pinnedNav = validPinnedKeys.map(k => moduleNav.find(m => m.key === k)!)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) setProfileOpen(false)
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

        <div style={{ width: 1, height: 22, background: 'rgba(255,255,255,0.15)', flexShrink: 0 }} />

        <ModuleSwitcher modules={moduleNav} pinnedKeys={pinnedKeys} onTogglePin={togglePin} currentPath={location.pathname} />

        <nav style={{ display: 'flex', alignItems: 'center', gap: 1, flex: 1, overflowX: 'auto', msOverflowStyle: 'none' as any }}>
          <style>{`nav::-webkit-scrollbar{display:none}`}</style>
          {pinnedNav.map(({ icon: Icon, label, route, key }) => (
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

          {pinnedNav.length > 0 && <div style={{ width: 1, height: 18, background: 'rgba(255,255,255,0.15)', margin: '0 6px', flexShrink: 0 }} />}

          {STATIC_NAV.map(({ icon: Icon, label, route }) => (
            <NavLink key={route} to={route}
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
          {/* FIX: "implement in-app notifications" — was a hardcoded
              "No new notifications" dropdown with no backend call. Now a
              real slide-out drawer — see NotificationDrawer.tsx for the
              honest caveat on the endpoint paths it assumes. */}
          <button onClick={() => setNotifDrawerOpen(true)}
            style={{ position: 'relative', background: notifDrawerOpen ? 'rgba(255,255,255,0.12)' : 'none', border: 'none', cursor: 'pointer', padding: '7px', borderRadius: 8, display: 'flex', alignItems: 'center', color: 'rgba(255,255,255,0.7)' }}>
            <Bell size={17} />
            {unreadCount > 0 && (
              <span style={{ position: 'absolute', top: 4, right: 4, minWidth: 14, height: 14, padding: '0 3px', background: '#EF4444', borderRadius: 7, border: '1.5px solid #1B3A6B', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 700, color: 'white' }}>
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}          </button>

          <div style={{ width: 1, height: 20, background: 'rgba(255,255,255,0.15)' }} />

          {/* Profile */}
          <div ref={profileRef} style={{ position: 'relative' }}>
            <button onClick={() => setProfileOpen(o => !o)}
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

      <NotificationDrawer open={notifDrawerOpen} onClose={() => setNotifDrawerOpen(false)} />

      <div style={{ padding: '28px 32px', maxWidth: 1200, margin: '0 auto' }}>
        <Outlet />
      </div>
    </div>
  )
}
