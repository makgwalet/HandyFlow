// src/pages/marketing/FormsTab.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, X, Copy, Pencil, Trash2, ClipboardList, Tag, ExternalLink, Check, Download } from 'lucide-react'
import { C, Card, Btn, Pill, Tabs, Empty, Switch, inp, lbl, fmtN } from './ui'

const DEFAULT_CONSENT = 'I agree to receive marketing emails from this business. I can unsubscribe at any time.'
const emptyForm = { name: '', title: '', description: '', buttonLabel: 'Subscribe', consentText: DEFAULT_CONSENT, successMessage: '', collectName: true, topicIds: [] as string[] }

export default function FormsTab() {
  const [sub, setSub] = useState('forms')
  return (
    <div>
      <div style={{ marginBottom: 18 }}>
        <Tabs active={sub} onChange={setSub} tabs={[{ id: 'forms', label: 'Signup forms', icon: <ClipboardList size={14} /> }, { id: 'topics', label: 'Topics', icon: <Tag size={14} /> }]} />
      </div>
      {sub === 'forms' ? <Forms /> : <Topics />}
    </div>
  )
}

function Forms() {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<any>(null)
  const [form, setForm] = useState<any>(emptyForm)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState<string | null>(null)
  const [embed, setEmbed] = useState<string | null>(null)

  const { data: forms = [], isLoading } = useQuery<any[]>({ queryKey: ['marketing-forms'], queryFn: async () => (await apiClient.get('/api/v1/marketing/forms')).data?.data ?? [] })
  const { data: topics = [] } = useQuery<any[]>({ queryKey: ['marketing-topics'], queryFn: async () => (await apiClient.get('/api/v1/marketing/topics')).data?.data ?? [] })
  const refresh = () => qc.invalidateQueries({ queryKey: ['marketing-forms'] })

  const open = (f?: any) => {
    setEditing(f ?? {}); setError('')
    setForm(f ? { name: f.name, title: f.title, description: f.description ?? '', buttonLabel: f.buttonLabel, consentText: f.consentText, successMessage: f.successMessage ?? '', collectName: f.collectName, topicIds: f.topicIds } : emptyForm)
  }
  const save = useMutation({
    mutationFn: () => {
      const body = { ...form, name: form.name.trim(), title: form.title.trim(), description: form.description.trim() || null, successMessage: form.successMessage.trim() || null }
      return editing?.id ? apiClient.put(`/api/v1/marketing/forms/${editing.id}`, body) : apiClient.post('/api/v1/marketing/forms', body)
    },
    onSuccess: () => { refresh(); setEditing(null) },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to save form'),
  })
  const toggle = useMutation({
    mutationFn: (f: any) => apiClient.post(`/api/v1/marketing/forms/${f.id}/${f.status === 'ACTIVE' ? 'disable' : 'enable'}`),
    onSuccess: refresh,
  })
  const exportSubmissions = useMutation({
    mutationFn: async () => {
      const r = await apiClient.get('/api/v1/marketing/forms/submissions/export', { responseType: 'blob' })
      const href = URL.createObjectURL(r.data as Blob)
      const a = document.createElement('a')
      a.href = href; a.download = 'form-submissions.csv'; a.click()
      URL.revokeObjectURL(href)
    },
    onError: (e: any) => setError(e.response?.status === 403 ? 'Exporting submissions needs the Marketing admin permission.' : 'Export failed'),
  })
  const url = (slug: string) => `${window.location.origin}/subscribe/${slug}`
  const copy = (key: string, text: string) => { navigator.clipboard?.writeText(text); setCopied(key); setTimeout(() => setCopied(null), 1800) }
  const ok = form.name.trim() && form.title.trim() && form.consentText.trim().length >= 20

  return (
    <>
      <div style={{ padding: '12px 16px', background: '#F0F9FF', border: '1px solid #BAE6FD', borderRadius: 10, marginBottom: 16, fontSize: 12, color: '#0C4A6E', lineHeight: 1.6 }}>
        Forms use <strong>double opt-in</strong>: submitting only sends a confirmation email, and the contact is opted in when they confirm. The exact consent wording, time and IP are stored as proof of permission.
        People already subscribed or on your suppression list get the same “check your inbox” reply, so the form can’t be used to probe your list.
      </div>
      {error && !editing && <div style={{ marginBottom: 10, padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginBottom: 14 }}>
        <Btn onClick={() => { setError(''); exportSubmissions.mutate() }} disabled={exportSubmissions.isPending}><Download size={14} /> Export submissions</Btn>
        <Btn variant="primary" onClick={() => open()}><Plus size={14} /> New form</Btn></div>

      {isLoading ? <div style={{ textAlign: 'center', padding: 40, color: C.mute }}>Loading…</div>
        : forms.length === 0 ? <Empty icon={<ClipboardList size={36} />} title="No signup forms yet" text="Create a form to collect opted-in subscribers from your website or social media." />
        : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {forms.map((f: any) => (
              <Card key={f.id}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                      <span style={{ fontWeight: 700, fontSize: 15, color: C.ink }}>{f.name}</span>
                      <Pill color={f.status === 'ACTIVE' ? '#166534' : '#64748B'} bg={f.status === 'ACTIVE' ? '#DCFCE7' : '#F1F5F9'}>{f.status === 'ACTIVE' ? 'Live' : 'Disabled'}</Pill>
                    </div>
                    <div style={{ fontSize: 12, color: C.text, marginTop: 3 }}>{f.title} · {f.topicIds.length} topic(s)</div>
                    <div style={{ fontSize: 12, color: C.mute, marginTop: 6 }}>{fmtN(f.requests)} sign-up(s) · {fmtN(f.confirmed)} confirmed{f.requests > 0 ? ` (${Math.round((f.confirmed / f.requests) * 100)}%)` : ''}</div>
                  </div>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <Switch on={f.status === 'ACTIVE'} onChange={() => toggle.mutate(f)} />
                    <Btn onClick={() => open(f)}><Pencil size={13} /> Edit</Btn>
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 8, marginTop: 12, alignItems: 'center', flexWrap: 'wrap' }}>
                  <code style={{ flex: 1, minWidth: 220, fontSize: 12, background: C.bg, padding: '7px 10px', borderRadius: 7, color: C.text, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{url(f.slug)}</code>
                  <Btn onClick={() => copy(f.id, url(f.slug))}>{copied === f.id ? <><Check size={13} /> Copied</> : <><Copy size={13} /> Copy link</>}</Btn>
                  <Btn onClick={() => window.open(url(f.slug), '_blank', 'noopener')}><ExternalLink size={13} /> Open</Btn>
                  <Btn onClick={() => setEmbed(embed === f.id ? null : f.id)}>Embed</Btn>
                </div>
                {embed === f.id && (
                  <div style={{ marginTop: 10 }}>
                    <code style={{ display: 'block', fontSize: 11, background: C.bg, padding: 10, borderRadius: 7, color: C.text, wordBreak: 'break-all' }}>{`<iframe src="${url(f.slug)}" width="100%" height="560" style="border:0"></iframe>`}</code>
                    <div style={{ fontSize: 11, color: C.mute, marginTop: 4 }}>Your web server must allow this page to be framed (no <code>X-Frame-Options: DENY</code>). A plain link always works.</div>
                  </div>)}
              </Card>))}
          </div>)}

      {editing && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 620, maxWidth: '96vw', maxHeight: '92vh', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 18 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>{editing.id ? `Edit — ${editing.name}` : 'New signup form'}</h3>
              <button onClick={() => setEditing(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: C.mute }}><X size={20} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div><label style={lbl}>Internal name *</label><input style={inp} value={form.name} maxLength={150} onChange={e => setForm((p: any) => ({ ...p, name: e.target.value }))} autoFocus /></div>
                <div><label style={lbl}>Heading shown to visitors *</label><input style={inp} value={form.title} maxLength={150} onChange={e => setForm((p: any) => ({ ...p, title: e.target.value }))} placeholder="Join our newsletter" /></div>
              </div>
              <div><label style={lbl}>Description</label><textarea style={{ ...inp, resize: 'vertical' }} rows={2} value={form.description} maxLength={1000} onChange={e => setForm((p: any) => ({ ...p, description: e.target.value }))} /></div>
              <div>
                <label style={lbl}>Consent wording * (shown next to an unticked checkbox, stored as evidence)</label>
                <textarea style={{ ...inp, resize: 'vertical' }} rows={3} value={form.consentText} maxLength={1000} onChange={e => setForm((p: any) => ({ ...p, consentText: e.target.value }))} />
                <div style={{ fontSize: 11, color: C.mute, marginTop: 3 }}>Say who is sending, what they’ll get, and that they can unsubscribe. At least 20 characters.</div>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div><label style={lbl}>Button label</label><input style={inp} value={form.buttonLabel} maxLength={40} onChange={e => setForm((p: any) => ({ ...p, buttonLabel: e.target.value }))} /></div>
                <div style={{ display: 'flex', alignItems: 'end', gap: 10, paddingBottom: 8 }}><Switch on={form.collectName} onChange={v => setForm((p: any) => ({ ...p, collectName: v }))} /><span style={{ fontSize: 13 }}>Ask for a name</span></div>
              </div>
              <div>
                <label style={lbl}>Topics offered on this form</label>
                {topics.length === 0 ? <div style={{ fontSize: 12, color: C.mute }}>No topics yet. Create some in the Topics tab to let people choose.</div> : (
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    {topics.map((t: any) => { const on = form.topicIds.includes(t.id); return (
                      <button key={t.id} onClick={() => setForm((p: any) => ({ ...p, topicIds: on ? p.topicIds.filter((x: string) => x !== t.id) : [...p.topicIds, t.id] }))}
                        style={{ padding: '5px 13px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: `1px solid ${on ? C.primary : C.line}`, background: on ? C.primary : '#fff', color: on ? '#fff' : C.text }}>{t.name}</button>) })}
                  </div>)}
                <div style={{ fontSize: 11, color: C.mute, marginTop: 4 }}>Visitors start with every topic ticked. Any they untick are opted out of that topic.</div>
              </div>
            </div>
            {error && <div style={{ marginTop: 12, padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 18 }}>
              <Btn onClick={() => setEditing(null)}>Cancel</Btn>
              <Btn variant="primary" disabled={!ok || save.isPending} onClick={() => { setError(''); save.mutate() }}>{save.isPending ? 'Saving…' : 'Save form'}</Btn>
            </div>
          </div>
        </div>)}
    </>
  )
}

function Topics() {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<any>(null)
  const [form, setForm] = useState({ name: '', description: '', visible: true })
  const [error, setError] = useState('')
  const { data: topics = [], isLoading } = useQuery<any[]>({ queryKey: ['marketing-topics'], queryFn: async () => (await apiClient.get('/api/v1/marketing/topics')).data?.data ?? [] })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['marketing-topics'] }); qc.invalidateQueries({ queryKey: ['marketing-forms'] }) }

  const open = (t?: any) => { setEditing(t ?? {}); setError(''); setForm({ name: t?.name ?? '', description: t?.description ?? '', visible: t?.visible ?? true }) }
  const save = useMutation({
    mutationFn: () => {
      const body = { name: form.name.trim(), description: form.description.trim() || null, visible: form.visible }
      return editing?.id ? apiClient.put(`/api/v1/marketing/topics/${editing.id}`, body) : apiClient.post('/api/v1/marketing/topics', body)
    },
    onSuccess: () => { refresh(); setEditing(null) },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to save topic'),
  })
  const del = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/marketing/topics/${id}`),
    onSuccess: refresh, onError: (e: any) => window.alert(e.response?.data?.message || 'Delete failed'),
  })

  return (
    <>
      <div style={{ padding: '12px 16px', background: '#F0F9FF', border: '1px solid #BAE6FD', borderRadius: 10, marginBottom: 16, fontSize: 12, color: '#0C4A6E', lineHeight: 1.6 }}>
        Topics let people choose what they hear about. Everyone is subscribed to every topic unless they opt out. A campaign can be assigned a topic (in its Audience step), and contacts who opted out of it are left out.
        Topics marked visible appear on the public preference page linked in every email footer.
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 14 }}><Btn variant="primary" onClick={() => open()}><Plus size={14} /> New topic</Btn></div>
      {isLoading ? <div style={{ textAlign: 'center', padding: 40, color: C.mute }}>Loading…</div>
        : topics.length === 0 ? <Empty icon={<Tag size={36} />} title="No topics yet" text="Create topics like “Specials”, “News” or “Events”." />
        : (
          <div style={{ border: `1px solid ${C.line}`, borderRadius: 12, overflow: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead><tr style={{ background: C.bg, borderBottom: `1px solid ${C.line}` }}>
                {['Topic', 'Preference page', 'Opted out', 'Used by', ''].map(h => <th key={h} style={{ padding: '10px 16px', textAlign: 'left', fontSize: 11, fontWeight: 700, color: '#64748B' }}>{h}</th>)}
              </tr></thead>
              <tbody>
                {topics.map((t: any, i: number) => (
                  <tr key={t.id} style={{ background: i % 2 ? '#FAFAFA' : '#fff', borderBottom: '1px solid #F1F5F9' }}>
                    <td style={{ padding: '11px 16px' }}><div style={{ fontWeight: 700, color: C.ink }}>{t.name}</div>{t.description && <div style={{ fontSize: 11, color: C.mute }}>{t.description}</div>}</td>
                    <td style={{ padding: '11px 16px' }}><Pill color={t.visible ? '#166534' : '#64748B'} bg={t.visible ? '#DCFCE7' : '#F1F5F9'}>{t.visible ? 'Visible' : 'Hidden'}</Pill></td>
                    <td style={{ padding: '11px 16px' }}>{fmtN(t.optedOut)}</td>
                    <td style={{ padding: '11px 16px', color: t.usedByActiveCampaigns ? C.amber : C.mute }}>{t.usedByActiveCampaigns ? `${t.usedByActiveCampaigns} campaign(s)` : '—'}</td>
                    <td style={{ padding: '11px 16px' }}>
                      <div style={{ display: 'flex', gap: 6 }}>
                        <Btn onClick={() => open(t)} style={{ padding: '4px 9px' }}><Pencil size={12} /></Btn>
                        <Btn variant="danger" disabled={t.usedByActiveCampaigns > 0} onClick={() => { if (window.confirm(`Delete topic "${t.name}"?`)) del.mutate(t.id) }} style={{ padding: '4px 9px' }}><Trash2 size={12} /></Btn>
                      </div>
                    </td>
                  </tr>))}
              </tbody>
            </table>
          </div>)}

      {editing && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 460, maxWidth: '96vw' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>{editing.id ? 'Edit topic' : 'New topic'}</h3>
              <button onClick={() => setEditing(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: C.mute }}><X size={20} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div><label style={lbl}>Name *</label><input style={inp} autoFocus value={form.name} maxLength={100} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} /></div>
              <div><label style={lbl}>Description</label><input style={inp} value={form.description} maxLength={300} onChange={e => setForm(p => ({ ...p, description: e.target.value }))} placeholder="Shown to contacts next to the topic" /></div>
              <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}><Switch on={form.visible} onChange={v => setForm(p => ({ ...p, visible: v }))} /><span style={{ fontSize: 13 }}>Show on the public preference page</span></div>
            </div>
            {error && <div style={{ marginTop: 12, padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 18 }}>
              <Btn onClick={() => setEditing(null)}>Cancel</Btn>
              <Btn variant="primary" disabled={!form.name.trim() || save.isPending} onClick={() => { setError(''); save.mutate() }}>{save.isPending ? 'Saving…' : 'Save topic'}</Btn>
            </div>
          </div>
        </div>)}
    </>
  )
}