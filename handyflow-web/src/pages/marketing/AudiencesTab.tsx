// src/pages/marketing/AudiencesTab.tsx
import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, X, Filter, List, Users, RefreshCw, Trash2, Pencil, CheckCircle, AlertTriangle,
  ShieldAlert, ChevronLeft, ChevronRight, Info,
} from 'lucide-react'

const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box', background: '#fff', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }
const fmtDT = (d: any) => d ? new Date(d).toLocaleString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'

const SOURCES = ['MANUAL', 'WEB_FORM', 'EMAIL', 'PHONE', 'IN_PERSON', 'IMPORT', 'CRM_SYNC']
const TYPES   = ['CUSTOMER', 'SUBSCRIBER']
const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

type Kind = 'chips' | 'text' | 'date' | 'days'
const FIELDS: Record<string, { label: string; kind: Kind; options?: string[]; ops: [string, string][]; hint?: string }> = {
  OPT_IN_SOURCE: { label: 'Consent source', kind: 'chips', options: SOURCES, ops: [['IN', 'is any of'], ['NOT_IN', 'is none of']] },
  ENTITY_TYPE:   { label: 'Contact type',   kind: 'chips', options: TYPES,   ops: [['IN', 'is any of'], ['NOT_IN', 'is none of']] },
  EMAIL_DOMAIN:  { label: 'Email domain',   kind: 'text',  ops: [['IN', 'is any of'], ['NOT_IN', 'is none of']], hint: 'Comma-separated, e.g. acme.co.za, gmail.com' },
  OPTED_IN_DATE: { label: 'Opted-in date',  kind: 'date',  ops: [['ON_OR_AFTER', 'on or after'], ['BEFORE', 'before']] },
  CLICKED:       { label: 'Clicked a campaign link', kind: 'days', ops: [['WITHIN_DAYS', 'within the last'], ['NOT_WITHIN_DAYS', 'not within the last']] },
  OPENED:        { label: 'Opened a campaign (approx.)', kind: 'days', ops: [['WITHIN_DAYS', 'within the last'], ['NOT_WITHIN_DAYS', 'not within the last']] },
  EMAILED:       { label: 'Was sent a campaign', kind: 'days', ops: [['WITHIN_DAYS', 'within the last'], ['NOT_WITHIN_DAYS', 'not within the last']], hint: '“Not within” works as a frequency cap.' },
}

type Cond = { field: string; op: string; values: string[] }
const newCond = (field = 'OPT_IN_SOURCE'): Cond => ({ field, op: FIELDS[field].ops[0][0], values: [] })

const valuesOf = (c: Cond): string[] =>
  FIELDS[c.field].kind === 'text'
    ? (c.values[0] ?? '').split(',').map(s => s.trim()).filter(Boolean)
    : c.values.map(s => s.trim()).filter(Boolean)

const condValid = (c: Cond) => {
  const v = valuesOf(c)
  if (v.length === 0) return false
  if (FIELDS[c.field].kind === 'days') { const n = Number(v[0]); return Number.isInteger(n) && n >= 1 && n <= 730 }
  return true
}

const StatBox = ({ label, value, color, bg }: { label: string; value: number; color: string; bg: string }) => (
  <div style={{ background: bg, borderRadius: 9, padding: '9px 14px', minWidth: 110 }}>
    <div style={{ fontSize: 20, fontWeight: 800, color }}>{value.toLocaleString()}</div>
    <div style={{ fontSize: 11, color, opacity: 0.85 }}>{label}</div>
  </div>
)

