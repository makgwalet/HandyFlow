// src/pages/marketing/AnalyticsTab.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  X, Info, AlertTriangle, CheckCircle, Download, ChevronLeft, ChevronRight, Search,
  BarChart2, GitCompare, ChevronDown, ChevronUp, RefreshCw,
} from 'lucide-react'

const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
const fmtDT   = (d: any) => d ? new Date(d).toLocaleString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'
const pct = (r: number | null | undefined, digits = 1) => r == null ? '—' : `${(r * 100).toFixed(digits)}%`
const num = (n: number | null | undefined) => (n ?? 0).toLocaleString()

const OUTCOMES = ['ALL', 'SENT', 'DELIVERED', 'OPENED', 'CLICKED', 'BOUNCED', 'COMPLAINED', 'UNSUBSCRIBED', 'FAILED', 'SUPPRESSED', 'PENDING']
const STATUS_COLOR: Record<string, string> = { SENT: '#166534', SENDING: '#D97706', PAUSED: '#9333EA' }
const inp: React.CSSProperties = { padding: '7px 10px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }
const sectionTitle: React.CSSProperties = { fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }

const Tile = ({ label, value, sub, color, bg }: { label: string; value: string; sub?: string; color: string; bg: string }) => (
  <div style={{ background: bg, borderRadius: 12, padding: '14px 16px' }}>
    <div style={{ fontSize: 10, fontWeight: 700, color, textTransform: 'uppercase', letterSpacing: '0.06em', opacity: 0.85 }}>{label}</div>
    <div style={{ fontSize: 24, fontWeight: 800, color, marginTop: 3 }}>{value}</div>
    {sub && <div style={{ fontSize: 11, color, opacity: 0.8, marginTop: 2 }}>{sub}</div>}
  </div>
)

export default function AnalyticsTab(_props: { summary?: any }) {
  const [days, setDays] = useState(90)
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [showCompare, setShowCompare] = useState(false)
  const [drill, setDrill] = useState<any>(null)
  const [showDefs, setShowDefs] = useState(false)

  const { data: overview, isLoading } = useQuery<any>({
    queryKey: ['marketing-analytics-overview', days],
    queryFn: async () => (await apiClient.get('/api/v1/marketing/analytics/overview', { params: { days } })).data?.data,
  })
  const { data: defs = [] } = useQuery<any[]>({
    queryKey: ['marketing-analytics-defs'], enabled: showDefs, staleTime: Infinity,
    queryFn: async () => (await apiClient.get('/api/v1/marketing/analytics/definitions')).data?.data ?? [],
  })
  const { data: compared = [], isFetching: comparing } = useQuery<any[]>({
    queryKey: ['marketing-analytics-compare', selectedIds], enabled: showCompare && selectedIds.length >= 2,
    queryFn: async () => (await apiClient.get('/api/v1/marketing/analytics/compare', { params: { ids: selectedIds.join(',') } })).data?.data ?? [],
  })

  const t = overview?.totals
  const items: any[] = overview?.items ?? []
  const toggle = (id: string) => setSelectedIds(p => p.includes(id) ? p.filter(x => x !== id) : p.length >= 10 ? p : [...p, id])

  return (
    <div>
      {/* Controls */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 10 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#475569' }}>
          Campaigns from the last
          <select value={days} onChange={e => { setDays(Number(e.target.value)); setSelectedIds([]) }} style={inp}>
            {[30, 90, 180, 365].map(d => <option key={d} value={d}>{d} days</option>)}
          </select>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => setShowDefs(s => !s)} style={{ ...inp, display: 'flex', alignItems: 'center', gap: 5, cursor: 'pointer' }}>
            <Info size={13} /> How metrics are defined {showDefs ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
          </button>
          <button disabled={selectedIds.length < 2} onClick={() => setShowCompare(true)}
            style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '7px 14px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: selectedIds.length < 2 ? 'not-allowed' : 'pointer', opacity: selectedIds.length < 2 ? 0.45 : 1 }}>
            <GitCompare size={13} /> Compare{selectedIds.length ? ` (${selectedIds.length})` : ''}
          </button>
        </div>
      </div>

      {showDefs && (
        <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, padding: 16, marginBottom: 18, background: '#F8FAFC' }}>
          <div style={sectionTitle}>Metric definitions</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 10 }}>
            {defs.map((d: any) => (
              <div key={d.key} style={{ fontSize: 12, lineHeight: 1.5 }}>
                <strong style={{ color: '#0F172A' }}>{d.label}</strong> — <span style={{ color: '#475569' }}>{d.definition}</span>
                {d.caveat && <div style={{ color: '#92400E' }}>{d.caveat}</div>}
              </div>
            ))}
          </div>
        </div>
      )}

      {isLoading ? <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading analytics…</div>
        : items.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '50px 20px', color: '#94A3B8', border: '1px dashed #E2E8F0', borderRadius: 12 }}>
            <BarChart2 size={36} style={{ marginBottom: 12, opacity: 0.3 }} />
            <div style={{ fontWeight: 600, color: '#475569', marginBottom: 6 }}>No sent campaigns in this period</div>
            <div style={{ fontSize: 13 }}>Analytics appear once a campaign has been launched.</div>
          </div>
        ) : (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 12, marginBottom: 12 }}>
              <Tile label="Sent" value={num(t.sent)} sub={`${overview.campaigns} campaigns · ${num(t.targeted)} targeted`} color="#1B3A6B" bg="#EEF2FF" />
              <Tile label="Click rate" value={pct(t.clickRate)} sub={`${num(t.clicked)} unique clickers`} color="#7C3AED" bg="#F5F3FF" />
              <Tile label="Open rate (approx.)" value={pct(t.openRate)} sub={`${num(t.opened)} opens`} color="#0284C7" bg="#E0F2FE" />
              <Tile label="Bounce rate" value={pct(t.bounceRate, 2)} sub={`${num(t.bounced)} bounced`} color={(t.bounceRate ?? 0) > 0.02 ? '#DC2626' : '#475569'} bg={(t.bounceRate ?? 0) > 0.02 ? '#FEF2F2' : '#F8FAFC'} />
              <Tile label="Complaint rate" value={pct(t.complaintRate, 3)} sub={`${num(t.complained)} complaints`} color={(t.complaintRate ?? 0) > 0.001 ? '#DC2626' : '#475569'} bg={(t.complaintRate ?? 0) > 0.001 ? '#FEF2F2' : '#F8FAFC'} />
              <Tile label="Unsubscribe rate" value={pct(t.unsubscribeRate, 2)} sub={`${num(t.unsubscribed)} unsubscribed`} color="#9333EA" bg="#FAF5FF" />
            </div>
            <div style={{ fontSize: 11, color: '#94A3B8', marginBottom: 20 }}>
              Unique recipients, recomputed from per-recipient records. Delivery is {t.deliveryConfirmed ? `confirmed for ${num(t.delivered)} recipients (${pct(t.deliveryRate)})` : 'not confirmed: connect a provider webhook to see delivered, bounced-after-acceptance and complaints'}.
              Benchmarks vary by industry, so compare your own campaigns over time rather than to a generic figure.
            </div>

            <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                <thead>
                  <tr style={{ background: '#F8FAFC', borderBottom: '1px solid #E2E8F0' }}>
                    {['', 'Campaign', 'Sent', 'Click rate', 'Open rate', 'CTOR', 'Bounce', 'Complaint', 'Unsub', 'Date'].map(h => (
                      <th key={h} style={{ padding: '10px 14px', textAlign: 'left', fontSize: 11, fontWeight: 700, color: '#64748B', whiteSpace: 'nowrap' }}>{h}</th>))}
                  </tr>
                </thead>
                <tbody>
                  {items.map((c: any, i: number) => (
                    <tr key={c.id} style={{ background: i % 2 ? '#FAFAFA' : '#fff', borderBottom: '1px solid #F1F5F9', cursor: 'pointer' }} onClick={() => setDrill(c)}>
                      <td style={{ padding: '10px 14px' }} onClick={e => e.stopPropagation()}>
                        <input type="checkbox" checked={selectedIds.includes(c.id)} onChange={() => toggle(c.id)} title="Select to compare" />
                      </td>
                      <td style={{ padding: '10px 14px' }}>
                        <div style={{ fontWeight: 700, color: '#0F172A' }}>{c.name} <span style={{ fontSize: 10, color: '#94A3B8' }}>v{c.contentVersion}</span></div>
                        <div style={{ fontSize: 10, color: STATUS_COLOR[c.status] ?? '#94A3B8', fontWeight: 700 }}>{c.status}</div>
                      </td>
                      <td style={{ padding: '10px 14px', fontWeight: 600 }}>{num(c.sent)} <span style={{ color: '#94A3B8', fontWeight: 400 }}>/ {num(c.targeted)}</span></td>
                      <td style={{ padding: '10px 14px', color: '#7C3AED', fontWeight: 700 }}>{pct(c.clickRate)}</td>
                      <td style={{ padding: '10px 14px', color: '#0284C7' }}>{pct(c.openRate)}</td>
                      <td style={{ padding: '10px 14px' }}>{pct(c.clickToOpenRate)}</td>
                      <td style={{ padding: '10px 14px', color: (c.bounceRate ?? 0) > 0.02 ? '#DC2626' : '#475569' }}>{pct(c.bounceRate, 2)}</td>
                      <td style={{ padding: '10px 14px', color: (c.complaintRate ?? 0) > 0.001 ? '#DC2626' : '#475569' }}>{pct(c.complaintRate, 3)}</td>
                      <td style={{ padding: '10px 14px' }}>{pct(c.unsubscribeRate, 2)}</td>
                      <td style={{ padding: '10px 14px', color: '#94A3B8', fontSize: 11 }}>{fmtDate(c.sentAt ?? c.createdAt)}</td>
                    </tr>))}
                </tbody>
              </table>
            </div>
          </>
        )}

      {/* Deliverability checklist */}
      <div style={{ marginTop: 24, padding: '16px 20px', background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 12 }}>
        <div style={{ fontWeight: 700, fontSize: 13, color: '#92400E', marginBottom: 10 }}>Deliverability checklist</div>
        {[
          { done: false, text: 'SPF, DKIM and DMARC are set up for your sending domain. These are required by Gmail and Yahoo for bulk senders and are configured in DNS.' },
          { done: false, text: 'Provider webhooks point at /api/v1/marketing/webhooks/<provider>. This enables delivered, bounce-after-acceptance and complaint tracking and auto-pause.' },
          { done: true, text: 'Unsubscribe link and one-click List-Unsubscribe headers are added to every campaign email.' },
          { done: true, text: 'POPIA opt-in evidence is stored per contact and suppressed addresses are checked again just before every send.' },
        ].map((c, i) => (
          <div key={i} style={{ display: 'flex', gap: 10, padding: '6px 0', borderTop: i ? '1px solid #FEF3C7' : 'none', alignItems: 'flex-start' }}>
            {c.done ? <CheckCircle size={14} color="#166534" style={{ flexShrink: 0, marginTop: 1 }} /> : <AlertTriangle size={14} color="#D97706" style={{ flexShrink: 0, marginTop: 1 }} />}
            <div style={{ fontSize: 12, color: c.done ? '#166534' : '#92400E', lineHeight: 1.5 }}>{c.text}</div>
          </div>))}
      </div>

      {/* Compare modal */}
      {showCompare && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 26, width: 'min(1100px, 94vw)', maxHeight: '90vh', overflow: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Compare campaigns</h3>
              <button onClick={() => setShowCompare(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={18} /></button>
            </div>
            {comparing && !compared.length ? <div style={{ color: '#94A3B8' }}>Loading…</div> : (
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                <thead><tr style={{ borderBottom: '2px solid #E2E8F0' }}>
                  <th style={{ textAlign: 'left', padding: 8 }} />
                  {compared.map((c: any) => <th key={c.id} style={{ textAlign: 'right', padding: 8, minWidth: 130 }}>{c.name}<div style={{ fontSize: 10, color: '#94A3B8', fontWeight: 400 }}>{fmtDate(c.sentAt ?? c.createdAt)}</div></th>)}
                </tr></thead>
                <tbody>
                  {[
                    ['Targeted', (c: any) => num(c.targeted)], ['Sent', (c: any) => num(c.sent)],
                    ['Click rate', (c: any) => pct(c.clickRate)], ['Open rate (approx.)', (c: any) => pct(c.openRate)],
                    ['Click-to-open', (c: any) => pct(c.clickToOpenRate)], ['Bounce rate', (c: any) => pct(c.bounceRate, 2)],
                    ['Complaint rate', (c: any) => pct(c.complaintRate, 3)], ['Unsubscribe rate', (c: any) => pct(c.unsubscribeRate, 2)],
                    ['Delivery rate', (c: any) => c.deliveryConfirmed ? pct(c.deliveryRate) : 'not confirmed'],
                    ['Suppressed', (c: any) => num(c.suppressed)], ['Failed', (c: any) => num(c.failed)],
                  ].map(([label, fn]: any) => (
                    <tr key={label} style={{ borderBottom: '1px solid #F1F5F9' }}>
                      <td style={{ padding: 8, color: '#64748B', fontWeight: 600 }}>{label}</td>
                      {compared.map((c: any) => <td key={c.id} style={{ padding: 8, textAlign: 'right' }}>{fn(c)}</td>)}
                    </tr>))}
                </tbody>
              </table>
            )}
            <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 12 }}>Differences between small campaigns are often noise. Treat gaps under a few percentage points on fewer than ~1,000 sends with caution.</div>
          </div>
        </div>
      )}

      {drill && <CampaignDrill campaign={drill} onClose={() => setDrill(null)} />}
    </div>
  )
}

