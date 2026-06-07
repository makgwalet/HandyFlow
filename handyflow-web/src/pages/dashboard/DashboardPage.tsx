import { useState, useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  Users, FileText, Package, Clock, Bell, Settings,
  ChevronDown, Search, ExternalLink, User, Lock,
  CreditCard, LogOut, X, UserPlus, FilePlus, Plus,
  TrendingUp, ChevronRight, Building2, CheckCircle,
  AlertCircle, Shield, Fuel, HardHat, Car, Briefcase,
  BookOpen, Calculator, Calendar, HeartPulse, PartyPopper, FilePen, Wallet,
  Palette, Headphones, CheckSquare, Megaphone, UserCheck, ShoppingCart,
} from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuthStore } from '../../store/auth.store'
import type { Subscription } from '../../types/billing.types'

interface AppTile {
  key: string
  name: string
  description: string
  icon: React.ElementType
  bg: string
  iconColor: string
  route: string
}

interface Notification {
  id: string
  type: 'warning' | 'info' | 'success'
  text: string
  time: string
  read: boolean
}

const MODULE_REGISTRY: Record<string, AppTile> = {
  crm:         { key: 'crm',         name: 'CRM',          description: 'Customers & contacts',          icon: Users,       bg: '#DBEAFE', iconColor: '#1D4ED8', route: '/customers'   },
  invoicing:   { key: 'invoicing',   name: 'Invoicing',    description: 'Quotes & invoices',             icon: FileText,    bg: '#DCFCE7', iconColor: '#166534', route: '/invoices'    },
  catalogue:   { key: 'catalogue',   name: 'Catalogue',    description: 'Products & services',           icon: Package,     bg: '#F3E8FF', iconColor: '#7C3AED', route: '/catalogue'   },
  security:    { key: 'security',    name: 'Security',     description: 'Guards, sites & QR patrols',   icon: Shield,      bg: '#F0FDF4', iconColor: '#0D9488', route: '/security'    },
  fuel:        { key: 'fuel',        name: 'Fuel',         description: 'Tanks, dispatch & deliveries', icon: Fuel,        bg: '#FEF3C7', iconColor: '#D97706', route: '/fuel'        },
  earthmoving: { key: 'earthmoving', name: 'Earthmoving',  description: 'Assets & operators',           icon: HardHat,     bg: '#FEF9C3', iconColor: '#854D0E', route: '/earthmoving' },
  property:    { key: 'property',    name: 'Property',     description: 'Units, leases & rent',         icon: Building2,   bg: '#EDE9FE', iconColor: '#7C3AED', route: '/property'    },
  fleet:       { key: 'fleet',       name: 'Fleet',        description: 'Vehicles & trips',             icon: Car,         bg: '#E0F2FE', iconColor: '#0369A1', route: '/fleet'       },
  hr:          { key: 'hr',          name: 'HR & Payroll', description: 'Employees & pay runs',         icon: Briefcase,   bg: '#FCE7F3', iconColor: '#9D174D', route: '/hr'          },
  accounting:  { key: 'accounting',  name: 'Accounting',   description: 'Accounts & reports',           icon: Calculator,  bg: '#ECFDF5', iconColor: '#059669', route: '/accounting'  },
  bookings:    { key: 'bookings',    name: 'Bookings',     description: 'Appointments & scheduling',    icon: Calendar,    bg: '#FFF7ED', iconColor: '#EA580C', route: '/bookings'    },
  clinic:      { key: 'clinic',      name: 'Clinic',       description: 'Patients & consultations',     icon: HeartPulse,  bg: '#FFF1F2', iconColor: '#BE123C', route: '/clinic'      },
  events:      { key: 'events',      name: 'Events',       description: 'Ticketing & QR check-in',      icon: PartyPopper, bg: '#F0F9FF', iconColor: '#0284C7', route: '/events'      },
  contracting: { key: 'contracting', name: 'Contracting',  description: 'Contracts & OTP signing',      icon: FilePen,     bg: '#F0F9FF', iconColor: '#0284C7', route: '/contracts'   },
  expenses:    { key: 'expenses',    name: 'Expenses',     description: 'Staff expense claims',         icon: Wallet,      bg: '#FDF4FF', iconColor: '#9333EA', route: '/expenses'    },
  creative:    { key: 'creative',    name: 'Creative',     description: 'Design jobs & proofs',         icon: Palette,      bg: '#FDF4FF', iconColor: '#9333EA', route: '/creative'    },
  desk:        { key: 'desk',        name: 'Desk Support', description: 'Helpdesk & SLA tracking',      icon: Headphones,   bg: '#F0F9FF', iconColor: '#0369A1', route: '/desk'        },
  tasks:       { key: 'tasks',       name: 'Tasks',        description: 'Kanban boards & time logs',    icon: CheckSquare,  bg: '#F0FDF4', iconColor: '#059669', route: '/tasks'       },
  marketing:   { key: 'marketing',   name: 'Marketing',    description: 'Email campaigns & contacts',   icon: Megaphone,    bg: '#FFF7ED', iconColor: '#EA580C', route: '/marketing'   },
  recruiter:   { key: 'recruiter',   name: 'Recruiter',    description: 'Jobs, pipeline & hiring',      icon: UserCheck,    bg: '#ECFDF5', iconColor: '#059669', route: '/recruiter'   },
  pos:         { key: 'pos',         name: 'POS & Stock',  description: 'Point of sale & inventory',    icon: ShoppingCart, bg: '#EFF6FF', iconColor: '#2563EB', route: '/pos'         },
  accountant: { key: 'accountant',   name: 'Accountant',   description: 'Clients, SARS & billing',      icon: BookOpen,     bg: '#EFF6FF', iconColor: '#1B3A6B', route: '/accountant' },
}

