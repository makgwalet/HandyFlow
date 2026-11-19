// src/pages/marketing/JourneysTab.tsx
import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, X, Workflow, Play, Pause, Archive, Copy, Pencil, Mail, Clock, Flag, ChevronUp, ChevronDown,
  Trash2, AlertTriangle, CheckCircle, Info, ChevronLeft, ChevronRight,
} from 'lucide-react'
import JourneyFlow from './JourneyFlow'

const fmtDT = (d: any) => d ? new Date(d).toLocaleString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'
const pct = (r: number | null | undefined) => r == null ? '—' : `${(r * 100).toFixed(1)}%`
const inp: React.CSSProperties = { width: '100%', padding: '8px 11px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 13, boxSizing: 'border-box', background: '#fff', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 5 }
const sec: React.CSSProperties = { fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.06em', margin: '20px 0 8px' }

const STATUS: Record<string, { c: string; bg: string; l: string }> = {
  DRAFT: { c: '#64748B', bg: '#F1F5F9', l: 'Draft' }, ACTIVE: { c: '#166534', bg: '#DCFCE7', l: 'Active' },
  PAUSED: { c: '#9333EA', bg: '#FAF5FF', l: 'Paused' }, ARCHIVED: { c: '#94A3B8', bg: '#F1F5F9', l: 'Archived' },
}
const TRIGGERS: Record<string, string> = {
  OPTED_IN: 'Contact opts in (after activation)', SEGMENT_ENTRY: 'Contact matches a segment', OPT_IN_ANNIVERSARY: 'Opt-in anniversary (yearly)',
}
const E = (name: string, subject: string, htmlBody: string) => ({ type: 'EMAIL', name, subject, htmlBody, fromName: '', replyTo: '' })
const W = (amount: number, unit = 'DAYS') => ({ type: 'WAIT', amount, unit })
const X_IF = (condition: string) => ({ type: 'EXIT_IF', condition })
const STARTERS: Record<string, any> = {
  welcome: { name: 'Welcome series', triggerType: 'OPTED_IN', reentryDays: '', steps: [
    E('Welcome', 'Welcome to {{company_name}}, {{first_name}}', '<h2>Hi {{first_name}},</h2><p>Thanks for joining us. Here is what you can expect from {{company_name}}.</p>'),
    W(3), E('What to expect', 'A few things worth knowing, {{first_name}}', '<p>Hi {{first_name}},</p><p>Tell them about your products, services and how to reach you.</p>')] },
  reengage: { name: 'Re-engagement', triggerType: 'SEGMENT_ENTRY', reentryDays: '90', steps: [
    E('We miss you', '{{first_name}}, still interested?', '<p>Hi {{first_name}},</p><p>We have not heard from you in a while. Reply to tell us what you would like to see.</p>'),
    W(7), X_IF('CLICKED'), E('Last note', 'Should we keep in touch?', '<p>Hi {{first_name}},</p><p>This is our last reminder. You can unsubscribe below at any time.</p>')] },
  anniversary: { name: 'Anniversary thank-you', triggerType: 'OPT_IN_ANNIVERSARY', reentryDays: '', steps: [
    E('Thank you', 'A year together, {{first_name}}', '<h2>Thank you, {{first_name}}</h2><p>You joined our list a year ago today. Thanks for being with {{company_name}}.</p>')] },
}
const blank = () => ({ name: '', description: '', triggerType: 'OPTED_IN', segmentId: '', reentryDays: '', minGapHours: '24', windowStartHour: '8', windowEndHour: '17', skipWeekends: true, steps: [E('Email 1', '', '')] as any[] })

export default function JourneysTab() {
  const qc = useQueryClient()
  const [editor, setEditor] = useState<{ journey?: any; prefill?: any } | null>(null)
  const [detail, setDetail] = useState<any>(null)
  const [toast, setToast] = useState<{ msg: string; ok: boolean } | null>(null)
  const say = (msg: string, ok = true) => { setToast({ msg, ok }); setTimeout(() => setToast(null), 6000) }

  const { data: journeys = [], isLoading } = useQuery<any[]>({
    queryKey: ['marketing-journeys'],
    queryFn: async () => (await apiClient.get('/api/v1/marketing/journeys')).data?.data ?? [],
    refetchInterval: 30_000,
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['marketing-journeys'] }); qc.invalidateQueries({ queryKey: ['marketing-journey-stats'] }) }

  return (
    <div>
      <div style={{ padding: '12px 16px', background: '#F0F9FF', border: '1px solid #BAE6FD', borderRadius: 10, marginBottom: 18, fontSize: 12, color: '#0C4A6E', lineHeight: 1.6, display: 'flex', gap: 10 }}>
        <Info size={16} color="#0369A1" style={{ flexShrink: 0, marginTop: 2 }} />
        <div>Journeys send automatically, so every email still goes through consent and suppression checks <strong>just before it is sent</strong>.
          Contacts who opt out leave every journey within minutes. Emails respect a send window (South African time) and a minimum gap between journey emails.
          Activation needs an admin, and steps are frozen once active — clone a journey to change it.</div>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap', fontSize: 12, color: '#64748B' }}>
          Start from:
          {Object.entries(STARTERS).map(([k, s]) => (
            <button key={k} onClick={() => setEditor({ prefill: s })} style={{ padding: '4px 11px', background: '#fff', border: '1px solid #E2E8F0', borderRadius: 20, fontSize: 12, cursor: 'pointer' }}>{s.name}</button>))}
        </div>
        <button onClick={() => setEditor({})} style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}><Plus size={15} /> New journey</button>
      </div>

      {isLoading ? <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading…</div>
        : journeys.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '50px 20px', color: '#94A3B8' }}>
            <Workflow size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
            <div style={{ fontWeight: 700, color: '#475569', marginBottom: 6 }}>No journeys yet</div>
            <div style={{ fontSize: 13 }}>Pick a starter above to build your first automated sequence.</div>
          </div>
        ) : (
          <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead><tr style={{ background: '#F8FAFC', borderBottom: '1px solid #E2E8F0' }}>
                {['Journey', 'Status', 'Trigger', 'In progress', 'Completed', 'Exited', 'Updated'].map(h => <th key={h} style={{ padding: '10px 16px', textAlign: 'left', fontSize: 11, fontWeight: 700, color: '#64748B' }}>{h}</th>)}
              </tr></thead>
              <tbody>
                {journeys.map((j: any, i: number) => { const s = STATUS[j.status] ?? STATUS.DRAFT; return (
                  <tr key={j.id} onClick={() => setDetail(j)} style={{ background: i % 2 ? '#FAFAFA' : '#fff', borderBottom: '1px solid #F1F5F9', cursor: 'pointer' }}>
                    <td style={{ padding: '12px 16px' }}><div style={{ fontWeight: 700, color: '#0F172A' }}>{j.name}</div><div style={{ fontSize: 11, color: '#94A3B8' }}>{j.steps.filter((x: any) => x.type === 'EMAIL').length} email(s)</div></td>
                    <td style={{ padding: '12px 16px' }}><span style={{ background: s.bg, color: s.c, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{s.l}</span></td>
                    <td style={{ padding: '12px 16px', color: '#475569', fontSize: 12 }}>{TRIGGERS[j.triggerType]}{j.segmentName ? `: ${j.segmentName}` : ''}</td>
                    <td style={{ padding: '12px 16px', fontWeight: 700 }}>{j.activeCount}</td>
                    <td style={{ padding: '12px 16px', color: '#166534' }}>{j.completedCount}</td>
                    <td style={{ padding: '12px 16px', color: '#64748B' }}>{j.exitedCount}</td>
                    <td style={{ padding: '12px 16px', color: '#94A3B8', fontSize: 11 }}>{fmtDT(j.updatedAt)}</td>
                  </tr>) })}
              </tbody>
            </table>
          </div>)}

      {detail && <JourneyDetail journey={journeys.find((j: any) => j.id === detail.id) ?? detail} onClose={() => setDetail(null)} say={say} refresh={refresh}
        onEdit={(j: any) => { setEditor({ journey: j }) }} onCloned={(j: any) => { refresh(); setDetail(j) }} />}
      {editor && <JourneyEditor journey={editor.journey} prefill={editor.prefill} onClose={() => setEditor(null)}
        onSaved={(j: any) => { setEditor(null); refresh(); say('Journey saved.'); setDetail(j) }} />}

      {toast && <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 3000, display: 'flex', gap: 10, alignItems: 'center', background: toast.ok ? '#DCFCE7' : '#FEF2F2', border: `1px solid ${toast.ok ? '#86EFAC' : '#FECACA'}`, borderRadius: 10, padding: '12px 18px', maxWidth: 440 }}>
        {toast.ok ? <CheckCircle size={16} color="#166534" /> : <AlertTriangle size={16} color="#DC2626" />}
        <span style={{ fontSize: 13, fontWeight: 600, color: toast.ok ? '#166534' : '#DC2626' }}>{toast.msg}</span></div>}
    </div>
  )
}

