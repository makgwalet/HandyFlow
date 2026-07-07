// src/pages/contracting/ContractsDashboard.tsx
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  FileText, Clock, CheckCircle, AlertTriangle,
  ArrowRight, Calendar, TrendingUp, XCircle,
} from 'lucide-react'
import { unwrap, fmtR } from './ContractingPage'

const fmtDate = (d: any) =>
  d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'

const daysUntil = (d: string) =>
  Math.ceil((new Date(d).getTime() - Date.now()) / 86_400_000)

const TYPE_COLOR: Record<string, string> = {
  SERVICE_AGREEMENT: '#0D9488', NDA: '#7C3AED', EMPLOYMENT: '#1D4ED8',
  JOINT_VENTURE: '#D97706', EQUIPMENT_HIRE: '#EA580C', LEASE: '#166534',
  SUBCONTRACTOR: '#DC2626', SERVICE_LEVEL: '#0891B2', CONSULTING: '#DB2777',
  RETAINER: '#854D0E', SUPPLY: '#475569', OTHER: '#64748B',
}

const TYPE_LABEL: Record<string, string> = {
  SERVICE_AGREEMENT: 'Service Agreement', NDA: 'NDA', EMPLOYMENT: 'Employment',
  JOINT_VENTURE: 'Joint Venture', EQUIPMENT_HIRE: 'Equipment Hire', LEASE: 'Lease',
  SUBCONTRACTOR: 'Subcontractor', SERVICE_LEVEL: 'SLA', CONSULTING: 'Consulting',
  RETAINER: 'Retainer', SUPPLY: 'Supply Agreement', OTHER: 'Other',
}

