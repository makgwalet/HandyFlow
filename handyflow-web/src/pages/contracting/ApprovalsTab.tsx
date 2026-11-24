// src/pages/contracting/ApprovalsTab.tsx
//
// Maker-checker inbox and policy. Backend: /api/v1/contracts/approvals.
//  • Inbox: what is waiting, who asked, what it is for; approve / reject / withdraw.
//  • Approving your own request is only offered when the tenant policy allows a
//    sole-user exception, and then needs a written reason (>= 15 characters).
//  • Policy: which actions need a second person (CONTRACTS_ADMIN only).
//  • Waiting requests show whether they have been reminded or escalated, and who is reminded first;
//    reminders, escalation, time away and response times are in EscalationPanels.
// The server decides everything; buttons just mirror canDecide / mine.

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { usePermission } from '../../hooks/usePermission'
import { ACTION_LABEL, listOf, MIN_EXCEPTION_REASON, decisionBlocker, type Approval, type PolicyRow } from './approvalsShared'
import { stageLabel } from './escalationShared'
import { EscalationSettingsPanel, ReassignControl, ResponseTimesPanel } from './EscalationPanels'
import { useUsers } from './useContractUsers'

const STATUS_STYLE: Record<string, { label: string; color: string; bg: string }> = {
  PENDING:     { label: 'Waiting',     color: 'var(--hf-warning-text)',        bg: 'var(--hf-warning-soft)' },
  APPROVED:    { label: 'Approved',    color: 'var(--hf-success-text-strong)', bg: 'var(--hf-success-soft-strong)' },
  REJECTED:    { label: 'Rejected',    color: 'var(--hf-danger-text)',         bg: 'var(--hf-danger-soft)' },
  CANCELLED:   { label: 'Withdrawn',   color: 'var(--hf-text-muted)',          bg: 'var(--hf-surface-2, var(--hf-surface))' },
  INVALIDATED: { label: 'Out of date', color: 'var(--hf-text-muted)',          bg: 'var(--hf-surface-2, var(--hf-surface))' },
  CONSUMED:    { label: 'Used',        color: 'var(--hf-info-text)',           bg: 'var(--hf-info-soft)' },
}

const card: React.CSSProperties = {
  background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: 16,
}
const btn = (primary = false, danger = false): React.CSSProperties => ({
  padding: '7px 14px', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer',
  border: primary || danger ? 'none' : '1.5px solid var(--hf-border)',
  background: danger ? 'var(--hf-danger-text)' : primary ? 'var(--hf-primary-text)' : 'var(--hf-surface)',
  color: primary || danger ? '#fff' : 'var(--hf-text)',
})
const field: React.CSSProperties = {
  width: '100%', padding: '8px 10px', border: '1.5px solid var(--hf-border)', borderRadius: 8,
  fontSize: 13, boxSizing: 'border-box', background: 'var(--hf-surface)', resize: 'vertical',
}

function StatusPill({ status }: { status: string }) {
  const s = STATUS_STYLE[status] ?? STATUS_STYLE.CANCELLED
  return <span style={{ fontSize: 11, fontWeight: 700, padding: '3px 9px', borderRadius: 999, color: s.color, background: s.bg }}>{s.label}</span>
}

function errMsg(e: unknown): string {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? 'Something went wrong'
}