function JourneyDetail({ journey: j, onClose, onEdit, onCloned, say, refresh }: any) {
  const qc = useQueryClient()
  const [status, setStatus] = useState('ALL')
  const [page, setPage] = useState(0)
  const [openLog, setOpenLog] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<any>(null)
  const err = (e: any, d: string) => say(e.response?.status === 403 ? 'That action needs the Marketing admin permission.' : (e.response?.data?.message || d), false)

  const { data: stats } = useQuery<any>({ queryKey: ['marketing-journey-stats', j.id], queryFn: async () => (await apiClient.get(`/api/v1/marketing/journeys/${j.id}/stats`)).data?.data, refetchInterval: 30_000 })
  const { data: enr } = useQuery<any>({
    queryKey: ['marketing-journey-enrollments', j.id, status, page], placeholderData: (p: any) => p,
    queryFn: async () => (await apiClient.get(`/api/v1/marketing/journeys/${j.id}/enrollments`, { params: { status, page, size: 15 } })).data?.data,
  })
  const { data: log = [] } = useQuery<any[]>({ queryKey: ['marketing-journey-log', j.id, openLog], enabled: !!openLog,
    queryFn: async () => (await apiClient.get(`/api/v1/marketing/journeys/${j.id}/enrollments/${openLog}/log`)).data?.data ?? [] })

  const act = useMutation({
    mutationFn: (a: string) => apiClient.post(`/api/v1/marketing/journeys/${j.id}/${a}`),
    onSuccess: (r: any, a) => { refresh(); qc.invalidateQueries({ queryKey: ['marketing-journey-enrollments'] }); setConfirm(null)
      if (a === 'clone') onCloned(r.data?.data); else say(a === 'resume-steps' ? `Resumed ${r.data?.data?.resumed ?? 0} step(s).` : 'Done.') },
    onError: (e: any) => { setConfirm(null); err(e, 'Action failed') },
  })
  const cancel = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/marketing/journeys/${j.id}/enrollments/${id}/cancel`),
    onSuccess: () => { refresh(); qc.invalidateQueries({ queryKey: ['marketing-journey-enrollments'] }) }, onError: (e: any) => err(e, 'Failed'),
  })
  const askActivate = async () => {
    try { const p = (await apiClient.get(`/api/v1/marketing/journeys/${j.id}/preview-activation`)).data?.data; setConfirm({ kind: 'activate', preview: p }) }
    catch (e: any) { err(e, 'Could not prepare activation') }
  }

  const s = STATUS[j.status] ?? STATUS.DRAFT
  const pausedSteps = (stats?.steps ?? []).filter((x: any) => x.campaignStatus === 'PAUSED')
  const pages = enr?.totalPages ?? 1
  const btn = (c: string): React.CSSProperties => ({ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#fff', border: `1px solid ${c}`, color: c, borderRadius: 8, fontSize: 12, fontWeight: 700, cursor: 'pointer' })

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', justifyContent: 'flex-end', zIndex: 1000 }} onClick={onClose}>
      <div style={{ background: '#fff', width: 700, maxWidth: '96vw', height: '100%', overflowY: 'auto', padding: 28 }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
          <div><div style={{ fontWeight: 800, fontSize: 17 }}>{j.name}</div>
            <div style={{ marginTop: 5, display: 'flex', gap: 8, alignItems: 'center' }}><span style={{ background: s.bg, color: s.c, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{s.l}</span>
              <span style={{ fontSize: 12, color: '#64748B' }}>{TRIGGERS[j.triggerType]}{j.segmentName ? `: ${j.segmentName}` : ''}</span></div></div>
          <button onClick={onClose} style={{ background: '#F1F5F9', border: 'none', borderRadius: '50%', width: 30, height: 30, cursor: 'pointer' }}><X size={14} /></button>
        </div>
        {j.description && <div style={{ fontSize: 12, color: '#64748B', marginBottom: 12 }}>{j.description}</div>}

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {j.status === 'DRAFT' && <button style={btn('#166534')} onClick={askActivate}><Play size={12} /> Activate</button>}
          {j.status === 'ACTIVE' && <button style={btn('#D97706')} onClick={() => act.mutate('pause')}><Pause size={12} /> Pause</button>}
          {j.status === 'PAUSED' && <button style={btn('#166534')} onClick={() => act.mutate('resume')}><Play size={12} /> Resume</button>}
          {j.status !== 'ARCHIVED' && <button style={btn('#475569')} onClick={() => onEdit(j)}><Pencil size={12} /> {j.status === 'DRAFT' ? 'Edit' : 'Settings'}</button>}
          <button style={btn('#475569')} onClick={() => act.mutate('clone')}><Copy size={12} /> Clone</button>
          {j.status !== 'ARCHIVED' && <button style={btn('#DC2626')} onClick={() => setConfirm({ kind: 'archive' })}><Archive size={12} /> Archive</button>}
        </div>

        {pausedSteps.length > 0 && (
          <div style={{ marginTop: 14, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 12, color: '#991B1B', lineHeight: 1.55 }}>
            <strong>{pausedSteps.length} email step(s) were auto-paused</strong> because of delivery problems: {pausedSteps[0].pauseReason ?? 'high bounce or complaint rate'}.
            Contacts are held at those steps. Fix the list or content, then resume.
            <div style={{ marginTop: 8 }}><button onClick={() => act.mutate('resume-steps')} style={btn('#991B1B')}>Resume paused steps (admin)</button></div>
          </div>)}

        <div style={sec}>Steps</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {(stats?.steps ?? []).map((st: any) => (
            <div key={st.index} style={{ border: '1px solid #E2E8F0', borderRadius: 10, padding: '10px 14px', display: 'flex', gap: 12, alignItems: 'center' }}>
              <div style={{ color: '#64748B' }}>{st.type === 'EMAIL' ? <Mail size={16} color="#1B3A6B" /> : st.type === 'WAIT' ? <Clock size={16} /> : <Flag size={16} color="#7C3AED" />}</div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{st.index + 1}. {st.summary}</div>
                {st.metrics && <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>{st.metrics.sent} sent · click {pct(st.metrics.clickRate)} · open (approx.) {pct(st.metrics.openRate)} · bounce {pct(st.metrics.bounceRate)} · unsub {pct(st.metrics.unsubscribeRate)}</div>}
              </div>
              <div style={{ fontSize: 11, color: st.waiting ? '#B45309' : '#CBD5E1', fontWeight: 700 }}>{st.waiting} here</div>
            </div>))}
        </div>

        <div style={sec}>People</div>
        <div style={{ display: 'flex', gap: 6, marginBottom: 10, flexWrap: 'wrap' }}>
          {['ALL', 'ACTIVE', 'COMPLETED', 'EXITED', 'CANCELLED'].map(x => (
            <button key={x} onClick={() => { setStatus(x); setPage(0) }} style={{ padding: '3px 11px', borderRadius: 20, fontSize: 11, fontWeight: 600, cursor: 'pointer', border: '1px solid #E2E8F0', background: status === x ? '#1B3A6B' : '#fff', color: status === x ? '#fff' : '#64748B' }}>
              {x === 'ALL' ? 'All' : x.charAt(0) + x.slice(1).toLowerCase()}{stats && x !== 'ALL' ? ` (${stats.enrollments[x] ?? 0})` : ''}</button>))}
        </div>
        <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
          {(enr?.content ?? []).map((r: any, i: number) => (
            <div key={r.id} style={{ borderTop: i ? '1px solid #F1F5F9' : 'none' }}>
              <div style={{ display: 'flex', gap: 10, padding: '9px 12px', alignItems: 'center', fontSize: 12 }}>
                <div style={{ flex: 1, minWidth: 0, cursor: 'pointer' }} onClick={() => setOpenLog(openLog === r.id ? null : r.id)}>
                  <div style={{ fontWeight: 600, color: '#0F172A' }}>{r.name || r.email}</div>
                  <div style={{ color: '#94A3B8', fontSize: 10 }}>{r.email} · step {r.currentStep + 1} · {r.status === 'ACTIVE' ? `next ${fmtDT(r.nextRunAt)}` : (r.exitReason ?? r.status)}</div>
                  {r.lastError && <div style={{ color: '#B91C1C', fontSize: 10 }}>{r.lastError}</div>}
                </div>
                <span style={{ fontSize: 10, fontWeight: 700, color: r.status === 'ACTIVE' ? '#B45309' : r.status === 'COMPLETED' ? '#166534' : '#64748B' }}>{r.status}</span>
                {r.status === 'ACTIVE' && <button title="Remove from journey" onClick={() => { if (window.confirm(`Remove ${r.email} from this journey?`)) cancel.mutate(r.id) }} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><Trash2 size={13} /></button>}
              </div>
              {openLog === r.id && (
                <div style={{ background: '#F8FAFC', padding: '8px 14px', fontSize: 11, color: '#475569' }}>
                  {log.length === 0 ? 'No history yet.' : log.map((l: any, k: number) => <div key={k}>{fmtDT(l.at)} — step {l.stepIndex + 1} {l.stepType.toLowerCase()}: <strong>{l.outcome.replace(/_/g, ' ').toLowerCase()}</strong>{l.detail ? ` (${l.detail})` : ''}</div>)}
                </div>)}
            </div>))}
          {(enr?.content ?? []).length === 0 && <div style={{ padding: 20, textAlign: 'center', color: '#94A3B8', fontSize: 12 }}>Nobody here yet.</div>}
        </div>
        {(enr?.totalElements ?? 0) > 15 && (
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 8, alignItems: 'center', fontSize: 11, color: '#94A3B8' }}>Page {page + 1} of {pages}
            <button disabled={page === 0} onClick={() => setPage(p => p - 1)} style={{ display: 'flex', padding: 5, border: '1px solid #E2E8F0', borderRadius: 7, background: '#fff', opacity: page === 0 ? 0.4 : 1 }}><ChevronLeft size={13} /></button>
            <button disabled={page + 1 >= pages} onClick={() => setPage(p => p + 1)} style={{ display: 'flex', padding: 5, border: '1px solid #E2E8F0', borderRadius: 7, background: '#fff', opacity: page + 1 >= pages ? 0.4 : 1 }}><ChevronRight size={13} /></button></div>)}

        {confirm && (
          <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000 }}>
            <div style={{ background: '#fff', borderRadius: 14, padding: 26, width: 480 }}>
              {confirm.kind === 'archive' ? (
                <><div style={{ fontWeight: 700, fontSize: 15, marginBottom: 8 }}>Archive “{j.name}”?</div>
                  <div style={{ fontSize: 13, color: '#64748B', lineHeight: 1.6 }}>All {j.activeCount} contact(s) currently in progress are removed from the journey. Emails already queued still go out. This cannot be undone.</div></>
              ) : (
                <><div style={{ fontWeight: 700, fontSize: 15, marginBottom: 8 }}>Activate “{j.name}”?</div>
                  <div style={{ fontSize: 13, color: '#374151', lineHeight: 1.6, marginBottom: 10 }}>{confirm.preview.note}</div>
                  {confirm.preview.issues.length > 0 && (
                    <div style={{ border: '1px solid #E2E8F0', borderRadius: 8, padding: '6px 12px', marginBottom: 10, maxHeight: 180, overflowY: 'auto' }}>
                      {confirm.preview.issues.map((x: any, i: number) => <div key={i} style={{ fontSize: 12, padding: '4px 0', color: x.level === 'ERROR' ? '#991B1B' : '#92400E' }}>{x.level === 'ERROR' ? '✖' : '⚠'} {x.message}</div>)}
                    </div>)}
                  <div style={{ fontSize: 12, color: '#64748B' }}>Steps are frozen once active. Emails send inside the send window, after the consent re-check.</div></>)}
              <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 18 }}>
                <button onClick={() => setConfirm(null)} style={{ padding: '8px 16px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', cursor: 'pointer' }}>Back</button>
                <button disabled={act.isPending || (confirm.kind === 'activate' && confirm.preview.issues.some((x: any) => x.level === 'ERROR'))}
                  onClick={() => act.mutate(confirm.kind === 'archive' ? 'archive' : 'activate')}
                  style={{ padding: '8px 18px', background: confirm.kind === 'archive' ? '#DC2626' : '#166534', color: '#fff', border: 'none', borderRadius: 8, fontWeight: 700, cursor: 'pointer',
                    opacity: confirm.kind === 'activate' && confirm.preview.issues.some((x: any) => x.level === 'ERROR') ? 0.5 : 1 }}>{act.isPending ? 'Working…' : confirm.kind === 'archive' ? 'Archive' : 'Activate'}</button>
              </div></div></div>)}
      </div>
    </div>
  )
}

function JourneyEditor({ journey, prefill, onClose, onSaved }: any) {
  const locked = !!journey && journey.status !== 'DRAFT'
  const init = useMemo(() => {
    if (journey) return { name: journey.name, description: journey.description ?? '', triggerType: journey.triggerType, segmentId: journey.segmentId ?? '',
      reentryDays: journey.reentryDays?.toString() ?? '', minGapHours: String(journey.minGapHours), windowStartHour: String(journey.windowStartHour),
      windowEndHour: String(journey.windowEndHour), skipWeekends: journey.skipWeekends, steps: journey.steps.map((s: any) => ({ ...s })) }
    return { ...blank(), ...(prefill ?? {}), steps: (prefill?.steps ?? blank().steps).map((s: any) => ({ ...s })) }
  }, [journey, prefill])
  const [f, setF] = useState<any>(init)
  const [error, setError] = useState('')
  const set = (k: string, v: any) => setF((p: any) => ({ ...p, [k]: v }))
  const setStep = (i: number, patch: any) => setF((p: any) => ({ ...p, steps: p.steps.map((s: any, k: number) => k === i ? { ...s, ...patch } : s) }))
  const move = (i: number, d: number) => setF((p: any) => { const a = [...p.steps]; const j = i + d; if (j < 0 || j >= a.length) return p; [a[i], a[j]] = [a[j], a[i]]; return { ...p, steps: a } })

  const { data: segments = [] } = useQuery<any[]>({ queryKey: ['marketing-segments'], queryFn: async () => (await apiClient.get('/api/v1/marketing/segments')).data?.data ?? [] })

  const save = useMutation({
    mutationFn: () => {
      const num = (v: string) => v === '' ? null : Number(v)
      if (locked) return apiClient.put(`/api/v1/marketing/journeys/${journey.id}/settings`, { name: f.name.trim(), description: f.description.trim() || null,
        minGapHours: num(f.minGapHours), windowStartHour: num(f.windowStartHour), windowEndHour: num(f.windowEndHour), skipWeekends: f.skipWeekends })
      const body = { name: f.name.trim(), description: f.description.trim() || null, triggerType: f.triggerType,
        segmentId: f.triggerType === 'SEGMENT_ENTRY' ? f.segmentId || null : null, reentryDays: num(f.reentryDays),
        minGapHours: num(f.minGapHours), windowStartHour: num(f.windowStartHour), windowEndHour: num(f.windowEndHour), skipWeekends: f.skipWeekends,
        steps: f.steps.map((s: any) => s.type === 'WAIT' ? { type: 'WAIT', amount: Number(s.amount), unit: s.unit } : s.type === 'EXIT_IF' ? { type: 'EXIT_IF', condition: s.condition }
          : { type: 'EMAIL', name: s.name, subject: s.subject, htmlBody: s.htmlBody, fromName: s.fromName || null, replyTo: s.replyTo || null }) }
      return journey ? apiClient.put(`/api/v1/marketing/journeys/${journey.id}`, body) : apiClient.post('/api/v1/marketing/journeys', body)
    },
    onSuccess: (r: any) => onSaved(r.data?.data),
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to save journey'),
  })

  const ok = f.name.trim() && (locked || (f.steps.length > 0 && (f.triggerType !== 'SEGMENT_ENTRY' || f.segmentId)))
  const stepBtn: React.CSSProperties = { display: 'flex', padding: 4, background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1500 }}>
      <div style={{ background: '#fff', borderRadius: 16, padding: 26, width: 720, maxHeight: '92vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>{journey ? (locked ? `Settings — ${journey.name}` : `Edit — ${journey.name}`) : 'New journey'}</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={20} /></button>
        </div>
        {locked && <div style={{ padding: '9px 12px', background: '#FFF7ED', border: '1px solid #FED7AA', borderRadius: 8, fontSize: 12, color: '#92400E', marginBottom: 12 }}>Steps and trigger are frozen because this journey has been activated. You can change its name and pacing; clone it to change the steps.</div>}

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 12 }}>
          <div><label style={lbl}>Name *</label><input style={inp} value={f.name} maxLength={150} onChange={e => set('name', e.target.value)} autoFocus /></div>
          <div><label style={lbl}>Description</label><input style={inp} value={f.description} maxLength={1000} onChange={e => set('description', e.target.value)} /></div>
          <div><label style={lbl}>Trigger</label>
            <select style={inp} disabled={locked} value={f.triggerType} onChange={e => set('triggerType', e.target.value)}>{Object.entries(TRIGGERS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
          {f.triggerType === 'SEGMENT_ENTRY' ? (
            <div><label style={lbl}>Segment *</label><select style={inp} disabled={locked} value={f.segmentId} onChange={e => set('segmentId', e.target.value)}>
              <option value="">Select…</option>{segments.map((s: any) => <option key={s.id} value={s.id}>{s.name}{s.lastCount != null ? ` (~${s.lastCount})` : ''}</option>)}</select></div>
          ) : <div style={{ fontSize: 11, color: '#94A3B8', alignSelf: 'end', paddingBottom: 9 }}>{f.triggerType === 'OPTED_IN' ? 'Only contacts who opt in after activation are enrolled.' : 'Enrols contacts on the yearly anniversary of their opt-in.'}</div>}
          <div><label style={lbl}>Allow re-entry after (days)</label><input style={inp} type="number" min={1} disabled={locked} value={f.reentryDays} placeholder="never" onChange={e => set('reentryDays', e.target.value)} /></div>
          <div><label style={lbl}>Min. hours between journey emails</label><input style={inp} type="number" min={0} max={720} value={f.minGapHours} onChange={e => set('minGapHours', e.target.value)} /></div>
          <div><label style={lbl}>Send window start (SAST hour)</label><input style={inp} type="number" min={0} max={23} value={f.windowStartHour} onChange={e => set('windowStartHour', e.target.value)} /></div>
          <div><label style={lbl}>Send window end (SAST hour)</label><input style={inp} type="number" min={1} max={24} value={f.windowEndHour} onChange={e => set('windowEndHour', e.target.value)} /></div>
        </div>
        <label style={{ display: 'flex', gap: 8, fontSize: 13, alignItems: 'center', marginBottom: 6 }}><input type="checkbox" checked={f.skipWeekends} onChange={e => set('skipWeekends', e.target.checked)} /> Don’t send on weekends</label>

        <div style={sec}>Steps</div>
        {f.steps.map((s: any, i: number) => (
          <div key={i} style={{ border: '1px solid #E2E8F0', borderRadius: 10, padding: 12, marginBottom: 10, background: '#FAFAFA' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: s.type === 'EMAIL' ? 8 : 0 }}>
              {s.type === 'EMAIL' ? <Mail size={14} color="#1B3A6B" /> : s.type === 'WAIT' ? <Clock size={14} /> : <Flag size={14} color="#7C3AED" />}
              <span style={{ fontSize: 12, fontWeight: 700 }}>{i + 1}. {s.type === 'EMAIL' ? 'Email' : s.type === 'WAIT' ? 'Wait' : 'Exit if'}</span>
              {s.type === 'WAIT' && <><input style={{ ...inp, width: 70 }} type="number" min={1} disabled={locked} value={s.amount ?? ''} onChange={e => setStep(i, { amount: e.target.value })} />
                <select style={{ ...inp, width: 100 }} disabled={locked} value={s.unit} onChange={e => setStep(i, { unit: e.target.value })}><option value="DAYS">days</option><option value="HOURS">hours</option></select></>}
              {s.type === 'EXIT_IF' && <><span style={{ fontSize: 12 }}>the contact</span><select style={{ ...inp, width: 120 }} disabled={locked} value={s.condition} onChange={e => setStep(i, { condition: e.target.value })}><option value="CLICKED">clicked</option><option value="OPENED">opened</option></select><span style={{ fontSize: 12 }}>an earlier email</span></>}
              {s.type === 'EMAIL' && <input style={{ ...inp, flex: 1 }} placeholder="Step name" disabled={locked} value={s.name ?? ''} onChange={e => setStep(i, { name: e.target.value })} />}
              {!locked && <div style={{ marginLeft: 'auto', display: 'flex' }}>
                <button style={stepBtn} onClick={() => move(i, -1)}><ChevronUp size={15} /></button><button style={stepBtn} onClick={() => move(i, 1)}><ChevronDown size={15} /></button>
                <button style={stepBtn} disabled={f.steps.length <= 1} onClick={() => setF((p: any) => ({ ...p, steps: p.steps.filter((_: any, k: number) => k !== i) }))}><Trash2 size={14} /></button></div>}
            </div>
            {s.type === 'EMAIL' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <input style={inp} placeholder="Subject — e.g. Welcome, {{first_name}}" disabled={locked} value={s.subject ?? ''} onChange={e => setStep(i, { subject: e.target.value })} />
                <textarea style={{ ...inp, fontFamily: 'monospace', fontSize: 12, resize: 'vertical' }} rows={5} placeholder="<p>Hi {{first_name}},</p>" disabled={locked} value={s.htmlBody ?? ''} onChange={e => setStep(i, { htmlBody: e.target.value })} />
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                  <input style={inp} placeholder="From name (optional)" disabled={locked} value={s.fromName ?? ''} onChange={e => setStep(i, { fromName: e.target.value })} />
                  <input style={inp} placeholder="Reply-to (optional)" disabled={locked} value={s.replyTo ?? ''} onChange={e => setStep(i, { replyTo: e.target.value })} />
                </div>
              </div>)}
          </div>))}
        {!locked && <div style={{ display: 'flex', gap: 8 }}>
          {[['EMAIL', 'Email', E(`Email ${f.steps.filter((s: any) => s.type === 'EMAIL').length + 1}`, '', '')], ['WAIT', 'Wait', W(2)], ['EXIT_IF', 'Exit check', X_IF('CLICKED')]].map(([k, l, v]: any) => (
            <button key={k} onClick={() => setF((p: any) => ({ ...p, steps: [...p.steps, { ...v }] }))} style={{ padding: '6px 12px', background: '#fff', border: '1px dashed #CBD5E1', borderRadius: 8, fontSize: 12, cursor: 'pointer' }}>+ {l}</button>))}
        </div>}
        {!locked && <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 8 }}>The last step must be an email. Content is checked when you activate; errors such as broken links block activation. Placeholders: {'{{first_name}} {{name}} {{email}} {{company_name}}'}. An unsubscribe footer is added automatically.</div>}

        {error && <div style={{ marginTop: 12, padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 18 }}>
          <button onClick={onClose} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: '#fff', cursor: 'pointer' }}>Cancel</button>
          <button disabled={!ok || save.isPending} onClick={() => { setError(''); save.mutate() }} style={{ padding: '9px 22px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 9, fontWeight: 700, cursor: 'pointer', opacity: ok ? 1 : 0.5 }}>{save.isPending ? 'Saving…' : 'Save journey'}</button>
        </div>
      </div>
    </div>
  )
}