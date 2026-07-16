import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Check, Zap, Clock, AlertTriangle, X, Plus,
  Shield, Fuel, HardHat, Car, Briefcase, Calculator,
  CalendarCheck, HeartPulse, PartyPopper, FileText,
  Package, Wallet, FilePen, Users, Building2,
} from 'lucide-react'

// ── Types ─────────────────────────────────────────────────────────────────────
interface Subscription {
  id: string; planName: string; planDisplayName: string
  status: 'PILOT' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED'
  pilotEndsAt?: string; pilotDaysRemaining?: number
  currentPeriodEnd: string; priceInRands: number
}
interface TenantModule {
  id: string; moduleKey: string; moduleName: string; description: string
  monthlyPrice: number; status: string; trialEndsAt: string | null
  activatedAt: string; accessible: boolean
}
interface CatalogueModule {
  id: string; key: string; name: string; description: string
  monthlyPrice: number; currency: string; category: string; sortOrder: number
}
interface CancelPreview {
  moduleKey: string; moduleName: string
  affectedRecords: number; message: string; accessUntil: string
}

// ── Icon registry ─────────────────────────────────────────────────────────────
const MODULE_ICONS: Record<string, React.ElementType> = {
  crm: Users, invoicing: FileText, catalogue: Package,
  security: Shield, fuel: Fuel, earthmoving: HardHat,
  property: Building2, fleet: Car, hr: Briefcase,
  accounting: Calculator, bookings: CalendarCheck,
  clinic: HeartPulse, events: PartyPopper,
  contracting: FilePen, expenses: Wallet,
}
const MODULE_COLORS: Record<string, { bg: string; color: string }> = {
  crm:         { bg: '#DBEAFE', color: '#1D4ED8' },
  invoicing:   { bg: '#DCFCE7', color: '#166534' },
  catalogue:   { bg: '#F3E8FF', color: '#7C3AED' },
  security:    { bg: '#F0FDF4', color: '#0D9488' },
  fuel:        { bg: '#FEF3C7', color: '#D97706' },
  earthmoving: { bg: '#FEF9C3', color: '#854D0E' },
  property:    { bg: '#EDE9FE', color: '#7C3AED' },
  fleet:       { bg: '#E0F2FE', color: '#0369A1' },
  hr:          { bg: '#FCE7F3', color: '#9D174D' },
  accounting:  { bg: '#ECFDF5', color: '#059669' },
  bookings:    { bg: '#FFF7ED', color: '#EA580C' },
  clinic:      { bg: '#FFF1F2', color: '#BE123C' },
  events:      { bg: '#FFFBEB', color: '#D97706' },
  contracting: { bg: '#F0F9FF', color: '#0284C7' },
  expenses:    { bg: '#FDF4FF', color: '#9333EA' },
}
const CORE_KEYS = ['crm', 'invoicing', 'catalogue']

function fmtDate(d: string) {
  return new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })
}
function trialDaysLeft(endsAt: string) {
  return Math.max(0, Math.ceil((new Date(endsAt).getTime() - Date.now()) / 86400000))
}

