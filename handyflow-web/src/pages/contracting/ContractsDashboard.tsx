// src/pages/contracting/ContractsDashboard.tsx
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  FileText, Clock, CheckCircle, AlertTriangle,
  ArrowRight, Calendar, TrendingUp, XCircle,
} from 'lucide-react'
import { unwrapOne, fmtR, type ContractDashboard } from './contractingApi'
import { registerLink, type ViewKey } from './contractRegister'

const fmtDate = (d: string | null | undefined) =>
  d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'

const daysUntil = (d: string | null | undefined) => d == null ? 0 :
  Math.ceil((new Date(d).getTime() - Date.now()) / 86_400_000)

const TYPE_COLOR: Record<string, string> = {
  SERVICE_AGREEMENT: 'var(--hf-accent-text)', NDA: 'var(--hf-violet-text)', EMPLOYMENT: 'var(--hf-info-text)',
  JOINT_VENTURE: 'var(--hf-warning-text)', EQUIPMENT_HIRE: 'var(--hf-orange-text)', LEASE: 'var(--hf-success-text-strong)',
  SUBCONTRACTOR: 'var(--hf-danger-text)', SERVICE_LEVEL: 'var(--hf-sky-text)', CONSULTING: 'var(--hf-pink-text)',
  RETAINER: 'var(--hf-warning-text-deep)', SUPPLY: 'var(--hf-text-tertiary)', ACKNOWLEDGMENT_OF_DEBT: 'var(--hf-warning-text-strong)', OTHER: 'var(--hf-text-muted)',
}

const TYPE_LABEL: Record<string, string> = {
  SERVICE_AGREEMENT: 'Service Agreement', NDA: 'NDA', EMPLOYMENT: 'Employment',
  JOINT_VENTURE: 'Joint Venture', EQUIPMENT_HIRE: 'Equipment Hire', LEASE: 'Lease',
  SUBCONTRACTOR: 'Subcontractor', SERVICE_LEVEL: 'SLA', CONSULTING: 'Consulting',
  RETAINER: 'Retainer', SUPPLY: 'Supply Agreement', ACKNOWLEDGMENT_OF_DEBT: 'Acknowledgment of Debt', OTHER: 'Other',
}