function CampaignDrill({ campaign, onClose }: { campaign: any; onClose: () => void }) {
  const qc = useQueryClient()
  const id = campaign.id
  const [outcome, setOutcome] = useState('ALL')
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [err, setErr] = useState('')

  const { data: m = campaign } = useQuery<any>({
    queryKey: ['marketing-analytics-campaign', id],
    queryFn: async () => (await apiClient.get(`/api/v1/marketing/analytics/campaigns/${id}`)).data?.data,
    initialData: campaign,
  })
  const { data: timeline = [] } = useQuery<any[]>({
    queryKey: ['marketing-analytics-timeline', id],
    queryFn: async () => (await apiClient.get(`/api/v1/marketing/analytics/campaigns/${id}/timeline`)).data?.data ?? [],
  })
  const { data: links = [] } = useQuery<any[]>({
    queryKey: ['marketing-analytics-links', id],
    queryFn: async () => (await apiClient.get(`/api/v1/marketing/analytics/campaigns/${id}/links`)).data?.data ?? [],
  })
  const { data: recip, isFetching } = useQuery<any>({
    queryKey: ['marketing-analytics-recipients', id, outcome, search, page],
    queryFn: async () => (await apiClient.get(`/api/v1/marketing/analytics/campaigns/${id}/recipients`, { params: { outcome, search: search || undefined, page, size: 25 } })).data?.data,
    placeholderData: (p: any) => p,
  })
  const { data: recon } = useQuery<any>({
    queryKey: ['marketing-analytics-recon', id],
    queryFn: async () => (await apiClient.get(`/api/v1/marketing/analytics/campaigns/${id}/reconciliation`)).data?.data,
  })

  const repair = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/marketing/analytics/campaigns/${id}/reconciliation/repair`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['marketing-analytics-recon', id] })
      qc.invalidateQueries({ queryKey: ['marketing-campaigns'] })
      qc.invalidateQueries({ queryKey: ['marketing-analytics-overview'] })
      setErr('')
    },
    onError: (e: any) => setErr(e.response?.status === 403 ? 'Repairing counters needs the Marketing admin permission.' : (e.response?.data?.message || 'Repair failed')),
  })
  const exportCsv = useMutation({
    mutationFn: async () => {
      const r = await apiClient.get(`/api/v1/marketing/analytics/campaigns/${id}/recipients/export`, { params: { outcome, search: search || undefined }, responseType: 'blob' })
      const url = URL.createObjectURL(r.data as Blob)
      const a = document.createElement('a'); a.href = url; a.download = 'campaign-recipients.csv'; a.click(); URL.revokeObjectURL(url)
    },
    onError: (e: any) => setErr(e.response?.status === 403 ? 'Exporting recipients needs the Marketing admin permission.' : 'Export failed'),
  })

  const maxDay = Math.max(1, ...timeline.map((p: any) => Math.max(p.opened, p.clicked)))
  const stages = [
    { label: 'Targeted', value: m.targeted, color: '#94A3B8' },
    { label: 'Sent', value: m.sent, color: '#1B3A6B' },
    ...(m.deliveryConfirmed ? [{ label: 'Delivered (confirmed)', value: m.delivered, color: '#0D9488' }] : []),
    { label: 'Opened (approx.)', value: m.opened, color: '#0284C7' },
    { label: 'Clicked', value: m.clicked, color: '#7C3AED' },
  ]
  const base = Math.max(1, m.targeted, m.sent)
  const pages = recip?.totalPages ?? 1

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', justifyContent: 'flex-end', zIndex: 1000 }} onClick={onClose}>
      <div style={{ background: '#fff', width: 720, maxWidth: '96vw', height: '100%', overflowY: 'auto', boxShadow: '-10px 0 40px rgba(0,0,0,0.15)', padding: 28 }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 18 }}>
          <div>
            <div style={{ fontWeight: 800, fontSize: 17, color: '#0F172A' }}>{m.name}</div>
            <div style={{ fontSize: 12, color: '#64748B', marginTop: 3 }}>{m.status} · version {m.contentVersion} · {fmtDate(m.sentAt ?? m.createdAt)}</div>
          </div>
          <button onClick={onClose} style={{ background: '#F1F5F9', border: 'none', borderRadius: '50%', width: 30, height: 30, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#64748B' }}><X size={14} /></button>
        </div>

        {err && <div style={{ marginBottom: 14, padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 12, color: '#DC2626' }}>{err}</div>}

        {/* Funnel */}
        <div style={sectionTitle}>Funnel</div>
        <div style={{ marginBottom: 14 }}>
          {stages.map(s => (
            <div key={s.label} style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
              <div style={{ width: 150, fontSize: 12, color: '#475569' }}>{s.label}</div>
              <div style={{ flex: 1, background: '#F1F5F9', borderRadius: 6, height: 18 }}>
                <div style={{ width: `${Math.min(100, (s.value / base) * 100)}%`, background: s.color, height: '100%', borderRadius: 6 }} />
              </div>
              <div style={{ width: 90, fontSize: 12, fontWeight: 700, textAlign: 'right' }}>{num(s.value)}</div>
            </div>))}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: 8, marginBottom: 6 }}>
          {[
            ['Click rate', pct(m.clickRate)], ['Open rate (approx.)', pct(m.openRate)], ['Click-to-open', pct(m.clickToOpenRate)],
            ['Bounce rate', pct(m.bounceRate, 2)], ['Complaint rate', pct(m.complaintRate, 3)], ['Unsub rate', pct(m.unsubscribeRate, 2)],
            ['Suppressed', num(m.suppressed)], ['Failed', num(m.failed)],
          ].map(([l, v]) => (
            <div key={l} style={{ background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 9, padding: '8px 10px' }}>
              <div style={{ fontSize: 10, color: '#94A3B8', fontWeight: 700, textTransform: 'uppercase' }}>{l}</div>
              <div style={{ fontSize: 16, fontWeight: 800, color: '#0F172A' }}>{v}</div>
            </div>))}
        </div>
        {!m.deliveryConfirmed && m.sent > 0 && <div style={{ fontSize: 11, color: '#92400E', marginBottom: 6 }}>Delivery is not confirmed for this campaign, so Delivered and post-acceptance bounces may be understated.</div>}

        {/* Timeline */}
        <div style={{ ...sectionTitle, marginTop: 22 }}>Engagement by day</div>
        {timeline.length === 0 ? <div style={{ fontSize: 12, color: '#94A3B8', marginBottom: 6 }}>No opens or clicks recorded yet.</div> : (
          <div style={{ display: 'flex', gap: 6, alignItems: 'flex-end', height: 110, overflowX: 'auto', paddingBottom: 4 }}>
            {timeline.map((p: any) => (
              <div key={p.date} title={`${p.date}: ${p.opened} opens, ${p.clicked} clicks`} style={{ minWidth: 34, textAlign: 'center' }}>
                <div style={{ display: 'flex', gap: 2, alignItems: 'flex-end', height: 80, justifyContent: 'center' }}>
                  <div style={{ width: 11, height: Math.max(2, (p.opened / maxDay) * 80), background: '#0284C7', borderRadius: '3px 3px 0 0' }} />
                  <div style={{ width: 11, height: Math.max(2, (p.clicked / maxDay) * 80), background: '#7C3AED', borderRadius: '3px 3px 0 0' }} />
                </div>
                <div style={{ fontSize: 9, color: '#94A3B8', marginTop: 3 }}>{p.date.slice(5)}</div>
              </div>))}
          </div>)}
        <div style={{ fontSize: 10, color: '#94A3B8' }}><span style={{ color: '#0284C7' }}>■</span> first opens · <span style={{ color: '#7C3AED' }}>■</span> first clicks · South African time</div>

        {/* Links */}
        <div style={{ ...sectionTitle, marginTop: 22 }}>Link clicks</div>
        {links.length === 0 ? <div style={{ fontSize: 12, color: '#94A3B8' }}>No link-level clicks recorded. Link detail is captured for clicks from now on, so campaigns sent earlier show none.</div> : (
          <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
            {links.map((l: any, i: number) => (
              <div key={l.url} style={{ display: 'flex', gap: 10, padding: '9px 12px', borderTop: i ? '1px solid #F1F5F9' : 'none', fontSize: 12, alignItems: 'center' }}>
                <div style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: '#374151' }} title={l.url}>{l.url}</div>
                <div style={{ fontWeight: 700, width: 90, textAlign: 'right' }}>{num(l.uniqueClickers)} people</div>
                <div style={{ color: '#94A3B8', width: 80, textAlign: 'right' }}>{num(l.totalClicks)} clicks</div>
              </div>))}
          </div>)}

        {/* Recipients */}
        <div style={{ ...sectionTitle, marginTop: 22 }}>Recipients</div>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 10 }}>
          {OUTCOMES.map(o => (
            <button key={o} onClick={() => { setOutcome(o); setPage(0) }}
              style={{ padding: '3px 11px', borderRadius: 20, fontSize: 11, fontWeight: 600, cursor: 'pointer', border: '1px solid #E2E8F0', background: outcome === o ? '#1B3A6B' : '#fff', color: outcome === o ? '#fff' : '#64748B' }}>{o.charAt(0) + o.slice(1).toLowerCase()}</button>))}
        </div>
        <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
          <div style={{ position: 'relative', flex: 1 }}>
            <Search size={13} style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: '#94A3B8' }} />
            <input value={searchInput} onChange={e => setSearchInput(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') { setSearch(searchInput.trim()); setPage(0) } }}
              onBlur={() => { if (searchInput.trim() !== search) { setSearch(searchInput.trim()); setPage(0) } }}
              placeholder="Search name or email, then press Enter" style={{ ...inp, width: '100%', paddingLeft: 28, boxSizing: 'border-box' }} />
          </div>
          <button onClick={() => exportCsv.mutate()} disabled={exportCsv.isPending} style={{ ...inp, display: 'flex', alignItems: 'center', gap: 5, cursor: 'pointer' }}><Download size={13} /> {exportCsv.isPending ? 'Exporting…' : 'Export'}</button>
        </div>
        <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'auto', opacity: isFetching ? 0.7 : 1 }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
            <thead><tr style={{ background: '#F8FAFC' }}>
              {['Recipient', 'Status', 'Sent', 'Delivered', 'Opened', 'Clicked', 'Detail'].map(h => <th key={h} style={{ padding: '8px 10px', textAlign: 'left', fontSize: 10, color: '#64748B', fontWeight: 700 }}>{h}</th>)}
            </tr></thead>
            <tbody>
              {(recip?.content ?? []).map((r: any) => (
                <tr key={r.id} style={{ borderTop: '1px solid #F1F5F9' }}>
                  <td style={{ padding: '8px 10px' }}><div style={{ fontWeight: 600, color: '#0F172A' }}>{r.name || r.email}</div>{r.name && <div style={{ color: '#94A3B8', fontSize: 10 }}>{r.email}</div>}</td>
                  <td style={{ padding: '8px 10px' }}>{r.status}{r.deliveryStatus && r.deliveryStatus !== r.status ? <div style={{ fontSize: 10, color: '#94A3B8' }}>{r.deliveryStatus}</div> : null}</td>
                  <td style={{ padding: '8px 10px', color: '#64748B' }}>{fmtDT(r.sentAt)}</td>
                  <td style={{ padding: '8px 10px', color: '#64748B' }}>{fmtDT(r.deliveredAt)}</td>
                  <td style={{ padding: '8px 10px', color: '#64748B' }}>{fmtDT(r.openedAt)}</td>
                  <td style={{ padding: '8px 10px', color: '#64748B' }}>{fmtDT(r.clickedAt)}</td>
                  <td style={{ padding: '8px 10px', color: '#B91C1C', maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={r.error}>{r.bouncedAt ? 'Bounced' : r.complainedAt ? 'Complained' : r.error ?? ''}</td>
                </tr>))}
              {(recip?.content ?? []).length === 0 && <tr><td colSpan={7} style={{ padding: 20, textAlign: 'center', color: '#94A3B8' }}>No recipients match.</td></tr>}
            </tbody>
          </table>
        </div>
        {(recip?.totalElements ?? 0) > 0 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 10 }}>
            <span style={{ fontSize: 11, color: '#94A3B8' }}>{num(recip.totalElements)} recipients</span>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 11, color: '#94A3B8' }}>Page {page + 1} of {pages}</span>
              <button disabled={page === 0} onClick={() => setPage(p => p - 1)} style={{ display: 'flex', padding: 5, border: '1px solid #E2E8F0', borderRadius: 7, background: '#fff', opacity: page === 0 ? 0.4 : 1, cursor: 'pointer' }}><ChevronLeft size={13} /></button>
              <button disabled={page + 1 >= pages} onClick={() => setPage(p => p + 1)} style={{ display: 'flex', padding: 5, border: '1px solid #E2E8F0', borderRadius: 7, background: '#fff', opacity: page + 1 >= pages ? 0.4 : 1, cursor: 'pointer' }}><ChevronRight size={13} /></button>
            </div>
          </div>)}

        {/* Reconciliation */}
        <div style={{ ...sectionTitle, marginTop: 24 }}>Reconciliation</div>
        {recon && (
          <div style={{ border: `1px solid ${recon.inSync ? '#86EFAC' : '#FDE68A'}`, background: recon.inSync ? '#F0FDF4' : '#FFFBEB', borderRadius: 10, padding: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 700, color: recon.inSync ? '#166534' : '#92400E', marginBottom: 8 }}>
              {recon.inSync ? <CheckCircle size={15} /> : <AlertTriangle size={15} />}
              {recon.inSync ? 'Stored counters match the recipient records.' : 'Stored counters differ from the recipient records.'}
            </div>
            {!recon.inSync && (
              <table style={{ fontSize: 12, borderCollapse: 'collapse', marginBottom: 10 }}>
                <thead><tr><th style={{ textAlign: 'left', paddingRight: 24, color: '#64748B' }}>Metric</th><th style={{ paddingRight: 24, color: '#64748B' }}>Stored</th><th style={{ color: '#64748B' }}>Actual</th></tr></thead>
                <tbody>{recon.diffs.filter((d: any) => d.stored !== d.actual).map((d: any) => (
                  <tr key={d.metric}><td style={{ paddingRight: 24 }}>{d.metric}</td><td style={{ paddingRight: 24, textAlign: 'center' }}>{d.stored}</td><td style={{ textAlign: 'center', fontWeight: 700 }}>{d.actual}</td></tr>))}</tbody>
              </table>)}
            {recon.notes.map((n: string, i: number) => <div key={i} style={{ fontSize: 12, color: '#475569', lineHeight: 1.5, marginBottom: 4 }}>• {n}</div>)}
            {!recon.inSync && (
              <div style={{ marginTop: 8 }}>
                <button disabled={!recon.repairable || repair.isPending}
                  onClick={() => { if (window.confirm('Rewrite this campaign\'s stored counters from the recipient records? This does not change any recipient data.')) repair.mutate() }}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '7px 14px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, fontSize: 12, fontWeight: 700, cursor: recon.repairable ? 'pointer' : 'not-allowed', opacity: recon.repairable ? 1 : 0.5 }}>
                  <RefreshCw size={12} /> {repair.isPending ? 'Repairing…' : 'Repair counters'}
                </button>
                {!recon.repairable && <span style={{ fontSize: 11, color: '#94A3B8', marginLeft: 8 }}>Not available while the campaign is sending.</span>}
              </div>)}
          </div>)}
      </div>
    </div>
  )
}