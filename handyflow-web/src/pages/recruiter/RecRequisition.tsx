// How many people a job will hire, the company's own reference for it, and the saved versions of its wording.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'

type Version = { versionNo: number; title: string; changedAt: string }
type View = { jobId: string; vacancies: number; requisitionRef: string | null; hired: number; stillOpen: number; status: string; versions: Version[] }
const unwrap = (r: { data?: { data?: View } & View }): View => (r.data?.data ?? r.data) as View
const message = (e: unknown, fallback: string): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback
const field = { padding: '5px 8px', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12 } as const

export default function RequisitionPanel({ jobId, canEdit }: { jobId: string; canEdit: boolean }) {
  const qc = useQueryClient()
  const key = ['rec-requisition', jobId]
  const [vac, setVac] = useState<string | null>(null)
  const [ref, setRef] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const q = useQuery<View>({ queryKey: key, queryFn: async () => unwrap(await apiClient.get(`/api/v1/recruiter/jobs/${jobId}/requisition`)), retry: false })
  const save = useMutation({
    mutationFn: async () => unwrap(await apiClient.put(`/api/v1/recruiter/jobs/${jobId}/requisition`, {
      vacancies: Number(vac ?? q.data?.vacancies), requisitionRef: (ref ?? q.data?.requisitionRef ?? '').trim() || null,
    })),
    onSuccess: v => { qc.setQueryData(key, v); qc.invalidateQueries({ queryKey: ['rec-jobs'] }); setVac(null); setRef(null); setError(null) },
    onError: e => setError(message(e, 'That could not be saved.')),
  })
  const v = q.data
  if (!v) return null
  const dirty = vac !== null || ref !== null
  return (
    <div style={{ border: '1px solid var(--hf-border)', borderRadius: 8, padding: 12, margin: '8px 0', fontSize: 12 }}>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
        <strong>Vacancies</strong>
        <input type="number" min={1} max={500} aria-label="Vacancies" disabled={!canEdit} value={vac ?? v.vacancies} onChange={e => setVac(e.target.value)} style={{ ...field, width: 64 }} />
        <input aria-label="Requisition reference" placeholder="Requisition ref" maxLength={40} disabled={!canEdit} value={ref ?? v.requisitionRef ?? ''} onChange={e => setRef(e.target.value)} style={{ ...field, width: 150 }} />
        {canEdit && <button disabled={!dirty || save.isPending} onClick={() => save.mutate()} style={{ ...field, cursor: 'pointer' }}>{save.isPending ? 'Saving...' : 'Save'}</button>}
        <span style={{ color: 'var(--hf-text-muted)' }}>{v.hired} hired, {v.stillOpen} still to fill</span>
      </div>
      {error && <div role="alert" style={{ marginTop: 6, color: 'var(--hf-danger-text)' }}>{error}</div>}
      {v.versions.length > 1 && (
        <details style={{ marginTop: 8 }}>
          <summary>Wording versions ({v.versions.length})</summary>
          {v.versions.map(x => (
            <div key={x.versionNo} style={{ marginTop: 4, color: 'var(--hf-text-muted)' }}>v{x.versionNo}, {x.changedAt.slice(0, 10)}: {x.title}</div>
          ))}
        </details>
      )}
    </div>
  )
}
