// src/pages/projects/ProjectReportsPage.tsx
// Projects > Reports: delivery (on-time %, finish against baseline), profitability (revenue, cost, margin for completed
// projects; forecast margin for live ones), by client and by type, and every project. Exports to CSV and PDF.
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Download, FileText } from 'lucide-react'
import { apiClient } from '../../api/client'
import { Btn, Empty, ErrorLine, Pill, SectionTitle, Stat, card, day, downloadFile, inp, money, muted, one, td, th } from './tabs/pmKit'

interface Row { id: string; projectNumber: string; name: string; clientName: string | null; projectType: string; status: string; health: string
  startDate: string | null; endDate: string | null; baselineEnd: string | null; completedOn: string | null; onTime: boolean | null; finishVarianceDays: number | null
  costVisible: boolean; contractValue: number | null; budget: number | null; actual: number | null; forecast: number | null; margin: number | null; marginPct: number | null; forecastMargin: boolean }
interface Group { key: string; projects: number; completed: number; onTime: number; revenue: number; cost: number; margin: number; marginPct: number | null }
interface Report {
  from: string; to: string; generatedOn: string
  delivery: { completed: number; onTime: number; onTimePct: number | null; avgFinishVarianceDays: number | null; liveOverdue: number; liveForecastLate: number }
  money: { revenue: number; cost: number; margin: number; marginPct: number | null; liveRevenue: number; liveForecastCost: number; liveForecastMargin: number
    liveForecastMarginPct: number | null; costOverrun: number; overBudgetProjects: number }
  projects: Row[]; byClient: Group[]; byType: Group[]; hiddenCostProjects: number
}

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
function preset(k: string): [string, string] {
  const now = new Date(), y = now.getFullYear(), m = now.getMonth()
  switch (k) {
    case 'quarter': { const q = Math.floor(m / 3) * 3; return [iso(new Date(y, q, 1)), iso(now)] }
    case 'last-year': return [`${y - 1}-01-01`, `${y - 1}-12-31`]
    case '12m': return [iso(new Date(y - 1, m, now.getDate() + 1)), iso(now)]
    // South African financial year: 1 March to end February
    case 'fy': { const start = m >= 2 ? y : y - 1; return [`${start}-03-01`, iso(now)] }
    default: return [`${y}-01-01`, iso(now)]
  }
}

