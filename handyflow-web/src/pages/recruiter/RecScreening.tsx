// Screening questions: the editor on a job, and the answers on an application. A criterion only marks an answer for a person to look at.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'

type Type = 'YES_NO' | 'NUMBER' | 'TEXT'
type Q = { text: string; type: Type; required: boolean; expectedAnswer: string | null; minimumNumber: number | null }
const MAX = 15
const field = { padding: '5px 8px', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12 } as const
const message = (e: unknown, fallback: string): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T

export function ScreeningEditor({ jobId, canEdit }: { jobId: string; canEdit: boolean }) {
  const qc = useQueryClient()
  const key = ['rec-screening', jobId]
  const [draft, setDraft] = useState<Q[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const q = useQuery<Q[]>({ queryKey: key, queryFn: async () => data<Q[]>(await apiClient.get(`/api/v1/recruiter/jobs/${jobId}/screening`)), retry: false })
  const save = useMutation({
    mutationFn: async () => data<Q[]>(await apiClient.put(`/api/v1/recruiter/jobs/${jobId}/screening`, { questions: draft })),
    onSuccess: v => { qc.setQueryData(key, v); setDraft(null); setError(null) },
    onError: e => setError(message(e, 'The questions could not be saved.')),
  })
  const rows = draft ?? q.data ?? []
  if (!q.data) return null
  const change = (i: number, patch: Partial<Q>) => setDraft(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)))
  return (
    <details style={{ border: '1px solid var(--hf-border)', borderRadius: 8, padding: '8px 12px', margin: '8px 0', fontSize: 12 }}>
      <summary style={{ cursor: 'pointer' }}><strong>Screening questions</strong> ({q.data.length})</summary>
      {rows.map((r, i) => (
        <div key={i} style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center', marginTop: 8 }}>
          <input aria-label="Question" value={r.text} maxLength={300} disabled={!canEdit} onChange={e => change(i, { text: e.target.value })} style={{ ...field, flex: '1 1 220px' }} />
          <select aria-label="Type" value={r.type} disabled={!canEdit}
            onChange={e => change(i, { type: e.target.value as Type, expectedAnswer: null, minimumNumber: null })} style={field}>
            <option value="YES_NO">Yes/No</option><option value="NUMBER">Number</option><option value="TEXT">Text</option>
          </select>
          {r.type === 'YES_NO' && (
            <select aria-label="Required answer" value={r.expectedAnswer ?? ''} disabled={!canEdit}
              onChange={e => change(i, { expectedAnswer: e.target.value || null, required: e.target.value ? true : r.required })} style={field}>
              <option value="">Any answer</option><option value="YES">Must be Yes</option><option value="NO">Must be No</option>
            </select>
          )}
          {r.type === 'NUMBER' && (
            <input aria-label="Minimum" type="number" min={0} placeholder="Minimum" value={r.minimumNumber ?? ''} disabled={!canEdit}
              onChange={e => change(i, { minimumNumber: e.target.value === '' ? null : Number(e.target.value), required: e.target.value !== '' ? true : r.required })} style={{ ...field, width: 90 }} />
          )}
          <label><input type="checkbox" checked={r.required} disabled={!canEdit || r.expectedAnswer != null || r.minimumNumber != null}
            onChange={e => change(i, { required: e.target.checked })} /> Required</label>
          {canEdit && <button type="button" onClick={() => setDraft(rows.filter((_, j) => j !== i))} style={{ ...field, cursor: 'pointer' }}>Remove</button>}
        </div>
      ))}
      {canEdit && (
        <div style={{ marginTop: 8, display: 'flex', gap: 6 }}>
          <button type="button" disabled={rows.length >= MAX} style={{ ...field, cursor: 'pointer' }}
            onClick={() => setDraft([...rows, { text: '', type: 'YES_NO', required: false, expectedAnswer: null, minimumNumber: null }])}>Add question</button>
          <button type="button" disabled={draft === null || save.isPending} style={{ ...field, cursor: 'pointer' }} onClick={() => save.mutate()}>{save.isPending ? 'Saving...' : 'Save questions'}</button>
        </div>
      )}
      <div style={{ marginTop: 6, color: 'var(--hf-text-muted)' }}>An answer that misses a criterion is flagged for a person to review. Nobody is rejected automatically.</div>
      {error && <div role="alert" style={{ marginTop: 6, color: 'var(--hf-danger-text)' }}>{error}</div>}
    </details>
  )
}

type Answer = { question: string; type: Type; knockout: boolean; answer: string | null; meetsCriteria: boolean | null }
export function ScreeningAnswers({ applicationId }: { applicationId: string }) {
  const q = useQuery<{ knockoutsFailed: number; answers: Answer[] }>({
    queryKey: ['rec-app-screening', applicationId],
    queryFn: async () => data(await apiClient.get(`/api/v1/recruiter/applications/${applicationId}/screening`)),
    retry: false,
  })
  const v = q.data
  if (!v || v.answers.length === 0) return null
  return (
    <div style={{ border: '1px solid var(--hf-border)', borderRadius: 8, padding: 12, margin: '10px 0', fontSize: 12 }}>
      <strong>Screening answers</strong>
      {v.knockoutsFailed > 0 && (
        <span style={{ marginLeft: 8, color: 'var(--hf-warning-text)', fontWeight: 600 }}>
          {v.knockoutsFailed} {v.knockoutsFailed === 1 ? 'answer needs' : 'answers need'} review
        </span>
      )}
      {v.answers.map((a, i) => (
        <div key={i} style={{ marginTop: 6 }}>
          <div style={{ color: 'var(--hf-text-muted)' }}>{a.question}</div>
          <div style={{ color: a.meetsCriteria === false ? 'var(--hf-danger-text)' : undefined }}>
            {a.answer ?? 'No answer'}{a.meetsCriteria === false && ' (does not meet the criteria)'}
          </div>
        </div>
      ))}
    </div>
  )
}
