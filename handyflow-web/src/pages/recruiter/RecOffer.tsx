// Where an offer stands after the letter goes out: awaiting an answer, lapsed, accepted, declined or withdrawn. The server decides what is allowed;
// this only shows the state and asks for the reason when one is needed.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { usePermission } from '../../hooks/usePermission'

const BASE = '/api/v1/recruiter/applications'
type Revision = { oldSalary: number | null; oldFrequency: string | null; oldStartDate: string | null; reason: string; revisedAt: string }
export type OfferView = {
  applicationId: string; status: 'DRAFT' | 'AWAITING' | 'EXPIRED' | 'ACCEPTED' | 'DECLINED' | 'WITHDRAWN'
  offeredSalary: number | null; offeredSalaryFrequency: string | null; offeredStartDate: string | null; offerBenefits: string | null
  sentAt: string | null; expiresOn: string | null; response: string; respondedAt: string | null; responseNote: string | null; revisions: Revision[]
}
const LABEL: Record<OfferView['status'], string> = {
  DRAFT: 'Not sent yet', AWAITING: 'Awaiting answer', EXPIRED: 'Lapsed', ACCEPTED: 'Accepted', DECLINED: 'Declined', WITHDRAWN: 'Withdrawn',
}
const TONE: Record<OfferView['status'], string> = {
  DRAFT: 'var(--hf-text-muted)', AWAITING: 'var(--hf-warning-text)', EXPIRED: 'var(--hf-danger-text)',
  ACCEPTED: 'var(--hf-success-text-strong)', DECLINED: 'var(--hf-danger-text)', WITHDRAWN: 'var(--hf-text-muted)',
}
const message = (e: unknown, fallback: string): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback
const unwrap = (r: { data?: { data?: OfferView } & OfferView }): OfferView => (r.data?.data ?? r.data) as OfferView
const btn = { padding: '5px 12px', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12, cursor: 'pointer' } as const

const FREQUENCIES: [string, string][] = [['MONTHLY', 'Monthly'], ['WEEKLY', 'Weekly'], ['BIWEEKLY', 'Every two weeks'], ['ANNUALLY', 'Yearly']]
const field = { padding: '6px 9px', background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12 } as const

/** Change the terms of an offer. Before the letter goes out no reason is needed; afterwards the old terms are kept with the reason and the letter must be sent again. */
export function ReviseOfferForm({ id, onChanged, onClose }: { id: string; onChanged: () => void; onClose: () => void }) {
  const qc = useQueryClient()
  const q = useQuery<OfferView>({ queryKey: ['rec-offer', id], queryFn: async () => unwrap(await apiClient.get(`${BASE}/${id}/offer`)), retry: false })
  const v = q.data
  const [salary, setSalary] = useState<string | null>(null)
  const [frequency, setFrequency] = useState<string | null>(null)
  const [start, setStart] = useState<string | null>(null)
  const [benefits, setBenefits] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const sent = v?.sentAt != null
  const save = useMutation({
    mutationFn: async () => unwrap(await apiClient.post(`${BASE}/${id}/offer/revise`, {
      offeredSalary: Number(salary ?? v?.offeredSalary), offeredSalaryFrequency: frequency ?? v?.offeredSalaryFrequency ?? 'MONTHLY',
      offeredStartDate: (start ?? v?.offeredStartDate) || null, offerBenefits: (benefits ?? v?.offerBenefits) || null, reason: reason.trim() || null,
    })),
    onSuccess: view => { qc.setQueryData(['rec-offer', id], view); setError(null); onChanged(); onClose() },
    onError: e => setError(message(e, 'The terms could not be changed.')),
  })
  if (!v) return null
  const salaryText = salary ?? (v.offeredSalary == null ? '' : String(v.offeredSalary))
  const bad = !(Number(salaryText) > 0) || (sent && !reason.trim())
  return (
    <div style={{ marginTop: 8, display: 'grid', gap: 8, padding: 10, border: '1px solid var(--hf-border)', borderRadius: 8 }}>
      <strong>Change the offer terms</strong>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <label>Salary (R) <input inputMode="decimal" aria-label="Offered salary" value={salaryText} onChange={e => setSalary(e.target.value)} style={{ ...field, width: 110 }} /></label>
        <label>Paid <select value={frequency ?? v.offeredSalaryFrequency ?? 'MONTHLY'} onChange={e => setFrequency(e.target.value)} style={field}>
          {FREQUENCIES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></label>
        <label>Start date <input type="date" value={start ?? v.offeredStartDate ?? ''} onChange={e => setStart(e.target.value)} style={field} /></label>
      </div>
      <label>Benefits <textarea rows={2} value={benefits ?? v.offerBenefits ?? ''} onChange={e => setBenefits(e.target.value)} style={{ ...field, width: '100%' }} /></label>
      {sent && <label>Why are the terms changing? <input aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} style={{ ...field, width: '100%' }} /></label>}
      {sent && <div style={{ color: 'var(--hf-text-muted)' }}>The old terms are kept with this reason. The letter has to be sent again.</div>}
      {error && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{error}</div>}
      <div style={{ display: 'flex', gap: 8 }}>
        <button style={btn} disabled={save.isPending || bad} onClick={() => save.mutate()}>{save.isPending ? 'Saving...' : 'Save terms'}</button>
        <button style={btn} onClick={onClose}>Cancel</button>
      </div>
    </div>
  )
}