export function ProjectReportsPage({ onOpen }: { onOpen: (id: string) => void }) {
  const [period, setPeriod] = useState('ytd')
  const [[from, to], setRange] = useState<[string, string]>(preset('ytd'))
  const [show, setShow] = useState<'all' | 'completed' | 'live' | 'late' | 'loss'>('all')
  const qs = `from=${from}&to=${to}`
  const { data: r, isLoading, isError, error } = useQuery<Report>({ queryKey: ['pm-report', from, to], queryFn: async () => one<Report>(await apiClient.get(`/api/v1/projects/reports?${qs}`)) })

  const rows = useMemo(() => (r?.projects ?? []).filter(p => {
    switch (show) {
      case 'completed': return !!p.completedOn && p.completedOn >= from && p.completedOn <= to
      case 'live': return ['APPROVED', 'ACTIVE', 'ON_HOLD'].includes(p.status)
      case 'late': return p.onTime === false || (['APPROVED', 'ACTIVE', 'ON_HOLD'].includes(p.status) && !!p.endDate && p.endDate < (r?.generatedOn ?? ''))
      case 'loss': return p.margin != null && p.margin < 0
      default: return true
    }
  }), [r, show, from, to])

  const tone = (n: number | null | undefined) => n == null ? undefined : n < 0 ? 'bad' as const : 'good' as const
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 10, flexWrap: 'wrap', marginBottom: 14 }}>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <label style={{ fontSize: 12 }}><span style={{ display: 'block', ...muted, marginBottom: 3 }}>Period</span>
            <select value={period} onChange={e => { setPeriod(e.target.value); if (e.target.value !== 'custom') setRange(preset(e.target.value)) }} style={{ ...inp, width: 190 }}>
              <option value="ytd">This year to date</option><option value="fy">Financial year to date</option><option value="quarter">This quarter</option>
              <option value="12m">Last 12 months</option><option value="last-year">Last year</option><option value="custom">Choose dates…</option></select></label>
          <label style={{ fontSize: 12 }}><span style={{ display: 'block', ...muted, marginBottom: 3 }}>From</span>
            <input type="date" value={from} max={to} onChange={e => { setPeriod('custom'); setRange([e.target.value, to]) }} style={{ ...inp, width: 150 }} /></label>
          <label style={{ fontSize: 12 }}><span style={{ display: 'block', ...muted, marginBottom: 3 }}>To</span>
            <input type="date" value={to} min={from} onChange={e => { setPeriod('custom'); setRange([from, e.target.value]) }} style={{ ...inp, width: 150 }} /></label>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Btn disabled={!r} onClick={() => downloadFile(`/api/v1/projects/reports?${qs}&format=csv`, `projects-report-${from}-to-${to}.csv`)}><Download size={13} /> CSV</Btn>
          <Btn disabled={!r} onClick={() => downloadFile(`/api/v1/projects/reports?${qs}&format=pdf`, `projects-report-${from}-to-${to}.pdf`)}><FileText size={13} /> PDF</Btn>
        </div>
      </div>

      {isLoading ? <Empty>Building the report…</Empty> : isError || !r ? <ErrorLine text={(error as any)?.response?.data?.message ?? 'Could not build the report.'} /> : <>
        <SectionTitle>Delivery</SectionTitle>
        <div style={{ ...card, display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 14, marginBottom: 16 }}>
          <Stat label="Completed in period" value={r.delivery.completed} />
          <Stat label="On time" value={r.delivery.onTimePct == null ? '—' : `${r.delivery.onTimePct}%`} sub={`${r.delivery.onTime} of ${r.delivery.completed} by the baseline end`}
            tone={r.delivery.onTimePct == null ? undefined : r.delivery.onTimePct >= 80 ? 'good' : r.delivery.onTimePct >= 60 ? 'warn' : 'bad'} />
          <Stat label="Average finish" value={r.delivery.avgFinishVarianceDays == null ? '—' : r.delivery.avgFinishVarianceDays === 0 ? 'on the day'
            : `${Math.abs(r.delivery.avgFinishVarianceDays)} days ${r.delivery.avgFinishVarianceDays > 0 ? 'late' : 'early'}`} sub="against the baseline end"
            tone={r.delivery.avgFinishVarianceDays == null ? undefined : r.delivery.avgFinishVarianceDays > 0 ? 'bad' : 'good'} />
          <Stat label="Live past end date" value={r.delivery.liveOverdue} tone={r.delivery.liveOverdue ? 'bad' : undefined} />
          <Stat label="Live, end moved past baseline" value={r.delivery.liveForecastLate} tone={r.delivery.liveForecastLate ? 'warn' : undefined} />
        </div>

        <SectionTitle>Profitability</SectionTitle>
        <div style={{ ...card, display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 14, marginBottom: 6 }}>
          <Stat label="Revenue (completed)" value={money(r.money.revenue)} sub="contract value" />
          <Stat label="Cost (completed)" value={money(r.money.cost)} sub="actual cost" />
          <Stat label="Margin (completed)" value={money(r.money.margin)} sub={r.money.marginPct == null ? undefined : `${r.money.marginPct}% of revenue`} tone={tone(r.money.margin)} />
          <Stat label="Forecast margin (live)" value={money(r.money.liveForecastMargin)} sub={r.money.liveForecastMarginPct == null ? `on ${money(r.money.liveRevenue)} of contracts` : `${r.money.liveForecastMarginPct}% of ${money(r.money.liveRevenue)}`}
            tone={tone(r.money.liveForecastMargin)} />
          <Stat label="Over budget" value={r.money.overBudgetProjects} sub={r.money.overBudgetProjects ? `${money(r.money.costOverrun)} over in total` : undefined} tone={r.money.overBudgetProjects ? 'bad' : undefined} />
        </div>
        <div style={{ ...muted, marginBottom: 16 }}>
          Margin = contract value less actual cost for projects completed in the period; for live projects, contract value less the forecast at completion.
          Projects without a contract value are left out of margin.{r.hiddenCostProjects > 0 && ` Costs of ${r.hiddenCostProjects} project(s) you cannot see are left out.`}
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(380px,1fr))', gap: 12, marginBottom: 16 }}>
          <GroupTable title="By client" rows={r.byClient} />
          <GroupTable title="By project type" rows={r.byType} />
        </div>

        <SectionTitle right={<select value={show} onChange={e => setShow(e.target.value as typeof show)} style={{ ...inp, width: 210 }} aria-label="Show">
          <option value="all">All projects ({r.projects.length})</option><option value="completed">Completed in period</option><option value="live">Live</option>
          <option value="late">Late</option><option value="loss">Making a loss</option></select>}>Projects</SectionTitle>
        <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
          {rows.length === 0 ? <Empty>No projects match.</Empty> : (
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead><tr>{['Project', 'Client', 'Status', 'Baseline end', 'Completed', 'On time', 'Contract', 'Actual', 'Forecast', 'Margin'].map(h =>
                <th key={h} style={{ ...th, textAlign: ['Contract', 'Actual', 'Forecast', 'Margin'].includes(h) ? 'right' : 'left' }}>{h}</th>)}</tr></thead>
              <tbody>{rows.map(p => (
                <tr key={p.id} onClick={() => onOpen(p.id)} style={{ cursor: 'pointer' }}>
                  <td style={td}><b>{p.projectNumber}</b> {p.name}</td>
                  <td style={td}>{p.clientName ?? '—'}</td>
                  <td style={td}><Pill tone={p.status === 'ACTIVE' ? 'info' : p.status === 'COMPLETED' || p.status === 'CLOSED' ? 'success' : p.status === 'ON_HOLD' ? 'warning' : 'neutral'}>{p.status.replace('_', ' ')}</Pill></td>
                  <td style={{ ...td, whiteSpace: 'nowrap' }}>{day(p.baselineEnd)}</td>
                  <td style={{ ...td, whiteSpace: 'nowrap' }}>{day(p.completedOn)}</td>
                  <td style={td}>{p.onTime == null ? '—' : p.onTime ? <Pill tone="success">On time</Pill> : <Pill tone="danger">{p.finishVarianceDays} d late</Pill>}</td>
                  <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>{p.costVisible ? money(p.contractValue) : <span style={muted}>hidden</span>}</td>
                  <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>{p.costVisible ? money(p.actual) : ''}</td>
                  <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>{p.costVisible ? money(p.forecast) : ''}</td>
                  <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap', fontWeight: 700, color: p.margin == null ? undefined : p.margin < 0 ? 'var(--hf-danger-text)' : 'var(--hf-success-text)' }}>
                    {p.margin == null ? '—' : <>{money(p.margin)}{p.marginPct != null && <span style={{ fontWeight: 500 }}> ({p.marginPct}%)</span>}{p.forecastMargin && <div style={{ ...muted, fontWeight: 500 }}>forecast</div>}</>}</td>
                </tr>
              ))}</tbody>
            </table>
          )}
        </div>
      </>}
    </div>
  )
}

