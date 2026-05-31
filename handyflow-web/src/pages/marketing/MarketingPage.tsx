import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, Megaphone, Send, Users, FileText, X } from 'lucide-react'

interface Campaign {
  id: string; name: string; status: string; channel: string
  templateName: string; audienceType: string; recipientCount: number
  sentCount: number; fromName: string | null; scheduledAt: string | null
  sentAt: string | null; createdAt: string
}
interface Template {
  id: string; name: string; subject: string; category: string; createdAt: string
}

const STATUS_CONFIG: Record<string, { color: string; bg: string; label: string }> = {
  DRAFT:    { color: '#64748B', bg: '#F8FAFC', label: 'Draft'    },
  SENDING:  { color: '#D97706', bg: '#FFFBEB', label: 'Sending'  },
  SENT:     { color: '#166534', bg: '#DCFCE7', label: 'Sent'     },
  SCHEDULED:{ color: '#1D4ED8', bg: '#EFF6FF', label: 'Scheduled'},
  FAILED:   { color: '#DC2626', bg: '#FEF2F2', label: 'Failed'   },
}

type Tab = 'campaigns' | 'templates' | 'contacts'

export function MarketingPage() {
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState<Tab>('campaigns')
  const [showCreate, setShowCreate] = useState(false)
  const [createTemplate, setCreateTemplate] = useState(false)
  const [error, setError] = useState('')

  const [campForm, setCampForm] = useState({ name: '', templateId: '', audienceType: 'ALL_OPTED_IN', fromName: '' })
  const cf = (k: keyof typeof campForm, v: string) => setCampForm(p => ({ ...p, [k]: v }))

  const [tmplForm, setTmplForm] = useState({ name: '', subject: '', htmlBody: '', category: 'NEWSLETTER', previewText: '' })
  const tf = (k: keyof typeof tmplForm, v: string) => setTmplForm(p => ({ ...p, [k]: v }))

  const { data: summary } = useQuery({
    queryKey: ['marketing-summary'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/marketing/summary'); return r.data },
  })
  const { data: campaigns = [], isLoading: loadingCampaigns } = useQuery<Campaign[]>({
    queryKey: ['marketing-campaigns'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/marketing/campaigns?size=50'); return r.data?.content || [] },
  })
  const { data: templates = [] } = useQuery<Template[]>({
    queryKey: ['marketing-templates'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/marketing/templates?size=50'); return r.data?.content || [] },
  })
  const { data: contacts } = useQuery({
    queryKey: ['marketing-contacts'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/marketing/contacts?size=200'); return r.data },
  })

  const createCampaign = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/marketing/campaigns', body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketing-campaigns'] }); setShowCreate(false); setCampForm({ name: '', templateId: '', audienceType: 'ALL_OPTED_IN', fromName: '' }) },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create campaign'),
  })
  const launchCampaign = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/marketing/campaigns/${id}/launch`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['marketing-campaigns'] }),
  })
  const syncCrm = useMutation({
    mutationFn: () => apiClient.post('/api/v1/marketing/contacts/sync-crm'),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketing-contacts'] }); qc.invalidateQueries({ queryKey: ['marketing-summary'] }) },
  })
  const createTmpl = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/marketing/templates', body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketing-templates'] }); setCreateTemplate(false); setTmplForm({ name: '', subject: '', htmlBody: '', category: 'NEWSLETTER', previewText: '' }) },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create template'),
  })

  const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'

  const stats = [
    { label: 'Total Contacts',  value: summary?.totalContacts  ?? 0, color: '#1B3A6B' },
    { label: 'Opted In',        value: summary?.optedInCount   ?? 0, color: '#166534' },
    { label: 'Campaigns Sent',  value: summary?.sentCampaigns  ?? 0, color: '#0D9488' },
    { label: 'Queue Pending',   value: summary?.queuePending   ?? 0, color: '#D97706' },
  ]

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0F172A', margin: '0 0 4px' }}>Marketing</h1>
        <p style={{ fontSize: 14, color: '#64748B', margin: 0 }}>Email campaigns, templates and POPIA-compliant contact management</p>
      </div>

      <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, minWidth: 120, background: '#fff', border: '1px solid #E2E8F0', borderRadius: 12, padding: '16px 20px' }}>
            <div style={{ fontSize: 26, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: '#64748B', marginTop: 3 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 12, padding: 24 }}>
        {/* Tabs */}
        <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid #E2E8F0', marginBottom: 24 }}>
          {(['campaigns', 'templates', 'contacts'] as Tab[]).map(tab => (
            <button key={tab} onClick={() => setActiveTab(tab)} style={{
              display: 'flex', alignItems: 'center', gap: 7, padding: '10px 18px',
              background: 'none', border: 'none', borderBottom: activeTab === tab ? '2px solid #0D9488' : '2px solid transparent',
              color: activeTab === tab ? '#0D9488' : '#64748B', fontWeight: activeTab === tab ? 600 : 400,
              fontSize: 14, cursor: 'pointer', marginBottom: -1,
            }}>
              {tab === 'campaigns' ? <Megaphone size={15} /> : tab === 'templates' ? <FileText size={15} /> : <Users size={15} />}
              {tab.charAt(0).toUpperCase() + tab.slice(1)}
            </button>
          ))}
        </div>

        {activeTab === 'campaigns' && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
              <button onClick={() => { setShowCreate(true); setError('') }} style={btnPrimary}><Plus size={15} /> New Campaign</button>
            </div>
            {loadingCampaigns ? (
              <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading...</div>
            ) : campaigns.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
                <Megaphone size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
                <div style={{ fontWeight: 600, color: '#475569' }}>No campaigns yet</div>
              </div>
            ) : (
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead><tr style={{ background: '#F8FAFC' }}>
                    {['Campaign', 'Template', 'Audience', 'Sent', 'Status', 'Actions'].map(h => <th key={h} style={th}>{h}</th>)}
                  </tr></thead>
                  <tbody>
                    {campaigns.map((c, i) => {
                      const cfg = STATUS_CONFIG[c.status] || STATUS_CONFIG.DRAFT
                      return (
                        <tr key={c.id} style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA' }}>
                          <td style={td}>
                            <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>{c.name}</div>
                            <div style={{ fontSize: 11, color: '#94A3B8' }}>{fmtDate(c.createdAt)}</div>
                          </td>
                          <td style={td}><span style={{ fontSize: 13, color: '#475569' }}>{c.templateName}</span></td>
                          <td style={td}><span style={{ fontSize: 12, color: '#64748B' }}>{c.audienceType.replace('_', ' ')}</span></td>
                          <td style={td}><span style={{ fontWeight: 600, color: '#0F172A' }}>{c.sentCount}/{c.recipientCount}</span></td>
                          <td style={td}><span style={{ background: cfg.bg, color: cfg.color, padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{cfg.label}</span></td>
                          <td style={td}>
                            {c.status === 'DRAFT' && (
                              <button onClick={() => launchCampaign.mutate(c.id)}
                                style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 7, fontSize: 12, cursor: 'pointer' }}>
                                <Send size={12} /> Launch
                              </button>
                            )}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {activeTab === 'templates' && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
              <button onClick={() => { setCreateTemplate(true); setError('') }} style={btnPrimary}><Plus size={15} /> New Template</button>
            </div>
            {templates.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
                <FileText size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
                <div style={{ fontWeight: 600, color: '#475569' }}>No templates yet</div>
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 14 }}>
                {templates.map(t => (
                  <div key={t.id} style={{ border: '1px solid #E2E8F0', borderRadius: 12, padding: '18px 20px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                      <div style={{ width: 36, height: 36, borderRadius: 8, background: '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <FileText size={17} color="#1D4ED8" />
                      </div>
                      <span style={{ background: '#F1F5F9', color: '#64748B', padding: '2px 8px', borderRadius: 20, fontSize: 11 }}>{t.category}</span>
                    </div>
                    <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A', marginBottom: 3 }}>{t.name}</div>
                    <div style={{ fontSize: 12, color: '#94A3B8', marginBottom: 6 }}>{t.subject}</div>
                    <div style={{ fontSize: 11, color: '#CBD5E1' }}>{fmtDate(t.createdAt)}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {activeTab === 'contacts' && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <div style={{ fontSize: 14, color: '#64748B' }}>{contacts?.content?.length || 0} contacts</div>
              <button onClick={() => syncCrm.mutate()} disabled={syncCrm.isPending} style={btnPrimary}>
                <Users size={15} /> {syncCrm.isPending ? 'Syncing...' : 'Sync from CRM'}
              </button>
            </div>
            {(!contacts?.content || contacts.content.length === 0) ? (
              <div style={{ textAlign: 'center', padding: '40px 20px', color: '#94A3B8' }}>
                <Users size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
                <div style={{ fontWeight: 600, color: '#475569' }}>No contacts yet</div>
                <div style={{ fontSize: 13, marginTop: 4 }}>Sync from CRM to import your customers.</div>
              </div>
            ) : (
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead><tr style={{ background: '#F8FAFC' }}>
                    {['Name', 'Email', 'Source', 'Opted In'].map(h => <th key={h} style={th}>{h}</th>)}
                  </tr></thead>
                  <tbody>
                    {(contacts.content as any[]).map((c: any, i: number) => (
                      <tr key={c.id} style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA' }}>
                        <td style={td}><span style={{ fontWeight: 600, fontSize: 13 }}>{c.name}</span></td>
                        <td style={td}><span style={{ fontSize: 13, color: '#475569' }}>{c.email}</span></td>
                        <td style={td}><span style={{ fontSize: 12, color: '#94A3B8' }}>{c.optInSource || '—'}</span></td>
                        <td style={td}>
                          <span style={{ background: c.emailOptedIn ? '#DCFCE7' : '#F8FAFC', color: c.emailOptedIn ? '#166534' : '#94A3B8', padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>
                            {c.emailOptedIn ? 'Yes' : 'No'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Create Campaign Modal */}
      {showCreate && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 480, boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>New Campaign</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={20} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <F label="Campaign Name *"><input value={campForm.name} onChange={e => cf('name', e.target.value)} placeholder="May 2026 Newsletter" style={inp} /></F>
              <F label="Template *">
                <select value={campForm.templateId} onChange={e => cf('templateId', e.target.value)} style={inp}>
                  <option value="">Select template...</option>
                  {templates.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                </select>
              </F>
              <F label="From Name"><input value={campForm.fromName} onChange={e => cf('fromName', e.target.value)} placeholder="Your Business Name" style={inp} /></F>
              <F label="Audience">
                <select value={campForm.audienceType} onChange={e => cf('audienceType', e.target.value)} style={inp}>
                  <option value="ALL_OPTED_IN">All opted-in contacts</option>
                  <option value="ALL_CONTACTS">All contacts</option>
                </select>
              </F>
            </div>
            {error && <div style={{ marginTop: 10, color: '#DC2626', fontSize: 13 }}>{error}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => createCampaign.mutate({ name: campForm.name, templateId: campForm.templateId, audienceType: campForm.audienceType, fromName: campForm.fromName || null })}
                disabled={!campForm.name || !campForm.templateId || createCampaign.isPending} style={btnPrimary}>
                {createCampaign.isPending ? 'Creating...' : 'Create Campaign'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create Template Modal */}
      {createTemplate && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 540, maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>New Email Template</h3>
              <button onClick={() => setCreateTemplate(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={20} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <F label="Template Name *"><input value={tmplForm.name} onChange={e => tf('name', e.target.value)} placeholder="May Newsletter" style={inp} /></F>
              <F label="Email Subject *"><input value={tmplForm.subject} onChange={e => tf('subject', e.target.value)} placeholder="Hello {{first_name}} — news from {{company_name}}" style={inp} /></F>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <F label="Category">
                  <select value={tmplForm.category} onChange={e => tf('category', e.target.value)} style={inp}>
                    {['NEWSLETTER','PROMOTIONAL','TRANSACTIONAL','ANNOUNCEMENT'].map(c => <option key={c}>{c}</option>)}
                  </select>
                </F>
                <F label="Preview Text"><input value={tmplForm.previewText} onChange={e => tf('previewText', e.target.value)} placeholder="Short preview shown in inbox" style={inp} /></F>
              </div>
              <F label="HTML Body *">
                <textarea value={tmplForm.htmlBody} onChange={e => tf('htmlBody', e.target.value)} rows={6} placeholder="<h2>Hi {{first_name}},</h2><p>Your message here...</p>" style={{ ...inp, resize: 'vertical' as const, fontFamily: 'monospace', fontSize: 13 }} />
              </F>
              <div style={{ padding: '10px 14px', background: '#F0F9FF', borderRadius: 8, fontSize: 12, color: '#0369A1' }}>
                Use {'{{first_name}}'}, {'{{company_name}}'} as personalization tokens. Unsubscribe link is auto-injected.
              </div>
            </div>
            {error && <div style={{ marginTop: 10, color: '#DC2626', fontSize: 13 }}>{error}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 }}>
              <button onClick={() => setCreateTemplate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => createTmpl.mutate({ name: tmplForm.name, subject: tmplForm.subject, htmlBody: tmplForm.htmlBody, category: tmplForm.category, previewText: tmplForm.previewText || null })}
                disabled={!tmplForm.name || !tmplForm.subject || !tmplForm.htmlBody || createTmpl.isPending} style={btnPrimary}>
                {createTmpl.isPending ? 'Creating...' : 'Create Template'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function F({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: '#374151', marginBottom: 5 }}>{label}</label>{children}</div>
}
const btnPrimary: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 500, cursor: 'pointer' }
const btnCancel: React.CSSProperties  = { padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 14, cursor: 'pointer', color: '#374151' }
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff' }
const th: React.CSSProperties = { padding: '10px 16px', textAlign: 'left', fontSize: 11, fontWeight: 600, color: '#64748B', letterSpacing: '0.05em', borderBottom: '1px solid #E2E8F0' }
const td: React.CSSProperties = { padding: '12px 16px', fontSize: 13, borderBottom: '1px solid #F1F5F9' }
