// Placements: candidates hired onto a client's jobs, with what each placement earned.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { usePermission } from '../../hooks/usePermission'

type Row = { applicationId: string; candidate: string; jobTitle: string; clientName: string; hiredAt: string; startDate: string | null; fee: number | null; onHr: boolean }
type Result = { days: number; count: number; feeTotal: number; withoutFee: number; rows: Row[] }
const BASE = '/api/v1/recruiter'
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const field = { padding: '6px 9px', background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12 } as const
const th = { textAlign: 'left', padding: '6px 8px', borderBottom: '1px solid var(--hf-border)', fontWeight: 600 } as const
const td = { padding: '6px 8px', borderBottom: '1px solid var(--hf-border-subtle)' } as const
const rand = (n: number) => new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR', maximumFractionDigits: 2 }).format(n)
const day = (iso: string) => new Date(iso).toLocaleDateString('en-ZA', { timeZone: 'Africa/Johannesburg', day: 'numeric', month: 'short', year: 'numeric' })
const message = (e: unknown): string => (e as { response?: { data?: { message?: string } } })?.response?.data?.message || 'That did not work. Try again.'

function FeeCell({ row }: { row: Row }) {
  const qc = useQueryClient()
  const may = usePermission('RECRUITER_MANAGE')
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(row.fee == null ? '' : String(row.fee))
  const [error, setError] = useState('')
  const save = useMutation({
    mutationFn: () => apiClient.put(`${BASE}/placements/${row.applicationId}/fee`, { fee: value.trim() === '' ? null : Number(value) }),
    onSuccess: () => { setEditing(false); setError(''); qc.invalidateQueries({ queryKey: ['rec-placements'] }) },
    onError: e => setError(message(e)),
  })
  if (!editing) {
    return <span>{row.fee == null ? <span style={{ color: 'var(--hf-text-muted)' }}>Not recorded</span> : rand(row.fee)} {may && <button style={{ ...field, cursor: 'pointer', marginLeft: 6 }} onClick={() => setEditing(true)}>{row.fee == null ? 'Add' : 'Change'}</button>}</span>
  }
  return (
    <span>
      <input aria-label={`Fee for ${row.candidate}`} inputMode="decimal" value={value} onChange={e => setValue(e.target.value)} style={{ ...field, width: 110 }} />{' '}
      <button style={{ ...field, cursor: 'pointer' }} disabled={save.isPending || (value.trim() !== '' && Number.isNaN(Number(value)))} onClick={() => save.mutate()}>Save</button>{' '}
      <button style={{ ...field, cursor: 'pointer' }} onClick={() => { setEditing(false); setError('') }}>Cancel</button>
      {error && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{error}</div>}
    </span>
  )
}

export default function RecPlacements() {
  const [days, setDays] = useState(30)
  const q = useQuery<Result>({ queryKey: ['rec-placements', days], queryFn: async () => data(await apiClient.get(`${BASE}/placements?days=${days}`)), retry: false })
  const d = q.data
  return (
    <div style={{ fontSize: 12, display: 'grid', gap: 12 }}>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
        <select aria-label="Period" value={days} onChange={e => setDays(Number(e.target.value))} style={field}>
          <option value={7}>Last 7 days</option><option value={30}>Last 30 days</option><option value={90}>Last 90 days</option>
        </select>
        {d && <span><strong>{d.count}</strong> placement{d.count === 1 ? '' : 's'}, <strong>{rand(d.feeTotal)}</strong> in fees recorded{d.withoutFee > 0 ? `, ${d.withoutFee} without a fee yet` : ''}</span>}
      </div>
      {q.isError && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{message(q.error)}</div>}
      <div style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 10, overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr>{['Candidate', 'Job', 'Client', 'Hired', 'Start date', 'Fee'].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
          <tbody>
            {d && d.rows.length === 0 && <tr><td colSpan={6} style={{ ...td, color: 'var(--hf-text-muted)' }}>No placements in this period. A placement is a hire on a job that is tied to a client.</td></tr>}
            {(d?.rows ?? []).map(r => (
              <tr key={r.applicationId}>
                <td style={td}><strong>{r.candidate}</strong></td><td style={td}>{r.jobTitle}</td><td style={td}>{r.clientName}</td>
                <td style={td}>{day(r.hiredAt)}</td><td style={td}>{r.startDate ? day(r.startDate + 'T12:00:00') : ''}</td>
                <td style={td}><FeeCell row={r} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
