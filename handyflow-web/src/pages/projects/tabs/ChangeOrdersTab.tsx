// src/pages/projects/tabs/ChangeOrdersTab.tsx
// Change orders: draft → submit → approve / reject / withdraw, reopen a rejected one for revision, then the client's approval or
// dispute. Shows what approval WOULD do (impact analysis) before anyone approves, the revision history, attachments and the PDF.
import React, { useMemo, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, FileDown, Paperclip, Trash2, AlertTriangle, CheckCircle2, History } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { usePermission } from '../../../hooks/usePermission'
import { toast } from '../../../store/toast.store'
import { errorText } from '../../../api/projectDocs'
import { Btn, Dialog, Empty, ErrorLine, Field, Pill, ReasonDialog, SectionTitle, Stat, card, day, downloadFile, fail, inp, list, money, muted, one, signedMoney, td, th, when } from './pmKit'

export interface ChangeOrder {
  id: string; projectId: string; changeNumber: string; title: string; description: string | null; reason: string | null
  status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'; revision: number
  costImpact: number; scheduleImpact: number; scopeImpact: string | null; riskImpact: string | null; procurementImpact: string | null
  approvedByName: string | null; approvedAt: string | null; rejectionReason: string | null; rejectedAt: string | null
  withdrawnReason: string | null; withdrawnAt: string | null
  clientApprovedAt: string | null; clientDisputeOpen: boolean; clientDisputedAt: string | null; clientDisputeReason: string | null
  clientDisputeResolvedAt: string | null; clientDisputeResolution: string | null
  appliedAt: string | null; baselineVersion: number | null; createdAt: string
}
interface Detail {
  changeOrder: ChangeOrder
  costLines: { id: string; budgetLineId: string | null; category: string | null; description: string | null; amount: number }[]
  affectedTasks: { taskId: string; taskNumber: string; title: string; delayDays: number }[]
  revisions: { revision: number; title: string; costImpact: number; scheduleImpact: number; submittedByName: string | null; submittedAt: string | null
    outcome: string | null; outcomeReason: string | null; decidedAt: string | null }[]
  linkedRfiIds: string[]
}
interface Impact {
  changeOrderId: string; changeNumber: string; warnings: string[]
  cost: { declaredCostImpact: number; revisedBudgetBefore: number; revisedBudgetAfter: number; contingencyRemaining: number; exceedsContingency: boolean; problems: string[] }
  schedule: { declaredScheduleImpactDays: number; forecastFinishBefore: string | null; forecastFinishAfter: string | null; computedFinishDeltaDays: number | null
    affectedTaskCount: number; newlyCriticalTaskIds: string[]; cycleDetected: boolean }
}
interface Attachment { id: string; fileName: string; contentType: string; fileSizeBytes: number; uploadedByName: string | null; createdAt: string }
interface BaselineRevision { version: number; changeOrderId: string | null; summary: string; detailJson: string | null; appliedByName: string | null; appliedAt: string }
export interface BudgetLine { id: string; category: string; description: string; budgetedAmount: number; committedAmount: number; actualAmount: number; forecastToComplete: number | null; originalAmount: number | null; variance: number }
export interface TaskLite { id: string; taskNumber: string; title: string; status: string; plannedStart: string | null; plannedEnd: string | null }

