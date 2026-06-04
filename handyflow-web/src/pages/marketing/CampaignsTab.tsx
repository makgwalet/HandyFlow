// src/pages/marketing/CampaignsTab.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, Send, X, Play, Pause, XCircle, Clock, Eye,
  BarChart2, ChevronRight, AlertTriangle, Megaphone,
  MousePointer, CheckCircle, Users,
} from 'lucide-react'

const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
const fmtDT   = (d: any) => d ? new Date(d).toLocaleString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'
const pct     = (n: number, d: number) => d > 0 ? `${Math.round((n / d) * 100)}%` : '—'

const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }

const STATUS: Record<string, { color: string; bg: string; border: string; label: string; dot: string }> = {
  DRAFT:     { color: '#64748B', bg: '#F8FAFC', border: '#E2E8F0', label: 'Draft',     dot: '#94A3B8' },
  SCHEDULED: { color: '#1D4ED8', bg: '#EFF6FF', border: '#BFDBFE', label: 'Scheduled', dot: '#3B82F6' },
  SENDING:   { color: '#D97706', bg: '#FFFBEB', border: '#FDE68A', label: 'Sending',   dot: '#F59E0B' },
  SENT:      { color: '#166534', bg: '#DCFCE7', border: '#86EFAC', label: 'Sent',      dot: '#22C55E' },
  PAUSED:    { color: '#9333EA', bg: '#FAF5FF', border: '#E9D5FF', label: 'Paused',    dot: '#A855F7' },
  CANCELLED: { color: '#94A3B8', bg: '#F1F5F9', border: '#E2E8F0', label: 'Cancelled', dot: '#CBD5E1' },
  FAILED:    { color: '#DC2626', bg: '#FEF2F2', border: '#FECACA', label: 'Failed',    dot: '#EF4444' },
}
const AUDIENCE_TYPES = ['ALL_OPTED_IN', 'SEGMENT', 'MANUAL']

