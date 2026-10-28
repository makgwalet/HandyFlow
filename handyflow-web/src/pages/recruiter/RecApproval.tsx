// Approval of a job before it is published, or of an offer before the letter is sent. Shown only when the company asks for approval
// or one has been started. The server decides who may decide: never the person who sent it.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useAuth } from '../../hooks/useAuth'
import { APPROVAL_LABEL, decisionProblem, fmtDay, mayResubmit, recruiterRights, showApproval, type ApprovalView } from './recApproval.logic'

const BASE = '/api/v1/recruiter'
type Unwrapped = { data?: { data?: ApprovalView } & ApprovalView }
const unwrap = (r: Unwrapped): ApprovalView => (r.data?.data ?? r.data) as ApprovalView
const message = (e: unknown, fallback: string): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback

const tone = (s: ApprovalView['status']) =>
  s === 'APPROVED' ? 'var(--hf-success-text-strong)' : s === 'REJECTED' ? 'var(--hf-danger-text)' : s === 'SUBMITTED' ? 'var(--hf-warning-text)' : 'var(--hf-text-muted)'

export default function ApprovalPanel({ kind, id }: { kind: 'job' | 'offer'; id: string }) {
  const qc = useQueryClient()
  const { user } = useAuth() as { user?: { permissions?: string[] } }
  const rights = recruiterRights(user?.permissions ?? [])
  const path = kind === 'job' ? `${BASE}/jobs/${id}/approval` : `${BASE}/applications/${id}/offer-approval`
  const key = ['rec-approval', kind, id]
  const [note, setNote] = useState('')
  const [tried, setTried] = useState<'approve' | 'reject' | null>(null)
  const [error, setError] = useState<string | null>(null)

  const q = useQuery<ApprovalView>({ queryKey: key, queryFn: async () => unwrap(await apiClient.get(path)), retry: false })
  const save = (v: ApprovalView) => { qc.setQueryData(key, v); setError(null); setNote(''); setTried(null) }
  const submit = useMutation({
    mutationFn: async () => unwrap(await apiClient.post(`${path}/submit`, {})),
    onSuccess: save, onError: e => setError(message(e, 'That could not be sent for approval.')),
  })
  const decide = useMutation({
    mutationFn: async (approve: boolean) => unwrap(await apiClient.post(`${path}/decision`, { approve, note: note.trim() || null })),
    onSuccess: save, onError: e => setError(message(e, 'That could not be saved.')),
  })

  const v = q.data
  if (!showApproval(v) || !v) return null
  const manage = kind === 'offer' ? rights.manage : rights.admin
  const problem = tried ? decisionProblem(tried === 'approve', note) : null
  const btn = { padding: '5px 12px', borderRadius: 7, fontSize: 12, fontWeight: 700, cursor: 'pointer', border: '1px solid var(--hf-border)', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-secondary)' } as const

  return (
    <section aria-label={kind === 'job' ? 'Job approval' : 'Offer approval'}
      style={{ margin: '10px 0', padding: '10px 12px', border: '1px solid var(--hf-border)', borderRadius: 9, background: 'var(--hf-surface)' }}>
      <div style={{ fontSize: 12, fontWeight: 700, color: tone(v.status) }}>{APPROVAL_LABEL[v.status]}</div>
      {v.submittedByName && <div style={{ fontSize: 11, color: 'var(--hf-text-muted)' }}>Sent by {v.submittedByName}, {fmtDay(v.submittedAt)}</div>}
      {v.decidedByName && <div style={{ fontSize: 11, color: 'var(--hf-text-muted)' }}>{v.status === 'APPROVED' ? 'Approved' : 'Not approved'} by {v.decidedByName}, {fmtDay(v.decidedAt)}</div>}
      {v.note && <div style={{ fontSize: 12, marginTop: 4 }}>“{v.note}”</div>}
      {v.required && v.status !== 'APPROVED' && (
        <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 4 }}>
          {kind === 'job' ? 'This company needs a job to be approved before it is published.' : 'This company needs an offer to be approved before the letter is sent.'}
        </div>
      )}
      {manage && mayResubmit(v) && (
        <div style={{ marginTop: 8 }}>
          <button type="button" disabled={submit.isPending} onClick={() => submit.mutate()} style={btn}>{submit.isPending ? 'Sending…' : 'Send for approval'}</button>
        </div>
      )}
      {v.status === 'SUBMITTED' && rights.approve && !v.canDecide && (
        <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 6 }}>Someone else needs to decide: you sent this yourself.</div>
      )}
      {v.status === 'SUBMITTED' && rights.approve && v.canDecide && (
        <div style={{ marginTop: 8, display: 'grid', gap: 6 }}>
          <label style={{ fontSize: 12 }}>Note (needed if not approved)
            <textarea aria-label="Approval note" rows={2} value={note} onChange={e => setNote(e.target.value)}
              style={{ width: '100%', marginTop: 2, padding: 6, border: '1px solid var(--hf-border)', borderRadius: 6, background: 'var(--hf-surface)', color: 'var(--hf-text)' }} />
          </label>
          <div style={{ display: 'flex', gap: 8 }}>
            <button type="button" disabled={decide.isPending} onClick={() => { setTried('approve'); if (!decisionProblem(true, note)) decide.mutate(true) }}
              style={{ ...btn, background: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)' }}>Approve</button>
            <button type="button" disabled={decide.isPending} onClick={() => { setTried('reject'); if (!decisionProblem(false, note)) decide.mutate(false) }}
              style={{ ...btn, background: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)' }}>Do not approve</button>
          </div>
        </div>
      )}
      {(problem || error) && <div role="alert" style={{ color: 'var(--hf-danger-text)', fontSize: 12, marginTop: 6 }}>{problem ?? error}</div>}
    </section>
  )
}
