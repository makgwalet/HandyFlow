// src/pages/marketing/TemplatesTab.tsx
import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, X, Search, FileText, Pencil, MessageSquare } from 'lucide-react'
import BlocksTab from './BlocksTab'
import { C, Card, Btn, Pill, Tabs, Empty, inp, lbl, fmtDate } from './ui'

const CATEGORIES = ['Newsletter', 'Promotional', 'Onboarding', 'Event', 'Re-engagement', 'Announcement']

export default function TemplatesTab() {
  const qc = useQueryClient()
  const [sub, setSub] = useState('email')
  const [search, setSearch] = useState('')
  const [cat, setCat] = useState('')
  const [editing, setEditing] = useState<any>(null)   // {} = new
  const [form, setForm] = useState({ name: '', subject: '', previewText: '', category: '', htmlBody: '' })
  const [error, setError] = useState('')

  const { data: templates = [], isLoading } = useQuery<any[]>({
    queryKey: ['marketing-templates'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/marketing/templates'); return r.data?.data ?? r.data ?? [] },
  })

  const cats = useMemo(() => Array.from(new Set([...(templates as any[]).map(t => t.category).filter(Boolean), ...CATEGORIES])).sort(), [templates])
  const shown = (templates as any[]).filter(t =>
    (!cat || t.category === cat) && (!search.trim() || `${t.name} ${t.subject}`.toLowerCase().includes(search.trim().toLowerCase())))

  const open = (t?: any) => {
    setEditing(t ?? {}); setError('')
    setForm({ name: t?.name ?? '', subject: t?.subject ?? '', previewText: t?.previewText ?? '', category: t?.category ?? '', htmlBody: t?.htmlBody ?? '' })
  }

  const save = useMutation({
    mutationFn: () => {
      const body = { name: form.name.trim(), subject: form.subject.trim(), previewText: form.previewText.trim() || null, category: form.category.trim() || null, htmlBody: form.htmlBody }
      return editing?.id ? apiClient.put(`/api/v1/marketing/templates/${editing.id}`, body) : apiClient.post('/api/v1/marketing/templates', body)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketing-templates'] }); setEditing(null) },
    onError: (e: any) => setError(e.response?.status === 403 ? 'Saving templates needs the Marketing admin permission.' : (e.response?.data?.message || 'Failed to save template')),
  })
  const ok = form.name.trim() && form.subject.trim() && form.htmlBody.trim()

  return (
    <div>
      <div style={{ marginBottom: 18 }}>
        <Tabs active={sub} onChange={setSub} tabs={[
          { id: 'email', label: 'Email templates', icon: <FileText size={14} /> },
          { id: 'blocks', label: 'Content blocks' },
          { id: 'sms', label: 'SMS templates', icon: <MessageSquare size={14} /> }]} />
      </div>

      {sub === 'blocks' && <BlocksTab />}

      {sub === 'sms' && <Empty icon={<MessageSquare size={36} />} title="SMS templates aren’t available yet" text="No SMS provider is connected. Email is the only channel today." />}

      {sub === 'email' && (
        <>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, marginBottom: 16, flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', gap: 8 }}>
              <div style={{ position: 'relative' }}>
                <Search size={13} style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: C.mute }} />
                <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search templates…" style={{ ...inp, width: 240, padding: '8px 10px 8px 30px', fontSize: 13 }} />
              </div>
              <select value={cat} onChange={e => setCat(e.target.value)} style={{ ...inp, width: 'auto', padding: '8px 12px', fontSize: 13 }}>
                <option value="">All categories</option>{cats.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
            <Btn variant="primary" onClick={() => open()}><Plus size={14} /> New template</Btn>
          </div>

          {isLoading ? <div style={{ textAlign: 'center', padding: 40, color: C.mute }}>Loading…</div>
            : shown.length === 0 ? <Empty icon={<FileText size={36} />} title={(templates as any[]).length ? 'No templates match' : 'No templates yet'} text={(templates as any[]).length ? 'Try a different search or category.' : 'Create a reusable layout to start campaigns faster.'} />
            : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))', gap: 16 }}>
                {shown.map((t: any) => (
                  <Card key={t.id} pad={0} style={{ overflow: 'hidden' }}>
                    <div style={{ height: 150, background: C.bg, overflow: 'hidden', borderBottom: `1px solid ${C.line}`, position: 'relative' }}>
                      <iframe sandbox="" srcDoc={t.htmlBody} title={t.name} style={{ width: '200%', height: '200%', border: 'none', transform: 'scale(0.5)', transformOrigin: 'top left', pointerEvents: 'none' }} />
                    </div>
                    <div style={{ padding: '12px 14px' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'flex-start' }}>
                        <div style={{ fontWeight: 700, fontSize: 14, color: C.ink, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.name}</div>
                        {t.category && <Pill color="#1D4ED8" bg="#EFF6FF">{t.category}</Pill>}
                      </div>
                      <div style={{ fontSize: 12, color: C.text, marginTop: 3, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.subject}</div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 10 }}>
                        <span style={{ fontSize: 11, color: C.mute }}>Added {fmtDate(t.createdAt)}</span>
                        <Btn onClick={() => open(t)} style={{ padding: '4px 10px', fontSize: 12 }}><Pencil size={12} /> Edit</Btn>
                      </div>
                    </div>
                  </Card>))}
              </div>)}
          <div style={{ fontSize: 11, color: C.mute, marginTop: 14 }}>Starting a campaign from a template copies its content, so editing a template never changes campaigns that already used it.</div>
        </>)}

      {editing && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 'min(1000px, 96vw)', maxHeight: '92vh', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 18 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>{editing.id ? `Edit — ${editing.name}` : 'New template'}</h3>
              <button onClick={() => setEditing(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: C.mute }}><X size={20} /></button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1fr)', gap: 22 }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div><label style={lbl}>Name *</label><input style={inp} value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} autoFocus /></div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                  <div><label style={lbl}>Category</label><input style={inp} list="tpl-cats" value={form.category} onChange={e => setForm(p => ({ ...p, category: e.target.value }))} /><datalist id="tpl-cats">{cats.map(c => <option key={c} value={c} />)}</datalist></div>
                  <div><label style={lbl}>Preview text</label><input style={inp} value={form.previewText} onChange={e => setForm(p => ({ ...p, previewText: e.target.value }))} /></div>
                </div>
                <div><label style={lbl}>Subject *</label><input style={inp} value={form.subject} onChange={e => setForm(p => ({ ...p, subject: e.target.value }))} placeholder="Hello {{first_name}}" /></div>
                <div><label style={lbl}>HTML *</label><textarea style={{ ...inp, fontFamily: 'monospace', fontSize: 12, resize: 'vertical' }} rows={14} value={form.htmlBody} onChange={e => setForm(p => ({ ...p, htmlBody: e.target.value }))} /></div>
                <div style={{ fontSize: 11, color: C.mute }}>Tip: design visually in a campaign, then copy the HTML here. Placeholders: {'{{first_name}} {{name}} {{email}} {{company_name}} {{unsubscribe_url}}'}</div>
              </div>
              <div>
                <label style={lbl}>Preview (raw, placeholders not filled)</label>
                <iframe sandbox="" srcDoc={form.htmlBody} title="Template preview" style={{ width: '100%', height: 470, border: `1px solid ${C.line}`, borderRadius: 10, background: '#fff' }} />
              </div>
            </div>
            {error && <div style={{ marginTop: 12, padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 18 }}>
              <Btn onClick={() => setEditing(null)}>Cancel</Btn>
              <Btn variant="primary" disabled={!ok || save.isPending} onClick={() => { setError(''); save.mutate() }}>{save.isPending ? 'Saving…' : 'Save template'}</Btn>
            </div>
          </div>
        </div>)}
    </div>
  )
}