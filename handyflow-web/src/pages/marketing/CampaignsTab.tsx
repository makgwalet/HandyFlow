// src/pages/marketing/CampaignsTab.tsx
import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, Send, X, Play, Pause, XCircle, Eye, AlertTriangle, Megaphone, MousePointer, CheckCircle, Users,
  ShieldAlert, Info, Filter, Pencil, Settings, History, Mail, BadgeCheck,
} from 'lucide-react'
import CampaignEditor from './CampaignWizard'

const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
const fmtDT   = (d: any) => d ? new Date(d).toLocaleString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'
const pct     = (n: number, d: number) => d > 0 ? `${Math.round((n / d) * 100)}%` : '—'
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box', background: '#fff', outline: 'none' }
const sectionTitle: React.CSSProperties = { fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }

const STATUS: Record<string, { color: string; bg: string; border: string; label: string; dot: string }> = {
  DRAFT:     { color: '#64748B', bg: '#F8FAFC', border: '#E2E8F0', label: 'Draft',     dot: '#94A3B8' },
  SCHEDULED: { color: '#1D4ED8', bg: '#EFF6FF', border: '#BFDBFE', label: 'Scheduled', dot: '#3B82F6' },
  SENDING:   { color: '#D97706', bg: '#FFFBEB', border: '#FDE68A', label: 'Sending',   dot: '#F59E0B' },
  SENT:      { color: '#166534', bg: '#DCFCE7', border: '#86EFAC', label: 'Sent',      dot: '#22C55E' },
  PAUSED:    { color: '#9333EA', bg: '#FAF5FF', border: '#E9D5FF', label: 'Paused',    dot: '#A855F7' },
  CANCELLED: { color: '#94A3B8', bg: '#F1F5F9', border: '#E2E8F0', label: 'Cancelled', dot: '#CBD5E1' },
  FAILED:    { color: '#DC2626', bg: '#FEF2F2', border: '#FECACA', label: 'Failed',    dot: '#EF4444' },
}
const REVIEW: Record<string, { label: string; color: string; bg: string }> = {
  NOT_SUBMITTED:     { label: 'Not submitted',     color: '#64748B', bg: '#F1F5F9' },
  IN_REVIEW:         { label: 'In review',         color: '#B45309', bg: '#FEF3C7' },
  APPROVED:          { label: 'Approved',          color: '#166534', bg: '#DCFCE7' },
  CHANGES_REQUESTED: { label: 'Changes requested', color: '#B91C1C', bg: '#FEE2E2' },
}
const EDITABLE = ['DRAFT', 'SCHEDULED']
const CANCELLABLE = ['DRAFT', 'SCHEDULED', 'SENDING', 'PAUSED']

type ConfirmAction = { id: string; action: 'launch' | 'pause' | 'cancel'; title: string; label: string; confirmText: string }

const StatusBadge = ({ status }: { status: string }) => {
  const s = STATUS[status] ?? STATUS.DRAFT
  return <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: s.bg, color: s.color, border: `1px solid ${s.border}`, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
    <span style={{ width: 5, height: 5, borderRadius: '50%', background: s.dot }} />{s.label}</span>
}
const ReviewBadge = ({ c, show }: { c: any; show: boolean }) => {
  if (!EDITABLE.includes(c.status)) return null
  if (c.reviewStatus === 'NOT_SUBMITTED' && !show) return null
  const r = REVIEW[c.reviewStatus] ?? REVIEW.NOT_SUBMITTED
  return <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: r.bg, color: r.color, padding: '1px 8px', borderRadius: 20, fontSize: 10, fontWeight: 700, marginTop: 4 }}><BadgeCheck size={10} />{r.label}</span>
}
const audienceLabel = (c: any) => c.audienceType === 'SEGMENT' ? (c.segmentName ?? 'Segment') : 'All opted-in'

