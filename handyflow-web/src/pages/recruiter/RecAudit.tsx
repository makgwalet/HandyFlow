// The audit trail: who did what in Recruiter and when. Request bodies are never copied into it, so candidates' details and notes are not in it.
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'

type Entry = { id: string; occurredAt: string; actorName: string | null; action: string; entityType: string; entityId: string | null; applicationId: string | null; detail: string | null }
type Page = { entries: Entry[]; hasMore: boolean }
const BASE = '/api/v1/recruiter/audit'
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
