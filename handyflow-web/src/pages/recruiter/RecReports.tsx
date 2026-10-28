// Recruiting reports. Each one is a table with its definition shown above it, so the figures mean the same thing here and in the export.
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'

type Info = { id: string; title: string; definition: string; usesDateRange: boolean }
type Table = { id: string; title: string; definition: string; from: string | null; to: string | null; columns: string[]; rows: (string | number | null)[][] }
const BASE = '/api/v1/recruiter/reports'
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const field = { padding: '6px 9px', background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12 } as const
const errorText = (e: unknown): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message || 'The report could not be run.'

export default function RecReports() {
  const [id, setId] = useState('funnel')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [exportError, setExportError] = useState('')
  const list = useQuery<Info[]>({ queryKey: ['rec-report-list'], queryFn: async () => data(await apiClient.get(BASE)) })
  const report = useQuery<Table>({
    queryKey: ['rec-report', id, from, to],
    queryFn: async () => {
      const p = new URLSearchParams()
      if (from) p.set('from', from)
      if (to) p.set('to', to)
      return data(await apiClient.get(`${BASE}/${id}?${p}`))
    },
    retry: false,
  })
  const info = (list.data ?? []).find(r => r.id === id)

  const download = async () => {
    setExportError('')
    try {
      const p = new URLSearchParams()
      if (from) p.set('from', from)
      if (to) p.set('to', to)
      const r = await apiClient.get(`${BASE}/${id}/export.csv?${p}`, { responseType: 'blob' })
      const url = URL.createObjectURL(new Blob([r.data], { type: 'text/csv;charset=utf-8' }))
      const a = document.createElement('a')
      a.href = url; a.download = `recruiter-${id}.csv`; a.click()
      URL.revokeObjectURL(url)
    } catch { setExportError('The export could not be made.') }
  }

  return (
    <div style={{ fontSize: 12 }}>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', marginBottom: 10 }}>
        <select aria-label="Report" value={id} onChange={e => setId(e.target.value)} style={field}>
          {(list.data ?? []).map(r => <option key={r.id} value={r.id}>{r.title}</option>)}
        </select>
        {info?.usesDateRange !== false && (
          <>
            <label>From <input type="date" value={from} max={to || undefined} onChange={e => setFrom(e.target.value)} style={field} /></label>
            <label>To <input type="date" value={to} min={from || undefined} onChange={e => setTo(e.target.value)} style={field} /></label>
            <span style={{ color: 'var(--hf-text-muted)' }}>{from || to ? '' : 'Last 90 days'}</span>
          </>
        )}
        <button style={{ ...field, cursor: 'pointer' }} disabled={!report.data} onClick={download}>Export CSV</button>
      </div>
      {info && <p style={{ color: 'var(--hf-text-muted)', margin: '0 0 10px', maxWidth: 760 }}>{info.definition}</p>}
      {report.isError && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{errorText(report.error)}</div>}
      {exportError && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{exportError}</div>}
      {report.data && (
        <>
          {report.data.from && <div style={{ color: 'var(--hf-text-muted)', marginBottom: 6 }}>{report.data.from} to {report.data.to}</div>}
          <table style={{ borderCollapse: 'collapse', width: '100%' }}>
            <thead>
              <tr>{report.data.columns.map(c => <th key={c} style={{ textAlign: 'left', padding: '6px 8px', borderBottom: '1px solid var(--hf-border)' }}>{c}</th>)}</tr>
            </thead>
            <tbody>
              {report.data.rows.length === 0 && <tr><td colSpan={report.data.columns.length} style={{ padding: 10, color: 'var(--hf-text-muted)' }}>Nothing to show for this range.</td></tr>}
              {report.data.rows.map((row, i) => (
                <tr key={i}>{row.map((v, j) => <td key={j} style={{ padding: '6px 8px', borderBottom: '1px solid var(--hf-border)' }}>{v === null || v === '' ? '-' : String(v)}</td>)}</tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  )
}
