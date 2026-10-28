// Checks to complete before a candidate is hired. The server holds the hire until every required check is verified or waived; waiving needs an approver.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'

type Check = { id: string; type: string; description: string; required: boolean; status: 'PENDING' | 'VERIFIED' | 'FAILED' | 'WAIVED'; note: string | null }
type View = { checks: Check[]; outstanding: number; readyToHire: boolean; blockedReason: string | null }
const TYPES: [string, string][] = [['IDENTITY', 'Identity'], ['RIGHT_TO_WORK', 'Right to work'], ['QUALIFICATION', 'Qualification'], ['REFERENCE', 'Reference'], ['BACKGROUND', 'Background'], ['OTHER', 'Other']]
const TONE: Record<Check['status'], string> = { PENDING: 'var(--hf-warning-text)', VERIFIED: 'var(--hf-success-text-strong)', FAILED: 'var(--hf-danger-text)', WAIVED: 'var(--hf-text-muted)' }
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const message = (e: unknown, fallback: string): string => (e as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback
const field = { padding: '5px 8px', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12 } as const

export default function VerificationPanel({ applicationId, closed }: { applicationId: string; closed: boolean }) {
  const qc = useQueryClient()
  const key = ['rec-verification', applicationId]
  const base = `/api/v1/recruiter/applications/${applicationId}/verification`
  const [type, setType] = useState('IDENTITY')
  const [text, setText] = useState('')
  const [required, setRequired] = useState(true)
  const [deciding, setDeciding] = useState<{ id: string; status: 'FAILED' | 'WAIVED' } | null>(null)
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const q = useQuery<View>({ queryKey: key, queryFn: async () => data(await apiClient.get(base)), retry: false })
  const saved = (v: View) => { qc.setQueryData(key, v); setError(null); setText(''); setNote(''); setDeciding(null) }
  const add = useMutation({ mutationFn: async () => data<View>(await apiClient.post(base, { type, description: text, required })), onSuccess: saved, onError: e => setError(message(e, 'The check could not be added.')) })
  const decide = useMutation({
    mutationFn: async (v: { id: string; status: string; note: string | null }) => data<View>(await apiClient.post(`${base}/${v.id}/decision`, { status: v.status, note: v.note })),
    onSuccess: saved, onError: e => setError(message(e, 'That could not be recorded.')),
  })
  const remove = useMutation({ mutationFn: async (id: string) => data<View>(await apiClient.delete(`${base}/${id}`)), onSuccess: saved, onError: e => setError(message(e, 'The check could not be removed.')) })

  const v = q.data
  if (!v || (closed && v.checks.length === 0)) return null
  return (
    <div style={{ border: '1px solid var(--hf-border)', borderRadius: 8, padding: 12, margin: '10px 0', fontSize: 12 }}>
      <strong>Checks before hiring</strong>
      {v.checks.length > 0 && (
        <span style={{ marginLeft: 8, color: v.readyToHire ? 'var(--hf-success-text-strong)' : 'var(--hf-warning-text)', fontWeight: 600 }}>
          {v.readyToHire ? 'Clear to hire' : v.blockedReason}
        </span>
      )}
      {v.checks.map(c => (
        <div key={c.id} style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <span style={{ color: TONE[c.status], fontWeight: 600, minWidth: 70 }}>{c.status.charAt(0) + c.status.slice(1).toLowerCase()}</span>
          <span>{TYPES.find(t => t[0] === c.type)?.[1]}: {c.description}{c.required ? '' : ' (optional)'}{c.note ? `, ${c.note}` : ''}</span>
          {!closed && (c.status === 'PENDING' || c.status === 'FAILED') && (
            <>
              <button style={{ ...field, cursor: 'pointer' }} disabled={decide.isPending} onClick={() => decide.mutate({ id: c.id, status: 'VERIFIED', note: null })}>Verified</button>
              {c.status === 'PENDING' && <button style={{ ...field, cursor: 'pointer' }} onClick={() => setDeciding({ id: c.id, status: 'FAILED' })}>Failed</button>}
              <button style={{ ...field, cursor: 'pointer' }} onClick={() => setDeciding({ id: c.id, status: 'WAIVED' })}>Waive</button>
              {c.status === 'PENDING' && <button style={{ ...field, cursor: 'pointer' }} onClick={() => remove.mutate(c.id)}>Remove</button>}
            </>
          )}
          {deciding?.id === c.id && (
            <span style={{ display: 'flex', gap: 6, flex: '1 1 100%' }}>
              <input aria-label="Reason" placeholder={deciding.status === 'FAILED' ? 'What failed' : 'Why this check is waived'} maxLength={500} value={note} onChange={e => setNote(e.target.value)} style={{ ...field, flex: 1 }} />
              <button style={{ ...field, cursor: 'pointer' }} disabled={!note.trim() || decide.isPending} onClick={() => decide.mutate({ id: c.id, status: deciding.status, note: note.trim() })}>
                {deciding.status === 'FAILED' ? 'Record failure' : 'Waive'}
              </button>
              <button style={{ ...field, cursor: 'pointer' }} onClick={() => { setDeciding(null); setNote('') }}>Cancel</button>
            </span>
          )}
        </div>
      ))}
      {!closed && (
        <div style={{ marginTop: 10, display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
          <select aria-label="Kind of check" value={type} onChange={e => setType(e.target.value)} style={field}>
            {TYPES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select>
          <input aria-label="What is checked" placeholder="e.g. ID document, Matric certificate" maxLength={200} value={text} onChange={e => setText(e.target.value)} style={{ ...field, flex: '1 1 200px' }} />
          <label><input type="checkbox" checked={required} onChange={e => setRequired(e.target.checked)} /> Required</label>
          <button style={{ ...field, cursor: 'pointer' }} disabled={text.trim().length < 3 || add.isPending} onClick={() => add.mutate()}>Add check</button>
        </div>
      )}
      {error && <div role="alert" style={{ marginTop: 6, color: 'var(--hf-danger-text)' }}>{error}</div>}
    </div>
  )
}
