// src/pages/marketing/DashboardTab.tsx
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, CalendarClock, AlertTriangle } from 'lucide-react'
import { C, Card, Kpi, Pill, Btn, Empty, LineChart, Donut, change, fmtN, pct, fmtDate, fmtDT } from './ui'

const SOURCE_LABEL: Record<string, string> = {
  WEB_FORM: 'Web form', IMPORT: 'Import', CRM_SYNC: 'CRM sync', MANUAL: 'Manual entry', EMAIL: 'Email confirmation',
  PHONE: 'Phone', IN_PERSON: 'In person', UNKNOWN: 'Unknown',
}
const PALETTE = ['#2563EB', '#16A34A', '#F59E0B', '#8B5CF6', '#EC4899', '#94A3B8']
const STATUS: Record<string, { c: string; bg: string; l: string }> = {
  DRAFT: { c: '#64748B', bg: '#F1F5F9', l: 'Draft' }, SCHEDULED: { c: '#1D4ED8', bg: '#EFF6FF', l: 'Scheduled' },
  SENDING: { c: '#B45309', bg: '#FEF3C7', l: 'Sending' }, SENT: { c: '#166534', bg: '#DCFCE7', l: 'Completed' },
  PAUSED: { c: '#9333EA', bg: '#FAF5FF', l: 'Paused' }, CANCELLED: { c: '#94A3B8', bg: '#F1F5F9', l: 'Cancelled' },
  FAILED: { c: '#DC2626', bg: '#FEF2F2', l: 'Failed' },
}
const dayLabel = (iso: string) => new Date(iso + 'T00:00:00').toLocaleDateString('en-ZA', { day: 'numeric', month: 'short' })
const rate = (n: number, d: number) => (d > 0 ? n / d : null)

