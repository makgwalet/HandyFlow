// src/pages/marketing/CampaignWizard.tsx
import { useEffect, useRef, useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { X, Check, CheckCircle, AlertTriangle, XCircle, Info, Mail, MessageSquare, Smartphone, Puzzle, ChevronLeft, ChevronRight } from 'lucide-react'
import EmailBuilder, { parseBlocks } from './EmailBuilder'
import { C, inp, lbl, Btn, Pill } from './ui'

const STEPS = ['Details', 'Audience', 'Content', 'Review & Send']
const BUILTIN_BLOCKS = [
  { name: 'Greeting', html: '<h2 style="color:#1B3A6B;margin:0 0 12px">Hi {{first_name}},</h2>\n' },
  { name: 'Button', html: '<p style="text-align:center;margin:24px 0"><a href="https://example.com" style="background:#2563EB;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600">Learn more</a></p>\n' },
  { name: 'Divider', html: '<hr style="border:none;border-top:1px solid #E2E8F0;margin:24px 0">\n' },
  { name: 'Sign-off', html: '<p>Kind regards,<br/>The {{company_name}} team</p>\n' },
]

const toLocalInput = (iso?: string) => {
  if (!iso) return ''
  const d = new Date(iso); const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

interface Props { campaign?: any; onClose: () => void; onSaved: () => void }

export default function CampaignWizard({ campaign, onClose, onSaved }: Props) {
  const editing = !!campaign
  const [step, setStep] = useState(0)
  const [form, setForm] = useState({
    name: '', templateId: '', audienceType: 'ALL_OPTED_IN', segmentId: '', fromName: '', replyTo: '',
    scheduledAt: '', subject: '', htmlBody: '', changeNote: '', mode: 'visual' as 'visual' | 'html', launchNow: false,
  })
  const [loaded, setLoaded] = useState(!editing)
  const [builderKey, setBuilderKey] = useState(0)
  const [error, setError] = useState('')
  const [lint, setLint] = useState<any[]>([])
  const [preview, setPreview] = useState<any>(null)
  const [previewMode, setPreviewMode] = useState<'desktop' | 'mobile'>('desktop')
  const bodyRef = useRef<HTMLTextAreaElement>(null)
  const rid = useRef(0)
  const f = (k: string, v: any) => setForm(p => ({ ...p, [k]: v }))

  const { data: templates = [] } = useQuery<any[]>({ queryKey: ['marketing-templates'], queryFn: async () => (await apiClient.get('/api/v1/marketing/templates')).data?.data ?? [] })
  const { data: segments = [] } = useQuery<any[]>({ queryKey: ['marketing-segments'], queryFn: async () => (await apiClient.get('/api/v1/marketing/segments')).data?.data ?? [] })
  const { data: blocks = [] } = useQuery<any[]>({ queryKey: ['marketing-blocks'], queryFn: async () => (await apiClient.get('/api/v1/marketing/blocks')).data?.data ?? [] })
    const { data: topics = [] } = useQuery<any[]>({ queryKey: ['marketing-topics'], queryFn: async () => (await apiClient.get('/api/v1/marketing/topics')).data?.data ?? [] })
  const { data: settings } = useQuery<any>({ queryKey: ['marketing-settings'], queryFn: async () => (await apiClient.get('/api/v1/marketing/settings')).data?.data })
  const { data: current } = useQuery<any>({
    queryKey: ['marketing-campaign-version', campaign?.id, campaign?.contentVersion], enabled: editing,
    queryFn: async () => (await apiClient.get(`/api/v1/marketing/campaigns/${campaign.id}/versions/${campaign.contentVersion}`)).data?.data,
  })
  useEffect(() => {
    if (editing && current && !loaded) {
      setForm({
        name: campaign.name, templateId: campaign.templateId ?? '', audienceType: campaign.audienceType, segmentId: campaign.segmentId ?? '',
        fromName: campaign.fromName ?? '', replyTo: campaign.replyTo ?? '', scheduledAt: toLocalInput(campaign.scheduledAt),
        subject: current.subject ?? '', htmlBody: current.htmlBody ?? '', changeNote: '',
        mode: parseBlocks(current.htmlBody ?? '') ? 'visual' : 'html', launchNow: false,
      })
      setLoaded(true)
    }
  }, [current, editing, loaded, campaign])

  const useSegment = form.audienceType === 'SEGMENT' && !!form.segmentId
  const { data: audience } = useQuery<any>({
    queryKey: ['marketing-segment-preview', form.segmentId], enabled: useSegment,
    queryFn: async () => (await apiClient.post(`/api/v1/marketing/segments/${form.segmentId}/preview`)).data?.data,
  })
  const { data: summary } = useQuery<any>({ queryKey: ['marketing-summary'], queryFn: async () => { const r = await apiClient.get('/api/v1/marketing/summary'); return r.data?.data ?? r.data } })

  // Server-side content check + preview (debounced)
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
      } catch { /* advisory */ }
    }, 700)
    return () => clearTimeout(t)
  }, [form.subject, form.htmlBody, form.replyTo])

  // ── Per-step validation ──
  const errs: string[][] = [[], [], [], []]
  if (!form.name.trim()) errs[0].push('Give the campaign a name.')
  if (form.replyTo && !form.replyTo.includes('@')) errs[0].push('The reply-to address does not look valid.')
  if (form.scheduledAt) {
    const w = new Date(form.scheduledAt)
    if (isNaN(w.getTime()) || (w.getTime() < Date.now() - 60_000 && (!editing || toLocalInput(campaign.scheduledAt) !== form.scheduledAt))) errs[0].push('The scheduled time is in the past.')
  }
  if (form.audienceType === 'SEGMENT' && !form.segmentId) errs[1].push('Choose a segment, or use all opted-in contacts.')
  if (!form.subject.trim()) errs[2].push('Add an email subject.')
  if (!form.htmlBody.trim()) errs[2].push('Add email content.')
  const lintErrors = lint.filter(l => l.level === 'ERROR')
  const allErrs = errs.flat()
  const canSave = allErrs.length === 0 && lintErrors.length === 0
  const firstBad = errs.findIndex(e => e.length > 0)
  const goTo = (i: number) => { if (i <= step || errs.slice(0, i).every(e => e.length === 0)) setStep(i) }

  const applyTemplate = (id: string) => {
    const t = (templates as any[]).find(x => x.id === id)
    setForm(p => ({ ...p, templateId: id, subject: t?.subject ?? p.subject, htmlBody: t?.htmlBody ?? p.htmlBody, mode: t && !parseBlocks(t.htmlBody ?? '') ? 'html' : p.mode }))
    setBuilderKey(k => k + 1)
  }
  const switchMode = (m: 'visual' | 'html') => {
    if (m === form.mode) return
    if (m === 'visual' && form.htmlBody.trim() &&
      !window.confirm('The visual builder rebuilds the email from its saved blocks. Any HTML you edited by hand will be lost. Continue?')) return
    setForm(p => ({ ...p, mode: m, htmlBody: m === 'visual' && !parseBlocks(p.htmlBody) ? '' : p.htmlBody }))
    setBuilderKey(k => k + 1)
  }
  const insertBlock = (html: string) => {
    const el = bodyRef.current; const s = el?.selectionStart ?? form.htmlBody.length; const e = el?.selectionEnd ?? s
    f('htmlBody', form.htmlBody.slice(0, s) + html + form.htmlBody.slice(e))
  }

  const requireApproval = !!settings?.requireApproval
  const canLaunchNow = !editing && !form.scheduledAt && !requireApproval

  const save = useMutation({
    mutationFn: async () => {
      const body: any = {
        name: form.name.trim(), fromName: form.fromName.trim() || null, replyTo: form.replyTo.trim() || null,
        audienceType: form.audienceType, segmentId: form.audienceType === 'SEGMENT' ? form.segmentId : null,
        templateId: form.templateId || null, subject: form.subject, htmlBody: form.htmlBody,
        scheduledAt: form.scheduledAt ? new Date(form.scheduledAt).toISOString() : null,
      }
      if (editing) { await apiClient.put(`/api/v1/marketing/campaigns/${campaign.id}`, { ...body, changeNote: form.changeNote.trim() || null }); return { launchError: null } }
      const r = await apiClient.post('/api/v1/marketing/campaigns', { ...body, channel: 'EMAIL' })
      if (form.launchNow && canLaunchNow) {
        try { await apiClient.post(`/api/v1/marketing/campaigns/${(r.data?.data ?? r.data).id}/launch`) }
        catch (e: any) { return { launchError: e.response?.data?.message || 'Launch failed' } }
      }
      return { launchError: null }
    },
    onSuccess: (r: any) => { if (r.launchError) window.alert(`The campaign was saved as a draft, but it was not launched: ${r.launchError}`); onSaved() },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to save campaign'),
  })

  const ico = (l: string) => l === 'ERROR' || l === 'error' ? <XCircle size={14} color={C.red} style={{ flexShrink: 0, marginTop: 1 }} />
    : l === 'WARN' || l === 'warn' ? <AlertTriangle size={14} color={C.amber} style={{ flexShrink: 0, marginTop: 1 }} />
    : l === 'INFO' ? <Info size={14} color="#64748B" style={{ flexShrink: 0, marginTop: 1 }} /> : <CheckCircle size={14} color="#166534" style={{ flexShrink: 0, marginTop: 1 }} />
  const col = (l: string) => ({ ERROR: '#991B1B', error: '#991B1B', WARN: '#92400E', warn: '#92400E', INFO: '#64748B', ok: '#166534' } as any)[l]

  const showPreview = step !== 2
  const selectedSeg = (segments as any[]).find(s => s.id === form.segmentId)
  const audLabel = form.audienceType === 'SEGMENT' ? `Segment: ${selectedSeg?.name ?? '—'}` : 'All opted-in contacts'

  const checks = [
    ...(!form.fromName.trim() ? [{ level: 'warn', text: 'No “From name” set. Recipients will see the default sender name.' }] : []),
    ...(form.audienceType === 'SEGMENT' && audience ? [audience.eligible === 0
      ? { level: 'warn', text: 'Nobody in this segment is eligible right now.' }
      : { level: 'ok', text: `${audience.eligible.toLocaleString()} eligible contact(s) in this segment now${audience.suppressed ? `; ${audience.suppressed} suppressed will be excluded` : ''}. Re-evaluated at launch.` }] : []),
    ...(form.audienceType === 'ALL_OPTED_IN' && summary ? [{ level: 'ok', text: `Up to ${summary.optedInCount ?? 0} opted-in contact(s); suppressed addresses are excluded.` }] : []),
    ...(requireApproval ? [{ level: 'warn', text: 'Approval is required. Submit this campaign for review from the campaign list before it can be sent.' }] : []),
    ...lint.map(l => ({ level: l.level, text: l.message })),
  ]

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: '#fff', borderRadius: 16, width: 'min(1180px, 96vw)', maxHeight: '94vh', display: 'flex', flexDirection: 'column', boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
        {/* Header + stepper */}
        <div style={{ padding: '22px 28px 14px', borderBottom: `1px solid ${C.line}` }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <h3 style={{ margin: 0, fontSize: 19, fontWeight: 800, color: C.ink }}>{editing ? `Edit — ${campaign.name}` : 'Create Campaign'}</h3>
              <p style={{ margin: '3px 0 0', fontSize: 12, color: C.mute }}>{editing ? `Version ${campaign.contentVersion}. Changing the content creates version ${campaign.contentVersion + 1} and voids any approval.` : 'Saved as a draft, or scheduled if you pick a send time.'}</p>
            </div>
            <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: C.mute }}><X size={20} /></button>
          </div>
          <div style={{ display: 'flex', gap: 6, marginTop: 16, alignItems: 'center' }}>
            {STEPS.map((s, i) => (
              <div key={s} style={{ display: 'flex', alignItems: 'center', gap: 6, flex: i < STEPS.length - 1 ? 1 : 'none' }}>
                <button onClick={() => goTo(i)} style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
                  <span style={{ width: 24, height: 24, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700,
                    background: i < step ? '#16A34A' : i === step ? C.primary : '#fff', color: i <= step ? '#fff' : C.mute, border: i > step ? `1.5px solid ${C.line}` : 'none' }}>{i < step ? <Check size={13} /> : i + 1}</span>
                  <span style={{ fontSize: 13, fontWeight: i === step ? 700 : 500, color: i === step ? C.ink : C.mute }}>{s}</span>
                </button>
                {i < STEPS.length - 1 && <div style={{ flex: 1, height: 1, background: C.line, marginLeft: 6 }} />}
              </div>))}
          </div>
        </div>

        {/* Body */}
        <div style={{ padding: 28, overflowY: 'auto', flex: 1 }}>
          {!loaded ? <div style={{ padding: 60, textAlign: 'center', color: C.mute }}>Loading campaign…</div> : (
            <div style={{ display: 'grid', gridTemplateColumns: showPreview ? 'minmax(0,1fr) 340px' : 'minmax(0,1fr)', gap: 28, alignItems: 'start' }}>
              <div>
                {step === 0 && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                    <div style={{ fontWeight: 700, fontSize: 15, color: C.ink }}>Campaign details</div>
                    <div><label style={lbl}>Campaign name *</label><input style={inp} value={form.name} onChange={e => f('name', e.target.value)} placeholder="Spring Promo 2026" autoFocus /></div>
                    <div>
                      <label style={lbl}>Channel</label>
                      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                        {[{ k: 'Email', i: <Mail size={14} />, on: true }, { k: 'SMS', i: <MessageSquare size={14} />, on: false }, { k: 'WhatsApp', i: <Smartphone size={14} />, on: false }].map(c => (
                          <label key={c.k} title={c.on ? '' : 'Not available yet — no provider connected'} style={{ display: 'flex', alignItems: 'center', gap: 7, padding: '8px 14px', border: `1.5px solid ${c.on ? C.primary : C.line}`, background: c.on ? '#EFF6FF' : C.bg, borderRadius: 9, fontSize: 13, color: c.on ? C.primary : C.mute, opacity: c.on ? 1 : 0.7 }}>
                            <input type="checkbox" checked={c.on} disabled readOnly />{c.i}{c.k}{!c.on && <span style={{ fontSize: 10 }}>(not available)</span>}
                          </label>))}
                      </div>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                      <div><label style={lbl}>From name</label><input style={inp} value={form.fromName} onChange={e => f('fromName', e.target.value)} placeholder="Your Business Name" /></div>
                      <div><label style={lbl}>Reply-to email</label><input style={inp} type="email" value={form.replyTo} onChange={e => f('replyTo', e.target.value)} placeholder="hello@yourbusiness.co.za" /></div>
                    </div>
                    <div>
                      <label style={lbl}>Send date &amp; time (optional)</label>
                      <input style={inp} type="datetime-local" value={form.scheduledAt} onChange={e => f('scheduledAt', e.target.value)} />
                      <div style={{ fontSize: 11, color: C.mute, marginTop: 4 }}>Uses your device’s time zone. Leave blank to keep a draft and launch manually. Scheduled campaigns are launched automatically (checked every 5 minutes).</div>
                    </div>
                  </div>)}

                {step === 1 && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                    <div style={{ fontWeight: 700, fontSize: 15, color: C.ink }}>Who should receive this?</div>
                    {[{ v: 'ALL_OPTED_IN', t: 'All opted-in contacts', d: 'Everyone who has given consent. Suppressed addresses are always excluded.' },
                      { v: 'SEGMENT', t: 'A segment or list', d: 'Dynamic segments are re-evaluated at launch; static lists use the contacts you added.' }].map(o => (
                      <label key={o.v} style={{ display: 'flex', gap: 12, padding: '14px 16px', border: `1.5px solid ${form.audienceType === o.v ? C.primary : C.line}`, background: form.audienceType === o.v ? '#EFF6FF' : '#fff', borderRadius: 11, cursor: 'pointer' }}>
                        <input type="radio" checked={form.audienceType === o.v} onChange={() => setForm(p => ({ ...p, audienceType: o.v, segmentId: '' }))} style={{ marginTop: 3 }} />
                        <div><div style={{ fontWeight: 700, fontSize: 14, color: C.ink }}>{o.t}</div><div style={{ fontSize: 12, color: C.text, marginTop: 2 }}>{o.d}</div></div>
                      </label>))}
                    {form.audienceType === 'SEGMENT' && (
                      <div>
                        <select style={inp} value={form.segmentId} onChange={e => f('segmentId', e.target.value)}>
                          <option value="">Select a segment…</option>
                          {(segments as any[]).map((s: any) => <option key={s.id} value={s.id}>{s.name} ({s.type === 'STATIC' ? 'list' : 'dynamic'}{s.lastCount != null ? ` · ~${s.lastCount}` : ''})</option>)}
                        </select>
                        {(segments as any[]).length === 0 && <div style={{ fontSize: 12, color: C.amber, marginTop: 6 }}>You have no segments yet. Create one in the Segments tab.</div>}
                        {audience && (
                          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 12 }}>
                            {[['Match the rules', audience.matched, '#EEF2FF', C.navy], ['Not opted in', audience.notOptedIn, '#F8FAFC', '#64748B'], ['Suppressed', audience.suppressed, audience.suppressed ? '#FEF2F2' : '#F8FAFC', audience.suppressed ? '#B91C1C' : '#64748B'], ['Eligible to email', audience.eligible, '#DCFCE7', '#166534']].map(([l, v, bg, c]: any) => (
                              <div key={l} style={{ background: bg, borderRadius: 9, padding: '9px 14px', minWidth: 110 }}><div style={{ fontSize: 20, fontWeight: 800, color: c }}>{Number(v).toLocaleString()}</div><div style={{ fontSize: 11, color: c }}>{l}</div></div>))}
                          </div>)}
                      </div>)}
                  </div>)}

                {step === 2 && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
                      <div style={{ fontWeight: 700, fontSize: 15, color: C.ink }}>Email content</div>
                      <div style={{ display: 'flex', gap: 6 }}>
                        {(['visual', 'html'] as const).map(m => (
                          <button key={m} onClick={() => switchMode(m)} style={{ padding: '5px 14px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: `1px solid ${form.mode === m ? C.primary : C.line}`, background: form.mode === m ? C.primary : '#fff', color: form.mode === m ? '#fff' : C.text }}>{m === 'visual' ? 'Visual builder' : 'HTML'}</button>))}
                      </div>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                      <div><label style={lbl}>Start from a template (copies its content)</label>
                        <select style={inp} value={form.templateId} onChange={e => applyTemplate(e.target.value)}><option value="">— none —</option>{(templates as any[]).map((t: any) => <option key={t.id} value={t.id}>{t.name}</option>)}</select></div>
                      <div><label style={lbl}>Email subject *</label><input style={inp} value={form.subject} onChange={e => f('subject', e.target.value)} placeholder="Hello {{first_name}} — news from {{company_name}}" /></div>
                    </div>
                    {form.mode === 'visual'
                      ? <EmailBuilder key={builderKey} value={form.htmlBody} onChange={h => f('htmlBody', h)} />
                      : (
                        <div>
                          <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap', alignItems: 'center', marginBottom: 6 }}>
                            <Puzzle size={12} color={C.mute} />
                            {[...BUILTIN_BLOCKS, ...(blocks as any[])].map((b: any) => (
                              <button key={b.id ?? b.name} onClick={() => insertBlock(b.html)} title="Insert at cursor" style={{ padding: '2px 8px', background: '#F0F9FF', border: '1px solid #BAE6FD', borderRadius: 6, fontSize: 11, color: '#0284C7', cursor: 'pointer' }}>{b.name}</button>))}
                          </div>
                          <textarea ref={bodyRef} style={{ ...inp, resize: 'vertical', fontFamily: 'monospace', fontSize: 12, lineHeight: 1.5 }} rows={14} value={form.htmlBody} onChange={e => f('htmlBody', e.target.value)} placeholder="<h2>Hi {{first_name}},</h2><p>Your message…</p>" />
                          <div style={{ fontSize: 11, color: C.mute, marginTop: 4 }}>Placeholders: {'{{first_name}} {{name}} {{email}} {{company_name}} {{unsubscribe_url}}'}. Blocks are copied in, so editing a block later never changes a saved campaign.</div>
                        </div>)}
                    {lintErrors.length > 0 && <div style={{ padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 12, color: '#991B1B' }}>{lintErrors.length} error(s) must be fixed before you can save: {lintErrors.map(l => l.message).join(' ')}</div>}
                  </div>)}

                {step === 3 && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                    <div style={{ fontWeight: 700, fontSize: 15, color: C.ink }}>Review</div>
                    <div style={{ border: `1px solid ${C.line}`, borderRadius: 11, overflow: 'hidden' }}>
                      {[['Name', form.name], ['Channel', 'Email'], ['From', form.fromName || '—'], ['Reply-to', form.replyTo || '—'], ['Audience', audLabel],
                        ['Subject', preview?.subject ?? form.subject], ['Send', form.scheduledAt ? new Date(form.scheduledAt).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : 'Draft: launch manually']].map(([k, v], i) => (
                        <div key={k} style={{ display: 'flex', justifyContent: 'space-between', gap: 20, padding: '10px 16px', borderTop: i ? `1px solid #F1F5F9` : 'none', fontSize: 13 }}>
                          <span style={{ color: C.mute, fontWeight: 600 }}>{k}</span><span style={{ color: C.ink, textAlign: 'right' }}>{v}</span></div>))}
                    </div>
                    <div style={{ border: `1px solid ${C.line}`, borderRadius: 11, overflow: 'hidden' }}>
                      <div style={{ padding: '9px 14px', background: C.bg, borderBottom: `1px solid ${C.line}`, ...{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.06em' } }}>Pre-flight checks</div>
                      <div style={{ padding: '4px 14px' }}>
                        {checks.map((c, i) => <div key={i} style={{ display: 'flex', gap: 9, padding: '7px 0', borderBottom: i < checks.length - 1 ? '1px solid #F1F5F9' : 'none' }}>{ico(c.level)}<div style={{ fontSize: 12, lineHeight: 1.5, color: col(c.level) }}>{c.text}</div></div>)}
                        <div style={{ display: 'flex', gap: 9, padding: '7px 0' }}>{ico('ok')}<div style={{ fontSize: 12, color: '#166534' }}>Sender identification and an unsubscribe link are added to every email. Consent is re-checked just before each send.</div></div>
                      </div>
                    </div>
                    {editing && <div><label style={lbl}>What changed? (shown in version history)</label><input style={inp} value={form.changeNote} maxLength={300} onChange={e => f('changeNote', e.target.value)} placeholder="e.g. Updated offer end date" /></div>}
                    {canLaunchNow && (
                      <label style={{ display: 'flex', gap: 10, fontSize: 13, color: C.ink, alignItems: 'flex-start', cursor: 'pointer' }}>
                        <input type="checkbox" checked={form.launchNow} onChange={e => f('launchNow', e.target.checked)} style={{ marginTop: 3 }} />
                        <span><strong>Launch immediately after saving.</strong> Emails are queued for eligible contacts straight away.</span></label>)}
                    {allErrs.length > 0 && <div style={{ padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 12, color: '#991B1B' }}>
                      {allErrs.map((e, i) => <div key={i}>• {e}</div>)}
                      <button onClick={() => setStep(firstBad)} style={{ marginTop: 6, background: 'none', border: 'none', color: C.primary, cursor: 'pointer', fontSize: 12, fontWeight: 600, padding: 0 }}>Go to the first problem</button></div>}
                  </div>)}
              </div>

              {showPreview && (
                <div style={{ position: 'sticky', top: 0 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <span style={{ fontSize: 13, fontWeight: 700, color: C.ink }}>Campaign preview</span>
                    <div style={{ display: 'flex', gap: 4 }}>{(['desktop', 'mobile'] as const).map(v => (
                      <button key={v} onClick={() => setPreviewMode(v)} style={{ padding: '2px 9px', borderRadius: 20, border: `1px solid ${C.line}`, background: previewMode === v ? C.navy : '#fff', color: previewMode === v ? '#fff' : '#64748B', fontSize: 11, cursor: 'pointer', textTransform: 'capitalize' }}>{v}</button>))}</div>
                  </div>
                  {preview ? (
                    <>
                      <div style={{ fontSize: 12, color: '#374151', marginBottom: 6 }}><strong>Subject:</strong> {preview.subject}</div>
                      <div style={{ border: `1px solid ${C.line}`, borderRadius: 10, background: C.bg, padding: 8 }}>
                        <iframe sandbox="" srcDoc={preview.html} title="Campaign preview" style={{ width: previewMode === 'mobile' ? 300 : '100%', height: 420, border: 'none', borderRadius: 8, background: '#fff', display: 'block', margin: '0 auto' }} />
                      </div>
                      <div style={{ fontSize: 10, color: C.mute, marginTop: 6 }}>Rendered with a sample contact, using the same placeholder rules as a real send.</div>
                    </>
                  ) : <div style={{ border: `1px dashed ${C.line}`, borderRadius: 10, padding: '60px 20px', textAlign: 'center', fontSize: 12, color: C.mute }}>The preview appears once you add a subject and content.</div>}
                  <div style={{ marginTop: 12 }}><Pill color="#92400E" bg="#FEF3C7">POPIA</Pill> <span style={{ fontSize: 11, color: C.text }}>Only opted-in, non-suppressed contacts receive this.</span></div>
                </div>)}
            </div>)}
        </div>

        {/* Footer */}
        <div style={{ padding: '14px 28px', borderTop: `1px solid ${C.line}` }}>
          {error && <div style={{ marginBottom: 10, padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <Btn onClick={onClose}>Cancel</Btn>
            <div style={{ display: 'flex', gap: 10 }}>
              {step > 0 && <Btn onClick={() => setStep(s => s - 1)}><ChevronLeft size={14} /> Back</Btn>}
              {step < 3
                ? <Btn variant="primary" disabled={!loaded || errs[step].length > 0} onClick={() => setStep(s => s + 1)}>Next <ChevronRight size={14} /></Btn>
                : <Btn variant="primary" disabled={!canSave || save.isPending} onClick={() => { setError(''); save.mutate() }}>
                    {save.isPending ? 'Saving…' : editing ? 'Save changes' : form.scheduledAt ? 'Schedule campaign' : form.launchNow && canLaunchNow ? 'Save & launch' : 'Save as draft'}</Btn>}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}