const STATUS_TONE = { DRAFT: 'neutral', SUBMITTED: 'info', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'neutral' } as const
const FILTERS = ['ALL', 'DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'WITHDRAWN'] as const

function clientState(c: ChangeOrder) {
  if (c.status !== 'APPROVED') return null
  if (c.clientDisputeOpen) return <Pill tone="danger">Client disputes</Pill>
  if (c.clientApprovedAt) return <Pill tone="success">Client approved</Pill>
  return <Pill tone="warning">Awaiting client</Pill>
}

export function ChangeOrdersTab({ projectId }: { projectId: string }) {
  const qc = useQueryClient()
  const canWrite = usePermission('PM_WRITE')
  const [filter, setFilter] = useState<(typeof FILTERS)[number]>('ALL')
  const [openId, setOpenId] = useState<string | null>(null)
  const [editing, setEditing] = useState<{ detail: Detail | null } | null>(null)
  const [showBaseline, setShowBaseline] = useState(false)

  const { data: orders = [], isLoading, isError, error } = useQuery<ChangeOrder[]>({
    queryKey: ['pm-change-orders', projectId],
    queryFn: async () => list<ChangeOrder>(await apiClient.get(`/api/v1/projects/${projectId}/change-orders`)),
  })
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['pm-change-orders', projectId] })
    qc.invalidateQueries({ queryKey: ['pm-change-order'] })
    qc.invalidateQueries({ queryKey: ['pm-co-impact'] })
    qc.invalidateQueries({ queryKey: ['pm-project', projectId] })
    qc.invalidateQueries({ queryKey: ['pm-budget', projectId] })
    qc.invalidateQueries({ queryKey: ['pm-cost-summary', projectId] })
    qc.invalidateQueries({ queryKey: ['pm-baseline-history', projectId] })
  }

  const shown = filter === 'ALL' ? orders : orders.filter(o => o.status === filter)
  const totals = useMemo(() => {
    const approved = orders.filter(o => o.status === 'APPROVED')
    const pending = orders.filter(o => o.status === 'SUBMITTED' || o.status === 'DRAFT')
    return {
      approvedValue: approved.reduce((s, o) => s + Number(o.costImpact ?? 0), 0),
      approvedDays: approved.reduce((s, o) => s + (o.scheduleImpact ?? 0), 0),
      pendingValue: pending.reduce((s, o) => s + Number(o.costImpact ?? 0), 0),
      pendingCount: pending.length,
      awaitingClient: approved.filter(o => !o.clientApprovedAt).length,
    }
  }, [orders])

  if (isError) return <ErrorLine text={(error as any)?.response?.status === 403 ? 'You do not have access to this project’s change orders.' : 'Could not load change orders.'} />

  return (
    <div>
      <div style={{ ...card, display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 14, marginBottom: 16 }}>
        <Stat label="Approved value" value={signedMoney(totals.approvedValue)} />
        <Stat label="Approved time" value={`${totals.approvedDays > 0 ? '+' : ''}${totals.approvedDays} days`} />
        <Stat label="Pending" value={signedMoney(totals.pendingValue)} sub={`${totals.pendingCount} draft or submitted`} tone={totals.pendingCount ? 'warn' : undefined} />
        <Stat label="Awaiting client" value={totals.awaitingClient} tone={totals.awaitingClient ? 'warn' : undefined} />
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 10 }}>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {FILTERS.map(f => (
            <button key={f} onClick={() => setFilter(f)}
              style={{ padding: '4px 10px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer',
                border: '1px solid ' + (filter === f ? 'var(--hf-primary)' : 'var(--hf-border)'),
                background: filter === f ? 'var(--hf-primary-soft, var(--hf-info-soft))' : 'var(--hf-surface)',
                color: filter === f ? 'var(--hf-primary-text)' : 'var(--hf-text-muted)' }}>
              {f === 'ALL' ? `All (${orders.length})` : `${f[0]}${f.slice(1).toLowerCase()} (${orders.filter(o => o.status === f).length})`}
            </button>
          ))}
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Btn onClick={() => setShowBaseline(true)}><History size={13} /> Baseline history</Btn>
          {canWrite && <Btn kind="primary" onClick={() => setEditing({ detail: null })}><Plus size={14} /> New change order</Btn>}
        </div>
      </div>

      <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
        {isLoading ? <Empty>Loading…</Empty> : shown.length === 0 ? <Empty>{orders.length ? 'No change orders with this status.' : 'No change orders yet.'}</Empty> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr><th style={th}>Number</th><th style={th}>Title</th><th style={th}>Status</th><th style={th}>Client</th>
              <th style={{ ...th, textAlign: 'right' }}>Cost</th><th style={{ ...th, textAlign: 'right' }}>Time</th><th style={th}>Raised</th></tr></thead>
            <tbody>
              {shown.map(c => (
                <tr key={c.id} onClick={() => setOpenId(c.id)} style={{ cursor: 'pointer' }}>
                  <td style={{ ...td, fontWeight: 600, whiteSpace: 'nowrap' }}>{c.changeNumber}{c.revision > 1 && <span style={muted}> · rev {c.revision}</span>}</td>
                  <td style={td}>{c.title}</td>
                  <td style={td}><Pill tone={STATUS_TONE[c.status]}>{c.status}</Pill></td>
                  <td style={td}>{clientState(c) ?? <span style={muted}>—</span>}</td>
                  <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap', color: c.costImpact < 0 ? 'var(--hf-success-text)' : undefined }}>{signedMoney(c.costImpact)}</td>
                  <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>{c.scheduleImpact ? `${c.scheduleImpact > 0 ? '+' : ''}${c.scheduleImpact} d` : '—'}</td>
                  <td style={{ ...td, whiteSpace: 'nowrap' }}>{day(c.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {openId && <ChangeOrderDetail id={openId} projectId={projectId} onClose={() => setOpenId(null)} onChanged={refresh}
        onEdit={d => { setOpenId(null); setEditing({ detail: d }) }} />}
      {editing && <ChangeOrderEditor projectId={projectId} detail={editing.detail} onClose={() => setEditing(null)}
        onSaved={c => { refresh(); setEditing(null); setOpenId(c.id) }} />}
      {showBaseline && <BaselineHistory projectId={projectId} orders={orders} onClose={() => setShowBaseline(false)} />}
    </div>
  )
}

// ── Detail ───────────────────────────────────────────────────────────────────

function ChangeOrderDetail({ id, projectId, onClose, onChanged, onEdit }:
  { id: string; projectId: string; onClose: () => void; onChanged: () => void; onEdit: (d: Detail) => void }) {
  const canWrite = usePermission('PM_WRITE')
  const canApprove = usePermission('PM_APPROVE')
  const [ask, setAsk] = useState<null | 'reject' | 'withdraw' | 'dispute' | 'resolve' | 'approve'>(null)
  const [busy, setBusy] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  const { data: d, isLoading, refetch } = useQuery<Detail>({
    queryKey: ['pm-change-order', id],
    queryFn: async () => one<Detail>(await apiClient.get(`/api/v1/projects/change-orders/${id}`)),
  })
  const c = d?.changeOrder
  const live = c && (c.status === 'DRAFT' || c.status === 'SUBMITTED')
  const { data: impact, isFetching: impactLoading } = useQuery<Impact>({
    queryKey: ['pm-co-impact', id],
    queryFn: async () => one<Impact>(await apiClient.get(`/api/v1/projects/change-orders/${id}/impact-analysis`)),
    enabled: !!live,
  })
  const { data: files = [], refetch: refetchFiles } = useQuery<Attachment[]>({
    queryKey: ['pm-co-files', id],
    queryFn: async () => list<Attachment>(await apiClient.get(`/api/v1/projects/change-orders/${id}/attachments`)),
  })

  const act = async (path: string, body?: unknown, done?: string) => {
    setBusy(true)
    try {
      const r = await apiClient.post(`/api/v1/projects/change-orders/${id}/${path}`, body ?? {})
      toast.success(done ?? r?.data?.message ?? 'Done'); onChanged(); await refetch()
    } finally { setBusy(false) }
  }
  const safely = (fn: () => Promise<unknown>, fallback: string) => fn().catch(e => fail(e, fallback))

  const upload = async (f: File) => {
    const fd = new FormData(); fd.append('file', f, f.name)
    try { await apiClient.post(`/api/v1/projects/change-orders/${id}/attachments`, fd); toast.success('Attachment uploaded'); refetchFiles() }
    catch (e) { await fail(e, 'Upload failed') }
  }

  return (
    <Dialog width={900} onClose={onClose}
      title={c ? <span>{c.changeNumber} · {c.title} <span style={{ marginLeft: 6 }}><Pill tone={STATUS_TONE[c.status]}>{c.status}</Pill></span></span> : 'Change order'}
      footer={c && <>
        <Btn onClick={() => downloadFile(`/api/v1/projects/${projectId}/change-orders/${id}/export`, `${c.changeNumber}.pdf`)}><FileDown size={13} /> PDF</Btn>
        <span style={{ flex: 1 }} />
        {c.status === 'DRAFT' && canWrite && <>
          <Btn kind="ghost" onClick={() => setAsk('withdraw')}>Withdraw</Btn>
          <Btn onClick={() => onEdit(d!)}>Edit</Btn>
          <Btn kind="primary" disabled={busy} onClick={() => safely(() => act('submit', undefined, 'Submitted for approval'), 'Could not submit')}>Submit for approval</Btn>
        </>}
        {c.status === 'SUBMITTED' && <>
          {canWrite && <Btn kind="ghost" onClick={() => setAsk('withdraw')}>Withdraw</Btn>}
          {canApprove && <Btn kind="danger" onClick={() => setAsk('reject')}>Reject</Btn>}
          {canApprove && <Btn kind="primary" onClick={() => setAsk('approve')}>Approve…</Btn>}
        </>}
        {c.status === 'REJECTED' && canWrite && <Btn kind="primary" disabled={busy} onClick={() => safely(() => act('reopen', undefined, 'Reopened as a new revision'), 'Could not reopen')}>Revise and resubmit</Btn>}
        {c.status === 'APPROVED' && canApprove && !c.clientApprovedAt && !c.clientDisputeOpen && <>
          <Btn kind="danger" onClick={() => setAsk('dispute')}>Client disputes</Btn>
          <Btn kind="primary" disabled={busy} onClick={() => safely(() => act('client-approve', undefined, 'Client approval recorded'), 'Could not record client approval')}>Client approved</Btn>
        </>}
        {c.status === 'APPROVED' && canApprove && c.clientDisputeOpen && <Btn kind="primary" onClick={() => setAsk('resolve')}>Resolve dispute</Btn>}
      </>}>
      {isLoading || !c || !d ? <Empty>Loading…</Empty> : (
        <div style={{ display: 'grid', gap: 16 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 14 }}>
            <Stat label="Cost impact" value={signedMoney(c.costImpact)} tone={c.costImpact > 0 ? 'warn' : c.costImpact < 0 ? 'good' : undefined} />
            <Stat label="Time impact" value={`${c.scheduleImpact > 0 ? '+' : ''}${c.scheduleImpact} calendar days`} />
            <Stat label="Revision" value={c.revision} />
            <Stat label="Client" value={clientState(c) ?? '—'} />
          </div>

          <StatusNotes c={c} />

          {(c.description || c.reason) && <div>
            {c.description && <p style={{ margin: '0 0 6px', fontSize: 13, whiteSpace: 'pre-wrap' }}>{c.description}</p>}
            {c.reason && <p style={{ ...muted, margin: 0 }}><b>Why:</b> {c.reason}</p>}
          </div>}

          {(c.scopeImpact || c.riskImpact || c.procurementImpact) && (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(220px,1fr))', gap: 10 }}>
              {[['Scope', c.scopeImpact], ['Risk', c.riskImpact], ['Procurement', c.procurementImpact]].filter(x => x[1]).map(([k, v]) => (
                <div key={k} style={{ ...card, padding: 12 }}><div style={{ ...muted, fontWeight: 700, marginBottom: 4 }}>{k}</div><div style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>{v}</div></div>
              ))}
            </div>
          )}

          {live && <ImpactPanel impact={impact} loading={impactLoading} />}

          <div>
            <SectionTitle>Budget lines affected</SectionTitle>
            {d.costLines.length === 0 ? <div style={muted}>No cost allocation — the whole impact goes to contingency.</div> : (
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead><tr><th style={th}>Line</th><th style={th}>Description</th><th style={{ ...th, textAlign: 'right' }}>Amount</th></tr></thead>
                <tbody>{d.costLines.map(l => (
                  <tr key={l.id}><td style={td}>{l.budgetLineId ? (l.category ?? 'Existing line') : <><Pill tone="violet">New</Pill> {l.category}</>}</td>
                    <td style={td}>{l.description ?? '—'}</td><td style={{ ...td, textAlign: 'right' }}>{signedMoney(l.amount)}</td></tr>
                ))}</tbody>
              </table>
            )}
          </div>

          {d.affectedTasks.length > 0 && <div>
            <SectionTitle>Tasks that slip</SectionTitle>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead><tr><th style={th}>Task</th><th style={th}>Title</th><th style={{ ...th, textAlign: 'right' }}>Delay</th></tr></thead>
              <tbody>{d.affectedTasks.map(t => (
                <tr key={t.taskId}><td style={td}>{t.taskNumber}</td><td style={td}>{t.title}</td><td style={{ ...td, textAlign: 'right' }}>{t.delayDays} working days</td></tr>
              ))}</tbody>
            </table>
            <div style={{ ...muted, marginTop: 4 }}>Their dependent tasks are pushed out when the change order is approved.</div>
          </div>}

          <div>
            <SectionTitle right={canWrite && c.status !== 'WITHDRAWN' && <>
              <input ref={fileRef} type="file" hidden onChange={e => { const f = e.target.files?.[0]; if (f) upload(f); e.target.value = '' }} />
              <Btn small onClick={() => fileRef.current?.click()}><Paperclip size={12} /> Attach</Btn></>}>Attachments</SectionTitle>
            {files.length === 0 ? <div style={muted}>No quotations, drawings or instructions attached.</div> : (
              <div style={{ display: 'grid', gap: 6 }}>{files.map(f => (
                <button key={f.id} onClick={() => downloadFile(`/api/v1/projects/change-orders/${id}/attachments/${f.id}/download`, f.fileName)}
                  style={{ display: 'flex', alignItems: 'center', gap: 8, textAlign: 'left', background: 'var(--hf-surface-muted)', border: '1px solid var(--hf-border)', borderRadius: 8, padding: '7px 10px', cursor: 'pointer', color: 'var(--hf-text)', fontSize: 13 }}>
                  <Paperclip size={13} /> <span style={{ flex: 1 }}>{f.fileName}</span>
                  <span style={muted}>{(f.fileSizeBytes / 1024).toFixed(0)} KB · {f.uploadedByName ?? ''} · {day(f.createdAt)}</span>
                </button>
              ))}</div>
            )}
          </div>

          {d.revisions.length > 0 && <div>
            <SectionTitle>Submission history</SectionTitle>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead><tr><th style={th}>Rev</th><th style={th}>Submitted</th><th style={{ ...th, textAlign: 'right' }}>Cost</th><th style={{ ...th, textAlign: 'right' }}>Days</th><th style={th}>Outcome</th></tr></thead>
              <tbody>{d.revisions.map(r => (
                <tr key={r.revision}>
                  <td style={td}>{r.revision}</td>
                  <td style={td}>{when(r.submittedAt)}<div style={muted}>{r.submittedByName}</div></td>
                  <td style={{ ...td, textAlign: 'right' }}>{signedMoney(r.costImpact)}</td>
                  <td style={{ ...td, textAlign: 'right' }}>{r.scheduleImpact}</td>
                  <td style={td}>{r.outcome ? <><Pill tone={r.outcome === 'APPROVED' ? 'success' : r.outcome === 'REJECTED' ? 'danger' : 'neutral'}>{r.outcome}</Pill>
                    <div style={muted}>{when(r.decidedAt)}{r.outcomeReason ? ` — ${r.outcomeReason}` : ''}</div></> : <span style={muted}>Pending</span>}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>}
          {d.linkedRfiIds.length > 0 && <div style={muted}>Raised from {d.linkedRfiIds.length} RFI{d.linkedRfiIds.length === 1 ? '' : 's'} (see the RFI tab).</div>}
        </div>
      )}

      {ask === 'approve' && c && <ApproveDialog c={c} impact={impact} onClose={() => setAsk(null)} onApprove={() => act('approve', undefined, 'Approved — budget and schedule updated')} />}
      {ask === 'reject' && <ReasonDialog title="Reject change order" confirm="Reject" danger intro="The person who raised it can revise and resubmit it." onClose={() => setAsk(null)}
        onSubmit={reason => act('reject', { reason }, 'Rejected')} />}
      {ask === 'withdraw' && <ReasonDialog title="Withdraw change order" confirm="Withdraw" danger intro="A withdrawn change order is closed for good." onClose={() => setAsk(null)}
        onSubmit={reason => act('withdraw', { reason }, 'Withdrawn')} />}
      {ask === 'dispute' && <ReasonDialog title="Record a client dispute" label="What the client disputes" confirm="Record dispute" danger onClose={() => setAsk(null)}
        intro="The internal approval stands; the dispute stays open until you record how it was resolved." onSubmit={reason => act('client-dispute', { reason }, 'Dispute recorded')} />}
      {ask === 'resolve' && <ReasonDialog title="Resolve the client dispute" label="How it was resolved" confirm="Resolve" onClose={() => setAsk(null)}
        intro="Once resolved you can record the client’s approval." onSubmit={reason => act('client-dispute/resolve', { reason }, 'Dispute resolved')} />}
    </Dialog>
  )
}

function StatusNotes({ c }: { c: ChangeOrder }) {
  const rows: [string, string | null, string | null][] = [
    ['Approved', c.approvedAt ? `${when(c.approvedAt)} by ${c.approvedByName ?? '—'}` : null, c.baselineVersion ? `baseline v${c.baselineVersion}` : null],
    ['Rejected', c.rejectedAt ? when(c.rejectedAt) : null, c.rejectionReason],
    ['Withdrawn', c.withdrawnAt ? when(c.withdrawnAt) : null, c.withdrawnReason],
    ['Client approved', c.clientApprovedAt ? when(c.clientApprovedAt) : null, null],
    ['Client disputed', c.clientDisputedAt ? when(c.clientDisputedAt) : null, c.clientDisputeReason],
    ['Dispute resolved', c.clientDisputeResolvedAt ? when(c.clientDisputeResolvedAt) : null, c.clientDisputeResolution],
  ]
  const shown = rows.filter(r => r[1])
  if (!shown.length) return null
  return (
    <div style={{ display: 'grid', gap: 4, fontSize: 12 }}>
      {shown.map(([k, v, note]) => <div key={k}><b>{k}</b> {v}{note && <span style={{ color: 'var(--hf-text-muted)' }}> — {note}</span>}</div>)}
    </div>
  )
}

function ImpactPanel({ impact, loading }: { impact?: Impact; loading: boolean }) {
  if (loading && !impact) return <div style={muted}>Working out the impact…</div>
  if (!impact) return null
  const { cost, schedule } = impact
  const problems = [...cost.problems]
  return (
    <div style={{ ...card, background: 'var(--hf-surface-muted)' }}>
      <SectionTitle>If this is approved</SectionTitle>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 14, marginBottom: 10 }}>
        <Stat label="Revised budget" value={money(cost.revisedBudgetAfter)} sub={`now ${money(cost.revisedBudgetBefore)}`} />
        <Stat label="Contingency left" value={money(cost.contingencyRemaining)} tone={cost.exceedsContingency ? 'bad' : undefined} sub={cost.exceedsContingency ? 'impact exceeds contingency' : undefined} />
        <Stat label="Forecast finish" value={day(schedule.forecastFinishAfter)} sub={`now ${day(schedule.forecastFinishBefore)}`} />
        <Stat label="Finish moves" value={schedule.computedFinishDeltaDays == null ? '—' : `${schedule.computedFinishDeltaDays > 0 ? '+' : ''}${schedule.computedFinishDeltaDays} days`}
          sub={`declared ${schedule.declaredScheduleImpactDays} days`} tone={schedule.computedFinishDeltaDays && schedule.computedFinishDeltaDays > 0 ? 'warn' : undefined} />
      </div>
      {schedule.newlyCriticalTaskIds.length > 0 && <div style={{ fontSize: 12, marginBottom: 6 }}>{schedule.newlyCriticalTaskIds.length} task(s) would become critical.</div>}
      {schedule.cycleDetected && <div style={{ fontSize: 12, color: 'var(--hf-danger-text)', marginBottom: 6 }}>The task dependencies contain a loop, so the schedule effect cannot be worked out.</div>}
      {problems.map((p, i) => <div key={'p' + i} style={{ display: 'flex', gap: 6, fontSize: 12, color: 'var(--hf-danger-text)', marginBottom: 4 }}><AlertTriangle size={13} /> {p}</div>)}
      {impact.warnings.map((w, i) => <div key={'w' + i} style={{ display: 'flex', gap: 6, fontSize: 12, color: 'var(--hf-warning-text-deep)', marginBottom: 4 }}><AlertTriangle size={13} /> {w}</div>)}
      {!problems.length && !impact.warnings.length && <div style={{ display: 'flex', gap: 6, fontSize: 12, color: 'var(--hf-success-text)' }}><CheckCircle2 size={13} /> Nothing stops approval.</div>}
    </div>
  )
}

function ApproveDialog({ c, impact, onApprove, onClose }: { c: ChangeOrder; impact?: Impact; onApprove: () => Promise<unknown>; onClose: () => void }) {
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const blocked = (impact?.cost.problems.length ?? 0) > 0
  return (
    <Dialog title={`Approve ${c.changeNumber}`} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy || blocked}
        onClick={async () => { setBusy(true); setErr(''); try { await onApprove(); onClose() } catch (e) { setErr(await errorText(e, 'Could not approve')) } finally { setBusy(false) } }}>
        {busy ? 'Approving…' : 'Approve and apply'}</Btn></>}>
      <p style={{ fontSize: 13, marginTop: 0, lineHeight: 1.5 }}>
        Approving changes the budget by <b>{signedMoney(c.costImpact)}</b>{c.scheduleImpact ? <> and moves the project end date by <b>{c.scheduleImpact} days</b></> : null},
        and records a new baseline revision. This cannot be undone — a correction needs another change order.
      </p>
      {impact && <ImpactPanel impact={impact} loading={false} />}
      {blocked && <ErrorLine text="Fix the problems above (edit the budget lines or revise the change order) before approving." />}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

// ── Editor ───────────────────────────────────────────────────────────────────

type CostRow = { key: number; budgetLineId: string; category: string; description: string; amount: string }
type TaskRow = { key: number; taskId: string; delayDays: string }
const NEW_LINE = '__new'
const CATS = ['LABOUR', 'MATERIALS', 'SUBCONTRACT', 'EQUIPMENT', 'OVERHEAD', 'CONTINGENCY']
let rowKey = 1

function ChangeOrderEditor({ projectId, detail, onClose, onSaved }: { projectId: string; detail: Detail | null; onClose: () => void; onSaved: (c: ChangeOrder) => void }) {
  const c = detail?.changeOrder
  const [f, setF] = useState({
    title: c?.title ?? '', description: c?.description ?? '', reason: c?.reason ?? '',
    scheduleImpact: String(c?.scheduleImpact ?? 0), costImpact: detail && !detail.costLines.length ? String(c?.costImpact ?? '') : '',
    scopeImpact: c?.scopeImpact ?? '', riskImpact: c?.riskImpact ?? '', procurementImpact: c?.procurementImpact ?? '',
  })
  const [costs, setCosts] = useState<CostRow[]>(() => (detail?.costLines ?? []).map(l => ({ key: rowKey++, budgetLineId: l.budgetLineId ?? NEW_LINE, category: l.category ?? 'MATERIALS', description: l.description ?? '', amount: String(l.amount) })))
  const [tasks, setTasks] = useState<TaskRow[]>(() => (detail?.affectedTasks ?? []).map(t => ({ key: rowKey++, taskId: t.taskId, delayDays: String(t.delayDays) })))
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const set = (k: keyof typeof f, v: string) => setF(p => ({ ...p, [k]: v }))

  const { data: lines = [] } = useQuery<BudgetLine[]>({ queryKey: ['pm-budget', projectId], queryFn: async () => list<BudgetLine>(await apiClient.get(`/api/v1/projects/${projectId}/budget`)) })
  const { data: allTasks = [] } = useQuery<TaskLite[]>({ queryKey: ['pm-tasks', projectId], queryFn: async () => list<TaskLite>(await apiClient.get(`/api/v1/projects/${projectId}/tasks`)) })
  const lineTotal = costs.reduce((s, r) => s + (Number(r.amount) || 0), 0)

  const save = async () => {
    if (!f.title.trim()) { setErr('A title is required'); return }
    const body = {
      title: f.title.trim(), description: f.description.trim() || null, reason: f.reason.trim() || null,
      scheduleImpact: Number(f.scheduleImpact) || 0,
      costImpact: costs.length ? (f.costImpact.trim() ? Number(f.costImpact) : null) : (Number(f.costImpact) || 0),
      scopeImpact: f.scopeImpact.trim() || null, riskImpact: f.riskImpact.trim() || null, procurementImpact: f.procurementImpact.trim() || null,
      costLines: costs.map(r => ({ budgetLineId: r.budgetLineId === NEW_LINE ? null : r.budgetLineId, category: r.budgetLineId === NEW_LINE ? r.category : null,
        description: r.description.trim() || null, amount: Number(r.amount) })),
      affectedTasks: tasks.filter(t => t.taskId).map(t => ({ taskId: t.taskId, delayDays: Number(t.delayDays) || 0 })),
    }
    setBusy(true); setErr('')
    try {
      const r = c ? await apiClient.put(`/api/v1/projects/change-orders/${c.id}`, body) : await apiClient.post(`/api/v1/projects/${projectId}/change-orders`, body)
      toast.success(c ? 'Change order saved' : 'Change order created'); onSaved(one<ChangeOrder>(r))
    } catch (e) { setErr(await errorText(e, 'Could not save the change order')) } finally { setBusy(false) }
  }

  const grid2: React.CSSProperties = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(200px,1fr))', gap: 12 }
  return (
    <Dialog width={820} title={c ? `Edit ${c.changeNumber}` : 'New change order'} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save draft'}</Btn></>}>
      <Field label="Title"><input autoFocus maxLength={200} value={f.title} onChange={e => set('title', e.target.value)} style={inp} /></Field>
      <div style={grid2}>
        <Field label="What changes"><textarea rows={3} maxLength={2000} value={f.description} onChange={e => set('description', e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
        <Field label="Why"><textarea rows={3} maxLength={1000} value={f.reason} onChange={e => set('reason', e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
      </div>

      <SectionTitle right={<Btn small onClick={() => setCosts(p => [...p, { key: rowKey++, budgetLineId: lines[0]?.id ?? NEW_LINE, category: 'MATERIALS', description: '', amount: '' }])}><Plus size={12} /> Budget line</Btn>}>Cost</SectionTitle>
      {costs.length === 0 ? (
        <Field label="Cost impact (R)" hint="Positive adds cost, negative is a saving. A saving must say which budget lines it reduces — add them above.">
          <input type="number" step="0.01" value={f.costImpact} onChange={e => set('costImpact', e.target.value)} style={{ ...inp, maxWidth: 220 }} />
        </Field>
      ) : (
        <div style={{ marginBottom: 12 }}>
          {costs.map(r => (
            <div key={r.key} style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr 1.4fr 120px 30px', gap: 6, marginBottom: 6, alignItems: 'center' }}>
              <select value={r.budgetLineId} onChange={e => setCosts(p => p.map(x => x.key === r.key ? { ...x, budgetLineId: e.target.value } : x))} style={inp} aria-label="Budget line">
                {lines.map(l => <option key={l.id} value={l.id}>{l.category} — {l.description}</option>)}
                <option value={NEW_LINE}>+ New budget line</option>
              </select>
              {r.budgetLineId === NEW_LINE
                ? <select value={r.category} onChange={e => setCosts(p => p.map(x => x.key === r.key ? { ...x, category: e.target.value } : x))} style={inp} aria-label="Category">{CATS.map(k => <option key={k}>{k}</option>)}</select>
                : <span style={muted}>{money(lines.find(l => l.id === r.budgetLineId)?.budgetedAmount)} budget</span>}
              <input placeholder="Description" maxLength={300} value={r.description} onChange={e => setCosts(p => p.map(x => x.key === r.key ? { ...x, description: e.target.value } : x))} style={inp} />
              <input type="number" step="0.01" placeholder="Amount" value={r.amount} onChange={e => setCosts(p => p.map(x => x.key === r.key ? { ...x, amount: e.target.value } : x))} style={inp} aria-label="Amount" />
              <button aria-label="Remove" onClick={() => setCosts(p => p.filter(x => x.key !== r.key))} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)' }}><Trash2 size={14} /></button>
            </div>
          ))}
          <div style={{ ...muted, textAlign: 'right' }}>Total of lines: <b>{signedMoney(lineTotal)}</b> — this is the cost impact.</div>
        </div>
      )}

      <SectionTitle right={<Btn small onClick={() => setTasks(p => [...p, { key: rowKey++, taskId: '', delayDays: '1' }])}><Plus size={12} /> Task</Btn>}>Time</SectionTitle>
      <Field label="Project end date moves by (calendar days)" hint="Positive is a delay. Moves the project end date and the baseline end on approval.">
        <input type="number" min={-999} max={999} value={f.scheduleImpact} onChange={e => set('scheduleImpact', e.target.value)} style={{ ...inp, maxWidth: 160 }} />
      </Field>
      {tasks.map(t => (
        <div key={t.key} style={{ display: 'grid', gridTemplateColumns: '1fr 150px 30px', gap: 6, marginBottom: 6, alignItems: 'center' }}>
          <select value={t.taskId} onChange={e => setTasks(p => p.map(x => x.key === t.key ? { ...x, taskId: e.target.value } : x))} style={inp} aria-label="Task">
            <option value="">Choose a task…</option>
            {allTasks.filter(x => x.status !== 'COMPLETED' && x.status !== 'CANCELLED').map(x => <option key={x.id} value={x.id}>{x.taskNumber} — {x.title}</option>)}
          </select>
          <input type="number" min={0} max={365} value={t.delayDays} onChange={e => setTasks(p => p.map(x => x.key === t.key ? { ...x, delayDays: e.target.value } : x))} style={inp} aria-label="Delay in working days" title="Delay in working days" />
          <button aria-label="Remove" onClick={() => setTasks(p => p.filter(x => x.key !== t.key))} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)' }}><Trash2 size={14} /></button>
        </div>
      ))}
      {tasks.length > 0 && <div style={{ ...muted, marginBottom: 12 }}>Delays are in working days; dependent tasks move with them.</div>}

      <SectionTitle>Other effects</SectionTitle>
      <div style={grid2}>
        <Field label="Scope"><textarea rows={2} maxLength={2000} value={f.scopeImpact} onChange={e => set('scopeImpact', e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
        <Field label="Risk"><textarea rows={2} maxLength={2000} value={f.riskImpact} onChange={e => set('riskImpact', e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
        <Field label="Procurement"><textarea rows={2} maxLength={2000} value={f.procurementImpact} onChange={e => set('procurementImpact', e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
      </div>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

// ── Baseline history ────────────────────────────────────────────────────────

function BaselineHistory({ projectId, orders, onClose }: { projectId: string; orders: ChangeOrder[]; onClose: () => void }) {
  const { data = [], isLoading } = useQuery<BaselineRevision[]>({
    queryKey: ['pm-baseline-history', projectId],
    queryFn: async () => list<BaselineRevision>(await apiClient.get(`/api/v1/projects/${projectId}/budget/baseline-history`)),
  })
  const numberOf = (id: string | null) => orders.find(o => o.id === id)?.changeNumber
  return (
    <Dialog width={720} title="Baseline history" onClose={onClose}>
      <div style={{ ...muted, marginBottom: 10 }}>Each approved change order records a new baseline version: what moved, by whom and when.</div>
      {isLoading ? <Empty>Loading…</Empty> : data.length === 0 ? <Empty>No baseline revisions yet.</Empty> : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr><th style={th}>Version</th><th style={th}>Change</th><th style={th}>What moved</th><th style={th}>Applied</th></tr></thead>
          <tbody>{data.map(r => (
            <tr key={r.version}><td style={td}>v{r.version}</td><td style={td}>{numberOf(r.changeOrderId) ?? '—'}</td>
              <td style={td}>{r.summary}</td><td style={{ ...td, whiteSpace: 'nowrap' }}>{when(r.appliedAt)}<div style={muted}>{r.appliedByName}</div></td></tr>
          ))}</tbody>
        </table>
      )}
    </Dialog>
  )
}
