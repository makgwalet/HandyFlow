// The Recruiter overview: the figures for the last 7, 30 or 90 days next to the period before, the hiring funnel, where applications come from,
// the busiest jobs, the next interviews and what happened lately. Every number comes from the same records as the screens and reports.
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { Bell, CalendarDays, Plus, Search, Upload } from 'lucide-react'
import { NotificationDrawer } from '../../components/layout/NotificationDrawer'
import { usePermission } from '../../hooks/usePermission'
import { apiClient } from '../../api/client'
import RecFunnel from './RecFunnel'

type Metric = { value: number | null; previous: number | null; changePercent: number | null }
type Dash = {
  days: number; activeJobs: number
  applications: Metric; interviews: Metric; offers: Metric; hires: Metric; timeToHireDays: Metric
  funnel: { stage: string; count: number }[]
  topJobs: { id: string; title: string; applications: number; hires: number }[]
  sources: { source: string; count: number; hires: number }[]
  upcoming: { applicationId: string; candidate: string; jobTitle: string; type: string; scheduledAt: string }[]
  activity: { at: string; kind: 'APPLIED' | 'STAGE'; applicationId: string; candidate: string; jobTitle: string; stage: string }[]
}
const URL = '/api/v1/recruiter/dashboard'
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const card = { background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 10, padding: 14 } as const
const muted = { color: 'var(--hf-text-muted)' } as const
const STAGE_WORD: Record<string, string> = { APPLIED: 'Applied', SCREENING: 'Screening', INTERVIEW: 'Interview', ASSESSMENT: 'Assessment', OFFER: 'Offer', HIRED: 'Hired', REJECTED: 'Rejected', WITHDRAWN: 'Withdrawn' }
const SOURCE_WORD: Record<string, string> = { CAREERS_PAGE: 'Careers page', REFERRAL: 'Referral', LINKEDIN: 'LinkedIn', INDEED: 'Indeed', MANUAL: 'Added by us', TALENT_POOL: 'Talent pool', OTHER: 'Other' }
const TYPE_WORD: Record<string, string> = { PHONE: 'Phone', VIDEO: 'Video', IN_PERSON: 'On site', TECHNICAL: 'Technical', PANEL: 'Panel' }
const PALETTE = ['var(--hf-primary)', 'var(--hf-success)', 'var(--hf-warning)', 'var(--hf-violet)', 'var(--hf-sky)', 'var(--hf-orange)', 'var(--hf-text-faint)']
const word = (m: Record<string, string>, k: string) => m[k] ?? k.charAt(0) + k.slice(1).toLowerCase().replace(/_/g, ' ')
const when = (iso: string) => new Date(iso).toLocaleString('en-ZA', { timeZone: 'Africa/Johannesburg', weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
const ago = (iso: string) => {
  const mins = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000))
  if (mins < 60) return `${Math.max(1, mins)} min ago`
  if (mins < 60 * 24) return `${Math.round(mins / 60)} h ago`
  return `${Math.round(mins / 1440)} d ago`
}

function Change({ m, lowerIsBetter, days }: { m: Metric; lowerIsBetter?: boolean; days: number }) {
  if (m.changePercent == null) return <span style={{ ...muted, fontSize: 11 }}>No figure for the {days} days before</span>
  const up = m.changePercent > 0, flat = m.changePercent === 0
  const good = flat ? null : up !== !!lowerIsBetter
  const colour = good == null ? 'var(--hf-text-muted)' : good ? 'var(--hf-success-text)' : 'var(--hf-danger-text)'
  return <span style={{ fontSize: 11, color: colour }}>{flat ? 'No change' : `${up ? '▲' : '▼'} ${Math.abs(m.changePercent)}%`} <span style={muted}>vs the {days} days before</span></span>
}

function Kpi({ label, value, m, days, suffix, lowerIsBetter, plain }: { label: string; value: number | null; m?: Metric; days: number; suffix?: string; lowerIsBetter?: boolean; plain?: string }) {
  return (
    <div style={card}>
      <div style={{ ...muted, fontSize: 12 }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: 700, margin: '4px 0' }}>{value == null ? '–' : value}{value != null && suffix ? <span style={{ fontSize: 13, fontWeight: 500 }}> {suffix}</span> : null}</div>
      {m ? <Change m={m} days={days} lowerIsBetter={lowerIsBetter} /> : <span style={{ ...muted, fontSize: 11 }}>{plain}</span>}
    </div>
  )
}