export default function DashboardTab({ onNavigate, onCreate }: { onNavigate: (tab: string) => void; onCreate: () => void }) {
  const [days, setDays] = useState(30)

  const { data: summary } = useQuery<any>({
    queryKey: ['marketing-summary'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/marketing/summary'); return r.data?.data ?? r.data },
  })
  const { data: dash, isLoading } = useQuery<any>({
    queryKey: ['marketing-dashboard', days],
    queryFn: async () => (await apiClient.get('/api/v1/marketing/analytics/dashboard', { params: { days } })).data?.data,
  })
  const { data: campaigns = [] } = useQuery<any[]>({
    queryKey: ['marketing-campaigns'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/marketing/campaigns?size=100&sort=createdAt,desc')
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })

  const cur = dash?.current, prev = dash?.previous
  const series: any[] = dash?.series ?? []

  const sources = useMemo(() => {
    const all: any[] = dash?.sources ?? []
    const top = all.slice(0, 5).map((s, i) => ({ label: SOURCE_LABEL[s.source] ?? s.source, value: s.count, color: PALETTE[i] }))
    const rest = all.slice(5).reduce((a, s) => a + s.count, 0)
    if (rest > 0) top.push({ label: 'Other', value: rest, color: PALETTE[5] })
    return top
  }, [dash])
  const totalSources = sources.reduce((a, s) => a + s.value, 0)

  const recent = campaigns.slice(0, 5)
  const upcoming = campaigns.filter(c => c.status === 'SCHEDULED' && c.scheduledAt)
    .sort((a, b) => +new Date(a.scheduledAt) - +new Date(b.scheduledAt)).slice(0, 4)

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18, flexWrap: 'wrap', gap: 10 }}>
        <div style={{ fontSize: 13, color: C.text }}>Turn your audience into loyal customers.</div>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <select value={days} onChange={e => setDays(Number(e.target.value))} style={{ padding: '8px 12px', border: `1px solid ${C.line}`, borderRadius: 8, fontSize: 13, background: '#fff' }}>
            {[7, 30, 90].map(d => <option key={d} value={d}>Last {d} days</option>)}
          </select>
          <Btn variant="primary" onClick={onCreate}><Plus size={14} /> Create Campaign</Btn>
        </div>
      </div>

      {(summary?.consentExceptions ?? 0) > 0 && (
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 10, marginBottom: 16, fontSize: 12, color: '#991B1B' }}>
          <AlertTriangle size={15} /> {summary.consentExceptions} contact(s) are marked opted in but are on the suppression list. They are excluded from every send.
          <Btn onClick={() => onNavigate('contacts')} style={{ marginLeft: 'auto', padding: '4px 10px', fontSize: 11 }}>Review</Btn>
        </div>)}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 14, marginBottom: 18 }}>
        <Kpi label="Total contacts" value={fmtN(summary?.totalContacts)} sub={`${fmtN(summary?.optedInCount)} opted in`} />
        <Kpi label="Campaigns sent" value={fmtN(cur?.campaigns)} delta={change(cur?.campaigns, prev?.campaigns)} />
        <Kpi label="Open rate (approx.)" value={pct(cur?.openRate)} delta={change(cur?.openRate, prev?.openRate)} />
        <Kpi label="Click rate" value={pct(cur?.clickRate)} delta={change(cur?.clickRate, prev?.clickRate)} />
        <Kpi label="Unsubscribe rate" value={pct(cur?.unsubscribeRate, 2)} delta={change(cur?.unsubscribeRate, prev?.unsubscribeRate)} invert />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.7fr) minmax(0, 1fr)', gap: 16, marginBottom: 18 }}>
        <Card title="Campaign performance">
          {isLoading ? <div style={{ padding: 40, textAlign: 'center', color: C.mute }}>Loading…</div>
            : (cur?.sent ?? 0) === 0 && series.every(p => p.opened === 0 && p.clicked === 0)
              ? <Empty title="Nothing sent in this period" text="Performance appears once a campaign has been sent." />
              : <LineChart labels={series.map(p => dayLabel(p.date))} series={[
                  { name: 'Sent', color: C.primary, values: series.map(p => p.sent) },
                  { name: 'Opened (approx.)', color: C.green, values: series.map(p => p.opened) },
                  { name: 'Clicked', color: C.purple, values: series.map(p => p.clicked) }]} />}
          <div style={{ fontSize: 11, color: C.mute, marginTop: 8 }}>Sent by send date; opens and clicks by the day they were first recorded (South African time).</div>
        </Card>

        <Card title="Contact sources" action={<button onClick={() => onNavigate('contacts')} style={{ background: 'none', border: 'none', color: C.primary, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>View all</button>}>
          {sources.length === 0 ? <Empty title="No contacts yet" /> : (
            <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap', justifyContent: 'center' }}>
              <Donut slices={sources} center={fmtN(totalSources)} sub="Total contacts" />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12 }}>
                {sources.map(s => (
                  <div key={s.label} style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 150 }}>
                    <span style={{ width: 9, height: 9, borderRadius: '50%', background: s.color }} />
                    <span style={{ color: C.text, flex: 1 }}>{s.label}</span>
                    <span style={{ fontWeight: 700, color: C.ink }}>{Math.round((s.value / totalSources) * 100)}%</span>
                  </div>))}
              </div>
            </div>)}
        </Card>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.7fr) minmax(0, 1fr)', gap: 16 }}>
        <Card title="Recent campaigns" action={<button onClick={() => onNavigate('campaigns')} style={{ background: 'none', border: 'none', color: C.primary, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>View all campaigns</button>} pad={0}>
          {recent.length === 0 ? <Empty title="No campaigns yet" text="Create your first campaign to see it here." /> : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                <thead><tr style={{ borderBottom: `1px solid ${C.line}` }}>
                  {['Campaign', 'Audience', 'Sent', 'Open rate', 'Click rate', 'Status', 'Date'].map(h => <th key={h} style={{ padding: '10px 16px', textAlign: 'left', fontSize: 11, color: C.mute, fontWeight: 700, whiteSpace: 'nowrap' }}>{h}</th>)}
                </tr></thead>
                <tbody>
                  {recent.map(c => { const s = STATUS[c.status] ?? STATUS.DRAFT; return (
                    <tr key={c.id} style={{ borderBottom: '1px solid #F1F5F9' }}>
                      <td style={{ padding: '11px 16px', fontWeight: 700, color: C.ink }}>{c.name}</td>
                      <td style={{ padding: '11px 16px', color: C.text }}>{c.audienceType === 'SEGMENT' ? (c.segmentName ?? 'Segment') : 'All opted-in'}</td>
                      <td style={{ padding: '11px 16px' }}>{fmtN(c.sentCount)}</td>
                      <td style={{ padding: '11px 16px' }}>{pct(rate(c.openCount, c.sentCount))}</td>
                      <td style={{ padding: '11px 16px' }}>{pct(rate(c.clickCount, c.sentCount))}</td>
                      <td style={{ padding: '11px 16px' }}><Pill color={s.c} bg={s.bg}>{s.l}</Pill></td>
                      <td style={{ padding: '11px 16px', color: C.mute, fontSize: 12 }}>{fmtDate(c.sentAt ?? c.scheduledAt ?? c.createdAt)}</td>
                    </tr>) })}
                </tbody>
              </table>
            </div>)}
        </Card>

        <Card title="Upcoming" action={<button onClick={() => onNavigate('campaigns')} style={{ background: 'none', border: 'none', color: C.primary, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>View campaigns</button>}>
          {upcoming.length === 0 ? <Empty icon={<CalendarClock size={30} />} title="Nothing scheduled" text="Scheduled campaigns appear here." /> : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {upcoming.map(c => (
                <div key={c.id} style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                  <div style={{ width: 36, height: 36, borderRadius: 9, background: '#EFF6FF', color: C.primary, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><CalendarClock size={16} /></div>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 700, color: C.ink, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.name}</div>
                    <div style={{ fontSize: 11, color: C.mute }}>{fmtDT(c.scheduledAt)}{c.reviewStatus && c.reviewStatus !== 'NOT_SUBMITTED' ? ` · ${c.reviewStatus.replace(/_/g, ' ').toLowerCase()}` : ''}</div>
                  </div>
                </div>))}
            </div>)}
        </Card>
      </div>
    </div>
  )
}