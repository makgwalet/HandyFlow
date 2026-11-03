// src/pages/projects/tabs/Lifecycle.tsx
// The project lifecycle: the header buttons (driven by the server's availableActions and the user's permissions), the reason and
// closure dialogs, and the Lifecycle tab with the closure checklist and the status history.
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Send, CheckCircle2, Undo2, Play, Pause, Flag, Archive, XCircle, RotateCcw, Circle, AlertTriangle } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { usePermission } from '../../../hooks/usePermission'
import { toast } from '../../../store/toast.store'
import { errorText } from '../../../api/projectDocs'
import { Btn, Dialog, Empty, ErrorLine, Field, Pill, ReasonDialog, SectionTitle, card, inp, list, muted, one, td, th, when } from './pmKit'

interface Readiness { ready: boolean; checks: { code: string; description: string; blocking: boolean; count: number; passed: boolean }[] }
interface HistoryRow { id: string; fromStatus: string | null; toStatus: string; action: string; reason: string | null; changedByName: string | null; changedAt: string }

type Action = 'PROPOSE' | 'APPROVE' | 'RETURN' | 'ACTIVATE' | 'HOLD' | 'COMPLETE' | 'REOPEN' | 'CLOSE' | 'CANCEL'
const PERM: Record<Action, string> = { PROPOSE: 'PM_WRITE', APPROVE: 'PM_APPROVE', RETURN: 'PM_APPROVE', ACTIVATE: 'PM_WRITE', HOLD: 'PM_WRITE',
  COMPLETE: 'PM_ADMIN', REOPEN: 'PM_ADMIN', CLOSE: 'PM_ADMIN', CANCEL: 'PM_ADMIN' }
const LABEL: Record<Action, string> = { PROPOSE: 'Submit for approval', APPROVE: 'Approve', RETURN: 'Return to planning', ACTIVATE: 'Start', HOLD: 'Put on hold',
  COMPLETE: 'Mark complete', REOPEN: 'Reopen', CLOSE: 'Close project', CANCEL: 'Cancel project' }
const ICON: Record<Action, typeof Send> = { PROPOSE: Send, APPROVE: CheckCircle2, RETURN: Undo2, ACTIVATE: Play, HOLD: Pause, COMPLETE: Flag, REOPEN: RotateCcw, CLOSE: Archive, CANCEL: XCircle }
const ORDER: Action[] = ['PROPOSE', 'APPROVE', 'ACTIVATE', 'HOLD', 'COMPLETE', 'CLOSE', 'RETURN', 'REOPEN', 'CANCEL']

export const PROJECT_STATUS_TONE: Record<string, 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'violet'> = {
  PLANNING: 'neutral', PROPOSED: 'violet', APPROVED: 'info', ACTIVE: 'info', ON_HOLD: 'warning', COMPLETED: 'success', CLOSED: 'neutral', CANCELLED: 'danger' }

function useInvalidateProject(projectId: string) {
  const qc = useQueryClient()
  return () => {
    for (const k of ['pm-project', 'pm-status-history', 'pm-closure-readiness', 'pm-schedule', 'pm-cost-summary']) qc.invalidateQueries({ queryKey: [k, projectId] })
    for (const k of ['pm-projects', 'pm-projects-active', 'pm-summary']) qc.invalidateQueries({ queryKey: [k] })
  }
}

async function transition(projectId: string, action: Action, body?: Record<string, string | null>) {
  const r = await apiClient.post(`/api/v1/projects/${projectId}/${action.toLowerCase()}`, body ?? {})
  toast.success(r?.data?.message ?? 'Status changed')
}