function DecisionPanel({ a, allowSole, onDone }: { a: Approval; allowSole: boolean; onDone: () => void }) {
  const qc = useQueryClient()
  const [note, setNote] = useState('')
  const [sole, setSole] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const done = () => { qc.invalidateQueries({ queryKey: ['contract-approvals'] }); onDone() }
  const approve = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/contracts/approvals/${a.id}/approve`,
      { note: note || null, soleUserException: sole, exceptionReason: sole ? reason.trim() : null }),
    onSuccess: done, onError: e => setError(errMsg(e)),
  })
  const reject = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/contracts/approvals/${a.id}/reject`,
      { note, soleUserException: false, exceptionReason: null }),
    onSuccess: done, onError: e => setError(errMsg(e)),
  })

  const blocker = decisionBlocker(a, sole, reason, false)
  const busy = approve.isPending || reject.isPending

  return (
    <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--hf-border)' }}>
      <textarea style={field} rows={2} placeholder="Note (required to reject)" value={note} onChange={e => setNote(e.target.value)} />
      {a.mine && (
        allowSole ? (
          <label style={{ display: 'block', marginTop: 8, fontSize: 13 }}>
            <input type="checkbox" checked={sole} onChange={e => setSole(e.target.checked)} /> I am the only person who can approve this (recorded as an exception)
          </label>
        ) : (
          <div style={{ marginTop: 8, fontSize: 12, color: 'var(--hf-text-muted)' }}>You asked for this. Your policy does not allow approving your own request.</div>
        )
      )}
      {sole && (
        <textarea style={{ ...field, marginTop: 8 }} rows={2} placeholder={`Why is no one else available? (min ${MIN_EXCEPTION_REASON} characters)`}
          value={reason} onChange={e => setReason(e.target.value)} />
      )}
      {error && <div role="alert" style={{ marginTop: 8, fontSize: 12, color: 'var(--hf-danger-text)' }}>{error}</div>}
      {blocker && a.mine && <div style={{ marginTop: 8, fontSize: 12, color: 'var(--hf-text-muted)' }}>{blocker}</div>}
      <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
        <button style={btn(true)} disabled={busy || !!blocker} onClick={() => { setError(null); approve.mutate() }}>
          {sole ? 'Approve as exception' : 'Approve'}
        </button>
        <button style={btn(false, true)} disabled={busy || note.trim().length === 0} onClick={() => { setError(null); reject.mutate() }}>Reject</button>
      </div>
    </div>
  )
}