export function offerNeedsAnswer(s?: OfferView['status']) { return s === 'AWAITING' || s === 'EXPIRED' || s === 'DRAFT' }

export default function OfferPanel({ id, onChanged }: { id: string; onChanged: () => void }) {
  const mayOffer = usePermission('RECRUITER_OFFER')
  const qc = useQueryClient()
  const key = ['rec-offer', id]
  const [note, setNote] = useState('')
  const [mode, setMode] = useState<null | 'DECLINED' | 'WITHDRAWN'>(null)
  const [resendOn, setResendOn] = useState('')
  const [revising, setRevising] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const q = useQuery<OfferView>({ queryKey: key, queryFn: async () => unwrap(await apiClient.get(`${BASE}/${id}/offer`)), retry: false })
  const done = (v?: OfferView) => { if (v) qc.setQueryData(key, v); setError(null); setNote(''); setMode(null); onChanged() }

  const respond = useMutation({
    mutationFn: async (r: { response: string; note: string | null }) => unwrap(await apiClient.post(`${BASE}/${id}/offer/response`, r)),
    onSuccess: done, onError: e => setError(message(e, 'That could not be recorded.')),
  })
  const resend = useMutation({
    mutationFn: async () => apiClient.post(`${BASE}/${id}/offer-letter/send`, { expiresOn: resendOn || null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setResendOn(''); done() },
    onError: e => setError(message(e, 'The offer could not be sent.')),
  })

  const v = q.data
  if (!v || v.status === 'DRAFT') return null
  const open = v.status === 'AWAITING' || v.status === 'EXPIRED'
  return (
    <div style={{ border: '1px solid var(--hf-border)', borderRadius: 8, padding: 12, margin: '10px 0', fontSize: 12 }}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <strong>Offer</strong>
        <span style={{ color: TONE[v.status], fontWeight: 600 }}>{LABEL[v.status]}</span>
        {v.expiresOn && open && <span style={{ color: 'var(--hf-text-muted)' }}>open until {v.expiresOn}</span>}
        {v.respondedAt && <span style={{ color: 'var(--hf-text-muted)' }}>on {v.respondedAt.slice(0, 10)}</span>}
      </div>
      {v.responseNote && <div style={{ marginTop: 6, color: 'var(--hf-text-secondary)' }}>{v.responseNote}</div>}
      {open && mayOffer && (
        <div style={{ marginTop: 8, display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
          {v.status === 'AWAITING' && <button style={btn} disabled={respond.isPending} onClick={() => respond.mutate({ response: 'ACCEPTED', note: null })}>Candidate accepted</button>}
          <button style={btn} onClick={() => setMode('DECLINED')}>Candidate declined</button>
          <button style={btn} onClick={() => setMode('WITHDRAWN')}>Withdraw offer</button>
          <button style={btn} onClick={() => setRevising(r => !r)}>Change terms</button>
          {v.status === 'EXPIRED' && (
            <>
              <input type="date" aria-label="New expiry date" value={resendOn} onChange={e => setResendOn(e.target.value)} style={{ ...btn, cursor: 'text' }} />
              <button style={btn} disabled={resend.isPending} onClick={() => resend.mutate()}>{resend.isPending ? 'Sending...' : 'Send again'}</button>
            </>
          )}
      {revising && mayOffer && <ReviseOfferForm id={id} onChanged={onChanged} onClose={() => setRevising(false)} />}
        </div>
      )}
      {mode && (
        <div style={{ marginTop: 8, display: 'flex', gap: 6, alignItems: 'center' }}>
          <input aria-label="Reason" placeholder="Reason" maxLength={500} value={note} onChange={e => setNote(e.target.value)} style={{ ...btn, flex: 1, cursor: 'text' }} />
          <button style={btn} disabled={respond.isPending || !note.trim()} onClick={() => respond.mutate({ response: mode, note: note.trim() })}>
            {mode === 'DECLINED' ? 'Record decline' : 'Withdraw'}
          </button>
          <button style={btn} onClick={() => { setMode(null); setNote('') }}>Cancel</button>
        </div>
      )}
      {error && <div role="alert" style={{ marginTop: 6, color: 'var(--hf-danger-text)' }}>{error}</div>}
      {v.revisions.length > 0 && (
        <details style={{ marginTop: 8 }}>
          <summary>Earlier terms ({v.revisions.length})</summary>
          {v.revisions.map((r, i) => (
            <div key={i} style={{ marginTop: 4, color: 'var(--hf-text-muted)' }}>
              {r.revisedAt.slice(0, 10)}: was {r.oldSalary ?? '-'} {r.oldFrequency ?? ''}{r.oldStartDate ? `, start ${r.oldStartDate}` : ''}. {r.reason}
            </div>
          ))}
        </details>
      )}
    </div>
  )
}