export default function ContractsDashboard({ onNavigate }: { onNavigate: (t: string) => void }) {
  // The figures are calculated on the server, so the page no longer loads and counts every contract in the browser.
  const { data: dash } = useQuery<ContractDashboard>({
    queryKey: ['contracts', 'dashboard'],
    queryFn: async () => unwrapOne<ContractDashboard>(await apiClient.get('/api/v1/contracts/dashboard')),
  })

  const statusCounts: Record<string, number> = dash?.statusCounts ?? {}
  const count = (status: string) => statusCounts[status] ?? 0
  const signed     = { length: count('SIGNED') }
  const pending    = dash?.awaitingSignature ?? []
  const pendingCount = count('SENT')
  const draft      = { length: count('DRAFT') }
  const review     = { length: count('UNDER_REVIEW') }
  const terminated = { length: count('TERMINATED') }
  const expired    = { length: count('EXPIRED') }
  const totalContracts: number = dash?.totalContracts ?? 0

  const totalActiveVal = Number(dash?.activeValue) || 0
  const pendingVal     = Number(dash?.pendingValue) || 0

  // Expiring in 30 days (soonest first, from the server)
  const expiringSoon = dash?.expiringSoon ?? []

  // Type breakdown
  const typeEntries = Object.entries((dash?.typeCounts ?? {}) as Record<string, number>).sort((a, b) => b[1] - a[1])

  // Pipeline stages
  type Stage = { label: string; count: number; color: string; Icon: typeof FileText; view: ViewKey }
  const STAGES = ([
    { label: 'Draft',        count: draft.length,      color: 'var(--hf-text-muted)', Icon: FileText, view: 'drafts' },
    { label: 'Under Review', count: review.length,     color: 'var(--hf-warning-text)', Icon: Clock, view: 'review' },
    { label: 'Pending Sign', count: pendingCount,    color: 'var(--hf-info-text)', Icon: Clock, view: 'awaiting' },
    { label: 'Signed',       count: signed.length,     color: 'var(--hf-success-text-strong)', Icon: CheckCircle, view: 'active' },
    { label: 'Terminated',   count: terminated.length, color: 'var(--hf-danger-text)', Icon: XCircle, view: 'terminated' },
    { label: 'Expired',      count: expired.length,    color: 'var(--hf-text-faint)', Icon: AlertTriangle, view: 'expired' },
  ] as Stage[]).filter(s => s.count > 0 || ['Draft', 'Pending Sign', 'Signed'].includes(s.label))

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div>
      {/* KPI row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 28 }}>
        {([
          { label: 'Active contracts',      value: signed.length,     color: 'var(--hf-success-text-strong)', bg: 'var(--hf-success-soft-strong)', view: 'active' },
          { label: 'Awaiting signature',    value: pendingCount,    color: 'var(--hf-info-text)', bg: 'var(--hf-info-soft)', view: 'awaiting' },
          { label: 'Active contract value', value: fmtR(totalActiveVal), color: 'var(--hf-primary-text)', bg: 'var(--hf-indigo-soft)', view: 'active' },
          { label: 'Pending value',         value: fmtR(pendingVal),  color: 'var(--hf-warning-text)', bg: 'var(--hf-warning-soft)', view: 'awaiting' },
        ] as { label: string; value: number | string; color: string; bg: string; view: ViewKey }[]).map(k => (
          <button key={k.label} onClick={() => onNavigate(registerLink(k.view))} title="Open these contracts in the register"
            style={{ all: 'unset', display: 'block', boxSizing: 'border-box', cursor: 'pointer', background: k.bg, borderRadius: 12, padding: '15px 18px' }}>
            <div style={{ fontSize: typeof k.value === 'number' ? 26 : 20, fontWeight: 800, color: k.color }}>
              {k.value}
            </div>
            <div style={{ fontSize: 11, color: k.color, marginTop: 3, opacity: 0.8 }}>{k.label}</div>
          </button>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 280px', gap: 22 }}>

        {/* Left column */}
        <div>

          {/* Pipeline */}
          <div style={{ marginBottom: 26 }}>
            <div style={{ fontWeight: 700, fontSize: 14, color: 'var(--hf-text)', marginBottom: 14 }}>Contract Pipeline</div>
            <div style={{ display: 'flex', gap: 6, alignItems: 'stretch', overflowX: 'auto' }}>
              {STAGES.map((s, i) => {
                const { Icon } = s
                return (
                  <div key={s.label} style={{ display: 'flex', alignItems: 'center' }}>
                    <button onClick={() => onNavigate(registerLink(s.view))} title={`Open ${s.label.toLowerCase()} contracts`} style={{ all: 'unset', display: 'block', boxSizing: 'border-box', cursor: 'pointer', minWidth: 90, background: `color-mix(in srgb, ${s.color} 7%, transparent)`, border: `1px solid color-mix(in srgb, ${s.color} 19%, transparent)`, borderRadius: 10, padding: '14px 10px', textAlign: 'center' }}>
                      <Icon size={15} style={{ color: s.color, marginBottom: 6 }} />
                      <div style={{ fontSize: 22, fontWeight: 800, color: s.color }}>{s.count}</div>
                      <div style={{ fontSize: 10, color: s.color, opacity: 0.8, marginTop: 2, lineHeight: 1.3 }}>
                        {s.label}
                      </div>
                    </button>
                    {i < STAGES.length - 1 && (
                      <div style={{ color: 'var(--hf-text-disabled)', fontSize: 16, margin: '0 2px', flexShrink: 0 }}>→</div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>

          {/* Expiring soon */}
          <div style={{ marginBottom: 26 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                <Calendar size={14} style={{ color: 'var(--hf-warning-text)' }} />
                <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--hf-text)' }}>Expiring within 30 days</span>
                {expiringSoon.length > 0 && (
                  <span style={{ background: 'var(--hf-warning-soft-strong)', color: 'var(--hf-warning-text)', padding: '1px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                    {expiringSoon.length}
                  </span>
                )}
              </div>
              {expiringSoon.length > 3 && (
                <button onClick={() => onNavigate(registerLink('expiring'))} style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--hf-primary-text)', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 600 }}>
                  View all <ArrowRight size={12} />
                </button>
              )}
            </div>
            {expiringSoon.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '22px 20px', border: '1px dashed var(--hf-border)', borderRadius: 10, color: 'var(--hf-text-faint)', fontSize: 13 }}>
                No contracts expiring in the next 30 days ✓
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                {expiringSoon.slice(0, 5).map(c => {
                  const days   = daysUntil(c.endDate)
                  const urgent = days <= 7
                  return (
                    <div key={c.id} style={{
                      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                      padding: '11px 16px',
                      border: `1px solid ${urgent ? 'var(--hf-danger-border)' : 'var(--hf-warning-border)'}`,
                      borderLeft: `3px solid ${urgent ? 'var(--hf-danger)' : 'var(--hf-warning)'}`,
                      borderRadius: 8,
                      background: urgent ? 'var(--hf-danger-soft)' : 'var(--hf-warning-soft)',
                    }}>
                      <div>
                        <div style={{ fontWeight: 600, fontSize: 13, color: 'var(--hf-text)' }}>{c.title}</div>
                        <div style={{ fontSize: 11, color: 'var(--hf-text-muted)' }}>
                          {c.contractNumber} · expires {fmtDate(c.endDate)}
                          {c.autoRenew && <span style={{ marginLeft: 6, color: 'var(--hf-accent-text)', fontWeight: 600 }}>↻ auto-renew</span>}
                        </div>
                      </div>
                      <div style={{ textAlign: 'right', flexShrink: 0, marginLeft: 12 }}>
                        <div style={{ fontWeight: 800, fontSize: 15, color: urgent ? 'var(--hf-danger-text)' : 'var(--hf-warning-text)' }}>{days}d</div>
                        <div style={{ fontSize: 10, color: 'var(--hf-text-faint)' }}>remaining</div>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          {/* Awaiting signature */}
          {pendingCount > 0 && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                  <Clock size={14} style={{ color: 'var(--hf-info-text)' }} />
                  <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--hf-text)' }}>Awaiting signature</span>
                </div>
                <button onClick={() => onNavigate(registerLink('awaiting'))} style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--hf-primary-text)', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 600 }}>
                  Manage <ArrowRight size={12} />
                </button>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                {pending.slice(0, 4).map(c => {
                  const unsigned = (c.totalPartyCount ?? 0) - (c.signedPartyCount ?? 0)
                  const total    = c.totalPartyCount ?? 0
                  return (
                    <div key={c.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '11px 16px', border: '1px solid var(--hf-info-border)', borderLeft: '3px solid var(--hf-info)', borderRadius: 8, background: 'var(--hf-surface-muted)' }}>
                      <div>
                        <div style={{ fontWeight: 600, fontSize: 13, color: 'var(--hf-text)' }}>{c.title}</div>
                        <div style={{ fontSize: 11, color: 'var(--hf-text-muted)' }}>
                          {c.contractNumber}
                          {total > 0 && ` · ${unsigned} of ${total} yet to sign`}
                        </div>
                      </div>
                      {(c.valueAmount ?? 0) > 0 && (
                        <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--hf-info-text)', flexShrink: 0, marginLeft: 12 }}>
                          {fmtR(c.valueAmount)}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>
          )}
        </div>

        {/* Right sidebar */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>

          {/* Type breakdown */}
          <div style={{ background: 'var(--hf-surface-muted)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 14 }}>
              <TrendingUp size={13} style={{ color: 'var(--hf-primary-text)' }} />
              <span style={{ fontWeight: 700, fontSize: 13, color: 'var(--hf-text)' }}>By contract type</span>
            </div>
            {typeEntries.length === 0 ? (
              <div style={{ fontSize: 13, color: 'var(--hf-text-faint)' }}>No contracts yet</div>
            ) : typeEntries.map(([type, count]) => {
              const color = TYPE_COLOR[type] ?? 'var(--hf-text-muted)'
              const pct   = Math.round((count / Math.max(totalContracts, 1)) * 100)
              return (
                <div key={type} style={{ marginBottom: 10 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4, fontSize: 12 }}>
                    <span style={{ color: 'var(--hf-text-tertiary)', fontWeight: 500 }}>{TYPE_LABEL[type] ?? type.replace(/_/g, ' ')}</span>
                    <span style={{ color, fontWeight: 700 }}>{count}</span>
                  </div>
                  <div style={{ height: 4, background: 'var(--hf-surface-strong)', borderRadius: 99, overflow: 'hidden' }}>
                    <div style={{ height: '100%', width: `${pct}%`, background: color, borderRadius: 99, transition: 'width 0.3s' }} />
                  </div>
                </div>
              )
            })}
          </div>

          {/* Quick actions */}
          <div>
            <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>
              Quick actions
            </div>
            {[
              { label: 'New contract',     tab: 'contracts', color: 'var(--hf-primary-text)' },
              { label: 'Browse templates', tab: 'templates', color: 'var(--hf-accent-text)' },
            ].map(a => (
              <button key={a.label} onClick={() => onNavigate(a.tab)} style={{
                width: '100%', marginBottom: 7,
                padding: '10px 14px', background: 'var(--hf-surface)',
                border: '1px solid var(--hf-border)', borderRadius: 9,
                fontSize: 13, fontWeight: 600, color: a.color,
                cursor: 'pointer', textAlign: 'left',
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              }}>
                {a.label} <ArrowRight size={13} />
              </button>
            ))}
          </div>

          {/* ECT Act compliance note */}
          <div style={{ padding: '13px 15px', background: 'var(--hf-info-soft)', border: '1px solid var(--hf-info-border)', borderRadius: 10 }}>
            <div style={{ fontWeight: 700, fontSize: 11, color: 'var(--hf-info-text)', marginBottom: 6 }}>ECT Act Compliance</div>
            <div style={{ fontSize: 11, color: 'var(--hf-info-text-strong)', lineHeight: 1.6 }}>
              Electronic signatures are legally binding under the Electronic Communications
              and Transactions Act 25 of 2002, s 13. OTP signing with IP, timestamp, and
              phone audit trail is admissible as evidence.
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