/** Header buttons. The server says which actions are valid now; the user's permissions say which they may press. */
export function LifecycleActions({ projectId, status, available }: { projectId: string; status: string; available: string[] | undefined }) {
  const invalidate = useInvalidateProject(projectId)
  const perms = {
    PM_WRITE: usePermission('PM_WRITE'), PM_APPROVE: usePermission('PM_APPROVE'), PM_ADMIN: usePermission('PM_ADMIN'),
  } as Record<string, boolean>
  const [ask, setAsk] = useState<Action | null>(null)
  const [busy, setBusy] = useState(false)
  const actions = ORDER.filter(a => (available ?? []).includes(a) && perms[PERM[a]])
  if (!actions.length) return null

  const run = async (a: Action) => {
    if (a === 'RETURN' || a === 'REOPEN' || a === 'CANCEL' || a === 'CLOSE' || a === 'APPROVE' || a === 'COMPLETE') { setAsk(a); return }
    setBusy(true)
    try { await transition(projectId, a); invalidate() } catch (e) { toast.error(await errorText(e, 'Could not change the status')) } finally { setBusy(false) }
  }
  const primary = actions.find(a => a !== 'CANCEL' && a !== 'RETURN' && a !== 'REOPEN')
  return (
    <>
      {actions.map(a => {
        const Icon = ICON[a]
        return <Btn key={a} disabled={busy} kind={a === 'CANCEL' ? 'danger' : a === primary ? 'primary' : 'secondary'} onClick={() => run(a)}>
          <Icon size={13} /> {a === 'ACTIVATE' && status === 'ON_HOLD' ? 'Resume' : LABEL[a]}</Btn>
      })}
      {ask === 'RETURN' && <ReasonDialog title="Return to planning" confirm="Return" intro="Say what needs to change before it is submitted again." onClose={() => setAsk(null)}
        onSubmit={async reason => { await transition(projectId, 'RETURN', { reason }); invalidate() }} />}
      {ask === 'REOPEN' && <ReasonDialog title="Reopen the project" confirm="Reopen" intro="The project goes back to Active." onClose={() => setAsk(null)}
        onSubmit={async reason => { await transition(projectId, 'REOPEN', { reason }); invalidate() }} />}
      {ask === 'CANCEL' && <ReasonDialog title="Cancel the project" confirm="Cancel project" danger intro="A cancelled project cannot be restarted. Its records are kept." onClose={() => setAsk(null)}
        onSubmit={async reason => { await transition(projectId, 'CANCEL', { reason }); invalidate() }} />}
      {ask === 'APPROVE' && <ReasonDialog title="Approve the project" label="Note" required={false} confirm="Approve"
        intro="Approval locks the baseline: today’s dates and budget become what progress and cost are measured against. Later changes go through change orders."
        onClose={() => setAsk(null)} onSubmit={async reason => { await transition(projectId, 'APPROVE', { reason: reason || null }); invalidate() }} />}
      {ask === 'COMPLETE' && <ReasonDialog title="Mark the project complete" label="Note" required={false} confirm="Mark complete"
        intro="Work is finished. Closing it comes next, once the closure checklist is clear." onClose={() => setAsk(null)}
        onSubmit={async reason => { await transition(projectId, 'COMPLETE', { reason: reason || null }); invalidate() }} />}
      {ask === 'CLOSE' && <CloseDialog projectId={projectId} onClose={() => setAsk(null)} onDone={invalidate} />}
    </>
  )
}

function Checklist({ data }: { data: Readiness }) {
  return (
    <div style={{ display: 'grid', gap: 6 }}>
      {data.checks.map(c => (
        <div key={c.code} style={{ display: 'flex', gap: 8, alignItems: 'flex-start', fontSize: 13 }}>
          {c.passed ? <CheckCircle2 size={15} style={{ color: 'var(--hf-success-text)', flexShrink: 0, marginTop: 1 }} />
            : c.blocking ? <XCircle size={15} style={{ color: 'var(--hf-danger-text)', flexShrink: 0, marginTop: 1 }} />
              : <AlertTriangle size={15} style={{ color: 'var(--hf-warning-text)', flexShrink: 0, marginTop: 1 }} />}
          <span style={{ flex: 1 }}>{c.description}{!c.passed && <b> ({c.count})</b>}</span>
          {!c.passed && <Pill tone={c.blocking ? 'danger' : 'warning'}>{c.blocking ? 'Blocks closing' : 'Warning'}</Pill>}
        </div>
      ))}
    </div>
  )
}

function useReadiness(projectId: string, enabled = true) {
  return useQuery<Readiness>({
    queryKey: ['pm-closure-readiness', projectId], enabled,
    queryFn: async () => one<Readiness>(await apiClient.get(`/api/v1/projects/${projectId}/closure-readiness`)),
  })
}

