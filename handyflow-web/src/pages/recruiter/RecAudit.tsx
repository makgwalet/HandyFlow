// The audit trail: who did what in Recruiter and when. Request bodies are never copied into it, so candidates' details and notes are not in it.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'

type Entry = { id: string; occurredAt: string; actorName: string | null; action: string; entityType: string; entityId: string | null; applicationId: string | null; detail: string | null }
type Page = { entries: Entry[]; hasMore: boolean }
type CvRow = { actorName: string; views: number; candidates: number; busiestHour: string; busiestHourViews: number; flagged: boolean }
type Failed = { id: string; kind: string; toEmail: string; subject: string; attempts: number; lastError: string | null; createdAt: string }
type Outbox = { pending: number; sent: number; failed: number; failures: Failed[] }
const BASE = '/api/v1/recruiter/audit'
const OUTBOX = '/api/v1/recruiter/outbox'
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const field = { padding: '6px 9px', background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12 } as const
const words = (a: string) => { const s = a.toLowerCase().replace(/_/g, ' '); return s.charAt(0).toUpperCase() + s.slice(1) }
const when = (iso: string) => new Date(iso).toLocaleString('en-ZA', { timeZone: 'Africa/Johannesburg', dateStyle: 'medium', timeStyle: 'short' })

export default function RecAudit() {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [action, setAction] = useState('')
  const [page, setPage] = useState(0)
  const [exportError, setExportError] = useState('')
  const actions = useQuery<string[]>({ queryKey: ['rec-audit-actions'], queryFn: async () => data(await apiClient.get(`${BASE}/actions`)) })
  const cv = useQuery<CvRow[]>({ queryKey: ['rec-cv-access'], queryFn: async () => data(await apiClient.get(`${BASE}/cv-access?days=30`)), retry: false })
  const qc = useQueryClient()
  const outbox = useQuery<Outbox>({ queryKey: ['rec-outbox'], queryFn: async () => data(await apiClient.get(OUTBOX)), retry: false })
  const retry = useMutation({
    mutationFn: async (id: string) => apiClient.post(`${OUTBOX}/${id}/retry`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['rec-outbox'] }),
  })
  const params = () => { const p = new URLSearchParams(); if (from) p.set('from', from); if (to) p.set('to', to); if (action) p.set('action', action); return p }
  const q = useQuery<Page>({
    queryKey: ['rec-audit', from, to, action, page],
    queryFn: async () => { const p = params(); p.set('page', String(page)); return data(await apiClient.get(`${BASE}?${p}`)) },
    retry: false,
  })
  const reset = (fn: () => void) => { fn(); setPage(0) }
  const download = async () => {
    setExportError('')
    try {
      const r = await apiClient.get(`${BASE}/export.csv?${params()}`, { responseType: 'blob' })
      const url = URL.createObjectURL(new Blob([r.data], { type: 'text/csv;charset=utf-8' }))
      const a = document.createElement('a'); a.href = url; a.download = 'recruiter-audit.csv'; a.click(); URL.revokeObjectURL(url)
    } catch { setExportError('The export could not be made.') }
  }
  const err = (q.error as { response?: { data?: { message?: string } } } | null)?.response?.data?.message

  return (
    <div style={{ fontSize: 12 }}>
      {outbox.data && (outbox.data.failed > 0 || outbox.data.pending > 0) && (
        <div style={{ border: '1px solid var(--hf-border)', borderRadius: 8, padding: 12, marginBottom: 14 }}>
          <strong>Candidate emails</strong>
          <div style={{ marginTop: 4, color: 'var(--hf-text-muted)' }}>{outbox.data.pending} waiting to send. {outbox.data.failed} could not be delivered.</div>
          {retry.isError && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>That email could not be queued again.</div>}
          {outbox.data.failures.map(f => (
            <div key={f.id} style={{ marginTop: 8, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
              <span style={{ color: 'var(--hf-danger-text)' }}>{f.subject} to {f.toEmail}</span>
              <span style={{ color: 'var(--hf-text-muted)' }}>{f.attempts} attempts{f.lastError ? `: ${f.lastError}` : ''}</span>
              <button style={{ ...field, cursor: 'pointer' }} disabled={retry.isPending} onClick={() => retry.mutate(f.id)}>Try again</button>
            </div>
          ))}
        </div>
      )}
      {cv.data && cv.data.length > 0 && (
        <div style={{ border: '1px solid var(--hf-border)', borderRadius: 8, padding: 12, marginBottom: 14 }}>
          <strong>Who opened CVs in the last 30 days</strong>
          {cv.data.map(r => (
            <div key={r.actorName} style={{ marginTop: 6, color: r.flagged ? 'var(--hf-danger-text)' : undefined }}>
              {r.actorName}: {r.views} view{r.views === 1 ? '' : 's'} of {r.candidates} candidate{r.candidates === 1 ? '' : 's'}
              {r.flagged && `. ${r.busiestHourViews} in one hour on ${when(r.busiestHour)}. Worth asking why.`}
            </div>
          ))}
        </div>
      )}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', marginBottom: 10 }}>
        <label>From <input type="date" value={from} max={to || undefined} onChange={e => reset(() => setFrom(e.target.value))} style={field} /></label>
        <label>To <input type="date" value={to} min={from || undefined} onChange={e => reset(() => setTo(e.target.value))} style={field} /></label>
        <select aria-label="Action" value={action} onChange={e => reset(() => setAction(e.target.value))} style={field}>
          <option value="">All actions</option>
          {(actions.data ?? []).map(a => <option key={a} value={a}>{words(a)}</option>)}
        </select>
        <button style={{ ...field, cursor: 'pointer' }} onClick={download}>Export CSV</button>
        {!from && !to && <span style={{ color: 'var(--hf-text-muted)' }}>Last 90 days</span>}
      </div>
      {err && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{err}</div>}
      {exportError && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{exportError}</div>}
      <table style={{ borderCollapse: 'collapse', width: '100%' }}>
        <thead><tr>{['When', 'Who', 'Action', 'Record', 'Detail'].map(h => <th key={h} style={{ textAlign: 'left', padding: '6px 8px', borderBottom: '1px solid var(--hf-border)' }}>{h}</th>)}</tr></thead>
        <tbody>
          {q.data && q.data.entries.length === 0 && <tr><td colSpan={5} style={{ padding: 10, color: 'var(--hf-text-muted)' }}>Nothing recorded for this range.</td></tr>}
          {(q.data?.entries ?? []).map(e => (
            <tr key={e.id}>
              <td style={{ padding: '6px 8px', borderBottom: '1px solid var(--hf-border)', whiteSpace: 'nowrap' }}>{when(e.occurredAt)}</td>
              <td style={{ padding: '6px 8px', borderBottom: '1px solid var(--hf-border)' }}>{e.actorName ?? '-'}</td>
              <td style={{ padding: '6px 8px', borderBottom: '1px solid var(--hf-border)' }}>{words(e.action)}</td>
              <td style={{ padding: '6px 8px', borderBottom: '1px solid var(--hf-border)' }}>{words(e.entityType)}{e.entityId ? ` ${e.entityId.slice(0, 8)}` : ''}</td>
              <td style={{ padding: '6px 8px', borderBottom: '1px solid var(--hf-border)', color: 'var(--hf-text-muted)' }}>{e.detail ?? ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div style={{ marginTop: 8, display: 'flex', gap: 8 }}>
        <button style={{ ...field, cursor: 'pointer' }} disabled={page === 0} onClick={() => setPage(p => p - 1)}>Newer</button>
        <button style={{ ...field, cursor: 'pointer' }} disabled={!q.data?.hasMore} onClick={() => setPage(p => p + 1)}>Older</button>
      </div>
    </div>
  )
}
