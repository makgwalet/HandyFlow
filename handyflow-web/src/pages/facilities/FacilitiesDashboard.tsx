// src/pages/facilities/FacilitiesDashboard.tsx
//
// Live picture of the in-house facilities function, from GET /reports/dashboard (already limited to what the caller
// may see). Charts use useThemeColors() because recharts cannot read CSS variables.
import { useQuery } from '@tanstack/react-query'
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'
import { AlertTriangle, CalendarClock, CheckCircle2, ClipboardCheck, ShieldAlert, UserX, Wrench, Hourglass } from 'lucide-react'
import { facilitiesApi } from '../../api/facilities.api'
import { useThemeColors } from '../../theme/ThemeContext'
import { usePermissions } from '../../hooks/usePermission'
import Chip from '../../components/ui/Chip'
import { Card, EmptyState, KpiTile, Notice } from './ui'
import { SEVERITY_TONE, fmtDate, fmtMoney, kpiGrid, titleCase } from './format'

const STATUS_ORDER = ['REQUESTED', 'OPEN', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'PENDING_VERIFICATION']
const STATUS_COLOR: Record<string, string> = {
  REQUESTED: 'info-dot', OPEN: 'sky-text', ASSIGNED: 'violet-text', IN_PROGRESS: 'accent-dot', ON_HOLD: 'warning-text', PENDING_VERIFICATION: 'orange-text',
}
const PRIORITY_ORDER = ['EMERGENCY', 'URGENT', 'HIGH', 'NORMAL', 'LOW']
const PRIORITY_COLOR: Record<string, string> = {
  EMERGENCY: 'danger-text', URGENT: 'danger-text', HIGH: 'warning-text', NORMAL: 'info-dot', LOW: 'text-faint',
}

