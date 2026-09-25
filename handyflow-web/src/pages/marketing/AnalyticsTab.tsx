// src/pages/marketing/AnalyticsTab.tsx
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Send, Eye, MousePointer, XCircle, AlertTriangle,
  TrendingUp, BarChart2, Users, CheckCircle,
} from 'lucide-react'

const pct = (n: number, d: number) => d > 0 ? `${Math.round((n / d) * 100)}%` : '—'
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'

const STATUS_COLOR: Record<string, string> = {
  SENT: '#166534', SENDING: '#D97706', DRAFT: '#94A3B8', SCHEDULED: '#1D4ED8', CANCELLED: '#CBD5E1', FAILED: '#DC2626', PAUSED: '#9333EA',
}

interface Props { summary: any }

export default function AnalyticsTab({ summary }: Props) {
  const { data: campaigns = [] } = useQuery<any[]>({
    queryKey: ['marketing-campaigns'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/marketing/campaigns?size=100&sort=createdAt,desc')
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })

  const sentCampaigns = (campaigns as any[]).filter(c => ['SENT','SENDING'].includes(c.status))

  // Aggregate stats
  const totalRecipients = sentCampaigns.reduce((s, c) => s + (c.recipientCount ?? 0), 0)
  const totalSent       = sentCampaigns.reduce((s, c) => s + (c.sentCount ?? 0), 0)
  const totalOpens      = sentCampaigns.reduce((s, c) => s + (c.openCount ?? 0), 0)
  const totalClicks     = sentCampaigns.reduce((s, c) => s + (c.clickCount ?? 0), 0)
  const totalBounces    = sentCampaigns.reduce((s, c) => s + (c.bouncedCount ?? 0), 0)
  const totalUnsubs     = sentCampaigns.reduce((s, c) => s + (c.unsubscribedCount ?? 0), 0)

  const avgOpenRate  = pct(totalOpens,  totalSent)
  const avgClickRate = pct(totalClicks, totalSent)
  const ctor         = totalOpens > 0 ? pct(totalClicks, totalOpens) : '—'
  const bounceRate   = pct(totalBounces, totalSent)
  const unsubRate    = pct(totalUnsubs,  totalSent)

  return (
    <div>
      {/* Aggregate KPIs */}
      <div style={{ marginBottom: 28 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 14 }}>Portfolio overview — all sent campaigns</div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
          {[
            { label: 'Emails delivered', value: totalSent.toLocaleString(),   sub: `of ${totalRecipients.toLocaleString()} recipients`, color: 'var(--hf-primary-text)', bg: 'var(--hf-indigo-soft)', icon: <Send size={16} /> },
            { label: 'Avg open rate',    value: avgOpenRate,   sub: `${totalOpens.toLocaleString()} opens`, color: 'var(--hf-sky-text)', bg: 'var(--hf-sky-soft-strong)', icon: <Eye size={16} /> },
            { label: 'Avg click rate',   value: avgClickRate,  sub: `CTOR: ${ctor}`,                        color: 'var(--hf-violet-text)', bg: 'var(--hf-violet-soft)', icon: <MousePointer size={16} /> },
            { label: 'Bounce rate',      value: bounceRate,    sub: `${totalBounces} bounced`,             color: totalBounces > 0 ? 'var(--hf-danger-text)' : 'var(--hf-text-faint)', bg: totalBounces > 0 ? 'var(--hf-danger-soft)' : 'var(--hf-surface-muted)', icon: <AlertTriangle size={16} /> },
          ].map(k => (
            <div key={k.label} style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: '16px 18px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
                <div style={{ width: 34, height: 34, borderRadius: 9, background: k.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', color: k.color }}>{k.icon}</div>
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: k.color, letterSpacing: '-0.02em' }}>{k.value}</div>
              <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 4 }}>{k.label}</div>
              <div style={{ fontSize: 11, color: k.color, opacity: 0.7, marginTop: 2 }}>{k.sub}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Industry benchmarks */}
      <div style={{ marginBottom: 28, padding: '16px 20px', background: 'var(--hf-surface-muted)', border: '1px solid var(--hf-border)', borderRadius: 12 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 14 }}>SA / Africa industry benchmarks</div>
        <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
          {[
            { metric: 'Open rate',    yours: avgOpenRate,  bench: '22–28%', good: true },
            { metric: 'Click rate',   yours: avgClickRate, bench: '2.5–4%', good: true },
            { metric: 'Bounce rate',  yours: bounceRate,   bench: '< 2%',   good: false },
            { metric: 'Unsub rate',   yours: unsubRate,    bench: '< 0.5%', good: false },
          ].map(b => (
            <div key={b.metric} style={{ minWidth: 140 }}>
              <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', fontWeight: 600, marginBottom: 4 }}>{b.metric}</div>
              <div style={{ fontSize: 18, fontWeight: 800, color: 'var(--hf-text)' }}>{b.yours}</div>
              <div style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>Benchmark: {b.bench}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Per-campaign breakdown */}
      {sentCampaigns.length > 0 ? (
        <div>
          <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 14 }}>Per-campaign breakdown</div>
          <div style={{ border: '1px solid var(--hf-border)', borderRadius: 12, overflow: 'hidden' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
              <thead>
                <tr style={{ background: 'var(--hf-surface-muted)', borderBottom: '1px solid var(--hf-border)' }}>
                  {['Campaign', 'Sent', 'Delivered', 'Opens', 'Open %', 'Clicks', 'CTR', 'Bounced', 'Unsub', 'Date'].map(h => (
                    <th key={h} style={{ padding: '10px 14px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', whiteSpace: 'nowrap' as const }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {sentCampaigns.map((c: any, i: number) => (
                  <tr key={c.id} style={{ background: i % 2 === 0 ? 'var(--hf-surface)' : 'var(--hf-surface-muted)', borderBottom: '1px solid var(--hf-border-subtle)' }}>
                    <td style={{ padding: '11px 14px' }}>
                      <div style={{ fontWeight: 700, color: 'var(--hf-text)' }}>{c.name}</div>
                      <div style={{ fontSize: 10, color: STATUS_COLOR[c.status] ?? 'var(--hf-text-faint)', fontWeight: 700 }}>{c.status}</div>
                    </td>
                    <td style={{ padding: '11px 14px', fontWeight: 600 }}>{c.sentCount}</td>
                    <td style={{ padding: '11px 14px' }}>
                      {c.sentCount > 0 ? (
                        <span style={{ color: 'var(--hf-success-text-strong)', fontWeight: 700 }}>{pct(c.sentCount, c.recipientCount)}</span>
                      ) : '—'}
                    </td>
                    <td style={{ padding: '11px 14px', color: 'var(--hf-sky-text)', fontWeight: c.openCount > 0 ? 700 : 400 }}>{c.openCount ?? 0}</td>
                    <td style={{ padding: '11px 14px', color: 'var(--hf-sky-text)', fontWeight: 700 }}>{pct(c.openCount ?? 0, c.sentCount)}</td>
                    <td style={{ padding: '11px 14px', color: 'var(--hf-violet-text)', fontWeight: c.clickCount > 0 ? 700 : 400 }}>{c.clickCount ?? 0}</td>
                    <td style={{ padding: '11px 14px', color: 'var(--hf-violet-text)', fontWeight: 700 }}>{pct(c.clickCount ?? 0, c.sentCount)}</td>
                    <td style={{ padding: '11px 14px', color: c.bouncedCount > 0 ? 'var(--hf-danger-text)' : 'var(--hf-text-faint)', fontWeight: c.bouncedCount > 0 ? 700 : 400 }}>{c.bouncedCount}</td>
                    <td style={{ padding: '11px 14px', color: c.unsubscribedCount > 0 ? 'var(--hf-violet-text)' : 'var(--hf-text-faint)' }}>{c.unsubscribedCount}</td>
                    <td style={{ padding: '11px 14px', color: 'var(--hf-text-faint)', fontSize: 11 }}>{fmtDate(c.sentAt ?? c.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div style={{ textAlign: 'center', padding: '50px 20px', color: 'var(--hf-text-faint)', border: '1px dashed var(--hf-border)', borderRadius: 12 }}>
          <BarChart2 size={36} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: 'var(--hf-text-tertiary)', marginBottom: 6 }}>No campaign data yet</div>
          <div style={{ fontSize: 13 }}>Analytics will appear here once campaigns have been launched and sent.</div>
        </div>
      )}

      {/* Deliverability guidance */}
      <div style={{ marginTop: 24, padding: '16px 20px', background: 'var(--hf-warning-soft)', border: '1px solid var(--hf-warning-border)', borderRadius: 12 }}>
        <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--hf-warning-text-deep)', marginBottom: 10 }}>Deliverability checklist</div>
        {[
          { done: false, text: 'Set up SPF record for your sending domain — prevents spoofing and improves inbox placement' },
          { done: false, text: 'Configure DKIM signing — required by Gmail and Yahoo for bulk senders since Feb 2024' },
          { done: false, text: 'Add DMARC policy (p=quarantine) — protects your brand from phishing attacks' },
          { done: true,  text: 'Unsubscribe link is auto-injected into every campaign email' },
          { done: true,  text: 'POPIA opt-in audit trail is stored per contact with timestamp and source' },
          { done: false, text: 'Hard bounce auto-suppression — configure webhook from your SMTP provider to update bounce status' },
        ].map((item, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '6px 0', borderBottom: i < 5 ? '1px solid var(--hf-warning-border)' : 'none' }}>
            {item.done
              ? <CheckCircle size={14} color="#166534" style={{ flexShrink: 0, marginTop: 1 }} />
              : <AlertTriangle size={14} color="#D97706" style={{ flexShrink: 0, marginTop: 1 }} />}
            <div style={{ fontSize: 12, color: item.done ? 'var(--hf-success-text-strong)' : 'var(--hf-warning-text-deep)', lineHeight: 1.5 }}>{item.text}</div>
          </div>
        ))}
      </div>
    </div>
  )
}