export default function CampaignsTab({ autoOpenNew, onAutoOpened }: { autoOpenNew?: boolean; onAutoOpened?: () => void } = {}) {
  const qc = useQueryClient()
  const [editor, setEditor] = useState<{ campaign?: any } | null>(null)
  useEffect(() => { if (autoOpenNew) { setEditor({}); onAutoOpened?.() } }, [autoOpenNew]) // eslint-disable-line react-hooks/exhaustive-deps
  const [selected, setSelected] = useState<any>(null)
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null)
  const [noteModal, setNoteModal] = useState<{ id: string; kind: 'approve' | 'request-changes' } | null>(null)
  const [note, setNote] = useState('')
  const [actionError, setActionError] = useState('')
  const [filter, setFilter] = useState<'ALL' | 'REVIEW'>('ALL')
  const [showSettings, setShowSettings] = useState(false)
  const [settingsForm, setSettingsForm] = useState({ requireApproval: false, allowSelfApproval: false })
  const [settingsError, setSettingsError] = useState('')
  const [testEmails, setTestEmails] = useState('')
  const [testMsg, setTestMsg] = useState('')
  const [viewVersion, setViewVersion] = useState<any>(null)

  const { data: campaigns = [], isLoading } = useQuery<any[]>({
    queryKey: ['marketing-campaigns'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/marketing/campaigns?size=100&sort=createdAt,desc')
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })
  const { data: settings } = useQuery<any>({
    queryKey: ['marketing-settings'],
    queryFn: async () => (await apiClient.get('/api/v1/marketing/settings')).data?.data,
  })
  const { data: versions = [] } = useQuery<any[]>({
    queryKey: ['marketing-campaign-versions', selected?.id, selected?.contentVersion],
    enabled: !!selected,
    queryFn: async () => (await apiClient.get(`/api/v1/marketing/campaigns/${selected.id}/versions`)).data?.data ?? [],
  })

  const requireApproval = !!settings?.requireApproval
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['marketing-campaigns'] })
    qc.invalidateQueries({ queryKey: ['marketing-summary'] })
    qc.invalidateQueries({ queryKey: ['marketing-segments'] })
    qc.invalidateQueries({ queryKey: ['marketing-campaign-versions'] })
  }
  const reloadSelected = (id: string) =>
    apiClient.get(`/api/v1/marketing/campaigns/${id}`).then(r => setSelected(r.data?.data ?? r.data)).catch(() => {})

  const approvalBlocked = (c: any) =>
    requireApproval && EDITABLE.includes(c.status) && !(c.reviewStatus === 'APPROVED' && c.approvedVersion === c.contentVersion)

  const doAction = useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: any }) => apiClient.post(`/api/v1/marketing/campaigns/${id}/${action}`, body ?? undefined),
    onSuccess: (r: any, vars) => {
      refresh(); setConfirmAction(null); setNoteModal(null); setNote(''); setActionError('')
      const updated = r.data?.data
      if (selected?.id === vars.id) updated ? setSelected(updated) : reloadSelected(vars.id)
    },
    onError: (e: any) => { setActionError(e.response?.data?.message || 'Action failed'); setConfirmAction(null); setNoteModal(null) },
  })

  const testSend = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/marketing/campaigns/${selected.id}/test-send`, { emails: testEmails.split(/[\s,;]+/).filter(Boolean) }),
    onSuccess: (r: any) => {
      const d = r.data?.data
      setTestMsg(`Sent to ${d.sent.length}${d.refused.length ? `; refused: ${d.refused.map((x: any) => `${x.email} (${x.reason})`).join(', ')}` : ''}. Delivery is asynchronous — check the inbox.`)
    },
    onError: (e: any) => setTestMsg(e.response?.data?.message || 'Test send failed'),
  })

  const saveSettings = useMutation({
    mutationFn: () => apiClient.put('/api/v1/marketing/settings', settingsForm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketing-settings'] }); setShowSettings(false); setSettingsError('') },
    onError: (e: any) => setSettingsError(e.response?.status === 403 ? 'Changing approval settings needs the Marketing admin permission.' : (e.response?.data?.message || 'Failed to save')),
  })

  const restore = useMutation({
    mutationFn: async (v: number) => {
      const full = (await apiClient.get(`/api/v1/marketing/campaigns/${selected.id}/versions/${v}`)).data?.data
      return apiClient.put(`/api/v1/marketing/campaigns/${selected.id}`, {
        name: selected.name, templateId: selected.templateId, subject: full.subject, htmlBody: full.htmlBody,
        audienceType: full.audienceType, segmentId: full.segmentId, fromName: full.fromName, replyTo: full.replyTo,
        scheduledAt: selected.scheduledAt, changeNote: `Restored version ${v}`,
      })
    },
    onSuccess: (r: any) => { refresh(); setSelected(r.data?.data) },
    onError: (e: any) => setActionError(e.response?.data?.message || 'Restore failed'),
  })

  const openDetail = async (c: any) => {
    setTestMsg(''); setTestEmails(''); setViewVersion(null)
    try { setSelected((await apiClient.get(`/api/v1/marketing/campaigns/${c.id}`)).data?.data) } catch { setSelected(c) }
  }
  const openViewVersion = async (v: number) => {
    const full = (await apiClient.get(`/api/v1/marketing/campaigns/${selected.id}/versions/${v}`)).data?.data
    setViewVersion(full)
  }

  const askLaunch = (c: any) => setConfirmAction({
    id: c.id, action: 'launch', title: c.status === 'SCHEDULED' ? 'Send now' : 'Launch campaign',
    label: (c.status === 'SCHEDULED' ? `"${c.name}" is scheduled for ${fmtDT(c.scheduledAt)}. Send it now instead? ` : `Launch "${c.name}" (version ${c.contentVersion})? `)
      + (c.audienceType === 'SEGMENT' ? `The audience "${c.segmentName}" is evaluated now. ` : '')
      + 'Emails are queued for opted-in contacts only. Suppressed addresses are excluded, and eligibility is re-checked immediately before each email is sent.',
    confirmText: c.status === 'SCHEDULED' ? 'Send now' : 'Launch campaign',
  })
  const askPause = (c: any) => setConfirmAction({ id: c.id, action: 'pause', title: 'Pause campaign', label: `Pause "${c.name}"? No further emails are sent until you resume it.`, confirmText: 'Pause campaign' })
  const askCancel = (c: any) => setConfirmAction({ id: c.id, action: 'cancel', title: 'Cancel campaign', label: `Cancel "${c.name}"? Emails still waiting to be sent are discarded. This cannot be undone.`, confirmText: 'Cancel campaign' })

  const renderActions = (c: any) => (
    <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap' }}>
      {EDITABLE.includes(c.status) && (
        <button onClick={e => { e.stopPropagation(); askLaunch(c) }} disabled={approvalBlocked(c)}
          title={approvalBlocked(c) ? 'Approval is required before this version can be sent' : undefined}
          style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 11px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: approvalBlocked(c) ? 'not-allowed' : 'pointer', opacity: approvalBlocked(c) ? 0.45 : 1 }}>
          <Send size={11} /> {c.status === 'SCHEDULED' ? 'Send now' : 'Launch'}
        </button>
      )}
      {c.status === 'SENDING' && (
        <button onClick={e => { e.stopPropagation(); askPause(c) }} style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 11px', background: '#FFFBEB', color: '#D97706', border: '1px solid #FDE68A', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}><Pause size={11} /> Pause</button>
      )}
      {c.status === 'PAUSED' && (
        <button onClick={e => { e.stopPropagation(); doAction.mutate({ id: c.id, action: 'launch' }) }} style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 11px', background: '#F0FDF4', color: '#166534', border: '1px solid #86EFAC', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}><Play size={11} /> Resume</button>
      )}
      {CANCELLABLE.includes(c.status) && (
        <button title="Cancel campaign" onClick={e => { e.stopPropagation(); askCancel(c) }} style={{ display: 'flex', alignItems: 'center', padding: '5px 8px', background: 'none', border: '1px solid #E2E8F0', borderRadius: 7, color: '#94A3B8', cursor: 'pointer' }}><XCircle size={11} /></button>
      )}
    </div>
  )

  const shown = (campaigns as any[]).filter(c => filter === 'ALL' || c.reviewStatus === 'IN_REVIEW')
  const inReviewCount = (campaigns as any[]).filter(c => c.reviewStatus === 'IN_REVIEW' && EDITABLE.includes(c.status)).length

  // Review panel in the drawer
  const reviewPanel = (c: any) => {
    if (!EDITABLE.includes(c.status)) return null
    const canApproveOwn = !requireApproval || settings?.allowSelfApproval
    return (
      <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, padding: 14, marginBottom: 18 }}>
        <div style={sectionTitle}>Review · version {c.contentVersion}</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, flexWrap: 'wrap' }}>
          <ReviewBadge c={c} show />
          {requireApproval && <span style={{ fontSize: 11, color: '#64748B' }}>Approval is required before sending.</span>}
        </div>
        {c.reviewStatus === 'IN_REVIEW' && <div style={{ fontSize: 12, color: '#64748B', marginBottom: 8 }}>Submitted by {c.submittedByMe ? 'you' : (c.submittedByName ?? 'a colleague')} · {fmtDT(c.submittedAt)}</div>}
        {c.reviewStatus === 'APPROVED' && <div style={{ fontSize: 12, color: '#166534', marginBottom: 8 }}>Approved version {c.approvedVersion} by {c.reviewedByName ?? 'an admin'}{c.reviewNote ? ` — “${c.reviewNote}”` : ''}</div>}
        {c.reviewStatus === 'CHANGES_REQUESTED' && <div style={{ fontSize: 12, color: '#991B1B', marginBottom: 8, background: '#FEF2F2', padding: '8px 10px', borderRadius: 7 }}>Changes requested by {c.reviewedByName ?? 'a reviewer'}: {c.reviewNote}</div>}
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {(c.reviewStatus === 'NOT_SUBMITTED' || c.reviewStatus === 'CHANGES_REQUESTED') && (
            <button onClick={() => doAction.mutate({ id: c.id, action: 'submit' })} style={{ padding: '7px 14px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>Submit for review</button>
          )}
          {c.reviewStatus === 'IN_REVIEW' && c.submittedByMe && (
            <button onClick={() => doAction.mutate({ id: c.id, action: 'withdraw' })} style={{ padding: '7px 14px', background: '#fff', color: '#475569', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 12, cursor: 'pointer' }}>Withdraw</button>
          )}
          {c.reviewStatus === 'IN_REVIEW' && (!c.submittedByMe || canApproveOwn) && (
            <>
              <button onClick={() => { setNote(''); setNoteModal({ id: c.id, kind: 'approve' }) }} style={{ padding: '7px 14px', background: '#166534', color: '#fff', border: 'none', borderRadius: 8, fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>Approve</button>
              <button onClick={() => { setNote(''); setNoteModal({ id: c.id, kind: 'request-changes' }) }} style={{ padding: '7px 14px', background: '#fff', color: '#B91C1C', border: '1px solid #FECACA', borderRadius: 8, fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>Request changes</button>
            </>
          )}
        </div>
        {c.reviewStatus === 'IN_REVIEW' && c.submittedByMe && !canApproveOwn && <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 8 }}>A different admin must approve this (four-eyes).</div>}
      </div>
    )
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18, flexWrap: 'wrap', gap: 10 }}>
        <div style={{ display: 'flex', gap: 6 }}>
          {[{ k: 'ALL', l: 'All campaigns' }, { k: 'REVIEW', l: `Awaiting review${inReviewCount ? ` (${inReviewCount})` : ''}` }].map(t => (
            <button key={t.k} onClick={() => setFilter(t.k as any)}
              style={{ padding: '6px 14px', borderRadius: 20, border: '1px solid #E2E8F0', fontSize: 12, fontWeight: 600, cursor: 'pointer', background: filter === t.k ? '#1B3A6B' : '#fff', color: filter === t.k ? '#fff' : '#64748B' }}>{t.l}</button>
          ))}
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => { setSettingsForm({ requireApproval: !!settings?.requireApproval, allowSelfApproval: !!settings?.allowSelfApproval }); setSettingsError(''); setShowSettings(true) }}
            style={{ display: 'flex', alignItems: 'center', gap: 6, background: '#F1F5F9', color: '#374151', border: '1px solid #E2E8F0', borderRadius: 8, padding: '9px 14px', fontSize: 13, cursor: 'pointer' }}><Settings size={14} /> Approval settings</button>
          <button onClick={() => setEditor({})} style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}><Plus size={15} /> New Campaign</button>
        </div>
      </div>

      {actionError && (
        <div style={{ marginBottom: 16, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 10 }}>
          <AlertTriangle size={15} style={{ flexShrink: 0 }} /><span style={{ flex: 1 }}>{actionError}</span>
          <button onClick={() => setActionError('')} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#DC2626', display: 'flex' }}><X size={14} /></button>
        </div>
      )}

      {isLoading ? <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading campaigns...</div>
        : shown.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
            <Megaphone size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
            <div style={{ fontWeight: 700, color: '#475569', fontSize: 15, marginBottom: 6 }}>{filter === 'REVIEW' ? 'Nothing is waiting for review' : 'No campaigns yet'}</div>
            {filter === 'ALL' && <div style={{ fontSize: 13 }}>Create your first email campaign to start reaching your audience.</div>}
          </div>
        ) : (
          <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead><tr style={{ background: '#F8FAFC', borderBottom: '1px solid #E2E8F0' }}>
                {['Campaign', 'Status', 'Audience', 'Sent / Recipients', 'Open rate (approx.)', 'Created', ''].map(h => (
                  <th key={h} style={{ padding: '10px 16px', textAlign: 'left', fontSize: 11, fontWeight: 700, color: '#64748B', letterSpacing: '0.05em', whiteSpace: 'nowrap' }}>{h}</th>))}
              </tr></thead>
              <tbody>
                {shown.map((c, i) => {
                  const openRate = c.sentCount > 0 && c.openCount > 0 ? pct(c.openCount, c.sentCount) : '—'
                  return (
                    <tr key={c.id} style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA', cursor: 'pointer' }}
                      onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#F0F9FF'}
                      onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = i % 2 === 0 ? '#fff' : '#FAFAFA'}>
                      <td style={{ padding: '12px 16px' }} onClick={() => openDetail(c)}>
                        <div style={{ fontWeight: 700, color: '#0F172A' }}>{c.name} <span style={{ fontSize: 10, color: '#94A3B8', fontWeight: 600 }}>v{c.contentVersion}</span></div>
                        {c.fromName && <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 1 }}>From: {c.fromName}</div>}
                      </td>
                      <td style={{ padding: '12px 16px' }} onClick={() => openDetail(c)}>
                        <StatusBadge status={c.status} />
                        <div><ReviewBadge c={c} show={requireApproval} /></div>
                        {c.status === 'SCHEDULED' && <div style={{ fontSize: 11, color: '#64748B', marginTop: 3 }}>{fmtDT(c.scheduledAt)}</div>}
                        {c.status === 'PAUSED' && c.pauseReason && <div style={{ fontSize: 10, color: '#B91C1C', marginTop: 3, fontWeight: 700 }}>Auto-paused</div>}
                      </td>
                      <td style={{ padding: '12px 16px', color: '#64748B' }} onClick={() => openDetail(c)}>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>{c.audienceType === 'SEGMENT' && <Filter size={11} color="#1B3A6B" />}{audienceLabel(c)}</span>
                      </td>
                      <td style={{ padding: '12px 16px' }} onClick={() => openDetail(c)}>
                        {c.recipientCount > 0 ? (
                          <div>
                            <div style={{ fontWeight: 700, color: '#0F172A' }}>{c.sentCount} / {c.recipientCount}</div>
                            {c.sentCount > 0 && <div style={{ height: 3, background: '#E2E8F0', borderRadius: 99, marginTop: 4, width: 80 }}><div style={{ height: '100%', width: `${Math.min(100, Math.round(c.sentCount / c.recipientCount * 100))}%`, background: '#0D9488', borderRadius: 99 }} /></div>}
                            {(c.suppressedCount ?? 0) > 0 && <div style={{ fontSize: 11, color: '#B91C1C', marginTop: 4, display: 'flex', alignItems: 'center', gap: 3 }}><ShieldAlert size={10} /> {c.suppressedCount} suppressed</div>}
                          </div>
                        ) : <span style={{ color: '#94A3B8' }}>—</span>}
                      </td>
                      <td style={{ padding: '12px 16px', color: openRate !== '—' ? '#166534' : '#94A3B8', fontWeight: openRate !== '—' ? 700 : 400 }} onClick={() => openDetail(c)}>{openRate}</td>
                      <td style={{ padding: '12px 16px', color: '#94A3B8', fontSize: 11 }} onClick={() => openDetail(c)}>{fmtDate(c.createdAt)}</td>
                      <td style={{ padding: '12px 16px' }}>{renderActions(c)}</td>
                    </tr>)
                })}
              </tbody>
            </table>
          </div>
        )}

      {/* Detail drawer */}
      {selected && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', justifyContent: 'flex-end', zIndex: 1000 }} onClick={() => setSelected(null)}>
          <div style={{ background: '#fff', width: 600, height: '100%', overflowY: 'auto', boxShadow: '-10px 0 40px rgba(0,0,0,0.15)', padding: 28 }} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
              <div>
                <div style={{ fontWeight: 800, fontSize: 17, color: '#0F172A', marginBottom: 6 }}>{selected.name}</div>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}><StatusBadge status={selected.status} /><span style={{ fontSize: 11, color: '#94A3B8' }}>Version {selected.contentVersion}{selected.sentVersion ? ` · sent v${selected.sentVersion}` : ''}</span></div>
              </div>
              <button onClick={() => setSelected(null)} style={{ background: '#F1F5F9', border: 'none', borderRadius: '50%', width: 30, height: 30, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#64748B' }}><X size={14} /></button>
            </div>

            <div style={{ display: 'flex', gap: 8, marginBottom: 18, flexWrap: 'wrap' }}>
              {renderActions(selected)}
              {EDITABLE.includes(selected.status) && (
                <button onClick={() => setEditor({ campaign: selected })} style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 11px', background: '#fff', color: '#475569', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}><Pencil size={11} /> Edit</button>
              )}
            </div>

            {(selected.status === 'PAUSED' || selected.status === 'FAILED') && selected.pauseReason && (
              <div style={{ marginBottom: 18, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 12, color: '#991B1B', lineHeight: 1.55, display: 'flex', gap: 8 }}>
                <AlertTriangle size={14} style={{ flexShrink: 0, marginTop: 1 }} /><div>{selected.pauseReason}</div>
              </div>
            )}
            {selected.status === 'FAILED' && !selected.pauseReason && (
              <div style={{ marginBottom: 18, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 12, color: '#991B1B' }}>This campaign failed to launch at its scheduled time.</div>
            )}

            {reviewPanel(selected)}

            {EDITABLE.includes(selected.status) && (
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, padding: 14, marginBottom: 18 }}>
                <div style={sectionTitle}>Send a test</div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <input value={testEmails} onChange={e => setTestEmails(e.target.value)} placeholder="you@company.co.za, colleague@company.co.za (max 5)" style={inp} />
                  <button disabled={!testEmails.trim() || testSend.isPending} onClick={() => { setTestMsg(''); testSend.mutate() }}
                    style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '0 14px', background: '#0D9488', color: '#fff', border: 'none', borderRadius: 8, fontSize: 13, fontWeight: 700, cursor: 'pointer', whiteSpace: 'nowrap', opacity: !testEmails.trim() ? 0.5 : 1 }}><Mail size={13} /> {testSend.isPending ? 'Sending…' : 'Send test'}</button>
                </div>
                <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 6 }}>Marked [TEST], not tracked, unsubscribe disabled, no effect on stats or consent. Sends the saved version.</div>
                {testMsg && <div style={{ fontSize: 12, color: '#374151', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, padding: '8px 12px', marginTop: 8 }}>{testMsg}</div>}
              </div>
            )}

            {(selected.recipientCount > 0 || (selected.suppressedCount ?? 0) > 0) && (
              <>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10, marginBottom: 12 }}>
                  {[
                    { label: 'Recipients', value: selected.recipientCount, icon: <Users size={14} />, color: '#1B3A6B', bg: '#EEF2FF' },
                    { label: 'Sent', value: selected.sentCount, icon: <CheckCircle size={14} />, color: '#166534', bg: '#DCFCE7' },
                    { label: 'Delivered (confirmed)', value: selected.deliveredCount ?? 0, icon: <CheckCircle size={14} />, color: '#0D9488', bg: '#F0FDF9' },
                    { label: 'Suppressed', value: selected.suppressedCount ?? 0, icon: <ShieldAlert size={14} />, color: (selected.suppressedCount ?? 0) > 0 ? '#B91C1C' : '#94A3B8', bg: (selected.suppressedCount ?? 0) > 0 ? '#FEF2F2' : '#F8FAFC' },
                    { label: 'Opens (approx.)', value: selected.openCount ?? 0, icon: <Eye size={14} />, color: '#0284C7', bg: '#E0F2FE', sub: selected.sentCount > 0 ? pct(selected.openCount ?? 0, selected.sentCount) : null },
                    { label: 'Clicks', value: selected.clickCount ?? 0, icon: <MousePointer size={14} />, color: '#7C3AED', bg: '#F5F3FF', sub: selected.sentCount > 0 ? pct(selected.clickCount ?? 0, selected.sentCount) : null },
                    { label: 'Bounced', value: selected.bouncedCount, icon: <AlertTriangle size={14} />, color: selected.bouncedCount > 0 ? '#DC2626' : '#94A3B8', bg: selected.bouncedCount > 0 ? '#FEF2F2' : '#F8FAFC' },
                    { label: 'Complained', value: selected.complainedCount ?? 0, icon: <AlertTriangle size={14} />, color: (selected.complainedCount ?? 0) > 0 ? '#DC2626' : '#94A3B8', bg: (selected.complainedCount ?? 0) > 0 ? '#FEF2F2' : '#F8FAFC' },
                    { label: 'Failed to send', value: selected.failedCount ?? 0, icon: <XCircle size={14} />, color: (selected.failedCount ?? 0) > 0 ? '#C2410C' : '#94A3B8', bg: (selected.failedCount ?? 0) > 0 ? '#FFF7ED' : '#F8FAFC' },
                    { label: 'Unsubscribed', value: selected.unsubscribedCount, icon: <XCircle size={14} />, color: '#94A3B8', bg: '#F8FAFC' },
                  ].map((m: any) => (
                    <div key={m.label} style={{ background: m.bg, borderRadius: 10, padding: '12px 14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div style={{ fontSize: 10, color: m.color, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', opacity: 0.8 }}>{m.label}</div>
                        <div style={{ fontSize: 20, fontWeight: 800, color: m.color, marginTop: 2 }}>{m.value}</div>
                        {m.sub && <div style={{ fontSize: 11, color: m.color, fontWeight: 600 }}>{m.sub} rate</div>}
                      </div>
                      <div style={{ color: m.color, opacity: 0.5 }}>{m.icon}</div>
                    </div>))}
                </div>
                <div style={{ display: 'flex', gap: 8, padding: '9px 12px', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 11, color: '#64748B', lineHeight: 1.55, marginBottom: 22 }}>
                  <Info size={13} style={{ flexShrink: 0, marginTop: 1 }} />
                  <div><strong>Sent</strong> = accepted by the mail server, not a delivery confirmation. <strong>Delivered, bounced and complained</strong> come from your provider's webhook. Open counts are approximate.</div>
                </div>
              </>
            )}

            <div style={{ marginBottom: 20 }}>
              {[
                { label: 'Subject', value: selected.subject ?? '—' },
                { label: 'Audience', value: selected.audienceType === 'SEGMENT' ? `Segment: ${selected.segmentName ?? '—'}` : 'All opted-in contacts' },
                { label: 'From', value: selected.fromName ?? '—' }, { label: 'Reply-to', value: selected.replyTo ?? '—' },
                { label: 'Scheduled', value: fmtDT(selected.scheduledAt) }, { label: 'Completed', value: fmtDT(selected.sentAt) }, { label: 'Created', value: fmtDate(selected.createdAt) },
              ].map(row => (
                <div key={row.label} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid #F1F5F9', fontSize: 13 }}>
                  <span style={{ color: '#94A3B8', fontWeight: 600 }}>{row.label}</span>
                  <span style={{ color: '#374151', fontWeight: 500, maxWidth: 380, textAlign: 'right' }}>{row.value}</span>
                </div>))}
            </div>

            {/* Version history */}
            <div style={sectionTitle}><History size={11} style={{ verticalAlign: -1 }} /> Version history</div>
            <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
              {versions.map((v: any, i: number) => (
                <div key={v.version} style={{ padding: '10px 14px', borderTop: i ? '1px solid #F1F5F9' : 'none', display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>
                      v{v.version}
                      {v.version === selected.contentVersion && <span style={{ marginLeft: 6, fontSize: 10, background: '#EEF2FF', color: '#1B3A6B', padding: '1px 6px', borderRadius: 10 }}>Current</span>}
                      {v.version === selected.approvedVersion && <span style={{ marginLeft: 6, fontSize: 10, background: '#DCFCE7', color: '#166534', padding: '1px 6px', borderRadius: 10 }}>Approved</span>}
                      {v.version === selected.sentVersion && <span style={{ marginLeft: 6, fontSize: 10, background: '#FEF3C7', color: '#B45309', padding: '1px 6px', borderRadius: 10 }}>Sent</span>}
                    </div>
                    <div style={{ fontSize: 11, color: '#94A3B8', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{v.changeNote ?? '—'} · {v.createdByName ?? 'unknown'} · {fmtDT(v.createdAt)}</div>
                  </div>
                  <button onClick={() => openViewVersion(v.version)} style={{ padding: '4px 10px', background: '#fff', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 11, cursor: 'pointer' }}>View</button>
                  {EDITABLE.includes(selected.status) && v.version !== selected.contentVersion && (
                    <button onClick={() => { if (window.confirm(`Restore version ${v.version}? This creates version ${selected.contentVersion + 1} and voids any approval.`)) restore.mutate(v.version) }}
                      style={{ padding: '4px 10px', background: '#fff', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 11, cursor: 'pointer' }}>Restore</button>)}
                </div>))}
            </div>
            {viewVersion && (
              <div style={{ marginTop: 12 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 6 }}>
                  <span><strong>v{viewVersion.version}</strong> — {viewVersion.subject} <span style={{ color: '#94A3B8' }}>(raw, placeholders not filled)</span></span>
                  <button onClick={() => setViewVersion(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={14} /></button>
                </div>
                <iframe sandbox="" srcDoc={viewVersion.htmlBody} title="Version" style={{ width: '100%', height: 300, border: '1px solid #E2E8F0', borderRadius: 8 }} />
              </div>
            )}
          </div>
        </div>
      )}

      {editor && (
        <CampaignEditor campaign={editor.campaign}
          onClose={() => setEditor(null)}
          onSaved={() => { const id = editor.campaign?.id; setEditor(null); refresh(); if (id) reloadSelected(id) }} />
      )}

      {/* Approve / request changes note */}
      {noteModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000 }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 26, width: 440 }}>
            <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 10 }}>{noteModal.kind === 'approve' ? 'Approve this version' : 'Request changes'}</div>
            <textarea value={note} onChange={e => setNote(e.target.value)} rows={4} maxLength={1000}
              placeholder={noteModal.kind === 'approve' ? 'Optional note' : 'What needs to change? (required)'} style={{ ...inp, resize: 'vertical' }} />
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 16 }}>
              <button onClick={() => setNoteModal(null)} style={{ padding: '8px 16px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', cursor: 'pointer' }}>Back</button>
              <button disabled={doAction.isPending || (noteModal.kind === 'request-changes' && note.trim().length < 5)}
                onClick={() => doAction.mutate({ id: noteModal.id, action: noteModal.kind, body: { note: note.trim() || null } })}
                style={{ padding: '8px 18px', background: noteModal.kind === 'approve' ? '#166534' : '#B91C1C', color: '#fff', border: 'none', borderRadius: 8, fontWeight: 700, cursor: 'pointer', opacity: noteModal.kind === 'request-changes' && note.trim().length < 5 ? 0.5 : 1 }}>
                {noteModal.kind === 'approve' ? 'Approve' : 'Send back'}</button>
            </div>
          </div>
        </div>
      )}

      {/* Approval settings */}
      {showSettings && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000 }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 26, width: 460 }}>
            <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 14 }}>Approval settings</div>
            <label style={{ display: 'flex', gap: 10, alignItems: 'flex-start', marginBottom: 12, fontSize: 13, cursor: 'pointer', lineHeight: 1.5 }}>
              <input type="checkbox" checked={settingsForm.requireApproval} onChange={e => setSettingsForm(p => ({ ...p, requireApproval: e.target.checked }))} style={{ marginTop: 3 }} />
              <span><strong>Require approval before sending.</strong> Every campaign version must be approved by a Marketing admin. Editing the content voids the approval. Applies to scheduled sends too.</span>
            </label>
            <label style={{ display: 'flex', gap: 10, alignItems: 'flex-start', fontSize: 13, cursor: 'pointer', lineHeight: 1.5, opacity: settingsForm.requireApproval ? 1 : 0.5 }}>
              <input type="checkbox" disabled={!settingsForm.requireApproval} checked={settingsForm.allowSelfApproval} onChange={e => setSettingsForm(p => ({ ...p, allowSelfApproval: e.target.checked }))} style={{ marginTop: 3 }} />
              <span><strong>Allow self-approval.</strong> Lets the person who submitted also approve. Off by default (four-eyes). Turn on only if you have a single admin.</span>
            </label>
            {settingsError && <div style={{ marginTop: 12, fontSize: 13, color: '#DC2626' }}>{settingsError}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 18 }}>
              <button onClick={() => setShowSettings(false)} style={{ padding: '8px 16px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', cursor: 'pointer' }}>Cancel</button>
              <button disabled={saveSettings.isPending} onClick={() => saveSettings.mutate()} style={{ padding: '8px 18px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, fontWeight: 700, cursor: 'pointer' }}>{saveSettings.isPending ? 'Saving…' : 'Save'}</button>
            </div>
          </div>
        </div>
      )}

      {confirmAction && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 440, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 22 }}>
              <div style={{ width: 40, height: 40, borderRadius: '50%', background: confirmAction.action === 'cancel' ? '#FEF2F2' : '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                {confirmAction.action === 'launch' ? <Send size={18} color="#1D4ED8" /> : confirmAction.action === 'pause' ? <Pause size={18} color="#D97706" /> : <XCircle size={18} color="#DC2626" />}
              </div>
              <div><div style={{ fontWeight: 700, fontSize: 15, color: '#0F172A', marginBottom: 6 }}>{confirmAction.title}</div><div style={{ fontSize: 13, color: '#64748B', lineHeight: 1.6 }}>{confirmAction.label}</div></div>
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => setConfirmAction(null)} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 14, cursor: 'pointer' }}>Back</button>
              <button onClick={() => doAction.mutate({ id: confirmAction.id, action: confirmAction.action })} disabled={doAction.isPending}
                style={{ padding: '9px 20px', background: confirmAction.action === 'cancel' ? '#DC2626' : confirmAction.action === 'launch' ? '#1B3A6B' : '#D97706', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: 'pointer' }}>
                {doAction.isPending ? 'Working...' : confirmAction.confirmText}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}