export default function AudiencesTab() {
  const qc = useQueryClient()
  const [toast, setToast] = useState<{ msg: string; ok: boolean } | null>(null)
  const showToast = (msg: string, ok = true) => { setToast({ msg, ok }); setTimeout(() => setToast(null), 5000) }

  // ── List ──────────────────────────────────────────────────────────────────
  const { data: segments = [], isLoading } = useQuery<any[]>({
    queryKey: ['marketing-segments'],
    queryFn: async () => (await apiClient.get('/api/v1/marketing/segments')).data?.data ?? [],
  })
  const refreshList = () => qc.invalidateQueries({ queryKey: ['marketing-segments'] })

  // ── Editor ────────────────────────────────────────────────────────────────
  const [showEditor, setShowEditor] = useState(false)
  const [editing, setEditing]       = useState<any>(null)
  const [form, setForm] = useState({ name: '', description: '', type: 'DYNAMIC', match: 'ALL', conditions: [newCond()] as Cond[] })
  const [saveError, setSaveError]   = useState('')
  const [preview, setPreview]       = useState<any>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewError, setPreviewError]     = useState('')
  const reqId = useRef(0)

  const allValid = form.conditions.length > 0 && form.conditions.every(condValid)
  const condKey  = JSON.stringify(form.conditions)

  const toDef = () => ({ match: form.match, conditions: form.conditions.map(c => ({ field: c.field, op: c.op, values: valuesOf(c) })) })

  useEffect(() => {
    if (!showEditor || form.type !== 'DYNAMIC' || !allValid) { setPreview(null); setPreviewError(''); return }
    const my = ++reqId.current
    const t = setTimeout(async () => {
      setPreviewLoading(true)
      try {
        const r = await apiClient.post('/api/v1/marketing/segments/preview', { definition: toDef() })
        if (my === reqId.current) { setPreview(r.data?.data ?? r.data); setPreviewError('') }
      } catch (e: any) {
        if (my === reqId.current) { setPreview(null); setPreviewError(e.response?.data?.message || 'Preview failed') }
      } finally { if (my === reqId.current) setPreviewLoading(false) }
    }, 600)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showEditor, form.type, form.match, condKey, allValid])

  const openCreate = () => {
    setEditing(null); setSaveError(''); setPreview(null)
    setForm({ name: '', description: '', type: 'DYNAMIC', match: 'ALL', conditions: [newCond()] })
    setShowEditor(true)
  }
  const openEdit = (s: any) => {
    setEditing(s); setSaveError(''); setPreview(null)
    setForm({
      name: s.name, description: s.description ?? '', type: s.type, match: s.definition?.match ?? 'ALL',
      conditions: (s.definition?.conditions ?? []).map((c: any) => ({
        field: c.field, op: c.op,
        values: FIELDS[c.field]?.kind === 'text' ? [(c.values ?? []).join(', ')] : (c.values ?? []),
      })),
    })
    setShowEditor(true)
  }

  const updateCond = (i: number, patch: Partial<Cond>) =>
    setForm(p => ({ ...p, conditions: p.conditions.map((c, idx) => idx === i ? { ...c, ...patch } : c) }))

  const save = useMutation({
    mutationFn: () => {
      const body: any = { name: form.name.trim(), description: form.description.trim() || null, type: form.type,
        definition: form.type === 'DYNAMIC' ? toDef() : null }
      return editing ? apiClient.put(`/api/v1/marketing/segments/${editing.id}`, body)
                     : apiClient.post('/api/v1/marketing/segments', body)
    },
    onSuccess: (r: any) => {
      refreshList()
      setShowEditor(false)
      const saved = r.data?.data ?? r.data
      showToast(editing ? 'Segment updated.' : 'Segment created.')
      if (!editing && saved?.type === 'STATIC') setMembersSeg(saved)   // jump straight to adding members
    },
    onError: (e: any) => setSaveError(e.response?.data?.message || 'Failed to save segment'),
  })

  const canSave = form.name.trim().length > 0 && (form.type === 'STATIC' || allValid)

  // ── Row actions ───────────────────────────────────────────────────────────
  const refreshOne = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/marketing/segments/${id}/refresh`),
    onSuccess: () => { refreshList(); showToast('Count refreshed.') },
    onError: (e: any) => showToast(e.response?.data?.message || 'Refresh failed', false),
  })
  const del = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/marketing/segments/${id}`),
    onSuccess: () => { refreshList(); showToast('Segment deleted.') },
    onError: (e: any) => showToast(e.response?.data?.message || 'Delete failed', false),
  })

  // ── Static list members ───────────────────────────────────────────────────
  const [membersSeg, setMembersSeg]   = useState<any>(null)
  const [memberPage, setMemberPage]   = useState(0)
  const [emailText, setEmailText]     = useState('')
  const [memberMsg, setMemberMsg]     = useState('')

  const { data: membersData, isLoading: membersLoading } = useQuery<any>({
    queryKey: ['marketing-segment-members', membersSeg?.id, memberPage],
    enabled: !!membersSeg,
    queryFn: async () => (await apiClient.get(`/api/v1/marketing/segments/${membersSeg.id}/members?page=${memberPage}&size=25`)).data?.data,
    placeholderData: (prev: any) => prev,
  })
  const members: any[] = membersData?.content ?? []
  const memberPages = membersData?.totalPages ?? 1

  const parsedEmails = useMemo(() => {
    const all = emailText.split(/[\s,;]+/).map(s => s.trim().toLowerCase()).filter(Boolean)
    const valid = Array.from(new Set(all.filter(e => EMAIL_RE.test(e))))
    return { valid, invalid: all.length - all.filter(e => EMAIL_RE.test(e)).length }
  }, [emailText])

  const addMembers = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/marketing/segments/${membersSeg.id}/members`, { emails: parsedEmails.valid }),
    onSuccess: (r: any) => {
      const res = r.data?.data
      setMemberMsg(`${res.added} added · ${res.alreadyMembers} already in the list · ${res.notFound} not found in your contacts (they cannot be added).`)
      setEmailText(''); setMemberPage(0)
      qc.invalidateQueries({ queryKey: ['marketing-segment-members'] }); refreshList()
    },
    onError: (e: any) => setMemberMsg(e.response?.data?.message || 'Failed to add members'),
  })
  const removeMember = useMutation({
    mutationFn: (prefId: string) => apiClient.delete(`/api/v1/marketing/segments/${membersSeg.id}/members/${prefId}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketing-segment-members'] }); refreshList() },
    onError: (e: any) => showToast(e.response?.data?.message || 'Remove failed', false),
  })

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div>
      <div style={{ padding: '12px 16px', background: '#F0F9FF', border: '1px solid #BAE6FD', borderRadius: 10, marginBottom: 18, display: 'flex', gap: 10 }}>
        <Info size={16} color="#0369A1" style={{ flexShrink: 0, marginTop: 2 }} />
        <div style={{ fontSize: 12, color: '#0C4A6E', lineHeight: 1.6 }}>
          <strong>A segment only ever narrows who can be emailed.</strong> Whatever the rules say, a campaign only reaches contacts who are opted in and not suppressed.
          Dynamic segments are re-evaluated at the moment a campaign launches; static lists hold the contacts you add by hand.
          Counts below are the last computed number of <em>eligible</em> contacts.
        </div>
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <button onClick={openCreate}
          style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
          <Plus size={15} /> New audience
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading audiences…</div>
      ) : segments.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
          <Filter size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 700, color: '#475569', fontSize: 15, marginBottom: 6 }}>No audiences yet</div>
          <div style={{ fontSize: 13 }}>Create a segment to send a campaign to part of your list — for example, customers who clicked recently.</div>
        </div>
      ) : (
        <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ background: '#F8FAFC', borderBottom: '1px solid #E2E8F0' }}>
                {['Audience', 'Type', 'Eligible contacts', 'Used by', 'Updated', ''].map(h => (
                  <th key={h} style={{ padding: '10px 16px', textAlign: 'left', fontSize: 11, fontWeight: 700, color: '#64748B', letterSpacing: '0.05em', whiteSpace: 'nowrap' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {segments.map((s: any, i: number) => (
                <tr key={s.id} style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA', borderBottom: '1px solid #F1F5F9' }}>
                  <td style={{ padding: '12px 16px' }}>
                    <div style={{ fontWeight: 700, color: '#0F172A' }}>{s.name}</div>
                    {s.description && <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 2, maxWidth: 320 }}>{s.description}</div>}
                    {s.type === 'DYNAMIC' && s.definition && (
                      <div style={{ fontSize: 11, color: '#64748B', marginTop: 3 }}>
                        {s.definition.conditions.length} rule{s.definition.conditions.length === 1 ? '' : 's'} · match {s.definition.match === 'ANY' ? 'any' : 'all'}
                      </div>
                    )}
                  </td>
                  <td style={{ padding: '12px 16px' }}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: s.type === 'DYNAMIC' ? '#EEF2FF' : '#F0FDF9', color: s.type === 'DYNAMIC' ? '#1B3A6B' : '#0D9488', padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                      {s.type === 'DYNAMIC' ? <Filter size={10} /> : <List size={10} />}{s.type === 'DYNAMIC' ? 'Dynamic' : 'Static list'}
                    </span>
                  </td>
                  <td style={{ padding: '12px 16px' }}>
                    <div style={{ fontWeight: 800, color: '#0F172A', fontSize: 15 }}>{s.lastCount == null ? '—' : s.lastCount.toLocaleString()}</div>
                    <div style={{ fontSize: 10, color: '#94A3B8' }}>{s.lastCountedAt ? `as of ${fmtDT(s.lastCountedAt)}` : 'not counted yet'}</div>
                  </td>
                  <td style={{ padding: '12px 16px', color: s.usedByActiveCampaigns > 0 ? '#D97706' : '#94A3B8', fontSize: 12 }}>
                    {s.usedByActiveCampaigns > 0 ? `${s.usedByActiveCampaigns} active campaign${s.usedByActiveCampaigns === 1 ? '' : 's'}` : '—'}
                  </td>
                  <td style={{ padding: '12px 16px', color: '#94A3B8', fontSize: 11 }}>{fmtDT(s.updatedAt)}</td>
                  <td style={{ padding: '12px 16px' }}>
                    <div style={{ display: 'flex', gap: 5 }}>
                      {s.type === 'STATIC' && (
                        <button title="Manage members" onClick={() => { setMembersSeg(s); setMemberPage(0); setMemberMsg(''); setEmailText('') }}
                          style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 10px', background: '#F0FDF9', color: '#0D9488', border: '1px solid #99F6E4', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                          <Users size={11} /> Members
                        </button>
                      )}
                      <button title="Recompute count" onClick={() => refreshOne.mutate(s.id)} disabled={refreshOne.isPending}
                        style={{ display: 'flex', padding: '5px 8px', background: '#fff', border: '1px solid #E2E8F0', borderRadius: 7, color: '#64748B', cursor: 'pointer' }}><RefreshCw size={12} /></button>
                      <button title="Edit" onClick={() => openEdit(s)}
                        style={{ display: 'flex', padding: '5px 8px', background: '#fff', border: '1px solid #E2E8F0', borderRadius: 7, color: '#64748B', cursor: 'pointer' }}><Pencil size={12} /></button>
                      <button title={s.usedByActiveCampaigns > 0 ? 'In use by an unfinished campaign' : 'Delete'}
                        disabled={s.usedByActiveCampaigns > 0}
                        onClick={() => { if (window.confirm(`Delete "${s.name}"? This cannot be undone.`)) del.mutate(s.id) }}
                        style={{ display: 'flex', padding: '5px 8px', background: '#fff', border: '1px solid #FECACA', borderRadius: 7, color: '#DC2626', cursor: s.usedByActiveCampaigns > 0 ? 'not-allowed' : 'pointer', opacity: s.usedByActiveCampaigns > 0 ? 0.4 : 1 }}><Trash2 size={12} /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Editor modal */}
      {showEditor && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 680, maxHeight: '92vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>{editing ? `Edit — ${editing.name}` : 'New audience'}</h3>
              <button onClick={() => setShowEditor(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div>
                  <label style={lbl}>Name *</label>
                  <input autoFocus value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} placeholder="Engaged customers — last 90 days" style={inp} maxLength={150} />
                </div>
                <div>
                  <label style={lbl}>Type</label>
                  <select value={form.type} disabled={!!editing} onChange={e => setForm(p => ({ ...p, type: e.target.value }))} style={{ ...inp, background: editing ? '#F8FAFC' : '#fff' }}>
                    <option value="DYNAMIC">Dynamic — rules, re-evaluated at launch</option>
                    <option value="STATIC">Static list — contacts you add by hand</option>
                  </select>
                </div>
              </div>
              <div>
                <label style={lbl}>Description</label>
                <input value={form.description} onChange={e => setForm(p => ({ ...p, description: e.target.value }))} placeholder="Optional note for your team" style={inp} maxLength={1000} />
              </div>

              {form.type === 'STATIC' ? (
                <div style={{ padding: '10px 14px', background: '#F0FDF9', border: '1px solid #99F6E4', borderRadius: 8, fontSize: 12, color: '#0D9488', lineHeight: 1.6 }}>
                  After saving you can paste email addresses to add. Only people who already exist in your Contacts can be added — adding someone to a list never grants consent.
                </div>
              ) : (
                <>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 13, color: '#374151' }}>
                    Contacts must match
                    <select value={form.match} onChange={e => setForm(p => ({ ...p, match: e.target.value }))} style={{ ...inp, width: 'auto', padding: '6px 10px' }}>
                      <option value="ALL">all</option>
                      <option value="ANY">any</option>
                    </select>
                    of these rules:
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                    {form.conditions.map((c, i) => {
                      const meta = FIELDS[c.field]
                      return (
                        <div key={i} style={{ border: `1px solid ${condValid(c) ? '#E2E8F0' : '#FDE68A'}`, borderRadius: 10, padding: 12, background: '#FAFAFA' }}>
                          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                            <select value={c.field} onChange={e => setForm(p => ({ ...p, conditions: p.conditions.map((x, idx) => idx === i ? newCond(e.target.value) : x) }))}
                              style={{ ...inp, width: 'auto', minWidth: 210, padding: '7px 10px' }}>
                              {Object.entries(FIELDS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                            </select>
                            <select value={c.op} onChange={e => updateCond(i, { op: e.target.value })} style={{ ...inp, width: 'auto', padding: '7px 10px' }}>
                              {meta.ops.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                            </select>

                            {meta.kind === 'date' && (
                              <input type="date" value={c.values[0] ?? ''} onChange={e => updateCond(i, { values: [e.target.value] })} style={{ ...inp, width: 'auto', padding: '7px 10px' }} />
                            )}
                            {meta.kind === 'days' && (
                              <>
                                <input type="number" min={1} max={730} value={c.values[0] ?? ''} onChange={e => updateCond(i, { values: [e.target.value] })} placeholder="30" style={{ ...inp, width: 80, padding: '7px 10px' }} />
                                <span style={{ fontSize: 13, color: '#64748B' }}>days</span>
                              </>
                            )}
                            {meta.kind === 'text' && (
                              <input value={c.values[0] ?? ''} onChange={e => updateCond(i, { values: [e.target.value] })} placeholder="acme.co.za, gmail.com" style={{ ...inp, flex: 1, minWidth: 180, padding: '7px 10px' }} />
                            )}

                            {form.conditions.length > 1 && (
                              <button onClick={() => setForm(p => ({ ...p, conditions: p.conditions.filter((_, idx) => idx !== i) }))}
                                style={{ marginLeft: 'auto', background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={16} /></button>
                            )}
                          </div>

                          {meta.kind === 'chips' && (
                            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 10 }}>
                              {meta.options!.map(o => {
                                const on = c.values.includes(o)
                                return (
                                  <button key={o} onClick={() => updateCond(i, { values: on ? c.values.filter(v => v !== o) : [...c.values, o] })}
                                    style={{ padding: '4px 11px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: `1px solid ${on ? '#1B3A6B' : '#E2E8F0'}`, background: on ? '#1B3A6B' : '#fff', color: on ? '#fff' : '#475569' }}>
                                    {o.replace(/_/g, ' ')}
                                  </button>
                                )
                              })}
                            </div>
                          )}
                          {meta.hint && <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 8 }}>{meta.hint}</div>}
                        </div>
                      )
                    })}
                  </div>

                  <div>
                    <button disabled={form.conditions.length >= 20} onClick={() => setForm(p => ({ ...p, conditions: [...p.conditions, newCond()] }))}
                      style={{ display: 'inline-flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#fff', border: '1px dashed #CBD5E1', borderRadius: 8, fontSize: 12, color: '#475569', cursor: 'pointer' }}>
                      <Plus size={12} /> Add rule
                    </button>
                  </div>

                  {/* Live preview */}
                  <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
                    <div style={{ padding: '9px 14px', background: '#F8FAFC', borderBottom: '1px solid #E2E8F0', fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.06em', display: 'flex', justifyContent: 'space-between' }}>
                      <span>Live preview</span>{previewLoading && <span style={{ textTransform: 'none', fontWeight: 400 }}>Calculating…</span>}
                    </div>
                    <div style={{ padding: 14 }}>
                      {!allValid ? (
                        <div style={{ fontSize: 12, color: '#94A3B8' }}>Complete every rule to see how many contacts match.</div>
                      ) : previewError ? (
                        <div style={{ fontSize: 12, color: '#DC2626' }}>{previewError}</div>
                      ) : preview ? (
                        <>
                          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 12 }}>
                            <StatBox label="Match the rules" value={preview.matched} color="#1B3A6B" bg="#EEF2FF" />
                            <StatBox label="Not opted in" value={preview.notOptedIn} color="#64748B" bg="#F8FAFC" />
                            <StatBox label="Suppressed" value={preview.suppressed} color={preview.suppressed > 0 ? '#B91C1C' : '#64748B'} bg={preview.suppressed > 0 ? '#FEF2F2' : '#F8FAFC'} />
                            <StatBox label="Eligible to email" value={preview.eligible} color="#166534" bg="#DCFCE7" />
                          </div>
                          {preview.eligible === 0 && (
                            <div style={{ fontSize: 12, color: '#92400E', marginBottom: 8, display: 'flex', gap: 6, alignItems: 'center' }}>
                              <AlertTriangle size={13} /> Nobody would receive a campaign sent to this audience right now.
                            </div>
                          )}
                          {preview.sample?.length > 0 && (
                            <div style={{ fontSize: 12, color: '#64748B' }}>
                              <div style={{ fontWeight: 700, marginBottom: 4 }}>Sample of eligible contacts</div>
                              {preview.sample.map((c: any, i: number) => (
                                <div key={i} style={{ padding: '2px 0' }}>{c.name ? <strong style={{ color: '#374151' }}>{c.name}</strong> : null}{c.name ? ' · ' : ''}{c.email}</div>
                              ))}
                            </div>
                          )}
                        </>
                      ) : <div style={{ fontSize: 12, color: '#94A3B8' }}>…</div>}
                    </div>
                  </div>
                </>
              )}
            </div>

            {editing && editing.usedByActiveCampaigns > 0 && (
              <div style={{ marginTop: 14, padding: '9px 12px', background: '#FFF7ED', border: '1px solid #FED7AA', borderRadius: 8, fontSize: 12, color: '#92400E' }}>
                {editing.usedByActiveCampaigns} unfinished campaign{editing.usedByActiveCampaigns === 1 ? ' uses' : 's use'} this audience. Campaigns that haven’t launched yet will pick up your changes at launch.
              </div>
            )}
            {saveError && <div style={{ marginTop: 12, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{saveError}</div>}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 22 }}>
              <button onClick={() => setShowEditor(false)} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: '#fff', fontSize: 14, cursor: 'pointer' }}>Cancel</button>
              <button disabled={!canSave || save.isPending} onClick={() => save.mutate()}
                style={{ padding: '9px 22px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: canSave ? 'pointer' : 'not-allowed', opacity: canSave ? 1 : 0.5 }}>
                {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Create audience'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Static list members drawer */}
      {membersSeg && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', justifyContent: 'flex-end', zIndex: 1000 }} onClick={() => setMembersSeg(null)}>
          <div style={{ background: '#fff', width: 520, height: '100%', overflowY: 'auto', boxShadow: '-10px 0 40px rgba(0,0,0,0.15)', padding: 28 }} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 18 }}>
              <div>
                <div style={{ fontWeight: 800, fontSize: 17, color: '#0F172A' }}>{membersSeg.name}</div>
                <div style={{ fontSize: 12, color: '#64748B', marginTop: 3 }}>Static list · {membersData?.totalElements ?? 0} member{(membersData?.totalElements ?? 0) === 1 ? '' : 's'}</div>
              </div>
              <button onClick={() => setMembersSeg(null)} style={{ background: '#F1F5F9', border: 'none', borderRadius: '50%', width: 30, height: 30, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#64748B' }}><X size={14} /></button>
            </div>

            <label style={lbl}>Add by email address</label>
            <textarea value={emailText} onChange={e => setEmailText(e.target.value)} rows={4} placeholder="Paste addresses separated by commas, spaces or new lines" style={{ ...inp, resize: 'vertical', fontFamily: 'monospace', fontSize: 12 }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 6, marginBottom: 6 }}>
              <span style={{ fontSize: 11, color: '#94A3B8' }}>
                {parsedEmails.valid.length} valid{parsedEmails.invalid > 0 ? ` · ${parsedEmails.invalid} invalid (ignored)` : ''} · only existing contacts are added
              </span>
              <button disabled={parsedEmails.valid.length === 0 || addMembers.isPending} onClick={() => addMembers.mutate()}
                style={{ padding: '7px 16px', background: '#0D9488', color: '#fff', border: 'none', borderRadius: 8, fontSize: 13, fontWeight: 700, cursor: 'pointer', opacity: parsedEmails.valid.length === 0 ? 0.5 : 1 }}>
                {addMembers.isPending ? 'Adding…' : 'Add to list'}
              </button>
            </div>
            {memberMsg && <div style={{ fontSize: 12, color: '#374151', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, padding: '8px 12px', marginBottom: 14 }}>{memberMsg}</div>}

            <div style={{ marginTop: 18 }}>
              {membersLoading ? <div style={{ color: '#94A3B8', fontSize: 13 }}>Loading…</div>
                : members.length === 0 ? <div style={{ textAlign: 'center', padding: '30px 10px', color: '#94A3B8', fontSize: 13 }}>No members yet.</div>
                : (
                  <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
                    {members.map((m: any, i: number) => (
                      <div key={m.preferenceId} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px', borderTop: i ? '1px solid #F1F5F9' : 'none' }}>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', overflow: 'hidden', textOverflow: 'ellipsis' }}>{m.name || m.email}</div>
                          {m.name && <div style={{ fontSize: 11, color: '#94A3B8' }}>{m.email}</div>}
                        </div>
                        {m.suppressed ? (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, background: '#FEF2F2', color: '#B91C1C', padding: '2px 8px', borderRadius: 20, fontSize: 10, fontWeight: 700 }}><ShieldAlert size={9} /> Suppressed</span>
                        ) : m.optedIn ? (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, background: '#DCFCE7', color: '#166534', padding: '2px 8px', borderRadius: 20, fontSize: 10, fontWeight: 700 }}><CheckCircle size={9} /> Eligible</span>
                        ) : (
                          <span style={{ background: '#F8FAFC', color: '#94A3B8', padding: '2px 8px', borderRadius: 20, fontSize: 10 }} title="Will not receive emails until they opt in">Not opted in</span>
                        )}
                        <button title="Remove from list" onClick={() => removeMember.mutate(m.preferenceId)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={14} /></button>
                      </div>
                    ))}
                  </div>
                )}
              {memberPages > 1 && (
                <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 10, marginTop: 12 }}>
                  <span style={{ fontSize: 12, color: '#94A3B8' }}>Page {memberPage + 1} of {memberPages}</span>
                  <button disabled={memberPage === 0} onClick={() => setMemberPage(p => p - 1)} style={{ display: 'flex', padding: 6, border: '1px solid #E2E8F0', borderRadius: 7, background: '#fff', opacity: memberPage === 0 ? 0.4 : 1, cursor: 'pointer' }}><ChevronLeft size={14} /></button>
                  <button disabled={memberPage + 1 >= memberPages} onClick={() => setMemberPage(p => p + 1)} style={{ display: 'flex', padding: 6, border: '1px solid #E2E8F0', borderRadius: 7, background: '#fff', opacity: memberPage + 1 >= memberPages ? 0.4 : 1, cursor: 'pointer' }}><ChevronRight size={14} /></button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {toast && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 3000, display: 'flex', alignItems: 'center', gap: 10, background: toast.ok ? '#DCFCE7' : '#FEF2F2', border: `1px solid ${toast.ok ? '#86EFAC' : '#FECACA'}`, borderRadius: 10, padding: '12px 18px', boxShadow: '0 8px 24px rgba(0,0,0,0.12)', maxWidth: 420 }}>
          {toast.ok ? <CheckCircle size={16} color="#166534" /> : <AlertTriangle size={16} color="#DC2626" />}
          <span style={{ fontSize: 13, fontWeight: 600, color: toast.ok ? '#166534' : '#DC2626' }}>{toast.msg}</span>
          <button onClick={() => setToast(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: toast.ok ? '#166534' : '#DC2626', display: 'flex', padding: 0 }}><X size={14} /></button>
        </div>
      )}
    </div>
  )
}