const MOCK_NOTIFICATIONS: Notification[] = [
  { id: '1', type: 'warning', text: 'Pilot ends in 57 days — upgrade to keep access to all your data', time: 'Today, 08:00', read: false },
  { id: '2', type: 'success', text: 'Quote QT-00001 was successfully converted to an invoice', time: 'Yesterday, 09:05', read: false },
  { id: '3', type: 'info', text: 'New customer Acme Construction added to CRM', time: '2 days ago', read: true },
]

const QUICK_ACTIONS = [
  { label: 'Add customer', sub: 'CRM', icon: UserPlus, bg: '#DBEAFE', color: '#1D4ED8', route: '/customers' },
  { label: 'New quote', sub: 'Invoicing', icon: FilePlus, bg: '#DCFCE7', color: '#166534', route: '/quotes' },
  { label: 'Add catalogue item', sub: 'Catalogue', icon: Plus, bg: '#F3E8FF', color: '#7C3AED', route: '/catalogue' },
]

function AppTileCard({ app, onClick }: { app: AppTile; onClick: () => void }) {
  const [hovered, setHovered] = useState(false)
  return (
    <button
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        background: hovered ? '#FAFBFF' : 'white',
        border: `1.5px solid ${hovered ? '#1B3A6B' : '#E8EDF5'}`,
        borderRadius: 16,
        padding: '28px 16px 20px',
        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10,
        cursor: 'pointer', position: 'relative',
        transform: hovered ? 'translateY(-3px)' : 'translateY(0)',
        boxShadow: hovered
          ? '0 8px 24px rgba(27,58,107,0.12)'
          : '0 1px 4px rgba(0,0,0,0.04)',
        transition: 'all 0.18s ease',
        textAlign: 'center', width: '100%',
      }}
    >
      {hovered && (
        <ExternalLink size={12} color="#94A3B8"
          style={{ position: 'absolute', top: 12, right: 12 }} />
      )}
      <div style={{
        width: 56, height: 56, borderRadius: 16,
        background: app.bg,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        boxShadow: `0 2px 8px ${app.bg}`,
      }}>
        <app.icon size={26} color={app.iconColor} />
      </div>
      <div>
        <p style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', margin: '0 0 3px' }}>
          {app.name}
        </p>
        <p style={{ fontSize: 12, color: '#94A3B8', margin: 0, lineHeight: 1.4 }}>
          {app.description}
        </p>
      </div>
    </button>
  )
}

