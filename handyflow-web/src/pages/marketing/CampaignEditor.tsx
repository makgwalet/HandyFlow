// src/pages/marketing/CampaignEditor.tsx
import { useEffect, useRef, useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { X, CheckCircle, AlertTriangle, XCircle, Info, Puzzle } from 'lucide-react'

const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box', background: '#fff', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }

const BUILTIN_BLOCKS = [
  { name: 'Greeting', html: '<h2 style="color:#1B3A6B;margin:0 0 12px">Hi {{first_name}},</h2>\n' },
  { name: 'Button', html: '<p style="text-align:center;margin:24px 0"><a href="https://example.com" style="background:#0D9488;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600">Learn more</a></p>\n' },
  { name: 'Divider', html: '<hr style="border:none;border-top:1px solid #E2E8F0;margin:24px 0">\n' },
  { name: 'Sign-off', html: '<p>Kind regards,<br/>The {{company_name}} team</p>\n' },
]

const toLocalInput = (iso?: string) => {
  if (!iso) return ''
  const d = new Date(iso); const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

interface Props { campaign?: any; onClose: () => void; onSaved: () => void }

export default function CampaignEditor({ campaign, onClose, onSaved }: Props) {
  const editing = !!campaign
  const [form, setForm] = useState({
    name: '', templateId: '', audienceType: 'ALL_OPTED_IN', segmentId: '', fromName: '', replyTo: '',
    scheduledAt: '', subject: '', htmlBody: '', changeNote: '',
  })
  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))
  const [error, setError] = useState('')
  const [lint, setLint] = useState<any[]>([])
  const [preview, setPreview] = useState<any>(null)
  const [previewMode, setPreviewMode] = useState<'desktop' | 'mobile'>('desktop')
  const bodyRef = useRef<HTMLTextAreaElement>(null)
  const rid = useRef(0)
  const loaded = useRef(false)

  const { data: templates = [] } = useQuery<any[]>({
    queryKey: ['marketing-templates'],
    queryFn: async () => (await apiClient.get('/api/v1/marketing/templates')).data?.data ?? [],
  })
  const { data: segments = [] } = useQuery<any[]>({
    queryKey: ['marketing-segments'],
    queryFn: async () => (await apiClient.get('/api/v1/marketing/segments')).data?.data ?? [],
  })
  const { data: blocks = [] } = useQuery<any[]>({
    queryKey: ['marketing-blocks'],
    queryFn: async () => (await apiClient.get('/api/v1/marketing/blocks')).data?.data ?? [],
  })

  // Editing: the list view has no body, so load the current version.
  const { data: current } = useQuery<any>({
    queryKey: ['marketing-campaign-version', campaign?.id, campaign?.contentVersion],
    enabled: editing,
    queryFn: async () => (await apiClient.get(`/api/v1/marketing/campaigns/${campaign.id}/versions/${campaign.contentVersion}`)).data?.data,
  })
  useEffect(() => {
    if (editing && current && !loaded.current) {
      loaded.current = true
      setForm({
        name: campaign.name, templateId: campaign.templateId ?? '', audienceType: campaign.audienceType,
        segmentId: campaign.segmentId ?? '', fromName: campaign.fromName ?? '', replyTo: campaign.replyTo ?? '',
        scheduledAt: toLocalInput(campaign.scheduledAt), subject: current.subject ?? '', htmlBody: current.htmlBody ?? '', changeNote: '',
      })
    }
  }, [current, editing, campaign])

  const useSegment = form.audienceType === 'SEGMENT' && !!form.segmentId
  const { data: audiencePreview } = useQuery<any>({
    queryKey: ['marketing-segment-preview', form.segmentId],
    enabled: useSegment,
    queryFn: async () => (await apiClient.post(`/api/v1/marketing/segments/${form.segmentId}/preview`)).data?.data,
  })

  // Server-side lint + preview, debounced
  useEffect(() => {
    if (!form.subject.trim() && !form.htmlBody.trim()) { setLint([]); setPreview(null); return }
    const my = ++rid.current
    const t = setTimeout(async () => {
      try {
        const [l, p] = await Promise.all([
          apiClient.post('/api/v1/marketing/content/lint', { subject: form.subject, htmlBody: form.htmlBody }),
          apiClient.post('/api/v1/marketing/content/preview', { subject: form.subject, htmlBody: form.htmlBody, replyTo: form.replyTo || null }),
        ])
        if (my === rid.current) { setLint(l.data?.data ?? []); setPreview(p.data?.data ?? null) }
      } catch { /* checker is advisory; ignore transient errors */ }
    }, 700)
    return () => clearTimeout(t)
  }, [form.subject, form.htmlBody, form.replyTo])

  const insertBlock = (html: string) => {
    const el = bodyRef.current
    const s = el?.selectionStart ?? form.htmlBody.length
    const e = el?.selectionEnd ?? s
    setForm(p => ({ ...p, htmlBody: p.htmlBody.slice(0, s) + html + p.htmlBody.slice(e) }))
  }

  const applyTemplate = (id: string) => {
    f('templateId', id)
    const t = (templates as any[]).find(x => x.id === id)
    if (t) setForm(p => ({ ...p, templateId: id, subject: t.subject ?? p.subject, htmlBody: t.htmlBody ?? p.htmlBody }))
  }

  // Local checks (required fields). Content problems come from the server checker.
  const local: { level: 'error' | 'warn' | 'ok'; text: string }[] = []
  if (!form.name.trim()) local.push({ level: 'error', text: 'Give the campaign a name.' })
  if (!form.subject.trim()) local.push({ level: 'error', text: 'Add an email subject.' })
  if (!form.htmlBody.trim()) local.push({ level: 'error', text: 'Add email content.' })
  if (form.audienceType === 'SEGMENT' && !form.segmentId) local.push({ level: 'error', text: 'Choose a segment, or switch to “All opted-in contacts”.' })
  if (form.replyTo && !form.replyTo.includes('@')) local.push({ level: 'error', text: 'The reply-to address does not look valid.' })
  if (form.scheduledAt) {
    const w = new Date(form.scheduledAt)
    if (isNaN(w.getTime()) || (w.getTime() < Date.now() - 60_000 && (!editing || toLocalInput(campaign.scheduledAt) !== form.scheduledAt)))
      local.push({ level: 'error', text: 'The scheduled time is in the past.' })
  }
  if (!form.fromName.trim()) local.push({ level: 'warn', text: 'No “From name” set — recipients see the default sender name.' })
  if (audiencePreview) local.push(audiencePreview.eligible === 0
    ? { level: 'warn', text: 'Nobody in this segment is eligible right now.' }
    : { level: 'ok', text: `${audiencePreview.eligible.toLocaleString()} eligible contact(s) in this segment right now (re-evaluated at launch).` })
  const hasErrors = local.some(c => c.level === 'error')
  const lintErrors = lint.filter(l => l.level === 'ERROR')

  const save = useMutation({
    mutationFn: () => {
      const body: any = {
        name: form.name.trim(), fromName: form.fromName.trim() || null, replyTo: form.replyTo.trim() || null,
        audienceType: form.audienceType, segmentId: form.audienceType === 'SEGMENT' ? form.segmentId : null,
        templateId: form.templateId || null, subject: form.subject, htmlBody: form.htmlBody,
        scheduledAt: form.scheduledAt ? new Date(form.scheduledAt).toISOString() : null,
      }
      if (editing) return apiClient.put(`/api/v1/marketing/campaigns/${campaign.id}`, { ...body, changeNote: form.changeNote.trim() || null })
      return apiClient.post('/api/v1/marketing/campaigns', { ...body, channel: 'EMAIL' })
    },
    onSuccess: () => onSaved(),
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to save campaign'),
  })

  const loading = editing && !loaded.current && !current
  const icon = (l: string) => l === 'ERROR' || l === 'error' ? <XCircle size={14} color="#DC2626" style={{ flexShrink: 0, marginTop: 1 }} />
    : l === 'WARN' || l === 'warn' ? <AlertTriangle size={14} color="#D97706" style={{ flexShrink: 0, marginTop: 1 }} />
    : l === 'INFO' ? <Info size={14} color="#64748B" style={{ flexShrink: 0, marginTop: 1 }} />
    : <CheckCircle size={14} color="#166534" style={{ flexShrink: 0, marginTop: 1 }} />
  const color = (l: string) => ({ ERROR: '#991B1B', error: '#991B1B', WARN: '#92400E', warn: '#92400E', INFO: '#64748B', ok: '#166534' } as any)[l]

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 760, maxHeight: '94vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>{editing ? `Edit — ${campaign.name}` : 'New Campaign'}</h3>
            <p style={{ margin: '3px 0 0', fontSize: 13, color: '#64748B' }}>
              {editing ? `Currently version ${campaign.contentVersion}. Changing the content creates version ${campaign.contentVersion + 1} and voids any approval.` : 'Starts as Draft — or Scheduled if you pick a send time'}
            </p>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
        </div>

        {loading ? <div style={{ padding: 40, textAlign: 'center', color: '#94A3B8' }}>Loading campaign…</div> : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div><label style={lbl}>Campaign name *</label><input value={form.name} onChange={e => f('name', e.target.value)} style={inp} autoFocus /></div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div><label style={lbl}>From name</label><input value={form.fromName} onChange={e => f('fromName', e.target.value)} placeholder="Your Business Name" style={inp} /></div>
            <div><label style={lbl}>Reply-to email</label><input type="email" value={form.replyTo} onChange={e => f('replyTo', e.target.value)} placeholder="hello@yourbusiness.co.za" style={inp} /></div>
          </div>

          <div>
            <label style={lbl}>Audience</label>
            <select value={form.audienceType} onChange={e => setForm(p => ({ ...p, audienceType: e.target.value, segmentId: '' }))} style={inp}>
              <option value="ALL_OPTED_IN">All opted-in contacts (suppressed addresses excluded)</option>
              <option value="SEGMENT">A segment or list…</option>
            </select>
            {form.audienceType === 'SEGMENT' && (
              <select value={form.segmentId} onChange={e => f('segmentId', e.target.value)} style={{ ...inp, marginTop: 8 }}>
                <option value="">Select a segment…</option>
                {(segments as any[]).map((s: any) => <option key={s.id} value={s.id}>{s.name} ({s.type === 'STATIC' ? 'list' : 'dynamic'}{s.lastCount != null ? ` · ~${s.lastCount}` : ''})</option>)}
              </select>
            )}
          </div>

          <div>
            <label style={lbl}>Start from a template (copies its content into this campaign)</label>
            <select value={form.templateId} onChange={e => applyTemplate(e.target.value)} style={inp}>
              <option value="">— none —</option>
              {(templates as any[]).map((t: any) => <option key={t.id} value={t.id}>{t.name} — {t.subject}</option>)}
            </select>
          </div>

          <div><label style={lbl}>Email subject *</label><input value={form.subject} onChange={e => f('subject', e.target.value)} placeholder="Hello {{first_name}} — news from {{company_name}}" style={inp} /></div>

          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6, flexWrap: 'wrap', gap: 6 }}>
              <label style={{ ...lbl, marginBottom: 0 }}>HTML body *</label>
              <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap', alignItems: 'center' }}>
                <Puzzle size={12} color="#94A3B8" />
                {[...BUILTIN_BLOCKS, ...(blocks as any[])].map((b: any) => (
                  <button key={b.id ?? b.name} onClick={() => insertBlock(b.html)} title="Insert at cursor"
                    style={{ padding: '2px 8px', background: '#F0F9FF', border: '1px solid #BAE6FD', borderRadius: 6, fontSize: 11, color: '#0284C7', cursor: 'pointer' }}>{b.name}</button>
                ))}
              </div>
            </div>
            <textarea ref={bodyRef} value={form.htmlBody} onChange={e => f('htmlBody', e.target.value)} rows={10}
              placeholder="<h2>Hi {{first_name}},</h2><p>Your message here...</p>" style={{ ...inp, resize: 'vertical', fontFamily: 'monospace', fontSize: 12, lineHeight: 1.5 }} />
            <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 4 }}>Placeholders: {'{{first_name}} {{name}} {{email}} {{company_name}} {{unsubscribe_url}}'}. Blocks are copied in — editing a block later never changes a saved campaign.</div>
          </div>

          <div>
            <label style={lbl}>Schedule for later (optional)</label>
            <input type="datetime-local" value={form.scheduledAt} onChange={e => f('scheduledAt', e.target.value)} style={inp} />
            <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 4 }}>Blank = draft you launch manually. With approval required, a scheduled campaign that isn’t approved by its send time is marked Failed, not sent.</div>
          </div>

          {preview && (
            <div>
              <div style={{ display: 'flex', gap: 8, marginBottom: 8, alignItems: 'center' }}>
                <span style={{ fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Preview (sample contact)</span>
                {(['desktop', 'mobile'] as const).map(v => (
                  <button key={v} onClick={() => setPreviewMode(v)} style={{ padding: '2px 10px', borderRadius: 20, border: '1px solid #E2E8F0', background: previewMode === v ? '#1B3A6B' : '#fff', color: previewMode === v ? '#fff' : '#64748B', fontSize: 11, cursor: 'pointer', fontWeight: 600, textTransform: 'capitalize' }}>{v}</button>
                ))}
              </div>
              <div style={{ fontSize: 12, color: '#374151', marginBottom: 6 }}><strong>Subject:</strong> {preview.subject}</div>
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, background: '#F8FAFC', padding: 12 }}>
                <iframe sandbox="" srcDoc={preview.html} title="Campaign preview"
                  style={{ width: previewMode === 'mobile' ? 375 : '100%', height: 280, border: 'none', borderRadius: 8, background: '#fff', display: 'block', margin: previewMode === 'mobile' ? '0 auto' : undefined }} />
              </div>
            </div>
          )}

          <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
            <div style={{ padding: '9px 14px', background: '#F8FAFC', borderBottom: '1px solid #E2E8F0', fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
              Content check {lintErrors.length > 0 && <span style={{ color: '#DC2626' }}>· {lintErrors.length} error{lintErrors.length === 1 ? '' : 's'} must be fixed before review or launch</span>}
            </div>
            <div style={{ padding: '6px 14px' }}>
              {[...local.map(c => ({ level: c.level, text: c.text })), ...lint.map(l => ({ level: l.level, text: l.message }))].map((c, i, arr) => (
                <div key={i} style={{ display: 'flex', gap: 9, alignItems: 'flex-start', padding: '7px 0', borderBottom: i < arr.length - 1 ? '1px solid #F1F5F9' : 'none' }}>
                  {icon(c.level)}<div style={{ fontSize: 12, lineHeight: 1.5, color: color(c.level) }}>{c.text}</div>
                </div>
              ))}
              {local.length === 0 && lint.length === 0 && <div style={{ padding: '8px 0', fontSize: 12, color: '#94A3B8' }}>Start writing to see checks.</div>}
            </div>
          </div>

          {editing && (
            <div><label style={lbl}>What changed? (shown in version history)</label>
              <input value={form.changeNote} onChange={e => f('changeNote', e.target.value)} maxLength={300} placeholder="e.g. Updated offer end date" style={inp} /></div>
          )}

          <div style={{ padding: '10px 14px', background: '#FFF7ED', border: '1px solid #FED7AA', borderRadius: 8, fontSize: 12, color: '#92400E' }}>
            POPIA: only opted-in, non-suppressed contacts receive this. Eligibility is re-checked just before each email is sent.
          </div>
        </div>)}

        {error && <div style={{ marginTop: 12, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 22 }}>
          <button onClick={onClose} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: '#fff', fontSize: 14, cursor: 'pointer' }}>Cancel</button>
          <button disabled={hasErrors || loading || save.isPending} onClick={() => { setError(''); save.mutate() }}
            style={{ padding: '9px 22px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: hasErrors ? 'not-allowed' : 'pointer', opacity: hasErrors ? 0.5 : 1 }}>
            {save.isPending ? 'Saving…' : editing ? 'Save changes' : form.scheduledAt ? 'Schedule Campaign' : 'Create Campaign'}
          </button>
        </div>
      </div>
    </div>
  )
}