export default function FacilitiesDashboard({ onNavigate }: { onNavigate: (section: string) => void }) {
  const color = useThemeColors()
  const canSeeActions = usePermissions(['FACILITIES_READ', 'FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_COMPLIANCE_MANAGE'])
  const dash = useQuery({ queryKey: ['facilities', 'dashboard'], queryFn: facilitiesApi.dashboard, refetchInterval: 60_000 })
  const actions = useQuery({ queryKey: ['facilities', 'action-centre'], queryFn: facilitiesApi.actionCentre, enabled: canSeeActions })

  if (dash.isError) return <Notice tone="bad" icon={<AlertTriangle size={16} />}>The dashboard could not be loaded. Check your connection and try again.</Notice>
  const d = dash.data
  if (!d) return <div aria-busy="true" style={{ padding: 24, color: 'var(--hf-text-muted)', fontSize: 13 }}>Loading dashboard…</div>

  const statusRows = STATUS_ORDER.map(s => ({ key: s, name: titleCase(s), value: d.openByStatus[s] ?? 0 })).filter(r => r.value > 0)
  const priorityRows = PRIORITY_ORDER.map(p => ({ key: p, name: titleCase(p), value: d.openByPriority[p] ?? 0 }))
  const maxPriority = Math.max(1, ...priorityRows.map(r => r.value))
  const certProblems = d.certificatesExpired + d.certificatesExpiringIn30Days

  return (
    <div>
      <div style={kpiGrid}>
        <KpiTile label="Open work orders" value={d.open} icon={<Wrench size={19} />} tone="info" onClick={() => onNavigate('work-orders')} />
        <KpiTile label="Unassigned" value={d.unassigned} icon={<UserX size={19} />} tone={d.unassigned ? 'warn' : 'neutral'} onClick={() => onNavigate('work-orders')} />
        <KpiTile label="Overdue" value={d.overdue} icon={<CalendarClock size={19} />} tone={d.overdue ? 'bad' : 'neutral'} onClick={() => onNavigate('work-orders')} />
        <KpiTile label="Awaiting approval" value={d.awaitingApproval} icon={<ClipboardCheck size={19} />} tone={d.awaitingApproval ? 'warn' : 'neutral'} onClick={() => onNavigate('work-orders')} />
        <KpiTile label="Awaiting verification" value={d.awaitingVerification} icon={<Hourglass size={19} />} tone={d.awaitingVerification ? 'warn' : 'neutral'} onClick={() => onNavigate('work-orders')} />
        <KpiTile label="Certificates to renew" value={certProblems} hint={d.certificatesExpired ? `${d.certificatesExpired} already expired` : 'Expiring in 30 days'}
          icon={<ShieldAlert size={19} />} tone={d.certificatesExpired ? 'bad' : certProblems ? 'warn' : 'ok'} onClick={() => onNavigate('compliance')} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 16, marginBottom: 16 }}>
        <Card title="Open work by status">
          {statusRows.length === 0 ? <EmptyState>No open work. Nothing is waiting on anyone.</EmptyState> : (
            <div style={{ display: 'flex', gap: 18, alignItems: 'center', flexWrap: 'wrap' }}>
              <div style={{ width: 170, height: 170, position: 'relative' }} aria-hidden="true">
                <ResponsiveContainer>
                  <PieChart>
                    <Pie data={statusRows} dataKey="value" nameKey="name" innerRadius={52} outerRadius={80} paddingAngle={2} stroke="none" isAnimationActive={false}>
                      {statusRows.map(r => <Cell key={r.key} fill={color(STATUS_COLOR[r.key])} />)}
                    </Pie>
                  </PieChart>
                </ResponsiveContainer>
                <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                  <div style={{ fontSize: 26, fontWeight: 800, color: 'var(--hf-text)' }}>{d.open}</div>
                  <div style={{ fontSize: 11, color: 'var(--hf-text-muted)' }}>open</div>
                </div>
              </div>
              <ul style={{ listStyle: 'none', margin: 0, padding: 0, flex: 1, minWidth: 150, fontSize: 13 }}>
                {statusRows.map(r => (
                  <li key={r.key} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '4px 0' }}>
                    <span aria-hidden="true" style={{ width: 10, height: 10, borderRadius: 3, background: `var(--hf-${STATUS_COLOR[r.key]})` }} />
                    <span style={{ flex: 1, color: 'var(--hf-text-secondary)' }}>{r.name}</span>
                    <strong>{r.value}</strong>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </Card>

        <Card title="Open work by priority">
          <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {priorityRows.map(r => (
              <li key={r.key} style={{ display: 'grid', gridTemplateColumns: '90px 1fr 32px', alignItems: 'center', gap: 10, padding: '6px 0', fontSize: 13 }}>
                <span style={{ color: 'var(--hf-text-secondary)' }}>{r.name}</span>
                <span aria-hidden="true" style={{ height: 8, borderRadius: 4, background: 'var(--hf-surface-sunken)', overflow: 'hidden' }}>
                  <span style={{ display: 'block', height: '100%', width: `${(r.value / maxPriority) * 100}%`, background: `var(--hf-${PRIORITY_COLOR[r.key]})` }} />
                </span>
                <strong style={{ textAlign: 'right' }}>{r.value}</strong>
              </li>
            ))}
          </ul>
        </Card>

        {canSeeActions && (
          <Card title="Needs attention" action={<button type="button" onClick={() => onNavigate('action-centre')} style={linkBtn}>View all</button>}>
            {!actions.data ? <div style={{ fontSize: 13, color: 'var(--hf-text-muted)' }}>Loading…</div>
              : actions.data.items.length === 0 ? <EmptyState>All clear. No lapsed certificates or overdue actions.</EmptyState> : (
                <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
                  {actions.data.items.slice(0, 6).map(i => (
                    <li key={`${i.entityType}-${i.entityId}-${i.type}`} style={{ display: 'flex', gap: 10, alignItems: 'flex-start', padding: '8px 0', borderTop: '1px solid var(--hf-border-subtle)' }}>
                      <Chip tone={SEVERITY_TONE[i.severity]}>{titleCase(i.severity)}</Chip>
                      <div style={{ minWidth: 0, fontSize: 13 }}>
                        <div style={{ color: 'var(--hf-text)', fontWeight: 600 }}>{i.title}</div>
                        <div style={{ color: 'var(--hf-text-muted)', fontSize: 12 }}>{i.detail}</div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
          </Card>
        )}
      </div>

      <Card title="Last 30 days">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 16 }}>
          <Stat icon={<CheckCircle2 size={16} />} label="Completed" value={d.completedLast30Days} />
          <Stat label="Average time to complete" value={d.avgCompletionHoursLast30Days != null ? `${Number(d.avgCompletionHoursLast30Days).toFixed(1)} h` : '—'} />
          <Stat label="Sent back for rework" value={d.reworkRatePercentLast30Days != null ? `${Number(d.reworkRatePercentLast30Days).toFixed(0)}%` : '—'} />
          <Stat label="Downtime" value={`${(d.downtimeMinutesLast30Days / 60).toFixed(1)} h`} />
          <Stat label="Cost" value={fmtMoney(d.costLast30Days)} />
          <Stat label="PPM schedules overdue" value={d.ppmSchedulesOverdue} warn={d.ppmSchedulesOverdue > 0} />
        </div>
        <div style={{ marginTop: 12, fontSize: 11.5, color: 'var(--hf-text-faint)' }}>Updated {fmtDate(d.generatedAt)}. Figures cover the sites you have access to.</div>
      </Card>
    </div>
  )
}

const linkBtn: React.CSSProperties = { background: 'none', border: 'none', color: 'var(--hf-accent-text)', fontSize: 12.5, fontWeight: 600, cursor: 'pointer', padding: 0 }

function Stat({ label, value, icon, warn }: { label: string; value: React.ReactNode; icon?: React.ReactNode; warn?: boolean }) {
  return (
    <div>
      <div style={{ fontSize: 20, fontWeight: 800, color: warn ? 'var(--hf-warning-text)' : 'var(--hf-text)', display: 'flex', alignItems: 'center', gap: 6 }}>{icon}{value}</div>
      <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 2 }}>{label}</div>
    </div>
  )
}
