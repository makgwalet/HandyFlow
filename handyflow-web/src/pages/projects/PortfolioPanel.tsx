// src/pages/projects/PortfolioPanel.tsx
// Top of the Projects dashboard: portfolio KPIs with the change since the end of last month, health split, spend against
// budget, a six-month trend and the milestones due in the next two weeks. Figures come from /api/v1/projects/portfolio.
import type React from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, CalendarClock, CheckCircle2, Clock, FolderOpen, PauseCircle, Timer, Wallet } from 'lucide-react'
import { apiClient } from '../../api/client'
import { card, day, money, muted, one } from './tabs/pmKit'
import { RiskHeatmap } from './RiskHeatmap'
import type { Summary } from './ProjectDashboard'

interface Counts { total: number; active: number; onTrack: number; atRisk: number; overdue: number; onHold: number; green: number; amber: number; red: number
  budget: number; spent: number; committed: number }
interface Portfolio {
  asOf: string; now: Counts; previous: Counts | null; previousMonth: string | null; costProjects: number; hiddenCostProjects: number
  upcomingMilestones: { taskId: string; projectId: string; projectNumber: string; projectName: string; taskNumber: string; title: string; due: string; daysAway: number }[]
  trend: { month: string; onTrack: number; atRisk: number; overdue: number; spent: number }[]
}

/** "+2 vs end of Oct" — goodWhenUp says whether a rise is good news. */
function Delta({ now, before, goodWhenUp, money: isMoney, month }: { now: number; before: number | undefined; goodWhenUp: boolean | null; money?: boolean; month: string | null }) {
  if (before == null || month == null) return <span style={{ ...muted, fontSize: 11 }}>no history yet</span>
  const d = Number(now) - Number(before)
  const label = new Date(month + 'T00:00:00').toLocaleString('en-ZA', { month: 'short' })
  if (d === 0) return <span style={{ ...muted, fontSize: 11 }}>no change vs end of {label}</span>
  // null = neither good nor bad (spend going up is expected on live projects)
  const colour = goodWhenUp == null ? 'var(--hf-text-muted)' : (goodWhenUp ? d > 0 : d < 0) ? 'var(--hf-success-text)' : 'var(--hf-danger-text)'
  return <span style={{ fontSize: 11, fontWeight: 700, color: colour }}>
    {d > 0 ? '▲' : '▼'} {isMoney ? money(Math.abs(d)) : Math.abs(d)} <span style={{ fontWeight: 500, color: 'var(--hf-text-faint)' }}>vs end of {label}</span></span>
}