function GroupTable({ title, rows }: { title: string; rows: Group[] }) {
  return (
    <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
      <div style={{ padding: '12px 14px 0' }}><SectionTitle>{title}</SectionTitle></div>
      {rows.length === 0 ? <Empty>No projects.</Empty> : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr>{['', 'Projects', 'On time', 'Revenue', 'Margin'].map(h => <th key={h} style={{ ...th, textAlign: h === '' ? 'left' : 'right' }}>{h}</th>)}</tr></thead>
          <tbody>{rows.map(g => (
            <tr key={g.key}>
              <td style={td}>{g.key}</td>
              <td style={{ ...td, textAlign: 'right' }}>{g.projects}</td>
              <td style={{ ...td, textAlign: 'right' }}>{g.completed ? `${g.onTime}/${g.completed}` : '—'}</td>
              <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>{money(g.revenue)}</td>
              <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap', color: g.margin < 0 ? 'var(--hf-danger-text)' : undefined }}>{money(g.margin)}{g.marginPct != null && <span style={muted}> ({g.marginPct}%)</span>}</td>
            </tr>
          ))}</tbody>
        </table>
      )}
      <div style={{ ...muted, padding: '6px 14px 10px' }}>On time, revenue and margin count projects completed in the period.</div>
    </div>
  )
}