function CloseDialog({ projectId, onClose, onDone }: { projectId: string; onClose: () => void; onDone: () => void }) {
  const { data, isLoading } = useReadiness(projectId)
  const [lessons, setLessons] = useState('')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const save = async () => {
    if (!lessons.trim()) { setErr('Lessons learned are required to close a project'); return }
    setBusy(true); setErr('')
    try { await transition(projectId, 'CLOSE', { lessonsLearned: lessons.trim(), closureNotes: notes.trim() || null, reason: null }); onDone(); onClose() }
    catch (e) { setErr(await errorText(e, 'Could not close the project')) } finally { setBusy(false) }
  }
  return (
    <Dialog width={620} title="Close the project" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy || !data?.ready} onClick={save}>{busy ? 'Closing…' : 'Close project'}</Btn></>}>
      <SectionTitle>Closure checklist</SectionTitle>
      {isLoading || !data ? <Empty>Checking…</Empty> : <Checklist data={data} />}
      {data && !data.ready && <ErrorLine text="Clear the items marked “Blocks closing” first. Warnings do not stop you closing." />}
      <div style={{ height: 14 }} />
      <Field label="Lessons learned" hint="What went well, what to do differently next time. Required.">
        <textarea rows={4} maxLength={4000} value={lessons} onChange={e => setLessons(e.target.value)} style={{ ...inp, resize: 'vertical' }} />
      </Field>
      <Field label="Closure notes (optional)"><textarea rows={3} maxLength={4000} value={notes} onChange={e => setNotes(e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
      <div style={muted}>A closed project is read-only.</div>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

/** The Lifecycle tab: where the project is in its life, what stops it closing, and every status change. */
export function LifecycleTab({ projectId, status }: { projectId: string; status: string }) {
  const showChecklist = status === 'ACTIVE' || status === 'COMPLETED' || status === 'ON_HOLD'
  const { data: readiness, isLoading: rLoading } = useReadiness(projectId, showChecklist)
  const { data: history = [], isLoading, isError } = useQuery<HistoryRow[]>({
    queryKey: ['pm-status-history', projectId],
    queryFn: async () => list<HistoryRow>(await apiClient.get(`/api/v1/projects/${projectId}/status-history`)),
  })
  const path = ['PLANNING', 'PROPOSED', 'APPROVED', 'ACTIVE', 'COMPLETED', 'CLOSED']
  const at = path.indexOf(status === 'ON_HOLD' ? 'ACTIVE' : status)
  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <div style={{ ...card, display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
        {path.map((s, i) => (
          <span key={s} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            {i > 0 && <span style={{ width: 18, height: 1, background: 'var(--hf-border)' }} />}
            {i < at || (i === at && status === 'CLOSED') ? <CheckCircle2 size={14} style={{ color: 'var(--hf-success-text)' }} /> : <Circle size={14} style={{ color: i === at ? 'var(--hf-primary)' : 'var(--hf-text-faint)' }} />}
            <span style={{ fontSize: 12, fontWeight: i === at ? 700 : 500, color: i === at ? 'var(--hf-text)' : 'var(--hf-text-muted)' }}>
              {i === at && status === 'ON_HOLD' ? 'On hold' : s[0] + s.slice(1).toLowerCase()}</span>
          </span>
        ))}
        {status === 'CANCELLED' && <Pill tone="danger">Cancelled</Pill>}
      </div>

      {showChecklist && (
        <div style={card}>
          <SectionTitle right={readiness && <Pill tone={readiness.ready ? 'success' : 'danger'}>{readiness.ready ? 'Ready to close' : 'Not ready to close'}</Pill>}>Closure checklist</SectionTitle>
          {rLoading || !readiness ? <Empty>Checking…</Empty> : <Checklist data={readiness} />}
        </div>
      )}

      <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
        <div style={{ padding: '12px 14px 0' }}><SectionTitle>Status history</SectionTitle></div>
        {isLoading ? <Empty>Loading…</Empty> : isError ? <ErrorLine text="Could not load the status history." /> : history.length === 0 ? <Empty>No status changes yet.</Empty> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr><th style={th}>When</th><th style={th}>Change</th><th style={th}>By</th><th style={th}>Reason or note</th></tr></thead>
            <tbody>{history.map(h => (
              <tr key={h.id}>
                <td style={{ ...td, whiteSpace: 'nowrap' }}>{when(h.changedAt)}</td>
                <td style={td}>{h.fromStatus && <><Pill tone={PROJECT_STATUS_TONE[h.fromStatus]}>{h.fromStatus.replace('_', ' ')}</Pill> → </>}
                  <Pill tone={PROJECT_STATUS_TONE[h.toStatus]}>{h.toStatus.replace('_', ' ')}</Pill><div style={muted}>{h.action}</div></td>
                <td style={td}>{h.changedByName ?? '—'}</td>
                <td style={{ ...td, whiteSpace: 'pre-wrap' }}>{h.reason ?? '—'}</td>
              </tr>
            ))}</tbody>
          </table>
        )}
      </div>
    </div>
  )
}