export default function ContractsDashboard({ onNavigate }: { onNavigate: (t: any) => void }) {
  const { data: contracts = [] } = useQuery<any[]>({
    queryKey: ['contracts', 'all'],
    queryFn: async () => unwrap(await apiClient.get('/api/v1/contracts?size=200')),
  })

  const cs         = contracts as any[]
  const signed     = cs.filter(c => c.status === 'SIGNED')
  const pending    = cs.filter(c => c.status === 'SENT')
  const draft      = cs.filter(c => c.status === 'DRAFT')
  const review     = cs.filter(c => c.status === 'UNDER_REVIEW')
  const terminated = cs.filter(c => c.status === 'TERMINATED')
  const expired    = cs.filter(c => c.status === 'EXPIRED')

  const totalActiveVal = signed.reduce((s, c) => s + (Number(c.valueAmount) || 0), 0)
  const pendingVal     = pending.reduce((s, c) => s + (Number(c.valueAmount) || 0), 0)

  // Expiring in 30 days
  const expiringSoon = signed
    .filter(c => c.endDate && daysUntil(c.endDate) >= 0 && daysUntil(c.endDate) <= 30)
    .sort((a, b) => daysUntil(a.endDate) - daysUntil(b.endDate))

  // Type breakdown
  const typeMap: Record<string, number> = {}
  cs.forEach(c => { typeMap[c.contractType] = (typeMap[c.contractType] || 0) + 1 })
  const typeEntries = Object.entries(typeMap).sort((a, b) => b[1] - a[1])

  // Pipeline stages
  const STAGES = [
    { label: 'Draft',        count: draft.length,      color: '#64748B', Icon: FileText     },
    { label: 'Under Review', count: review.length,     color: '#D97706', Icon: Clock        },
    { label: 'Pending Sign', count: pending.length,    color: '#1D4ED8', Icon: Clock        },
    { label: 'Signed',       count: signed.length,     color: '#166534', Icon: CheckCircle  },
    { label: 'Terminated',   count: terminated.length, color: '#DC2626', Icon: XCircle      },
    { label: 'Expired',      count: expired.length,    color: '#94A3B8', Icon: AlertTriangle },
  ].filter(s => s.count > 0 || ['Draft', 'Pending Sign', 'Signed'].includes(s.label))

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div>
      {/* KPI row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 28 }}>
        {[
          { label: 'Active contracts',      value: signed.length,     color: '#166534', bg: '#DCFCE7' },
          { label: 'Awaiting signature',    value: pending.length,    color: '#1D4ED8', bg: '#EFF6FF' },
          { label: 'Active contract value', value: fmtR(totalActiveVal), color: '#1B3A6B', bg: '#EEF2FF' },
          { label: 'Pending value',         value: fmtR(pendingVal),  color: '#D97706', bg: '#FFFBEB' },
        ].map(k => (
          <div key={k.label} style={{ background: k.bg, borderRadius: 12, padding: '15px 18px' }}>
            <div style={{ fontSize: typeof k.value === 'number' ? 26 : 20, fontWeight: 800, color: k.color }}>
              {k.value}
            </div>
            <div style={{ fontSize: 11, color: k.color, marginTop: 3, opacity: 0.8 }}>{k.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 280px', gap: 22 }}>

        {/* Left column */}
        <div>

          {/* Pipeline */}
          <div style={{ marginBottom: 26 }}>
            <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A', marginBottom: 14 }}>Contract Pipeline</div>
            <div style={{ display: 'flex', gap: 6, alignItems: 'stretch', overflowX: 'auto' }}>
              {STAGES.map((s, i) => {
                const { Icon } = s
                return (
                  <div key={s.label} style={{ display: 'flex', alignItems: 'center' }}>
                    <div style={{ minWidth: 90, background: `${s.color}12`, border: `1px solid ${s.color}30`, borderRadius: 10, padding: '14px 10px', textAlign: 'center' }}>
                      <Icon size={15} color={s.color} style={{ marginBottom: 6 }} />
                      <div style={{ fontSize: 22, fontWeight: 800, color: s.color }}>{s.count}</div>
                      <div style={{ fontSize: 10, color: s.color, opacity: 0.8, marginTop: 2, lineHeight: 1.3 }}>
                        {s.label}
                      </div>
                    </div>
                    {i < STAGES.length - 1 && (
                      <div style={{ color: '#CBD5E1', fontSize: 16, margin: '0 2px', flexShrink: 0 }}>→</div>
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
                <Calendar size={14} color="#D97706" />
                <span style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>Expiring within 30 days</span>
                {expiringSoon.length > 0 && (
                  <span style={{ background: '#FEF3C7', color: '#D97706', padding: '1px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                    {expiringSoon.length}
                  </span>
                )}
              </div>
              {expiringSoon.length > 3 && (
                <button onClick={() => onNavigate('contracts')} style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#1B3A6B', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 600 }}>
                  View all <ArrowRight size={12} />
                </button>
              )}
            </div>
            {expiringSoon.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '22px 20px', border: '1px dashed #E2E8F0', borderRadius: 10, color: '#94A3B8', fontSize: 13 }}>
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
                      border: `1px solid ${urgent ? '#FECACA' : '#FDE68A'}`,
                      borderLeft: `3px solid ${urgent ? '#DC2626' : '#D97706'}`,
                      borderRadius: 8,
                      background: urgent ? '#FFF5F5' : '#FFFBEB',
                    }}>
                      <div>
                        <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>{c.title}</div>
                        <div style={{ fontSize: 11, color: '#64748B' }}>
                          {c.contractNumber} · expires {fmtDate(c.endDate)}
                          {c.autoRenew && <span style={{ marginLeft: 6, color: '#0D9488', fontWeight: 600 }}>↻ auto-renew</span>}
                        </div>
                      </div>
                      <div style={{ textAlign: 'right', flexShrink: 0, marginLeft: 12 }}>
                        <div style={{ fontWeight: 800, fontSize: 15, color: urgent ? '#DC2626' : '#D97706' }}>{days}d</div>
                        <div style={{ fontSize: 10, color: '#94A3B8' }}>remaining</div>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          {/* Awaiting signature */}
          {pending.length > 0 && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                  <Clock size={14} color="#1D4ED8" />
                  <span style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>Awaiting signature</span>
                </div>
                <button onClick={() => onNavigate('contracts')} style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#1B3A6B', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 600 }}>
                  Manage <ArrowRight size={12} />
                </button>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                {pending.slice(0, 4).map(c => {
                  // FIX: was (c.parties ?? []).filter(...) / (c.parties ?? []).length —
                  // ContractSummaryResponse (what this list endpoint actually
                  // returns) has never had a `parties` array, only
                  // signedPartyCount/totalPartyCount as plain integers. That
                  // meant `total` was always 0, so this line never rendered
                  // for any contract, ever.
                  const total    = c.totalPartyCount ?? 0
                  const unsigned = total - (c.signedPartyCount ?? 0)
                  return (
                    <div key={c.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '11px 16px', border: '1px solid #BFDBFE', borderLeft: '3px solid #1D4ED8', borderRadius: 8, background: '#F8FBFF' }}>
                      <div>
                        <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>{c.title}</div>
                        <div style={{ fontSize: 11, color: '#64748B' }}>
                          {c.contractNumber}
                          {total > 0 && ` · ${unsigned} of ${total} yet to sign`}
                        </div>
                      </div>
                      {(c.valueAmount ?? 0) > 0 && (
                        <div style={{ fontWeight: 700, fontSize: 13, color: '#1D4ED8', flexShrink: 0, marginLeft: 12 }}>
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
          <div style={{ background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 12, padding: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 14 }}>
              <TrendingUp size={13} color="#1B3A6B" />
              <span style={{ fontWeight: 700, fontSize: 13, color: '#0F172A' }}>By contract type</span>
            </div>
            {typeEntries.length === 0 ? (
              <div style={{ fontSize: 13, color: '#94A3B8' }}>No contracts yet</div>
            ) : typeEntries.map(([type, count]) => {
              const color = TYPE_COLOR[type] ?? '#64748B'
              const pct   = Math.round((count / Math.max(cs.length, 1)) * 100)
              return (
                <div key={type} style={{ marginBottom: 10 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4, fontSize: 12 }}>
                    <span style={{ color: '#475569', fontWeight: 500 }}>{TYPE_LABEL[type] ?? type.replace(/_/g, ' ')}</span>
                    <span style={{ color, fontWeight: 700 }}>{count}</span>
                  </div>
                  <div style={{ height: 4, background: '#E2E8F0', borderRadius: 99, overflow: 'hidden' }}>
                    <div style={{ height: '100%', width: `${pct}%`, background: color, borderRadius: 99, transition: 'width 0.3s' }} />
                  </div>
                </div>
              )
            })}
          </div>

          {/* Quick actions */}
          <div>
            <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>
              Quick actions
            </div>
            {[
              { label: 'New contract',     tab: 'contracts', color: '#1B3A6B' },
              { label: 'Browse templates', tab: 'templates', color: '#0D9488' },
            ].map(a => (
              <button key={a.label} onClick={() => onNavigate(a.tab)} style={{
                width: '100%', marginBottom: 7,
                padding: '10px 14px', background: '#fff',
                border: '1px solid #E2E8F0', borderRadius: 9,
                fontSize: 13, fontWeight: 600, color: a.color,
                cursor: 'pointer', textAlign: 'left',
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              }}>
                {a.label} <ArrowRight size={13} />
              </button>
            ))}
          </div>

          {/* ECT Act compliance note */}
          <div style={{ padding: '13px 15px', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 10 }}>
            <div style={{ fontWeight: 700, fontSize: 11, color: '#1D4ED8', marginBottom: 6 }}>ECT Act Compliance</div>
            <div style={{ fontSize: 11, color: '#1E40AF', lineHeight: 1.6 }}>
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
