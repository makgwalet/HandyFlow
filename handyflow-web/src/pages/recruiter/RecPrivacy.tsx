// Recruiting settings (approvals, how long unsuccessful applicants' details are kept) and the list of applicants whose period has ended.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useAuth } from '../../hooks/useAuth'
import { privacyUrlProblem } from '../careers/consent.logic'
import { fmtDay, recruiterRights, retentionProblem, type RecSettings, type RemovalCandidate, type RemovalResult } from './recApproval.logic'

const BASE = '/api/v1/recruiter'
const body = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const message = (e: unknown, fallback: string): string => (e as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback
const card = { border: '1px solid var(--hf-border)', borderRadius: 12, padding: 16, background: 'var(--hf-surface)', marginBottom: 16 } as const
const btn = { padding: '6px 14px', borderRadius: 7, fontSize: 12, fontWeight: 700, cursor: 'pointer', border: '1px solid var(--hf-border)', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-secondary)' } as const

export default function RecPrivacy() {
  const { user } = useAuth() as { user?: { permissions?: string[] } }
  const rights = recruiterRights(user?.permissions ?? [])
  if (!rights.read) return <p style={{ color: 'var(--hf-text-muted)' }}>You do not have permission to see these settings.</p>
  return (
    <div>
      <SettingsCard admin={rights.admin} />
      {rights.admin && <RetentionCard />}
      {rights.admin && <OldCvCard />}
    </div>
  )
}

function SettingsCard({ admin }: { admin: boolean }) {
  const qc = useQueryClient()
  const q = useQuery<RecSettings>({ queryKey: ['rec-settings'], queryFn: async () => body<RecSettings>(await apiClient.get(`${BASE}/settings`)) })
  const [draft, setDraft] = useState<{ job: boolean; offer: boolean; months: string; url: string } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const save = useMutation({
    mutationFn: async (d: { job: boolean; offer: boolean; months: string; url: string }) =>
      body<RecSettings>(await apiClient.put(`${BASE}/settings`, { requireJobApproval: d.job, requireOfferApproval: d.offer, retentionMonths: Number(d.months), privacyNoticeUrl: d.url.trim() })),
    onSuccess: s => { qc.setQueryData(['rec-settings'], s); qc.invalidateQueries({ queryKey: ['rec-retention'] }); qc.invalidateQueries({ queryKey: ['rec-approval'] }); setDraft(null); setError(null); setSaved(true) },
    onError: e => setError(message(e, 'The settings could not be saved.')),
  })
  if (q.isLoading) return <div style={card}>Loading…</div>
  if (!q.data) return <div style={card}>The settings could not be loaded.</div>
  const d = draft ?? { job: q.data.requireJobApproval, offer: q.data.requireOfferApproval, months: String(q.data.retentionMonths), url: q.data.privacyNoticeUrl ?? '' }
  const problem = retentionProblem(d.months) ?? privacyUrlProblem(d.url)
  const change = (patch: Partial<typeof d>) => { setSaved(false); setDraft({ ...d, ...patch }) }
  return (
    <section style={card} aria-label="Recruiting settings">
      <h3 style={{ margin: '0 0 8px', fontSize: 15 }}>Recruiting settings</h3>
      <label style={{ display: 'flex', gap: 8, fontSize: 13, marginBottom: 6 }}>
        <input type="checkbox" disabled={!admin} checked={d.job} onChange={e => change({ job: e.target.checked })} /> A job must be approved by someone else before it is published
      </label>
      <label style={{ display: 'flex', gap: 8, fontSize: 13, marginBottom: 10 }}>
        <input type="checkbox" disabled={!admin} checked={d.offer} onChange={e => change({ offer: e.target.checked })} /> An offer must be approved by someone else before the letter is sent
      </label>
      <label htmlFor="rec-months" style={{ fontSize: 13 }}>Keep unsuccessful applicants' details for (months)</label>
      <input id="rec-months" type="number" min={3} max={60} disabled={!admin} value={d.months} onChange={e => change({ months: e.target.value })}
        style={{ display: 'block', width: 90, margin: '4px 0 6px', padding: 6, border: '1px solid var(--hf-border)', borderRadius: 6, background: 'var(--hf-surface)', color: 'var(--hf-text)' }} />
      <label htmlFor="rec-privacy-url" style={{ fontSize: 13, display: 'block', marginTop: 6 }}>Privacy notice link (shown beside the consent text on your careers page)</label>
      <input id="rec-privacy-url" type="url" inputMode="url" placeholder="https://yourcompany.co.za/privacy" disabled={!admin} value={d.url} onChange={e => change({ url: e.target.value })}
        style={{ display: 'block', width: '100%', maxWidth: 420, margin: '4px 0 10px', padding: 6, border: '1px solid var(--hf-border)', borderRadius: 6, background: 'var(--hf-surface)', color: 'var(--hf-text)' }} />
      <p style={{ fontSize: 12, color: 'var(--hf-text-muted)', margin: '0 0 8px' }}>
        POPIA asks that personal information is not kept longer than it is needed. It does not name a number of months for job applicants, so this is your company's choice. Applicants with an open application, or who were hired, are never listed.
      </p>
      {admin && <button type="button" disabled={save.isPending || !draft} style={btn} onClick={() => { if (!problem) save.mutate(d); else setError(problem) }}>{save.isPending ? 'Saving…' : 'Save settings'}</button>}
      {saved && <span role="status" style={{ marginLeft: 10, fontSize: 12, color: 'var(--hf-success-text-strong)' }}>Saved.</span>}
      {error && <div role="alert" style={{ color: 'var(--hf-danger-text)', fontSize: 12, marginTop: 6 }}>{error}</div>}
    </section>
  )
}

function RetentionCard() {
  const qc = useQueryClient()
  const q = useQuery<RemovalCandidate[]>({ queryKey: ['rec-retention'], queryFn: async () => body<RemovalCandidate[]>(await apiClient.get(`${BASE}/retention`)) })
  const [chosen, setChosen] = useState<string[]>([])
  const [confirm, setConfirm] = useState(false)
  const [result, setResult] = useState<RemovalResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const remove = useMutation({
    mutationFn: async () => body<RemovalResult>(await apiClient.post(`${BASE}/retention/remove`, { applicantIds: chosen })),
    onSuccess: r => { setResult(r); setChosen([]); setConfirm(false); setError(null); qc.invalidateQueries({ queryKey: ['rec-retention'] }); qc.invalidateQueries({ queryKey: ['rec-applications'] }) },
    onError: e => { setError(message(e, 'The details could not be removed.')); setConfirm(false) },
  })
  const rows = q.data ?? []
  const toggle = (id: string) => setChosen(c => (c.includes(id) ? c.filter(x => x !== id) : [...c, id]))
  return (
    <section style={card} aria-label="Applicant data to remove">
      <h3 style={{ margin: '0 0 8px', fontSize: 15 }}>Applicants whose retention period has ended</h3>
      {q.isLoading && <p>Loading…</p>}
      {q.isError && <p role="alert" style={{ color: 'var(--hf-danger-text)' }}>The list could not be loaded.</p>}
      {q.data && rows.length === 0 && <p style={{ fontSize: 13, color: 'var(--hf-text-muted)' }}>Nobody is past the retention period.</p>}
      {rows.length > 0 && (
        <>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead><tr style={{ textAlign: 'left', fontSize: 11, color: 'var(--hf-text-muted)' }}><th /><th>Applicant</th><th>Last activity</th><th>Applications</th><th>CV</th></tr></thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.applicantId} style={{ borderTop: '1px solid var(--hf-border-subtle)' }}>
                  <td><input type="checkbox" aria-label={`Choose ${r.name}`} checked={chosen.includes(r.applicantId)} onChange={() => toggle(r.applicantId)} /></td>
                  <td>{r.name}<div style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>{r.email}</div></td>
                  <td>{fmtDay(r.lastActivity)}</td><td>{r.applications}</td><td>{r.hasCv ? 'Yes' : 'No'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div style={{ marginTop: 10, display: 'flex', gap: 8, alignItems: 'center' }}>
            <button type="button" style={btn} onClick={() => setChosen(chosen.length === rows.length ? [] : rows.map(r => r.applicantId))}>{chosen.length === rows.length ? 'Clear' : 'Choose all'}</button>
            <button type="button" disabled={chosen.length === 0} style={{ ...btn, background: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)' }} onClick={() => setConfirm(true)}>Remove details of {chosen.length}…</button>
          </div>
        </>
      )}
      {confirm && (
        <div role="alertdialog" aria-label="Confirm removal" style={{ marginTop: 10, padding: 10, border: '1.5px solid var(--hf-danger-text)', borderRadius: 8 }}>
          <p style={{ margin: '0 0 8px', fontSize: 13 }}>
            This removes the name, email, phone, links and CV of {chosen.length} applicant(s), and the notes written about them. It cannot be undone. Their applications stay, without the person, so your figures still add up.
          </p>
          <button type="button" disabled={remove.isPending} style={{ ...btn, background: 'var(--hf-danger-text)', color: 'var(--hf-text-on-solid)' }} onClick={() => remove.mutate()}>{remove.isPending ? 'Removing…' : 'Remove permanently'}</button>
          <button type="button" style={{ ...btn, marginLeft: 8 }} onClick={() => setConfirm(false)}>Cancel</button>
        </div>
      )}
      {result && (
        <div role="status" style={{ marginTop: 10, fontSize: 13 }}>
          {result.removed} removed.
          {result.refused.length > 0 && <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>{result.refused.map(f => <li key={f.applicantId}>{f.reason}</li>)}</ul>}
        </div>
      )}
      {error && <div role="alert" style={{ color: 'var(--hf-danger-text)', fontSize: 12, marginTop: 6 }}>{error}</div>}
    </section>
  )
}

/** CVs saved before file storage existed are moved into it, a few at a time, so every CV lives in one place. A CV that is not a readable PDF is left as it was. */
function OldCvCard() {
  const [running, setRunning] = useState(false)
  const [moved, setMoved] = useState<number | null>(null)
  const [unreadable, setUnreadable] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const run = async () => {
    setRunning(true); setError(null); setMoved(0); setUnreadable(0)
    let after: string | null = null, totalMoved = 0, totalUnreadable = 0
    try {
      for (let round = 0; round < 1000; round++) {
        const r: { moved: number; unreadable: number; done: boolean; nextAfter: string | null } =
          body(await apiClient.post(`${BASE}/cv/migrate-legacy${after ? `?after=${after}&limit=25` : '?limit=25'}`))
        totalMoved += r.moved; totalUnreadable += r.unreadable
        setMoved(totalMoved); setUnreadable(totalUnreadable)
        if (r.done || !r.nextAfter) break
        after = r.nextAfter
      }
    } catch (e) {
      setError(message(e, 'Moving the CVs stopped. What was moved is kept; you can start again.'))
    } finally {
      setRunning(false)
    }
  }
  return (
    <section style={card}>
      <h3 style={{ margin: '0 0 4px', fontSize: 14 }}>Move old CVs into file storage</h3>
      <p style={{ margin: '0 0 10px', fontSize: 12, color: 'var(--hf-text-muted)' }}>
        CVs uploaded a long time ago are kept inside the applicant record. This moves them into file storage like every newer CV. It is safe to run again, and any CV that cannot be read as a PDF is left as it is.
      </p>
      <button type="button" style={btn} disabled={running} onClick={run}>{running ? 'Moving…' : 'Move old CVs'}</button>
      {moved !== null && <span role="status" style={{ marginLeft: 10, fontSize: 12 }}>{moved} moved{unreadable > 0 ? `, ${unreadable} could not be read and were left alone` : ''}{running ? '' : '.'}</span>}
      {error && <div role="alert" style={{ color: 'var(--hf-danger-text)', fontSize: 12, marginTop: 6 }}>{error}</div>}
    </section>
  )
}
