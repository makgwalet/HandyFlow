// src/pages/projects/ClientPortalPage.tsx  — Public route, no auth required
// Route: /projects/portal/:token
import React from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CheckCircle, AlertTriangle, Calendar } from 'lucide-react'
import { apiClient } from '../../api/client'

interface Portal {
  projectNumber:string; name:string; clientName:string|null; status:string; health:string
  startDate:string|null; endDate:string|null; budgetTotal:number; completionPct:number
  milestones:Milestone[]; openSnags:Snag[]; redRisks:Risk[]
}
interface Milestone { id:string; title:string; status:string; plannedEnd:string|null; progressPct:number; isCritical:boolean }
interface Snag { id:string; snagNumber:string; title:string; severity:string; location:string|null; assignedToName:string|null }
interface Risk { id:string; title:string; riskScore:number; mitigation:string|null }

const fmtDate=(d:string|null)=>d?new Date(d).toLocaleDateString('en-ZA',{day:'numeric',month:'long',year:'numeric'}):'—'
const HEALTH_STYLES:{[k:string]:{bg:string;color:string;label:string}}={
  GREEN:{bg:'var(--hf-success-soft-strong)',color:'var(--hf-success-text-strong)',label:'On Track'},
  AMBER:{bg:'var(--hf-warning-soft-strong)',color:'var(--hf-warning-text-deep)',label:'Needs Attention'},
  RED:{bg:'var(--hf-danger-soft)',color:'var(--hf-danger-text)',label:'At Risk'},
}

export function ClientPortalPage() {
  const { token } = useParams<{ token: string }>()

  const { data: portal, isLoading, isError } = useQuery<Portal>({
    queryKey: ['pm-portal', token],
    queryFn: async () => {
      const r = await apiClient.get(`/api/public/projects/portal/${token}`)
      return r.data?.data ?? r.data
    },
    enabled: !!token,
    retry: false,
  })

  if (isLoading) return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: "'Inter',sans-serif", background: 'var(--hf-surface-muted)', color: 'var(--hf-text-faint)' }}>
      Loading project…
    </div>
  )

  if (isError || !portal) return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', fontFamily: "'Inter',sans-serif", background: 'var(--hf-surface-muted)' }}>
      <div style={{ fontSize: 48, marginBottom: 16 }}>🔒</div>
      <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--hf-text)', marginBottom: 8 }}>Portal not found</div>
      <div style={{ fontSize: 14, color: 'var(--hf-text-faint)' }}>This link may have expired or is invalid.</div>
    </div>
  )

  const hs = HEALTH_STYLES[portal.health] ?? HEALTH_STYLES.GREEN
  const pct = Math.round(Number(portal.completionPct ?? 0))

  return (
    <div style={{ minHeight: '100vh', background: 'var(--hf-surface-muted)', fontFamily: "'Inter',system-ui,sans-serif" }}>
      {/* Header bar */}
      <div style={{ background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', padding: '16px 32px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontSize: 11, opacity: .7, marginBottom: 2 }}>CLIENT PROJECT PORTAL</div>
          <div style={{ fontSize: 20, fontWeight: 800 }}>{portal.name}</div>
        </div>
        <div style={{ background: hs.bg, color: hs.color, padding: '8px 16px', borderRadius: 20, fontSize: 13, fontWeight: 700 }}>{hs.label}</div>
      </div>

      <div style={{ maxWidth: 900, margin: '32px auto', padding: '0 24px' }}>
        {/* KPIs */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 14, marginBottom: 28 }}>
          {[
            { label: 'Client', value: portal.clientName ?? '—' },
            { label: 'Start Date', value: fmtDate(portal.startDate) },
            { label: 'End Date', value: fmtDate(portal.endDate) },
            { label: 'Status', value: portal.status.replace('_', ' ') },
          ].map(s => (
            <div key={s.label} style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: '16px 18px' }}>
              <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 6 }}>{s.label}</div>
              <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--hf-text)' }}>{s.value}</div>
            </div>
          ))}
        </div>

        {/* Completion */}
        <div style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: '20px 22px', marginBottom: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
            <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--hf-text)' }}>Overall Completion</div>
            <div style={{ fontSize: 24, fontWeight: 800, color: 'var(--hf-primary-text)' }}>{pct}%</div>
          </div>
          <div style={{ height: 12, background: 'var(--hf-surface-sunken)', borderRadius: 6 }}>
            <div style={{ height: '100%', width: `${pct}%`, background: portal.health === 'RED' ? 'var(--hf-danger)' : portal.health === 'AMBER' ? 'var(--hf-warning)' : 'var(--hf-success)', borderRadius: 6, transition: 'width 0.5s' }} />
          </div>
        </div>

        {/* Milestones */}
        <Section title={`Milestones (${portal.milestones.length})`}>
          {portal.milestones.length === 0 ? (
            <div style={{ fontSize: 13, color: 'var(--hf-text-faint)', padding: '8px 0' }}>No milestones defined</div>
          ) : portal.milestones.map(m => (
            <div key={m.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 0', borderBottom: '1px solid var(--hf-border-subtle)' }}>
              {m.status === 'COMPLETED'
                ? <CheckCircle size={18} color="#16A34A" />
                : <div style={{ width: 18, height: 18, borderRadius: '50%', border: `2px solid ${m.isCritical ? '#EF4444' : '#CBD5E1'}`, flexShrink: 0 }} />
              }
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--hf-text)' }}>{m.title}</div>
                <div style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>{fmtDate(m.plannedEnd)}</div>
              </div>
              <div style={{ fontSize: 13, fontWeight: 700, color: m.status === 'COMPLETED' ? 'var(--hf-success-text)' : 'var(--hf-text-muted)' }}>
                {m.status === 'COMPLETED' ? 'Complete' : `${m.progressPct?.toFixed(0) ?? 0}%`}
              </div>
            </div>
          ))}
        </Section>

        {/* Open Snags */}
        {portal.openSnags.length > 0 && (
          <Section title={`Open Snags (${portal.openSnags.length})`}>
            {portal.openSnags.map(s => (
              <div key={s.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--hf-border-subtle)' }}>
                <div style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--hf-danger)', flexShrink: 0 }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--hf-text)' }}>{s.title}</div>
                  <div style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>{s.location && `📍 ${s.location} · `}{s.assignedToName && `👤 ${s.assignedToName}`}</div>
                </div>
                <span style={{ fontSize: 10, fontWeight: 700, background: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)', padding: '2px 8px', borderRadius: 20 }}>{s.severity}</span>
              </div>
            ))}
          </Section>
        )}

        {/* Red Risks */}
        {portal.redRisks.length > 0 && (
          <Section title={`Active Risks (${portal.redRisks.length})`}>
            {portal.redRisks.map(r => (
              <div key={r.id} style={{ display: 'flex', gap: 10, padding: '10px 0', borderBottom: '1px solid var(--hf-border-subtle)' }}>
                <AlertTriangle size={16} color="#DC2626" style={{ flexShrink: 0, marginTop: 2 }} />
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--hf-text)' }}>{r.title}</div>
                  {r.mitigation && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 2 }}>Mitigation: {r.mitigation}</div>}
                </div>
              </div>
            ))}
          </Section>
        )}

        <div style={{ textAlign: 'center', fontSize: 12, color: 'var(--hf-text-disabled)', marginTop: 40, paddingBottom: 40 }}>
          Powered by HandyFlow · Project management for physical-world delivery
        </div>
      </div>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: '20px 22px', marginBottom: 16 }}>
      <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--hf-text)', marginBottom: 12 }}>{title}</div>
      {children}
    </div>
  )
}
