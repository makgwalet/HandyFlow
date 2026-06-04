// src/pages/marketing/TemplatesTab.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, X, FileText, Edit3, Eye, Send, AlertCircle } from 'lucide-react'

const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }

const CATEGORIES = ['NEWSLETTER','PROMOTIONAL','TRANSACTIONAL','ANNOUNCEMENT','REENGAGEMENT']
const CAT_COLOR: Record<string, string> = {
  NEWSLETTER: '#1B3A6B', PROMOTIONAL: '#D97706', TRANSACTIONAL: '#166534',
  ANNOUNCEMENT: '#0284C7', REENGAGEMENT: '#7C3AED',
}
const TOKENS = ['{{first_name}}','{{name}}','{{email}}','{{company_name}}','{{unsubscribe_url}}']

export default function TemplatesTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editTemplate, setEditTemplate] = useState<any>(null)
  const [previewTemplate, setPreviewTemplate] = useState<any>(null)
  const [error, setError] = useState('')
  const [previewTab, setPreviewTab] = useState<'desktop'|'mobile'>('desktop')

  const INIT = () => ({ name: '', subject: '', htmlBody: '', category: 'NEWSLETTER', previewText: '' })
  const [form, setForm] = useState(INIT())
  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: templates = [], isLoading } = useQuery<any[]>({
    queryKey: ['marketing-templates'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/marketing/templates')
      return r.data?.data ?? r.data ?? []
    },
  })

  const createTemplate = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/marketing/templates', body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketing-templates'] }); setShowCreate(false); setForm(INIT()); setError('') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create template'),
  })

  const updateTemplate = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`/api/v1/marketing/templates/${id}`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketing-templates'] }); setEditTemplate(null); setError('') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to update template'),
  })

  const openEdit = (t: any) => { setForm({ name: t.name, subject: t.subject, htmlBody: t.htmlBody || '', category: t.category || 'NEWSLETTER', previewText: t.previewText || '' }); setEditTemplate(t); setError('') }

  const TemplateForm = ({ title, onSave, onClose, saving }: { title: string; onSave: () => void; onClose: () => void; saving: boolean }) => (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 660, maxHeight: '92vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>{title}</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <label style={lbl}>Template name *</label>
              <input value={form.name} onChange={e => f('name', e.target.value)} placeholder="May 2026 Newsletter" style={inp} autoFocus />
            </div>
            <div>
              <label style={lbl}>Category</label>
              <select value={form.category} onChange={e => f('category', e.target.value)} style={{ ...inp, background: '#fff' }}>
                {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
          </div>
          <div>
            <label style={lbl}>Email subject *</label>
            <input value={form.subject} onChange={e => f('subject', e.target.value)} placeholder="Hello {{first_name}} — news from {{company_name}}" style={inp} />
            <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 4 }}>Personalisation tokens: {TOKENS.slice(0,3).join(', ')}</div>
          </div>
          <div>
            <label style={lbl}>Preview text</label>
            <input value={form.previewText} onChange={e => f('previewText', e.target.value)} placeholder="Short text shown in inbox preview before opening..." style={inp} />
          </div>
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
              <label style={{ ...lbl, marginBottom: 0 }}>HTML body *</label>
              <div style={{ display: 'flex', gap: 6 }}>
                {TOKENS.map(t => (
                  <button key={t} onClick={() => f('htmlBody', form.htmlBody + t)} style={{ padding: '2px 7px', background: '#F0F9FF', border: '1px solid #BAE6FD', borderRadius: 5, fontSize: 10, color: '#0284C7', cursor: 'pointer', fontFamily: 'monospace' }}>{t}</button>
                ))}
              </div>
            </div>
            <textarea value={form.htmlBody} onChange={e => f('htmlBody', e.target.value)} rows={10}
              placeholder={'<h2 style="color:#1B3A6B">Hi {{first_name}},</h2>\n<p>Your message here.</p>\n<p><a href="{{unsubscribe_url}}">Unsubscribe</a></p>'}
              style={{ ...inp, resize: 'vertical' as const, fontFamily: 'monospace', fontSize: 12, lineHeight: 1.5 }} />
          </div>

          {/* Live preview */}
          {form.htmlBody && (
            <div>
              <div style={{ display: 'flex', gap: 8, marginBottom: 8, alignItems: 'center' }}>
                <span style={{ fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Preview</span>
                {(['desktop','mobile'] as const).map(v => (
                  <button key={v} onClick={() => setPreviewTab(v)} style={{ padding: '2px 10px', borderRadius: 20, border: '1px solid #E2E8F0', background: previewTab === v ? '#1B3A6B' : '#fff', color: previewTab === v ? '#fff' : '#64748B', fontSize: 11, cursor: 'pointer', fontWeight: 600, textTransform: 'capitalize' }}>{v}</button>
                ))}
              </div>
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden', background: '#F8FAFC', padding: 12 }}>
                <iframe
                  srcDoc={form.htmlBody.replace('{{first_name}}','Thabo').replace('{{company_name}}','HandyFlow').replace('{{unsubscribe_url}}','#')}
                  style={{ width: previewTab === 'mobile' ? 375 : '100%', height: 300, border: 'none', borderRadius: 8, display: 'block', margin: previewTab === 'mobile' ? '0 auto' : undefined }}
                  title="Email preview"
                />
              </div>
            </div>
          )}

          <div style={{ padding: '10px 14px', background: '#F0F9FF', border: '1px solid #BAE6FD', borderRadius: 8, fontSize: 12, color: '#0369A1' }}>
            Available tokens: <strong>{TOKENS.join(', ')}</strong>. The unsubscribe link is auto-appended if <code>{'{{unsubscribe_url}}'}</code> is not present in your template.
          </div>
        </div>

        {error && <div style={{ marginTop: 12, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 22 }}>
          <button onClick={onClose} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: '#fff', fontSize: 14, cursor: 'pointer' }}>Cancel</button>
          <button onClick={onSave} disabled={!form.name || !form.subject || !form.htmlBody || saving}
            style={{ padding: '9px 22px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: 'pointer', opacity: (!form.name || !form.subject || !form.htmlBody) ? 0.5 : 1 }}>
            {saving ? 'Saving...' : 'Save Template'}
          </button>
        </div>
      </div>
    </div>
  )

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 18 }}>
        <button onClick={() => { setShowCreate(true); setForm(INIT()); setError('') }}
          style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
          <Plus size={15} /> New Template
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading templates...</div>
      ) : (templates as any[]).length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
          <FileText size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 700, color: '#475569', marginBottom: 6 }}>No templates yet</div>
          <div style={{ fontSize: 13, marginBottom: 16 }}>Create reusable email templates for your campaigns.</div>
          <button onClick={() => { setShowCreate(true); setForm(INIT()) }} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
            <Plus size={14} /> New Template
          </button>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 }}>
          {(templates as any[]).map((t: any) => {
            const catColor = CAT_COLOR[t.category] ?? '#64748B'
            return (
              <div key={t.id} style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden', background: '#fff', transition: 'box-shadow 0.15s' }}
                onMouseEnter={e => (e.currentTarget as HTMLElement).style.boxShadow = '0 4px 16px rgba(0,0,0,0.08)'}
                onMouseLeave={e => (e.currentTarget as HTMLElement).style.boxShadow = 'none'}>
                {/* Preview strip */}
                <div style={{ height: 100, overflow: 'hidden', background: '#F8FAFC', position: 'relative' as const }}>
                  {t.htmlBody ? (
                    <iframe srcDoc={t.htmlBody} style={{ width: '200%', height: '200%', border: 'none', transform: 'scale(0.5)', transformOrigin: 'top left', pointerEvents: 'none' }} title={t.name} />
                  ) : (
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#CBD5E1' }}>
                      <FileText size={28} />
                    </div>
                  )}
                </div>
                <div style={{ padding: '14px 16px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                    <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>{t.name}</div>
                    <span style={{ background: `${catColor}15`, color: catColor, padding: '1px 8px', borderRadius: 20, fontSize: 10, fontWeight: 700, flexShrink: 0, marginLeft: 8 }}>{t.category}</span>
                  </div>
                  <div style={{ fontSize: 12, color: '#64748B', marginBottom: 4, lineHeight: 1.4 }}>{t.subject}</div>
                  {t.previewText && <div style={{ fontSize: 11, color: '#94A3B8', marginBottom: 10, lineHeight: 1.4 }}>{t.previewText}</div>}
                  <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                    <button onClick={() => setPreviewTemplate(t)}
                      style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5, padding: '6px', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 12, color: '#374151', cursor: 'pointer', fontWeight: 500 }}>
                      <Eye size={12} /> Preview
                    </button>
                    <button onClick={() => openEdit(t)}
                      style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5, padding: '6px', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 12, color: '#374151', cursor: 'pointer', fontWeight: 500 }}>
                      <Edit3 size={12} /> Edit
                    </button>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Full-screen preview */}
      {previewTemplate && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 16, width: 700, maxHeight: '90vh', overflow: 'hidden', boxShadow: '0 24px 70px rgba(0,0,0,0.3)' }}>
            <div style={{ padding: '16px 20px', borderBottom: '1px solid #E2E8F0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: 15 }}>{previewTemplate.name}</div>
                <div style={{ fontSize: 12, color: '#94A3B8' }}>Subject: {previewTemplate.subject}</div>
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                {(['desktop','mobile'] as const).map(v => (
                  <button key={v} onClick={() => setPreviewTab(v)} style={{ padding: '4px 12px', borderRadius: 20, border: '1px solid #E2E8F0', background: previewTab === v ? '#1B3A6B' : '#fff', color: previewTab === v ? '#fff' : '#64748B', fontSize: 12, cursor: 'pointer', fontWeight: 600, textTransform: 'capitalize' }}>{v}</button>
                ))}
                <button onClick={() => setPreviewTemplate(null)} style={{ background: '#F1F5F9', border: 'none', borderRadius: '50%', width: 30, height: 30, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#64748B' }}><X size={14} /></button>
              </div>
            </div>
            <div style={{ padding: 16, background: '#F8FAFC', display: 'flex', justifyContent: 'center' }}>
              <iframe
                srcDoc={previewTemplate.htmlBody?.replace('{{first_name}}','Thabo').replace('{{company_name}}','HandyFlow').replace('{{unsubscribe_url}}','#')}
                style={{ width: previewTab === 'mobile' ? 375 : '100%', height: 500, border: 'none', background: '#fff', borderRadius: 8, boxShadow: '0 2px 12px rgba(0,0,0,0.08)' }}
                title="Template preview"
              />
            </div>
          </div>
        </div>
      )}

      {showCreate && (
        <TemplateForm title="New Email Template" onClose={() => setShowCreate(false)} saving={createTemplate.isPending}
          onSave={() => createTemplate.mutate({ name: form.name, subject: form.subject, htmlBody: form.htmlBody, category: form.category, previewText: form.previewText || null })} />
      )}
      {editTemplate && (
        <TemplateForm title={`Edit — ${editTemplate.name}`} onClose={() => setEditTemplate(null)} saving={updateTemplate.isPending}
          onSave={() => updateTemplate.mutate({ id: editTemplate.id, body: { name: form.name, subject: form.subject, htmlBody: form.htmlBody, category: form.category, previewText: form.previewText || null } })} />
      )}
    </div>
  )
}