function Donut({ parts, unit }: { parts: { label: string; n: number }[]; unit: string }) {
  const total = parts.reduce((s, p) => s + p.n, 0)
  const r = 52, c = 2 * Math.PI * r
  let used = 0
  return (
    <div style={{ display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
      <svg width="150" height="150" viewBox="0 0 150 150" role="img" aria-label={`${total} ${unit} by source`}>
        <circle cx="75" cy="75" r={r} fill="none" stroke="var(--hf-border)" strokeWidth="22" />
        {total > 0 && parts.map((p, i) => {
          const len = (p.n / total) * c
          const el = <circle key={p.label} cx="75" cy="75" r={r} fill="none" stroke={PALETTE[Math.min(i, PALETTE.length - 1)]} strokeWidth="22"
            strokeDasharray={`${len} ${c - len}`} strokeDashoffset={-used} transform="rotate(-90 75 75)" />
          used += len
          return el
        })}
        <text x="75" y="72" textAnchor="middle" fontSize="22" fontWeight="700" fill="var(--hf-text)">{total}</text>
        <text x="75" y="90" textAnchor="middle" fontSize="10" fill="var(--hf-text-muted)">{unit}</text>
      </svg>
      <div style={{ display: 'grid', gap: 5, fontSize: 12 }}>
        {parts.map((p, i) => (
          <div key={p.label} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span aria-hidden style={{ width: 9, height: 9, borderRadius: 2, background: PALETTE[Math.min(i, PALETTE.length - 1)] }} />
            {p.label} <span style={muted}>{p.n} ({Math.round((p.n / total) * 100)}%)</span>
          </div>
        ))}
      </div>
    </div>
  )
}

const quick = { display: 'inline-flex', alignItems: 'center', gap: 5, padding: '6px 10px', background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12, textDecoration: 'none', cursor: 'pointer' } as const

/** Find a candidate or job, jump to the common actions, and see unread notifications, from the top of the overview. */
function Toolbar() {
  const nav = useNavigate()
  const mayManage = usePermission('RECRUITER_MANAGE')
  const [text, setText] = useState('')
  const [bellOpen, setBellOpen] = useState(false)
  const unread = useQuery<{ unreadCount: number }>({
    queryKey: ['notifications-unread-count'], queryFn: async () => (await apiClient.get('/api/v1/notifications/unread-count')).data, refetchInterval: 60_000,
  }).data?.unreadCount ?? 0
  const go = (e: React.FormEvent) => {
    e.preventDefault()
    const t = text.trim()
    nav(t ? `/recruiter/applications?q=${encodeURIComponent(t.slice(0, 100))}` : '/recruiter/applications')
  }
  return (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
      <form role="search" onSubmit={go} style={{ position: 'relative', flex: '1 1 220px', maxWidth: 360 }}>
        <Search size={13} aria-hidden style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: 'var(--hf-text-faint)' }} />
        <input aria-label="Search candidates" placeholder="Search candidates by name, email or job" value={text} onChange={e => setText(e.target.value)}
          style={{ width: '100%', boxSizing: 'border-box', padding: '7px 10px 7px 28px', background: 'var(--hf-surface)', color: 'var(--hf-text)', border: '1px solid var(--hf-border)', borderRadius: 8, fontSize: 12 }} />
      </form>
      {mayManage && <Link to="/recruiter/jobs?new=1" style={quick}><Plus size={13} aria-hidden /> Post job</Link>}
      {mayManage && <Link to="/recruiter/jobs" style={quick} title="Open a job, then choose Import candidates from CSV"><Upload size={13} aria-hidden /> Import candidates</Link>}
      <Link to="/recruiter/calendar" style={quick}><CalendarDays size={13} aria-hidden /> Interview calendar</Link>
      <button type="button" style={{ ...quick, position: 'relative' }} onClick={() => setBellOpen(true)}
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}>
        <Bell size={14} aria-hidden />
        {unread > 0 && <span aria-hidden style={{ background: 'var(--hf-danger-solid-strong)', color: 'var(--hf-text-on-solid)', borderRadius: 20, padding: '0 6px', fontSize: 10, fontWeight: 700 }}>{unread > 99 ? '99+' : unread}</span>}
      </button>
      <NotificationDrawer open={bellOpen} onClose={() => setBellOpen(false)} />
    </div>
  )
}