export function DashboardPage() {
  const navigate = useNavigate()
  const { user, logout } = useAuthStore()
  const [notifOpen, setNotifOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const [notifications, setNotifications] = useState(MOCK_NOTIFICATIONS)
  const notifRef = useRef<HTMLDivElement>(null)
  const profileRef = useRef<HTMLDivElement>(null)

  const unreadCount = notifications.filter(n => !n.read).length

  useEffect(() => {
    function handler(e: MouseEvent) {
      if (notifRef.current && !notifRef.current.contains(e.target as Node)) setNotifOpen(false)
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) setProfileOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const { data: subscription } = useQuery<Subscription>({
    queryKey: ['subscription'],
    queryFn: async () => (await apiClient.get('/api/v1/billing/subscription')).data,
  })

  // Live stats queries
const { data: customersData } = useQuery({
  queryKey: ['dashboard-customers'],
  queryFn: async () => {
    const res = await apiClient.get('/api/v1/crm/customers?size=100')
    return res.data.content as { id: string; createdAt: string }[]
  },
})

const { data: quotesData } = useQuery({
  queryKey: ['dashboard-quotes'],
  queryFn: async () => {
    const res = await apiClient.get('/api/v1/invoicing/quotes?size=100')
    return res.data.content as { id: string; status: string; total: number }[]
  },
})

const { data: invoicesData } = useQuery({
  queryKey: ['dashboard-invoices'],
  queryFn: async () => {
    const res = await apiClient.get('/api/v1/invoicing/invoices?size=100')
    return res.data.content as { id: string; status: string; total: number; createdAt: string }[]
  },
})

// Computed stats
const now          = new Date()
const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1)

const customers    = customersData || []
const quotes       = quotesData    || []
const invoices     = invoicesData  || []

const customerCount     = customers.length
const customersThisMonth = customers.filter(c =>
  new Date(c.createdAt) >= startOfMonth
).length

const activeQuotes   = quotes.filter(q => ['DRAFT','SENT'].includes(q.status)).length
const invoicedQuotes = quotes.filter(q => q.status === 'INVOICED').length

const revenueMTD = invoices
  .filter(i => i.status === 'PAID' && new Date(i.createdAt) >= startOfMonth)
  .reduce((s, i) => s + (i.total || 0), 0)

const totalQuoted = quotes
  .reduce((s, q) => s + (q.total || 0), 0)

  const { data: tenantModules = [] } = useQuery<{moduleKey: string; accessible: boolean}[]>({
    queryKey: ['tenant-modules-dashboard'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/billing/modules/mine')
      return r.data || []
    },
    staleTime: 5 * 60 * 1000,
  })

  // CRM and Catalogue are always active (core, not in modules/mine)
  const CORE_ALWAYS = ['crm', 'catalogue']
  const apiModuleKeys = new Set(tenantModules.filter(m => m.accessible).map(m => m.moduleKey))
  const allActiveKeys = [...CORE_ALWAYS, ...Array.from(apiModuleKeys)]
  const activeApps = allActiveKeys
    .filter(key => MODULE_REGISTRY[key])
    .map(key => MODULE_REGISTRY[key])

  const today = new Date().toLocaleDateString('en-ZA', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  })

  const stats = [
    {
      label: 'Customers',
      value: customerCount,
      sub: customersThisMonth > 0 ? `↑ ${customersThisMonth} this month` : 'No new this month',
      positive: customersThisMonth > 0,
      icon: Users, bg: '#EFF6FF', iconColor: '#2563EB',
    },
    {
      label: 'Active quotes',
      value: activeQuotes,
      sub: invoicedQuotes > 0 ? `${invoicedQuotes} invoiced` : 'None invoiced yet',
      icon: FileText, bg: '#F0FDF4', iconColor: '#16A34A',
    },
    {
      label: 'Revenue MTD',
      value: revenueMTD > 0
        ? `R ${(revenueMTD / 1000).toFixed(0)}K`
        : 'R 0',
      sub: totalQuoted > 0
        ? `R ${(totalQuoted / 1000).toFixed(0)}K quoted`
        : 'No quotes yet',
      icon: TrendingUp, bg: '#FEFCE8', iconColor: '#CA8A04',
    },
    {
      label: 'Pilot days left',
      value: subscription?.pilotDaysRemaining ?? '—',
      sub: subscription?.pilotEndsAt
        ? `Ends ${new Date(subscription.pilotEndsAt).toLocaleDateString('en-ZA')}`
        : 'Loading...',
      icon: Clock, bg: '#FFF1F2', iconColor: '#BE123C',
    },
  ]

  return (
    <div style={{ minHeight: '100vh', background: '#F1F5F9', fontFamily: "'Inter', system-ui, sans-serif" }}>

      {/* ═══ TOP BAR ═══ */}
      <header style={{
        background: '#1B3A6B',
        height: 60,
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 32px',
        position: 'sticky', top: 0, zIndex: 100,
        boxShadow: '0 2px 12px rgba(0,0,0,0.2)',
      }}>

        {/* Logo + Search */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{
              width: 34, height: 34, background: '#0D9488',
              borderRadius: 9, display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 2px 8px rgba(13,148,136,0.4)',
            }}>
              <Building2 size={18} color="white" strokeWidth={2.5} />
            </div>
            <span style={{ color: 'white', fontWeight: 700, fontSize: 17, letterSpacing: '-0.3px' }}>
              HandyFlow
            </span>
          </div>

          <div style={{
            display: 'flex', alignItems: 'center', gap: 8,
            background: 'rgba(255,255,255,0.08)',
            border: '1px solid rgba(255,255,255,0.12)',
            borderRadius: 10, padding: '7px 14px',
            width: 240, cursor: 'text',
            transition: 'border-color 0.15s',
          }}>
            <Search size={13} color="rgba(255,255,255,0.35)" />
            <span style={{ color: 'rgba(255,255,255,0.3)', fontSize: 13 }}>Search anything...</span>
          </div>
        </div>

        {/* Right controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>

          {/* Bell */}
          <div ref={notifRef} style={{ position: 'relative' }}>
            <button
              onClick={() => { setNotifOpen(o => !o); setProfileOpen(false) }}
              style={{
                background: notifOpen ? 'rgba(255,255,255,0.12)' : 'none',
                border: 'none', cursor: 'pointer',
                color: 'rgba(255,255,255,0.7)',
                padding: '8px 10px', borderRadius: 9,
                display: 'flex', alignItems: 'center',
                transition: 'all 0.15s',
                position: 'relative',
              }}
              onMouseEnter={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.1)'; e.currentTarget.style.color = 'white' }}
              onMouseLeave={e => { if (!notifOpen) { e.currentTarget.style.background = 'none'; e.currentTarget.style.color = 'rgba(255,255,255,0.7)' } }}
            >
              <Bell size={20} />
              {unreadCount > 0 && (
                <span style={{
                  position: 'absolute', top: 4, right: 5,
                  width: 17, height: 17, background: '#EF4444',
                  borderRadius: '50%', fontSize: 10, color: 'white',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  border: '2px solid #1B3A6B', fontWeight: 700,
                }}>
                  {unreadCount}
                </span>
              )}
            </button>

            {/* Notification Panel */}
            {notifOpen && (
              <div style={{
                position: 'absolute', top: 'calc(100% + 10px)', right: 0,
                width: 360, background: 'white',
                border: '1px solid #E2E8F0', borderRadius: 16,
                boxShadow: '0 12px 40px rgba(0,0,0,0.15)',
                zIndex: 200, overflow: 'hidden',
              }}>
                <div style={{
                  padding: '16px 20px', borderBottom: '1px solid #F1F5F9',
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontWeight: 700, fontSize: 15, color: '#0F172A' }}>Notifications</span>
                    {unreadCount > 0 && (
                      <span style={{
                        background: '#EFF6FF', color: '#1D4ED8',
                        fontSize: 11, fontWeight: 700, padding: '2px 8px', borderRadius: 20,
                      }}>
                        {unreadCount} new
                      </span>
                    )}
                  </div>
                  <button onClick={() => setNotifOpen(false)}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', padding: 4, borderRadius: 6 }}>
                    <X size={16} />
                  </button>
                </div>

                <div>
                  {notifications.map(n => {
                    const dotColors = { warning: '#F59E0B', success: '#10B981', info: '#3B82F6' }
                    return (
                      <div key={n.id}
                        onClick={() => setNotifications(prev => prev.map(x => x.id === n.id ? { ...x, read: true } : x))}
                        style={{
                          display: 'flex', gap: 12, padding: '14px 20px',
                          borderBottom: '1px solid #F8FAFC',
                          background: n.read ? 'white' : '#F8FBFF',
                          cursor: 'pointer', transition: 'background 0.1s',
                          alignItems: 'flex-start',
                        }}
                        onMouseEnter={e => e.currentTarget.style.background = '#F8FAFC'}
                        onMouseLeave={e => e.currentTarget.style.background = n.read ? 'white' : '#F8FBFF'}
                      >
                        <span style={{
                          width: 8, height: 8, borderRadius: '50%',
                          background: dotColors[n.type], flexShrink: 0, marginTop: 5,
                          display: 'inline-block',
                        }} />
                        <div style={{ flex: 1 }}>
                          <p style={{ fontSize: 13, color: '#1E293B', lineHeight: 1.5, margin: '0 0 3px' }}>{n.text}</p>
                          <p style={{ fontSize: 11, color: '#94A3B8', margin: 0 }}>{n.time}</p>
                        </div>
                        {!n.read && (
                          <span style={{ width: 7, height: 7, background: '#3B82F6', borderRadius: '50%', flexShrink: 0, marginTop: 6 }} />
                        )}
                      </div>
                    )
                  })}
                </div>

                {/* Quick Actions inside panel */}
                <div style={{ padding: '14px 20px', borderTop: '1px solid #F1F5F9', background: '#FAFBFF' }}>
                  <p style={{ fontSize: 10, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.08em', margin: '0 0 10px' }}>
                    Quick actions
                  </p>
                  {QUICK_ACTIONS.map(qa => (
                    <button key={qa.label}
                      onClick={() => { setNotifOpen(false); navigate(qa.route) }}
                      style={{
                        width: '100%', display: 'flex', alignItems: 'center', gap: 10,
                        padding: '9px 10px', borderRadius: 9, border: 'none',
                        background: 'none', cursor: 'pointer', textAlign: 'left',
                        transition: 'background 0.1s',
                      }}
                      onMouseEnter={e => e.currentTarget.style.background = 'white'}
                      onMouseLeave={e => e.currentTarget.style.background = 'none'}
                    >
                      <div style={{
                        width: 32, height: 32, borderRadius: 8,
                        background: qa.bg, display: 'flex', alignItems: 'center', justifyContent: 'center',
                      }}>
                        <qa.icon size={15} color={qa.color} />
                      </div>
                      <div>
                        <p style={{ fontSize: 13, color: '#0F172A', margin: 0, fontWeight: 600 }}>{qa.label}</p>
                        <p style={{ fontSize: 11, color: '#94A3B8', margin: 0 }}>{qa.sub}</p>
                      </div>
                      <ChevronRight size={14} color="#CBD5E1" style={{ marginLeft: 'auto' }} />
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Divider */}
          <div style={{ width: 1, height: 22, background: 'rgba(255,255,255,0.12)', margin: '0 4px' }} />

          {/* Avatar */}
          <div ref={profileRef} style={{ position: 'relative' }}>
            <button
              onClick={() => { setProfileOpen(o => !o); setNotifOpen(false) }}
              style={{
                display: 'flex', alignItems: 'center', gap: 9,
                background: profileOpen ? 'rgba(255,255,255,0.12)' : 'none',
                border: '1px solid rgba(255,255,255,0)',
                borderRadius: 10, padding: '5px 10px 5px 6px',
                cursor: 'pointer', transition: 'all 0.15s',
              }}
              onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.1)'}
              onMouseLeave={e => { if (!profileOpen) e.currentTarget.style.background = 'none' }}
            >
              <div style={{
                width: 32, height: 32, borderRadius: '50%', background: '#0D9488',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: 'white', fontSize: 12, fontWeight: 700,
                boxShadow: '0 2px 6px rgba(13,148,136,0.4)',
              }}>
                {user?.firstName?.[0]}{user?.lastName?.[0]}
              </div>
              <span style={{ color: 'rgba(255,255,255,0.9)', fontSize: 14, fontWeight: 500 }}>
                {user?.firstName}
              </span>
              <ChevronDown size={14} color="rgba(255,255,255,0.4)" />
            </button>

            {profileOpen && (
              <div style={{
                position: 'absolute', top: 'calc(100% + 10px)', right: 0,
                width: 260, background: 'white',
                border: '1px solid #E2E8F0', borderRadius: 16,
                boxShadow: '0 12px 40px rgba(0,0,0,0.15)',
                zIndex: 200, overflow: 'hidden',
              }}>
                <div style={{ padding: '16px 18px', borderBottom: '1px solid #F1F5F9' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
                    <div style={{
                      width: 40, height: 40, borderRadius: '50%', background: '#1B3A6B',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      color: 'white', fontSize: 14, fontWeight: 700,
                    }}>
                      {user?.firstName?.[0]}{user?.lastName?.[0]}
                    </div>
                    <div>
                      <p style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                        {user?.firstName} {user?.lastName}
                      </p>
                      <p style={{ fontSize: 11, color: '#94A3B8', margin: '2px 0 0' }}>{user?.email}</p>
                    </div>
                  </div>
                  <div style={{
                    background: '#F1F5F9', borderRadius: 6,
                    padding: '3px 10px', fontSize: 11, color: '#64748B',
                    display: 'inline-block',
                  }}>
                    Essential · Pilot
                  </div>
                </div>

                <div style={{ padding: '6px 0' }}>
                  {[
                    { icon: User, label: 'Update profile', action: () => navigate('/profile') },
                    { icon: Lock, label: 'Change password', action: () => navigate('/profile') },
                    { icon: CreditCard, label: 'Billing & plan', action: () => navigate('/billing') },
                    { icon: Settings, label: 'Settings', action: () => navigate('/settings') },
                  ].map(item => (
                    <button key={item.label}
                      onClick={() => { item.action?.(); setProfileOpen(false) }}
                      style={{
                        width: '100%', display: 'flex', alignItems: 'center', gap: 10,
                        padding: '10px 18px', border: 'none', background: 'none',
                        cursor: 'pointer', fontSize: 13, color: '#374151', textAlign: 'left',
                      }}
                      onMouseEnter={e => e.currentTarget.style.background = '#F8FAFC'}
                      onMouseLeave={e => e.currentTarget.style.background = 'none'}
                    >
                      <item.icon size={16} color="#94A3B8" />
                      {item.label}
                    </button>
                  ))}
                  <div style={{ height: 1, background: '#F1F5F9', margin: '4px 0' }} />
                  <button
                    onClick={() => { logout(); navigate('/login') }}
                    style={{
                      width: '100%', display: 'flex', alignItems: 'center', gap: 10,
                      padding: '10px 18px', border: 'none', background: 'none',
                      cursor: 'pointer', fontSize: 13, color: '#DC2626', textAlign: 'left',
                    }}
                    onMouseEnter={e => e.currentTarget.style.background = '#FEF2F2'}
                    onMouseLeave={e => e.currentTarget.style.background = 'none'}
                  >
                    <LogOut size={16} color="#DC2626" />
                    Sign out
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* ═══ PAGE CONTENT ═══ */}
      <main style={{ maxWidth: 1100, margin: '0 auto', padding: '36px 32px' }}>

        {/* Welcome row */}
        <div style={{
          display: 'flex', alignItems: 'flex-start',
          justifyContent: 'space-between', marginBottom: 28,
        }}>
          <div>
            <h1 style={{ fontSize: 26, fontWeight: 800, color: '#0F172A', margin: '0 0 6px', letterSpacing: '-0.5px' }}>
              Good morning, {user?.firstName} 👋
            </h1>
            <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>
              {today}
              {subscription && <> &nbsp;·&nbsp; <strong style={{ color: '#64748B' }}>{subscription.planDisplayName} plan</strong></>}
            </p>
          </div>

          {subscription?.status === 'PILOT' && (
            <div style={{
              display: 'flex', alignItems: 'center', gap: 10,
              background: 'linear-gradient(135deg, #FFFBEB, #FEF3C7)',
              border: '1px solid #FCD34D',
              borderRadius: 24, padding: '8px 16px 8px 12px',
              boxShadow: '0 2px 8px rgba(245,158,11,0.15)',
            }}>
              <div style={{
                width: 28, height: 28, background: '#F59E0B',
                borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Clock size={15} color="white" strokeWidth={2.5} />
              </div>
              <div>
                <p style={{ fontSize: 12, fontWeight: 700, color: '#92400E', margin: 0 }}>
                  {subscription.pilotDaysRemaining} days left in pilot
                </p>
                <p style={{ fontSize: 11, color: '#B45309', margin: 0 }}>
                  Ends {new Date(subscription.pilotEndsAt!).toLocaleDateString('en-ZA')}
                </p>
              </div>
              <button
                onClick={() => navigate('/billing')}
                style={{
                  background: '#1B3A6B', color: 'white', border: 'none',
                  borderRadius: 8, padding: '5px 12px', fontSize: 12, fontWeight: 700,
                  cursor: 'pointer', marginLeft: 4,
                }}
              >
                Upgrade
              </button>
            </div>
          )}
        </div>

        {/* Stats Grid */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14, marginBottom: 36 }}>
          {stats.map(s => (
            <div key={s.label} style={{
              background: 'white', border: '1px solid #E8EDF5',
              borderRadius: 16, padding: '20px 20px',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              boxShadow: '0 1px 6px rgba(0,0,0,0.04)',
              transition: 'box-shadow 0.15s',
            }}>
              <div>
                <p style={{ fontSize: 12, color: '#94A3B8', margin: '0 0 6px', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                  {s.label}
                </p>
                <p style={{ fontSize: 28, fontWeight: 800, color: '#0F172A', margin: '0 0 4px', letterSpacing: '-0.5px' }}>
                  {s.value}
                </p>
                <p style={{ fontSize: 12, margin: 0, color: s.positive ? '#059669' : '#94A3B8', fontWeight: s.positive ? 600 : 400 }}>
                  {s.sub}
                </p>
              </div>
              <div style={{
                width: 48, height: 48, borderRadius: 14,
                background: s.bg, display: 'flex', alignItems: 'center', justifyContent: 'center',
                flexShrink: 0,
              }}>
                <s.icon size={22} color={s.iconColor} strokeWidth={2} />
              </div>
            </div>
          ))}
        </div>

        {/* Your Apps */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
          <div>
            <p style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.08em', margin: '0 0 2px' }}>
              Your apps
            </p>
            <p style={{ fontSize: 13, color: '#64748B', margin: 0 }}>
              {activeApps.length} active {activeApps.length === 1 ? 'app' : 'apps'} on your plan
            </p>
          </div>
          <button
            onClick={() => navigate('/billing')}
            style={{
              background: 'none', border: '1px solid #E2E8F0', cursor: 'pointer',
              fontSize: 13, color: '#1B3A6B', fontWeight: 600,
              padding: '7px 14px', borderRadius: 9, transition: 'all 0.15s',
            }}
            onMouseEnter={e => { e.currentTarget.style.background = '#EFF6FF'; e.currentTarget.style.borderColor = '#1B3A6B' }}
            onMouseLeave={e => { e.currentTarget.style.background = 'none'; e.currentTarget.style.borderColor = '#E2E8F0' }}
          >
            Explore more apps →
          </button>
        </div>

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(5, 1fr)',
          gap: 14, marginBottom: 36,
        }}>
          {activeApps.map(app => (
            <AppTileCard key={app.key} app={app} onClick={() => navigate(app.route)} />
          ))}
        </div>

        {/* Subscription Summary */}
        {subscription && (
          <div style={{
            background: 'white', border: '1px solid #E8EDF5',
            borderRadius: 16, padding: '20px 24px',
            boxShadow: '0 1px 6px rgba(0,0,0,0.04)',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
              <p style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                Current subscription
              </p>
              <span style={{
                background: subscription.status === 'PILOT' ? '#FEF3C7' : '#DCFCE7',
                color: subscription.status === 'PILOT' ? '#92400E' : '#166534',
                fontSize: 11, fontWeight: 700, padding: '4px 12px',
                borderRadius: 20, letterSpacing: '0.04em',
              }}>
                {subscription.status}
              </span>
            </div>

            <div style={{
              display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)',
              gap: 16, paddingTop: 16, borderTop: '1px solid #F1F5F9',
            }}>
              {[
                { label: 'Plan', value: subscription.planDisplayName },
                { label: 'Monthly price', value: `R ${subscription.priceInRands}/month` },
                { label: 'Period ends', value: new Date(subscription.currentPeriodEnd).toLocaleDateString('en-ZA') },
              ].map(item => (
                <div key={item.label}>
                  <p style={{ fontSize: 11, color: '#94A3B8', margin: '0 0 4px', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                    {item.label}
                  </p>
                  <p style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                    {item.value}
                  </p>
                </div>
              ))}
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