export default function CampaignsTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [selectedCampaign, setSelectedCampaign] = useState<any>(null)
  const [confirmAction, setConfirmAction] = useState<{ id: string; action: string; label: string } | null>(null)
  const [error, setError] = useState('')

  const INIT = () => ({ name: '', templateId: '', audienceType: 'ALL_OPTED_IN', fromName: '', replyTo: '', scheduledAt: '', subject: '', htmlBody: '' })
  const [form, setForm] = useState(INIT())
  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: campaigns = [], isLoading } = useQuery<any[]>({
    queryKey: ['marketing-campaigns'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/marketing/campaigns?size=100&sort=createdAt,desc')
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })

  const { data: templates = [] } = useQuery<any[]>({
    queryKey: ['marketing-templates'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/marketing/templates')
      return r.data?.data ?? r.data ?? []
    },
  })

  const createCampaign = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/marketing/campaigns', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['marketing-campaigns'] })
      qc.invalidateQueries({ queryKey: ['marketing-summary'] })
      setShowCreate(false); setForm(INIT()); setError('')
    },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create campaign'),
  })

  const doAction = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) =>
      apiClient.post(`/api/v1/marketing/campaigns/${id}/${action}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['marketing-campaigns'] })
      qc.invalidateQueries({ queryKey: ['marketing-summary'] })
      setConfirmAction(null)
      // Refresh selected campaign detail
      if (selectedCampaign) {
        apiClient.get(`/api/v1/marketing/campaigns/${selectedCampaign.id}`)
          .then(r => setSelectedCampaign(r.data?.data ?? r.data))
          .catch(() => {})
      }
    },
    onError: (e: any) => { setError(e.response?.data?.message || 'Action failed'); setConfirmAction(null) },
  })

  const openDetail = async (c: any) => {
    try {
      const r = await apiClient.get(`/api/v1/marketing/campaigns/${c.id}`)
      setSelectedCampaign(r.data?.data ?? r.data)
    } catch { setSelectedCampaign(c) }
  }

  const selectedTemplate = templates.find((t: any) => t.id === form.templateId) as any

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 18 }}>
        <button onClick={() => { setShowCreate(true); setError('') }}
          style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
          <Plus size={15} /> New Campaign
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading campaigns...</div>
      ) : (campaigns as any[]).length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
          <Megaphone size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 700, color: '#475569', fontSize: 15, marginBottom: 6 }}>No campaigns yet</div>
          <div style={{ fontSize: 13, marginBottom: 20 }}>Create your first email campaign to start reaching your audience.</div>
          <button onClick={() => setShowCreate(true)} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
            <Plus size={14} /> Create Campaign
          </button>
        </div>
      ) : (
        <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
            <thead>
              <tr style={{ background: '#F8FAFC', borderBottom: '1px solid #E2E8F0' }}>
                {['Campaign', 'Status', 'Template', 'Audience', 'Sent / Recipients', 'Open rate', 'Created', ''].map(h => (
                  <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#64748B', letterSpacing: '0.05em', whiteSpace: 'nowrap' as const }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(campaigns as any[]).map((c, i) => {
                const s = STATUS[c.status] ?? STATUS.DRAFT
                const openRate = c.sentCount > 0 && c.openCount > 0 ? pct(c.openCount, c.sentCount) : '—'
                return (
                  <tr key={c.id} style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA', cursor: 'pointer' }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#F0F9FF'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = i % 2 === 0 ? '#fff' : '#FAFAFA'}>
                    <td style={{ padding: '12px 16px' }} onClick={() => openDetail(c)}>
                      <div style={{ fontWeight: 700, color: '#0F172A' }}>{c.name}</div>
                      {c.fromName && <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 1 }}>From: {c.fromName}</div>}
                    </td>
                    <td style={{ padding: '12px 16px' }} onClick={() => openDetail(c)}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: s.bg, color: s.color, border: `1px solid ${s.border}`, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                        <span style={{ width: 5, height: 5, borderRadius: '50%', background: s.dot }} />{s.label}
                      </span>
                    </td>
                    <td style={{ padding: '12px 16px', color: '#475569' }} onClick={() => openDetail(c)}>{c.templateName ?? '—'}</td>
                    <td style={{ padding: '12px 16px', color: '#64748B' }} onClick={() => openDetail(c)}>
                      {c.audienceType?.replace('_', ' ')}
                    </td>
                    <td style={{ padding: '12px 16px' }} onClick={() => openDetail(c)}>
                      {c.recipientCount > 0 ? (
                        <div>
                          <div style={{ fontWeight: 700, color: '#0F172A' }}>{c.sentCount} / {c.recipientCount}</div>
                          {c.sentCount > 0 && (
                            <div style={{ height: 3, background: '#E2E8F0', borderRadius: 99, marginTop: 4, width: 80 }}>
                              <div style={{ height: '100%', width: `${Math.round(c.sentCount / c.recipientCount * 100)}%`, background: '#0D9488', borderRadius: 99 }} />
                            </div>
                          )}
                        </div>
                      ) : <span style={{ color: '#94A3B8' }}>—</span>}
                    </td>
                    <td style={{ padding: '12px 16px', color: openRate !== '—' ? '#166534' : '#94A3B8', fontWeight: openRate !== '—' ? 700 : 400 }} onClick={() => openDetail(c)}>{openRate}</td>
                    <td style={{ padding: '12px 16px', color: '#94A3B8', fontSize: 11 }} onClick={() => openDetail(c)}>{fmtDate(c.createdAt)}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <div style={{ display: 'flex', gap: 5 }}>
                        {c.status === 'DRAFT' && (
                          <button onClick={e => { e.stopPropagation(); setConfirmAction({ id: c.id, action: 'launch', label: `Launch "${c.name}"? This will queue emails to all opted-in contacts.` }) }}
                            style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 11px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                            <Send size={11} /> Launch
                          </button>
                        )}
                        {c.status === 'SENDING' && (
                          <button onClick={e => { e.stopPropagation(); setConfirmAction({ id: c.id, action: 'pause', label: `Pause "${c.name}"? Pending emails will not be sent until resumed.` }) }}
                            style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 11px', background: '#FFFBEB', color: '#D97706', border: '1px solid #FDE68A', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                            <Pause size={11} /> Pause
                          </button>
                        )}
                        {c.status === 'PAUSED' && (
                          <button onClick={e => { e.stopPropagation(); doAction.mutate({ id: c.id, action: 'launch' }) }}
                            style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 11px', background: '#F0FDF4', color: '#166534', border: '1px solid #86EFAC', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                            <Play size={11} /> Resume
                          </button>
                        )}
                        {['DRAFT','SCHEDULED','PAUSED'].includes(c.status) && (
                          <button onClick={e => { e.stopPropagation(); setConfirmAction({ id: c.id, action: 'cancel', label: `Cancel "${c.name}"? This cannot be undone.` }) }}
                            style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 8px', background: 'none', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 11, color: '#94A3B8', cursor: 'pointer' }}>
                            <XCircle size={11} />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Campaign Detail slide-over */}
      {selectedCampaign && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'flex-end', zIndex: 1000 }}>
          <div style={{ background: '#fff', width: 520, height: '100%', overflowY: 'auto', boxShadow: '-10px 0 40px rgba(0,0,0,0.15)', padding: 28 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 22 }}>
              <div>
                <div style={{ fontWeight: 800, fontSize: 17, color: '#0F172A', marginBottom: 6 }}>{selectedCampaign.name}</div>
                <div style={{ display: 'flex', gap: 8 }}>
                  {(() => { const s = STATUS[selectedCampaign.status] ?? STATUS.DRAFT; return (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: s.bg, color: s.color, border: `1px solid ${s.border}`, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                      <span style={{ width: 5, height: 5, borderRadius: '50%', background: s.dot }} />{s.label}
                    </span>
                  )})()}
                  <span style={{ fontSize: 11, color: '#94A3B8', alignSelf: 'center' }}>{selectedCampaign.channel || 'EMAIL'}</span>
                </div>
              </div>
              <button onClick={() => setSelectedCampaign(null)} style={{ background: '#F1F5F9', border: 'none', borderRadius: '50%', width: 30, height: 30, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#64748B' }}>
                <X size={14} />
              </button>
            </div>

            {/* Analytics cards */}
            {selectedCampaign.recipientCount > 0 && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 22 }}>
                {[
                  { label: 'Recipients', value: selectedCampaign.recipientCount, icon: <Users size={14} />, color: '#1B3A6B', bg: '#EEF2FF' },
                  { label: 'Delivered',  value: selectedCampaign.sentCount,      icon: <CheckCircle size={14} />, color: '#166534', bg: '#DCFCE7' },
                  { label: 'Opens',      value: selectedCampaign.openCount ?? 0, icon: <Eye size={14} />, color: '#0284C7', bg: '#E0F2FE',
                    sub: selectedCampaign.sentCount > 0 ? `${Math.round((selectedCampaign.openCount ?? 0) / selectedCampaign.sentCount * 100)}%` : null },
                  { label: 'Clicks',     value: selectedCampaign.clickCount ?? 0, icon: <MousePointer size={14} />, color: '#7C3AED', bg: '#F5F3FF',
                    sub: selectedCampaign.sentCount > 0 ? `${Math.round((selectedCampaign.clickCount ?? 0) / selectedCampaign.sentCount * 100)}%` : null },
                  { label: 'Bounced',    value: selectedCampaign.bouncedCount,   icon: <AlertTriangle size={14} />, color: '#DC2626', bg: '#FEF2F2' },
                  { label: 'Unsubscribed', value: selectedCampaign.unsubscribedCount, icon: <XCircle size={14} />, color: '#94A3B8', bg: '#F8FAFC' },
                ].map(m => (
                  <div key={m.label} style={{ background: m.bg, borderRadius: 10, padding: '12px 14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <div style={{ fontSize: 10, color: m.color, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', opacity: 0.8 }}>{m.label}</div>
                      <div style={{ fontSize: 20, fontWeight: 800, color: m.color, marginTop: 2 }}>{m.value}</div>
                      {(m as any).sub && <div style={{ fontSize: 11, color: m.color, fontWeight: 600 }}>{(m as any).sub} rate</div>}
                    </div>
                    <div style={{ color: m.color, opacity: 0.5 }}>{m.icon}</div>
                  </div>
                ))}
              </div>
            )}

            {/* Metadata */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {[
                { label: 'Template',   value: selectedCampaign.templateName ?? '—' },
                { label: 'Subject',    value: selectedCampaign.subject ?? '—' },
                { label: 'Audience',   value: selectedCampaign.audienceType?.replace('_', ' ') },
                { label: 'From',       value: selectedCampaign.fromName ?? '—' },
                { label: 'Reply-to',   value: selectedCampaign.replyTo ?? '—' },
                { label: 'Scheduled',  value: fmtDT(selectedCampaign.scheduledAt) },
                { label: 'Sent at',    value: fmtDT(selectedCampaign.sentAt) },
                { label: 'Created',    value: fmtDate(selectedCampaign.createdAt) },
              ].map(row => (
                <div key={row.label} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid #F1F5F9', fontSize: 13 }}>
                  <span style={{ color: '#94A3B8', fontWeight: 600 }}>{row.label}</span>
                  <span style={{ color: '#374151', fontWeight: 500, maxWidth: 300, textAlign: 'right' as const }}>{row.value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Create Campaign Modal */}
      {showCreate && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 560, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>New Campaign</h3>
                <p style={{ margin: '3px 0 0', fontSize: 13, color: '#64748B' }}>Campaign starts as Draft — launch when ready</p>
              </div>
              <button onClick={() => setShowCreate(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <label style={lbl}>Campaign name *</label>
                <input value={form.name} onChange={e => f('name', e.target.value)} placeholder="June 2026 Newsletter" style={inp} autoFocus />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div>
                  <label style={lbl}>From name</label>
                  <input value={form.fromName} onChange={e => f('fromName', e.target.value)} placeholder="Your Business Name" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Reply-to email</label>
                  <input type="email" value={form.replyTo} onChange={e => f('replyTo', e.target.value)} placeholder="hello@yourbusiness.co.za" style={inp} />
                </div>
              </div>
              <div>
                <label style={lbl}>Audience</label>
                <select value={form.audienceType} onChange={e => f('audienceType', e.target.value)} style={{ ...inp, background: '#fff' }}>
                  <option value="ALL_OPTED_IN">All opted-in contacts</option>
                  <option value="SEGMENT">Segment (coming soon)</option>
                  <option value="MANUAL">Manual list</option>
                </select>
              </div>
              <div>
                <label style={lbl}>Template</label>
                <select value={form.templateId} onChange={e => f('templateId', e.target.value)} style={{ ...inp, background: '#fff' }}>
                  <option value="">Select template... (or write content below)</option>
                  {(templates as any[]).map((t: any) => <option key={t.id} value={t.id}>{t.name} — {t.subject}</option>)}
                </select>
              </div>

              {/* Show subject/body fields if no template selected */}
              {!form.templateId && (
                <>
                  <div>
                    <label style={lbl}>Email subject *</label>
                    <input value={form.subject} onChange={e => f('subject', e.target.value)} placeholder="Hello {{first_name}} — news from {{company_name}}" style={inp} />
                  </div>
                  <div>
                    <label style={lbl}>HTML body *</label>
                    <textarea value={form.htmlBody} onChange={e => f('htmlBody', e.target.value)} rows={5} placeholder="<h2>Hi {{first_name}},</h2><p>Your message here...</p>" style={{ ...inp, resize: 'vertical' as const, fontFamily: 'monospace', fontSize: 13 }} />
                  </div>
                </>
              )}

              {form.templateId && selectedTemplate && (
                <div style={{ padding: '10px 14px', background: '#F0FDF9', border: '1px solid #99F6E4', borderRadius: 8, fontSize: 12, color: '#0D9488' }}>
                  Using template: <strong>{selectedTemplate.name}</strong> — Subject: {selectedTemplate.subject}
                </div>
              )}

              <div>
                <label style={lbl}>Schedule for later (optional)</label>
                <input type="datetime-local" value={form.scheduledAt} onChange={e => f('scheduledAt', e.target.value)} style={inp} />
                <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 4 }}>Leave blank to send immediately when launched</div>
              </div>

              <div style={{ padding: '10px 14px', background: '#FFF7ED', border: '1px solid #FED7AA', borderRadius: 8, fontSize: 12, color: '#92400E' }}>
                POPIA: This campaign will only be sent to contacts who have explicitly opted in to receive marketing emails. An unsubscribe link will be auto-injected.
              </div>
            </div>

            {error && <div style={{ marginTop: 12, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 22 }}>
              <button onClick={() => setShowCreate(false)} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: '#fff', fontSize: 14, cursor: 'pointer' }}>Cancel</button>
              <button
                disabled={!form.name || (!form.templateId && (!form.subject || !form.htmlBody)) || createCampaign.isPending}
                onClick={() => createCampaign.mutate({
                  name: form.name, fromName: form.fromName || null, replyTo: form.replyTo || null,
                  audienceType: form.audienceType,
                  templateId: form.templateId || null,
                  subject: form.subject || null, htmlBody: form.htmlBody || null,
                  scheduledAt: form.scheduledAt ? new Date(form.scheduledAt).toISOString() : null,
                  channel: 'EMAIL',
                })}
                style={{ padding: '9px 22px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: 'pointer', opacity: !form.name ? 0.5 : 1 }}>
                {createCampaign.isPending ? 'Creating...' : 'Create Campaign'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirm action modal */}
      {confirmAction && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 420, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 22 }}>
              <div style={{ width: 40, height: 40, borderRadius: '50%', background: confirmAction.action === 'cancel' ? '#FEF2F2' : '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                {confirmAction.action === 'launch' ? <Send size={18} color="#1D4ED8" /> : confirmAction.action === 'pause' ? <Pause size={18} color="#D97706" /> : <XCircle size={18} color="#DC2626" />}
              </div>
              <div>
                <div style={{ fontWeight: 700, fontSize: 15, color: '#0F172A', marginBottom: 6, textTransform: 'capitalize' }}>{confirmAction.action} campaign</div>
                <div style={{ fontSize: 13, color: '#64748B', lineHeight: 1.6 }}>{confirmAction.label}</div>
              </div>
            </div>
            {error && <div style={{ marginBottom: 12, padding: '8px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => { setConfirmAction(null); setError('') }} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 14, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => doAction.mutate({ id: confirmAction.id, action: confirmAction.action })}
                disabled={doAction.isPending}
                style={{ padding: '9px 20px', background: confirmAction.action === 'cancel' ? '#DC2626' : confirmAction.action === 'launch' ? '#1B3A6B' : '#D97706', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: 'pointer', textTransform: 'capitalize' }}>
                {doAction.isPending ? 'Working...' : confirmAction.action === 'launch' ? 'Launch campaign' : confirmAction.action === 'pause' ? 'Pause campaign' : 'Cancel campaign'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