export default function RecDashboard() {
  const [days, setDays] = useState(30)
  const [sourceBy, setSourceBy] = useState<'applications' | 'hires'>('applications')
  const q = useQuery<Dash>({ queryKey: ['rec-dashboard', days], queryFn: async () => data(await apiClient.get(`${URL}?days=${days}`)), retry: false })
  const d = q.data
  const err = (q.error as { response?: { data?: { message?: string } } } | null)?.response?.data?.message

  return (
    <div style={{ fontSize: 12, display: 'grid', gap: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
        <div>
          <div style={{ fontSize: 16, fontWeight: 700 }}>Recruiter overview</div>
          <div style={muted}>How hiring is going, and what needs you next.</div>
        </div>
        <select aria-label="Period" value={days} onChange={e => setDays(Number(e.target.value))}
          style={{ padding: '6px 9px', background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12 }}>
          <option value={7}>Last 7 days</option><option value={30}>Last 30 days</option><option value={90}>Last 90 days</option>
        </select>
      </div>
      <Toolbar />
      {err && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{err}</div>}
      {q.isLoading && <div style={muted}>Loading...</div>}
      {q.isError && !err && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>The dashboard could not be loaded.</div>}
      {d && (
        <>
          <div style={{ display: 'grid', gap: 10, gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))' }}>
            <Kpi label="Open jobs" value={d.activeJobs} days={days} plain="Right now" />
            <Kpi label="Applications" value={d.applications.value} m={d.applications} days={days} />
            <Kpi label="Interviews scheduled" value={d.interviews.value} m={d.interviews} days={days} />
            <Kpi label="Offers sent" value={d.offers.value} m={d.offers} days={days} />
            <Kpi label="Hires" value={d.hires.value} m={d.hires} days={days} />
            <Kpi label="Average time to hire" value={d.timeToHireDays.value} suffix="days" m={d.timeToHireDays} days={days} lowerIsBetter />
          </div>

          <div style={{ display: 'grid', gap: 12, gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))' }}>
            <div style={card}>
              <strong>Recruitment pipeline</strong>
              <div style={{ ...muted, margin: '2px 0 10px' }}>Of the {d.funnel[0].count} applications received in this period, how many reached each stage.</div>
              <RecFunnel stages={d.funnel.map(f => ({ label: word(STAGE_WORD, f.stage), count: f.count }))} />
            </div>

            <div style={card}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <strong>Source of hire</strong>
                <span role="group" aria-label="Count by" style={{ display: 'inline-flex', border: '1px solid var(--hf-border)', borderRadius: 7, overflow: 'hidden' }}>
                  {(['applications', 'hires'] as const).map(k => (
                    <button key={k} type="button" aria-pressed={sourceBy === k} onClick={() => setSourceBy(k)}
                      style={{ padding: '4px 10px', border: 'none', cursor: 'pointer', fontSize: 11, background: sourceBy === k ? 'var(--hf-primary)' : 'var(--hf-surface)', color: sourceBy === k ? 'var(--hf-text-on-solid)' : 'var(--hf-text-secondary)' }}>
                      {k === 'applications' ? 'Applications' : 'Hires'}
                    </button>
                  ))}
                </span>
              </div>
              <div style={{ marginTop: 10 }}>
                {(() => {
                  const parts = d.sources.map(s => ({ label: word(SOURCE_WORD, s.source), n: sourceBy === 'hires' ? s.hires : s.count })).filter(p => p.n > 0)
                  return parts.length === 0
                    ? <div style={muted}>{sourceBy === 'hires' ? 'Nobody who applied in this period has been hired yet.' : 'No applications in this period.'}</div>
                    : <Donut parts={parts} unit={sourceBy} />
                })()}
              </div>
            </div>

            <div style={card}>
              <strong>Busiest jobs</strong>
              {d.topJobs.length === 0 ? <div style={{ ...muted, marginTop: 8 }}>No applications in this period.</div> : (
                <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: 6 }}>
                  <thead><tr>{['Job', 'Applications', 'Hires'].map((h, i) => <th key={h} style={{ textAlign: i ? 'right' : 'left', padding: '4px 6px', borderBottom: '1px solid var(--hf-border)', fontWeight: 600 }}>{h}</th>)}</tr></thead>
                  <tbody>{d.topJobs.map(j => (
                    <tr key={j.id}>
                      <td style={{ padding: '5px 6px' }}>{j.title}</td>
                      <td style={{ padding: '5px 6px', textAlign: 'right' }}>{j.applications}</td>
                      <td style={{ padding: '5px 6px', textAlign: 'right' }}>{j.hires}</td>
                    </tr>))}</tbody>
                </table>
              )}
              <div style={{ marginTop: 8 }}><Link to="/recruiter/jobs">View all jobs</Link></div>
            </div>

            <div style={card}>
              <strong>Upcoming interviews</strong>
              {d.upcoming.length === 0 ? <div style={{ ...muted, marginTop: 8 }}>Nothing booked.</div> : d.upcoming.map(u => (
                <div key={u.applicationId + u.scheduledAt} style={{ display: 'flex', justifyContent: 'space-between', gap: 8, padding: '6px 0', borderBottom: '1px solid var(--hf-border-subtle)' }}>
                  <span><strong>{u.candidate}</strong><br /><span style={muted}>{u.jobTitle}</span></span>
                  <span style={{ textAlign: 'right' }}>{when(u.scheduledAt)}<br /><span style={muted}>{word(TYPE_WORD, u.type)}</span></span>
                </div>
              ))}
              <div style={{ marginTop: 8 }}><Link to="/recruiter/applications">View applications</Link></div>
            </div>

            <div style={card}>
              <strong>Recent activity</strong>
              {d.activity.length === 0 ? <div style={{ ...muted, marginTop: 8 }}>Nothing yet.</div> : d.activity.map(a => (
                <div key={a.kind + a.applicationId + a.at + a.stage} style={{ display: 'flex', justifyContent: 'space-between', gap: 8, padding: '6px 0', borderBottom: '1px solid var(--hf-border-subtle)' }}>
                  <span>
                    <strong>{a.candidate}</strong> {a.kind === 'APPLIED' ? 'applied for' : `moved to ${word(STAGE_WORD, a.stage)} for`} {a.jobTitle}
                  </span>
                  <span style={{ ...muted, whiteSpace: 'nowrap' }}>{ago(a.at)}</span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