export function BillingPage() {
  const qc = useQueryClient()
  const [tab, setTab]                   = useState<'modules' | 'subscription'>('modules')
  const [confirmCancel, setConfirmCancel] = useState<CancelPreview | null>(null)
  // NEW: was previously fired immediately on click with zero confirmation
  // — replaced with a confirm-before-add modal, matching the existing
  // confirm-before-remove (cancel-preview) flow's own pattern.
  const [confirmActivate, setConfirmActivate] = useState<CatalogueModule | null>(null)

  const { data: subscription } = useQuery<Subscription>({
    queryKey: ['subscription'],
    queryFn: async () => (await apiClient.get('/api/v1/billing/subscription')).data,
  })

  const { data: tenantModules = [], isLoading: loadingMine } = useQuery<TenantModule[]>({
    queryKey: ['tenant-modules'],
    queryFn: async () => (await apiClient.get('/api/v1/billing/modules/mine')).data,
  })

  const { data: catalogue = [] } = useQuery<CatalogueModule[]>({
    queryKey: ['module-catalogue'],
    queryFn: async () => (await apiClient.get('/api/v1/billing/modules')).data,
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['tenant-modules'] })
    qc.invalidateQueries({ queryKey: ['tenant-modules-nav'] })
    qc.invalidateQueries({ queryKey: ['tenant-modules-dashboard'] })
  }

  const activate = useMutation({
    mutationFn: (moduleKey: string) =>
      apiClient.post('/api/v1/billing/modules/activate', { moduleKey }),
    onSuccess: () => { invalidate(); setConfirmActivate(null) },
    onError: (e: any) => alert(e.response?.data?.message || 'Failed to add app'),
  })

  const fetchPreview = useMutation({
    mutationFn: (moduleKey: string) =>
      apiClient.get(`/api/v1/billing/modules/${moduleKey}/cancel-preview`),
    onSuccess: (res) => setConfirmCancel(res.data),
  })

  const cancelModule = useMutation({
    mutationFn: (moduleKey: string) =>
      apiClient.delete(`/api/v1/billing/modules/${moduleKey}`),
    onSuccess: () => { invalidate(); setConfirmCancel(null) },
    onError: (e: any) => alert(e.response?.data?.message || 'Failed to remove app'),
  })

  // Active module keys set
  const activeKeys = new Set(tenantModules.map(m => m.moduleKey))
  const tenantModuleMap = Object.fromEntries(tenantModules.map(m => [m.moduleKey, m]))

  // Catalogue grouped by category, excluding core
  const availableToAdd = catalogue.filter(m => !activeKeys.has(m.key) && !CORE_KEYS.includes(m.key))
  const grouped = availableToAdd.reduce((acc, m) => {
    if (!acc[m.category]) acc[m.category] = []
    acc[m.category].push(m)
    return acc
  }, {} as Record<string, CatalogueModule[]>)

  const monthlyTotal = tenantModules.reduce((s, m) => s + (m.monthlyPrice || 0), 0)

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif", maxWidth: 960, margin: '0 auto' }}>

      {/* Header */}
      <div style={{ marginBottom: 28 }}>
        <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', margin: '0 0 4px' }}>Billing & Apps</h1>
        <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>Manage your active apps and subscription</p>
      </div>

      {/* Subscription banner */}
      {subscription && (
        <div style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 14, padding: '20px 24px', marginBottom: 28, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', gap: 24 }}>
            {[
              { label: 'Status', value: subscription.status },
              { label: 'Plan',   value: subscription.planDisplayName },
              { label: 'Active apps', value: `${tenantModules.length + CORE_KEYS.length}` },
              { label: 'After trial', value: `R ${monthlyTotal.toLocaleString('en-ZA')}/mo` },
              ...(subscription.status === 'PILOT' && subscription.pilotDaysRemaining != null
                ? [{ label: 'Pilot ends', value: `${subscription.pilotDaysRemaining} days left` }]
                : []),
            ].map(({ label, value }) => (
              <div key={label}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase' as const, letterSpacing: '0.05em', marginBottom: 3 }}>{label}</div>
                <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>{value}</div>
              </div>
            ))}
          </div>
          {subscription.status === 'PILOT' && (
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 38, fontWeight: 900, color: '#F59E0B', lineHeight: 1 }}>{subscription.pilotDaysRemaining}</div>
              <div style={{ fontSize: 11, color: '#94A3B8', fontWeight: 600 }}>DAYS LEFT</div>
            </div>
          )}
        </div>
      )}

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid #E2E8F0', marginBottom: 24 }}>
        {[
          { id: 'modules' as const,      label: 'My Apps' },
          { id: 'subscription' as const, label: 'Add Apps' },
        ].map(t => (
          <button key={t.id} onClick={() => setTab(t.id)}
            style={{ padding: '10px 20px', background: 'none', border: 'none', borderBottom: tab === t.id ? '2px solid #0D9488' : '2px solid transparent', color: tab === t.id ? '#0D9488' : '#64748B', fontWeight: tab === t.id ? 700 : 400, fontSize: 14, cursor: 'pointer', marginBottom: -1 }}>
            {t.label}
            {t.id === 'modules' && <span style={{ marginLeft: 6, background: '#F1F5F9', color: '#64748B', padding: '1px 7px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{tenantModules.length + CORE_KEYS.length}</span>}
            {t.id === 'subscription' && availableToAdd.length > 0 && <span style={{ marginLeft: 6, background: '#EFF6FF', color: '#1D4ED8', padding: '1px 7px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{availableToAdd.length} available</span>}
          </button>
        ))}
      </div>

      {/* ── My Modules tab ──────────────────────────────────────────── */}
      {tab === 'modules' && (
        <div>
          {/* Core modules — always free */}
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.06em', marginBottom: 12 }}>CORE — ALWAYS INCLUDED FREE</div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10 }}>
              {CORE_KEYS.map(key => {
                const Icon = MODULE_ICONS[key] || Package
                const c = MODULE_COLORS[key] || { bg: '#F8FAFC', color: '#64748B' }
                const names: Record<string, string> = { crm: 'CRM', invoicing: 'Invoicing', catalogue: 'Catalogue' }
                const descs: Record<string, string> = { crm: 'Customers & contacts', invoicing: 'Quotes & invoices', catalogue: 'Products & services' }
                return (
                  <div key={key} style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 12, padding: '16px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                      <div style={{ width: 34, height: 34, borderRadius: 8, background: c.bg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <Icon size={16} color={c.color} />
                      </div>
                      <div>
                        <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>{names[key]}</div>
                        <div style={{ fontSize: 12, color: '#94A3B8' }}>{descs[key]}</div>
                      </div>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: 12, fontWeight: 700, color: '#166534' }}>Included free</span>
                      <span style={{ background: '#DCFCE7', color: '#166534', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>FREE</span>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          {/* Active paid apps */}
          <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.06em', marginBottom: 12 }}>
            ACTIVE APPS ({tenantModules.length})
          </div>

          {loadingMine ? (
            <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading apps...</div>
          ) : tenantModules.length === 0 ? (
            <div style={{ background: 'white', border: '2px dashed #E2E8F0', borderRadius: 14, padding: '40px', textAlign: 'center', color: '#94A3B8' }}>
              <Zap size={32} style={{ marginBottom: 10, opacity: 0.3 }} />
              <div style={{ fontWeight: 600, color: '#475569', marginBottom: 4 }}>No additional apps yet</div>
              <div style={{ fontSize: 13, marginBottom: 16 }}>Add industry or business apps to unlock more features.</div>
              <button onClick={() => setTab('subscription')}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '9px 18px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
                <Plus size={15} /> Browse apps
              </button>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 12 }}>
              {tenantModules.map(m => {
                const Icon = MODULE_ICONS[m.moduleKey] || Package
                const c    = MODULE_COLORS[m.moduleKey] || { bg: '#F8FAFC', color: '#64748B' }
                const isTrial   = m.status === 'TRIAL'
                const daysLeft  = isTrial && m.trialEndsAt ? trialDaysLeft(m.trialEndsAt) : null
                const isExpiring = daysLeft != null && daysLeft <= 14

                return (
                  <div key={m.moduleKey} style={{ background: 'white', border: `1px solid ${isExpiring ? '#FCD34D' : '#E2E8F0'}`, borderRadius: 12, padding: '16px', position: 'relative' }}>
                    <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 10 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <div style={{ width: 36, height: 36, borderRadius: 9, background: c.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                          <Icon size={17} color={c.color} />
                        </div>
                        <div>
                          <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>{m.moduleName}</div>
                          <div style={{ fontSize: 11, color: '#94A3B8' }}>{m.description}</div>
                        </div>
                      </div>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>
                          R {m.monthlyPrice}/mo
                        </div>
                        {isTrial && m.trialEndsAt && (
                          <div style={{ fontSize: 11, color: isExpiring ? '#D97706' : '#64748B', marginTop: 2, display: 'flex', alignItems: 'center', gap: 4 }}>
                            {isExpiring && <AlertTriangle size={11} />}
                            {daysLeft}d trial left · until {fmtDate(m.trialEndsAt)}
                          </div>
                        )}
                        {m.status === 'ACTIVE' && (
                          <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>Active · renews {fmtDate(m.activatedAt)}</div>
                        )}
                      </div>
                      <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                        <span style={{
                          background: isTrial ? '#FEF3C7' : '#DCFCE7',
                          color:      isTrial ? '#92400E' : '#166534',
                          padding: '3px 9px', borderRadius: 20, fontSize: 11, fontWeight: 600,
                        }}>{m.status}</span>
                        <button
                          onClick={() => fetchPreview.mutate(m.moduleKey)}
                          disabled={fetchPreview.isPending}
                          title="Remove app"
                          style={{ background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 7, padding: '5px 8px', cursor: 'pointer', color: '#DC2626', display: 'flex' }}>
                          <X size={13} />
                        </button>
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {/* ── Add Modules tab ─────────────────────────────────────────── */}
      {tab === 'subscription' && (
        <div>
          {availableToAdd.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
              <Check size={40} style={{ marginBottom: 12, color: '#0D9488', opacity: 0.6 }} />
              <div style={{ fontWeight: 600, color: '#475569', marginBottom: 4 }}>All apps are active</div>
              <div style={{ fontSize: 13 }}>You have access to every available app.</div>
            </div>
          ) : (
            Object.entries(grouped).sort().map(([category, mods]) => (
              <div key={category} style={{ marginBottom: 28 }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.06em', marginBottom: 12 }}>
                  {category.toUpperCase()}
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 12 }}>
                  {mods.sort((a, b) => a.sortOrder - b.sortOrder).map(m => {
                    const Icon = MODULE_ICONS[m.key] || Package
                    const c    = MODULE_COLORS[m.key] || { bg: '#F8FAFC', color: '#64748B' }
                    return (
                      <div key={m.key} style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 12, padding: '16px' }}>
                        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10, marginBottom: 12 }}>
                          <div style={{ width: 36, height: 36, borderRadius: 9, background: c.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                            <Icon size={17} color={c.color} />
                          </div>
                          <div>
                            <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>{m.name}</div>
                            <div style={{ fontSize: 12, color: '#94A3B8', lineHeight: 1.4 }}>{m.description}</div>
                          </div>
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <div>
                            <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>R {Number(m.monthlyPrice).toLocaleString('en-ZA')}/mo</div>
                            <div style={{ fontSize: 11, color: '#0D9488', fontWeight: 500 }}>60-day free trial</div>
                          </div>
                          <button
                            onClick={() => setConfirmActivate(m)}
                            style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '8px 14px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
                            <Plus size={13} /> Add
                          </button>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* ── Cancel preview modal ────────────────────────────────────── */}
      {confirmCancel && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: 'white', borderRadius: 16, padding: 28, width: 460, boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>Remove {confirmCancel.moduleName}?</h3>
              <button onClick={() => setConfirmCancel(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
            </div>

            {/* Access until */}
            <div style={{ padding: '12px 16px', background: '#FFFBEB', border: '1px solid #FCD34D', borderRadius: 10, marginBottom: 14 }}>
              <div style={{ display: 'flex', gap: 8 }}>
                <Clock size={15} color="#D97706" style={{ marginTop: 1, flexShrink: 0 }} />
                <div>
                  <div style={{ fontWeight: 600, fontSize: 13, color: '#92400E' }}>Access continues until {fmtDate(confirmCancel.accessUntil)}</div>
                  <div style={{ fontSize: 12, color: '#B45309', marginTop: 2 }}>You won't lose access immediately — billing stops at end of period.</div>
                </div>
              </div>
            </div>

            {/* Affected records */}
            {confirmCancel.affectedRecords > 0 && (
              <div style={{ padding: '12px 16px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 10, marginBottom: 14 }}>
                <div style={{ display: 'flex', gap: 8 }}>
                  <AlertTriangle size={15} color="#DC2626" style={{ marginTop: 1, flexShrink: 0 }} />
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13, color: '#DC2626' }}>{confirmCancel.affectedRecords} records will become inaccessible</div>
                    <div style={{ fontSize: 12, color: '#B91C1C', marginTop: 2 }}>{confirmCancel.message}</div>
                  </div>
                </div>
              </div>
            )}

            {confirmCancel.affectedRecords === 0 && (
              <div style={{ padding: '12px 16px', background: '#F0FDF4', border: '1px solid #BBF7D0', borderRadius: 10, marginBottom: 14 }}>
                <div style={{ display: 'flex', gap: 8 }}>
                  <Check size={15} color="#16A34A" style={{ marginTop: 1 }} />
                  <div style={{ fontSize: 13, color: '#166534' }}>No data will be affected. You can reactivate this app at any time.</div>
                </div>
              </div>
            )}

            <div style={{ fontSize: 13, color: '#64748B', marginBottom: 20 }}>
              {confirmCancel.message}
            </div>

            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => setConfirmCancel(null)}
                style={{ padding: '10px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: 'white', fontSize: 14, cursor: 'pointer', color: '#374151' }}>
                Keep app
              </button>
              <button
                onClick={() => cancelModule.mutate(confirmCancel.moduleKey)}
                disabled={cancelModule.isPending}
                style={{ padding: '10px 20px', background: '#DC2626', color: 'white', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: 'pointer' }}>
                {cancelModule.isPending ? 'Removing...' : 'Yes, remove app'}
              </button>
            </div>
          </div>
        </div>
      )}
      {/* ── Confirm add modal ───────────────────────────────────────── */}
      {/* NEW: "Add" previously fired the activation immediately on click
          with no confirmation step at all — this closes that gap,
          matching the confirm-before-remove modal above. */}
      {confirmActivate && (() => {
        const Icon = MODULE_ICONS[confirmActivate.key] || Package
        const c    = MODULE_COLORS[confirmActivate.key] || { bg: '#F8FAFC', color: '#64748B' }
        return (
          <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
            <div style={{ background: 'white', borderRadius: 16, padding: 28, width: 440, boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 18 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{ width: 40, height: 40, borderRadius: 10, background: c.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <Icon size={19} color={c.color} />
                  </div>
                  <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>Add {confirmActivate.name}?</h3>
                </div>
                <button onClick={() => setConfirmActivate(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
              </div>

              <div style={{ padding: '12px 16px', background: '#F0FDF4', border: '1px solid #BBF7D0', borderRadius: 10, marginBottom: 16 }}>
                <div style={{ display: 'flex', gap: 8 }}>
                  <Zap size={15} color="#16A34A" style={{ marginTop: 1, flexShrink: 0 }} />
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13, color: '#166534' }}>60 days free, then R {Number(confirmActivate.monthlyPrice).toLocaleString('en-ZA')}/mo</div>
                    <div style={{ fontSize: 12, color: '#15803D', marginTop: 2 }}>
                      You won't be charged until the trial ends. Remove it any time before then at no cost.
                    </div>
                  </div>
                </div>
              </div>

              <div style={{ fontSize: 13, color: '#64748B', marginBottom: 20, lineHeight: 1.5 }}>
                {confirmActivate.description}
              </div>

              <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
                <button onClick={() => setConfirmActivate(null)}
                  style={{ padding: '10px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: 'white', fontSize: 14, cursor: 'pointer', color: '#374151' }}>
                  Cancel
                </button>
                <button
                  onClick={() => activate.mutate(confirmActivate.key)}
                  disabled={activate.isPending}
                  style={{ padding: '10px 20px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: 'pointer' }}>
                  {activate.isPending ? 'Adding...' : 'Yes, add app'}
                </button>
              </div>
            </div>
          </div>
        )
      })()}
    </div>
  )
}
