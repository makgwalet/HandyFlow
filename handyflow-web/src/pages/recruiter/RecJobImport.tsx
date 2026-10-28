// Add candidates to a job from a CSV file. The file is checked first and nothing is saved until the person confirms.
import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { usePermission } from '../../hooks/usePermission'

type Result = { dryRun: boolean; lines: number; added: number; alreadyApplied: number; invalid: number; problems: { line: number; problem: string }[]; moreProblems: number }
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const field = { padding: '5px 8px', background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12 } as const
const message = (e: unknown): string => (e as { response?: { data?: { message?: string } } })?.response?.data?.message || 'That did not work. Try again.'

export default function RecJobImport({ jobId }: { jobId: string }) {
  const mayEdit = usePermission('RECRUITER_MANAGE')
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [csv, setCsv] = useState('')
  const [fileName, setFileName] = useState('')
  const [basis, setBasis] = useState(false)
  const [result, setResult] = useState<Result | null>(null)
  const [error, setError] = useState('')
  const base = `/api/v1/recruiter/jobs/${jobId}/import`
  const check = useMutation({
    mutationFn: async () => data<Result>(await apiClient.post(`${base}/check`, { csv, lawfulBasis: basis })),
    onSuccess: r => { setResult(r); setError('') }, onError: e => { setResult(null); setError(message(e)) },
  })
  const run = useMutation({
    mutationFn: async () => data<Result>(await apiClient.post(base, { csv, lawfulBasis: basis })),
    onSuccess: r => { setResult(r); setError(''); setCsv(''); setFileName(''); qc.invalidateQueries({ queryKey: ['rec-applications'] }); qc.invalidateQueries({ queryKey: ['rec-jobs'] }); qc.invalidateQueries({ queryKey: ['rec-dashboard'] }) },
    onError: e => setError(message(e)),
  })
  if (!mayEdit) return null
  const pick = async (f: File | undefined) => {
    setResult(null); setError('')
    if (!f) return
    if (f.size > 1_000_000) { setError('The file is too large (about 1 MB at most).'); return }
    setFileName(f.name); setCsv(await f.text())
  }
  const checked = result?.dryRun === true
  return (
    <div style={{ marginTop: 10, fontSize: 12 }}>
      <button style={{ ...field, cursor: 'pointer' }} aria-expanded={open} onClick={() => setOpen(!open)}>{open ? 'Hide import' : 'Import candidates from CSV'}</button>
      {open && (
        <div style={{ marginTop: 6, display: 'grid', gap: 6, maxWidth: 560 }}>
          <div style={{ color: 'var(--hf-text-muted)' }}>
            The first line names the columns: first name, last name (or one name column), email, and optionally phone, location, LinkedIn, portfolio. Up to 500 people.
            Nobody is emailed. People already on this job are skipped.
          </div>
          <input aria-label="CSV file" type="file" accept=".csv,text/csv" onChange={e => pick(e.target.files?.[0])} />
          <label style={{ display: 'flex', gap: 6, alignItems: 'flex-start' }}>
            <input type="checkbox" checked={basis} onChange={e => { setBasis(e.target.checked); setResult(null) }} />
            <span>I have a lawful reason to hold these people&apos;s details (for example they sent me their CV or agreed to be contacted).</span>
          </label>
          <div style={{ display: 'flex', gap: 6 }}>
            <button style={{ ...field, cursor: 'pointer' }} disabled={!csv || !basis || check.isPending} onClick={() => check.mutate()}>Check file</button>
            <button style={{ ...field, cursor: 'pointer' }} disabled={!checked || result!.added === 0 || run.isPending} onClick={() => run.mutate()}>
              {checked ? `Add ${result!.added} ${result!.added === 1 ? 'person' : 'people'}` : 'Add'}
            </button>
            {fileName && <span style={{ color: 'var(--hf-text-muted)', alignSelf: 'center' }}>{fileName}</span>}
          </div>
          {result && (
            <div role="status">
              {result.dryRun ? 'This file would add' : 'Added'} <strong>{result.added}</strong>; {result.alreadyApplied} already on this job; {result.invalid} could not be used.
              {result.problems.map(p => <div key={p.line} style={{ color: 'var(--hf-danger-text)' }}>Line {p.line}: {p.problem}</div>)}
              {result.moreProblems > 0 && <div style={{ color: 'var(--hf-text-muted)' }}>and {result.moreProblems} more.</div>}
            </div>
          )}
          {error && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{error}</div>}
        </div>
      )}
    </div>
  )
}