export function ApprovalCard({ a, allowSole }: { a: Approval; allowSole: boolean }) {
  const qc = useQueryClient()
  const { nameOf } = useUsers()
  const canReassign = usePermission('CONTRACTS_ADMIN') || (!!a.assignedTo && a.canDecide)
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const cancel = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/contracts/approvals/${a.id}/cancel`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['contract-approvals'] }),
    onError: e => setError(errMsg(e)),
  })
  const when = (s: string | null) => s ? new Date(s).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : ''

  return (
    <div style={card}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }}>
        <div>
          <div style={{ fontWeight: 700, fontSize: 14 }}>{ACTION_LABEL[a.action] ?? a.action}</div>
          <div style={{ fontSize: 13, color: 'var(--hf-text-muted)', marginTop: 2 }}>{a.subjectLabel ?? a.subjectId}</div>
        </div>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          {a.status === 'PENDING' && stageLabel(a.escalationStage) && (
            <span style={{ fontSize: 11, fontWeight: 700, padding: '3px 9px', borderRadius: 999,
              color: (a.escalationStage ?? 0) >= 3 ? 'var(--hf-danger-text)' : 'var(--hf-warning-text)',
              background: (a.escalationStage ?? 0) >= 3 ? 'var(--hf-danger-soft)' : 'var(--hf-warning-soft)' }}>
              {stageLabel(a.escalationStage)}
            </span>
          )}
          <StatusPill status={a.status} />
        </div>
      </div>
      <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 8 }}>
        {a.mine ? 'Asked by you' : 'Asked'} · {when(a.requestedAt)}
        {a.requestNote ? <> · “{a.requestNote}”</> : null}
      </div>
      {a.status === 'PENDING' && a.assignedTo && (
        <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 4 }}>Reminders go to {nameOf(a.assignedTo)} first</div>
      )}
      {a.decidedAt && (
        <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 4 }}>
          Decided {when(a.decidedAt)}{a.decisionNote ? <> · “{a.decisionNote}”</> : null}
        </div>
      )}
      {a.soleUserException && (
        <div style={{ fontSize: 12, marginTop: 6, color: 'var(--hf-warning-text)' }}>
          Sole-user exception: {a.exceptionReason}
        </div>
      )}
      {error && <div role="alert" style={{ marginTop: 8, fontSize: 12, color: 'var(--hf-danger-text)' }}>{error}</div>}
      {a.status === 'PENDING' && (
        <div style={{ display: 'flex', gap: 8, marginTop: 10, flexWrap: 'wrap' }}>
          {a.canDecide && <button style={btn(true)} onClick={() => setOpen(o => !o)}>{open ? 'Close' : 'Review'}</button>}
          {a.mine && <button style={btn()} disabled={cancel.isPending} onClick={() => cancel.mutate()}>Withdraw</button>}
          {canReassign && <ReassignControl a={a} />}
        </div>
      )}
      {open && a.canDecide && <DecisionPanel a={a} allowSole={allowSole} onDone={() => setOpen(false)} />}
    </div>
  )
}

function PolicyPanel() {
  const qc = useQueryClient()
  const isAdmin = usePermission('CONTRACTS_ADMIN')
  const [error, setError] = useState<string | null>(null)
  const { data: rows = [] } = useQuery<PolicyRow[]>({
    queryKey: ['contract-approvals', 'policy'],
    queryFn: async () => listOf(await apiClient.get('/api/v1/contracts/approvals/policy')),
  })
  const save = useMutation({
    mutationFn: (p: PolicyRow) => apiClient.put(`/api/v1/contracts/approvals/policy/${p.action}`,
      { required: p.required, allowSoleUserException: p.allowSoleUserException }),
    onSuccess: () => { setError(null); qc.invalidateQueries({ queryKey: ['contract-approvals'] }) },
    onError: e => setError(errMsg(e)),
  })

  return (
    <div style={card}>
      <div style={{ fontWeight: 700, marginBottom: 4 }}>Who must approve what</div>
      <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginBottom: 12 }}>
        When an action needs approval, a second person signs it off before it happens. An approval is tied to what was reviewed, so a later change asks again.
        {!isAdmin && ' Only a contracts administrator can change this.'}
      </div>
      {error && <div role="alert" style={{ fontSize: 12, color: 'var(--hf-danger-text)', marginBottom: 8 }}>{error}</div>}
      <div style={{ display: 'grid', gap: 8 }}>
        {rows.map(r => (
          <div key={r.action} style={{ display: 'grid', gridTemplateColumns: '1fr auto auto', gap: 16, alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--hf-border)' }}>
            <div style={{ fontSize: 13, fontWeight: 600 }}>
              {ACTION_LABEL[r.action] ?? r.action}
              {r.usingDefault && <span style={{ marginLeft: 8, fontSize: 11, fontWeight: 500, color: 'var(--hf-text-muted)' }}>default</span>}
            </div>
            <label style={{ fontSize: 13 }}>
              <input type="checkbox" checked={r.required} disabled={!isAdmin || save.isPending}
                onChange={e => save.mutate({ ...r, required: e.target.checked })} /> Needs approval
            </label>
            <label style={{ fontSize: 13, opacity: r.required ? 1 : 0.5 }}>
              <input type="checkbox" checked={r.allowSoleUserException} disabled={!isAdmin || !r.required || save.isPending}
                onChange={e => save.mutate({ ...r, allowSoleUserException: e.target.checked })} /> Allow sole-user exception
            </label>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function ApprovalsTab() {
  const [filter, setFilter] = useState<'PENDING' | 'ALL'>('PENDING')
  const { data: all = [], isLoading } = useQuery<Approval[]>({
    queryKey: ['contract-approvals', 'list'],
    queryFn: async () => listOf(await apiClient.get('/api/v1/contracts/approvals')),
  })
  const { data: policy = [] } = useQuery<PolicyRow[]>({
    queryKey: ['contract-approvals', 'policy'],
    queryFn: async () => listOf(await apiClient.get('/api/v1/contracts/approvals/policy')),
  })
  const allowSole = (a: Approval) => policy.find(p => p.action === a.action)?.allowSoleUserException ?? false
  const rows = filter === 'PENDING' ? all.filter(a => a.status === 'PENDING') : all
  const waitingForMe = all.filter(a => a.status === 'PENDING' && a.canDecide && !a.mine).length

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {(['PENDING', 'ALL'] as const).map(f => (
          <button key={f} style={{ ...btn(filter === f) }} onClick={() => setFilter(f)}>
            {f === 'PENDING' ? `Waiting${waitingForMe ? ` (${waitingForMe} for you)` : ''}` : 'All requests'}
          </button>
        ))}
      </div>
      {isLoading ? <div style={{ fontSize: 13 }}>Loading…</div> : rows.length === 0 ? (
        <div style={{ ...card, textAlign: 'center', color: 'var(--hf-text-muted)', fontSize: 13 }}>
          {filter === 'PENDING' ? 'Nothing is waiting for approval.' : 'No approval requests yet.'}
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 10 }}>{rows.map(a => <ApprovalCard key={a.id} a={a} allowSole={allowSole(a)} />)}</div>
      )}
      <EscalationSettingsPanel />
      <ResponseTimesPanel />
      <PolicyPanel />
    </div>
  )
}