function Kpi({ icon: Icon, label, value, tone, delta }: { icon: typeof FolderOpen; label: string; value: React.ReactNode; tone: string; delta?: React.ReactNode }) {
  return (
    <div style={{ ...card, padding: '13px 15px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 6 }}>
        <Icon size={14} style={{ color: tone }} />
        <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', textTransform: 'uppercase', letterSpacing: '.04em' }}>{label}</span>
      </div>
      <div style={{ fontSize: typeof value === 'string' && value.length > 9 ? 19 : 26, fontWeight: 800, color: tone, lineHeight: 1.25, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{value}</div>
      <div style={{ marginTop: 4, minHeight: 16 }}>{delta}</div>
    </div>
  )
}

export function PortfolioPanel({ summary, onOpen }: { summary: Summary | undefined; onOpen: (id: string) => void }) {
  const { data: p, isLoading, isError } = useQuery<Portfolio>({
    queryKey: ['pm-portfolio'], staleTime: 60_000,
    queryFn: async () => one<Portfolio>(await apiClient.get('/api/v1/projects/portfolio')),
  })
  if (isLoading) return <div style={{ ...muted, marginBottom: 24 }}>Loading portfolio…</div>
  if (isError || !p) return <div style={{ ...muted, marginBottom: 24 }}>Portfolio figures are not available right now.</div>
  const n = p.now, b = p.previous ?? undefined, m = p.previousMonth
  const spentPct = n.budget > 0 ? (n.spent / n.budget) * 100 : 0
  const committedPct = n.budget > 0 ? (n.committed / n.budget) * 100 : 0
  const live = n.green + n.amber + n.red
  const maxTrend = Math.max(1, ...p.trend.map(t => t.onTrack + t.atRisk + t.overdue))

  return (
    <div style={{ marginBottom: 26 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12, marginBottom: 12 }}>
        <Kpi icon={FolderOpen} label="Projects" value={n.total} tone="var(--hf-text)" delta={<Delta now={n.total} before={b?.total} goodWhenUp month={m} />} />
        <Kpi icon={CheckCircle2} label="On track" value={n.onTrack} tone="var(--hf-success-text)" delta={<Delta now={n.onTrack} before={b?.onTrack} goodWhenUp month={m} />} />
        <Kpi icon={AlertTriangle} label="At risk" value={n.atRisk} tone="var(--hf-warning-text)" delta={<Delta now={n.atRisk} before={b?.atRisk} goodWhenUp={false} month={m} />} />
        <Kpi icon={Timer} label="Overdue" value={n.overdue} tone={n.overdue ? 'var(--hf-danger-text)' : 'var(--hf-text)'} delta={<Delta now={n.overdue} before={b?.overdue} goodWhenUp={false} month={m} />} />
        <Kpi icon={Wallet} label="Total budget" value={money(n.budget)} tone="var(--hf-text)" delta={<Delta now={n.budget} before={b?.budget} goodWhenUp={null} money month={m} />} />
        <Kpi icon={Wallet} label="Spent" value={money(n.spent)} tone="var(--hf-info-text)" delta={<Delta now={n.spent} before={b?.spent} goodWhenUp={null} money month={m} />} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
        <div style={card}>
          <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 10 }}>Health of live projects</div>
          {live === 0 ? <div style={muted}>No active or on-hold projects.</div> : <>
            <div style={{ display: 'flex', height: 12, borderRadius: 6, overflow: 'hidden', marginBottom: 8 }} role="img" aria-label={`${n.green} green, ${n.amber} amber, ${n.red} red`}>
              <div style={{ width: `${n.green / live * 100}%`, background: 'var(--hf-success)' }} />
              <div style={{ width: `${n.amber / live * 100}%`, background: 'var(--hf-warning)' }} />
              <div style={{ width: `${n.red / live * 100}%`, background: 'var(--hf-danger)' }} />
            </div>
            <div style={{ display: 'flex', gap: 14, fontSize: 12, flexWrap: 'wrap' }}>
              <span><b style={{ color: 'var(--hf-success-text)' }}>{n.green}</b> green</span><span><b style={{ color: 'var(--hf-warning-text)' }}>{n.amber}</b> amber</span>
              <span><b style={{ color: 'var(--hf-danger-text)' }}>{n.red}</b> red</span><span style={{ ...muted, display: 'inline-flex', alignItems: 'center', gap: 4 }}><PauseCircle size={11} /> {n.onHold} on hold</span>
            </div>
          </>}
          <div style={{ fontSize: 13, fontWeight: 700, margin: '16px 0 8px' }}>Spend against budget</div>
          <div style={{ height: 10, background: 'var(--hf-surface-sunken)', borderRadius: 5, overflow: 'hidden', display: 'flex' }}>
            <div style={{ width: `${Math.min(100, spentPct)}%`, background: spentPct > 100 ? 'var(--hf-danger)' : 'var(--hf-info)' }} />
            <div style={{ width: `${Math.max(0, Math.min(100 - spentPct, committedPct))}%`, background: 'var(--hf-warning)', opacity: 0.7 }} />
          </div>
          <div style={{ ...muted, marginTop: 6 }}>{spentPct.toFixed(0)}% spent · {money(n.committed)} committed · {money(Math.max(0, n.budget - n.spent - n.committed))} uncommitted</div>
          {p.hiddenCostProjects > 0 && <div style={{ ...muted, marginTop: 4 }}>Costs of {p.hiddenCostProjects} project(s) you cannot see are left out.</div>}
        </div>

        <div style={card}>
          <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 10 }}>Last six months</div>
          {p.trend.length === 0 ? <div style={muted}>The trend builds up from month-end snapshots; the first one is taken at the end of this month.</div> : <>
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: 10, height: 110 }} role="img" aria-label="Projects on track, at risk and overdue by month">
              {p.trend.map(t => (
                <div key={t.month} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
                  <div style={{ width: '100%', maxWidth: 34, display: 'flex', flexDirection: 'column-reverse', height: 90 }} title={`${t.onTrack} on track, ${t.atRisk} at risk, ${t.overdue} overdue · ${money(t.spent)} spent`}>
                    <div style={{ height: `${t.onTrack / maxTrend * 100}%`, background: 'var(--hf-success)', borderRadius: '0 0 3px 3px' }} />
                    <div style={{ height: `${t.atRisk / maxTrend * 100}%`, background: 'var(--hf-warning)' }} />
                    <div style={{ height: `${t.overdue / maxTrend * 100}%`, background: 'var(--hf-danger)', borderRadius: '3px 3px 0 0' }} />
                  </div>
                  <span style={{ fontSize: 10, color: 'var(--hf-text-faint)' }}>{new Date(t.month + 'T00:00:00').toLocaleString('en-ZA', { month: 'short' })}</span>
                </div>
              ))}
            </div>
            <div style={{ ...muted, marginTop: 6 }}>Month-end: green on track · amber at risk · red overdue</div>
          </>}
        </div>

        <div style={card}>
          <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6 }}><CalendarClock size={14} /> Milestones in the next 14 days</div>
          {p.upcomingMilestones.length === 0 ? <div style={muted}>None due.</div> : (
            <div style={{ display: 'grid', gap: 6 }}>
              {p.upcomingMilestones.map(ms => (
                <button key={ms.taskId} onClick={() => onOpen(ms.projectId)}
                  style={{ display: 'flex', justifyContent: 'space-between', gap: 10, textAlign: 'left', background: 'none', border: 'none', borderBottom: '1px solid var(--hf-border)', padding: '5px 0', cursor: 'pointer', color: 'var(--hf-text)' }}>
                  <span style={{ minWidth: 0 }}><span style={{ fontSize: 13, fontWeight: 600 }}>{ms.title}</span>
                    <span style={{ display: 'block', fontSize: 11, color: 'var(--hf-text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{ms.projectNumber} · {ms.projectName}</span></span>
                  <span style={{ fontSize: 11, fontWeight: 700, whiteSpace: 'nowrap', color: ms.daysAway <= 2 ? 'var(--hf-danger-text)' : 'var(--hf-text-muted)' }}>
                    {ms.daysAway === 0 ? 'today' : ms.daysAway === 1 ? 'tomorrow' : `in ${ms.daysAway} days`}<span style={{ display: 'block', fontWeight: 500 }}>{day(ms.due)}</span></span>
                </button>
              ))}
            </div>
          )}
          {summary && <div style={{ display: 'flex', gap: 14, marginTop: 12, fontSize: 12, color: 'var(--hf-text-muted)', flexWrap: 'wrap' }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><Clock size={11} /> {summary.pendingTimeApprovals} time entr{summary.pendingTimeApprovals === 1 ? 'y' : 'ies'} to approve</span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: summary.openRedRisks ? 'var(--hf-danger-text)' : undefined }}><AlertTriangle size={11} /> {summary.openRedRisks} open red risk{summary.openRedRisks === 1 ? '' : 's'}</span>
          </div>}
        </div>
      </div>
      <div style={{ ...card, marginTop: 12 }}>
        <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 10 }}>Open risks across your projects</div>
        <RiskHeatmap onOpenProject={onOpen} compact />
      </div>
    </div>
  )
}
