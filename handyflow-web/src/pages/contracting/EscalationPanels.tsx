// src/pages/contracting/EscalationPanels.tsx
//
// Approval escalation screens. Backend: /api/v1/contracts/approval-escalation.
//  • Settings: reminders and escalation, on the working clock or around the clock (administrators change it).
//  • Closure days and time away (out of office, with someone to cover).
//  • Response times: how long decisions take, and what is waiting past the target.
//  • Reassign: say who is reminded first about a waiting request. It routes the reminders; it does not stop anyone else deciding it.
// The engine only follows requests up. Nothing here approves or rejects.

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { usePermission } from '../../hooks/usePermission'
import { ACTION_LABEL, type Approval } from './approvalsShared'
import { useUsers, userName } from './useContractUsers'
import {
  DAYS, absenceProblem, hoursInWords, isoToday, minutesInWords, settingsProblem,
  type Absence, type ClosureDay, type DayCode, type EscalationSettings, type SlaReport,
} from './escalationShared'

const BASE = '/api/v1/contracts/approval-escalation'
const card: React.CSSProperties = { background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: 16 }
const field: React.CSSProperties = { padding: '7px 9px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, background: 'var(--hf-surface)', boxSizing: 'border-box' }
const muted: React.CSSProperties = { fontSize: 12, color: 'var(--hf-text-muted)' }
const btn = (primary = false): React.CSSProperties => ({
  padding: '7px 14px', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer',
  border: primary ? 'none' : '1.5px solid var(--hf-border)', background: primary ? 'var(--hf-primary-text)' : 'var(--hf-surface)', color: primary ? '#fff' : 'var(--hf-text)',
})


function errMsg(e: unknown): string {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? 'Something went wrong'
}
const day = (d: string) => new Date(`${d}T00:00:00`).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })
const data = <T,>(r: { data?: { data?: T } }): T | undefined => r.data?.data

// ── reassign ────────────────────────────────────────────────────────────────

export function ReassignControl({ a }: { a: Approval }) {
  const qc = useQueryClient()
  const { users, nameOf } = useUsers()
  const [open, setOpen] = useState(false)
  const [to, setTo] = useState(a.assignedTo ?? '')
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/approvals/${a.id}/reassign`, { userId: to || null, note: note.trim() || null }),
    onSuccess: () => { setOpen(false); setNote(''); qc.invalidateQueries({ queryKey: ['contract-approvals'] }) },
    onError: e => setError(errMsg(e)),
  })
  if (!open) return <button style={btn()} onClick={() => setOpen(true)}>{a.assignedTo ? 'Reassign' : 'Assign'}</button>
  return (
    <div style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid var(--hf-border)', width: '100%' }}>
      <label style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>Remind first
        <select style={{ ...field, display: 'block', width: '100%', marginTop: 4 }} value={to} onChange={e => setTo(e.target.value)}>
          <option value="">Everyone who can approve it</option>
          {users.filter(u => u.id !== a.requestedBy).map(u => <option key={u.id} value={u.id}>{userName(u)}</option>)}
        </select>
      </label>
      <input style={{ ...field, width: '100%', marginTop: 6 }} maxLength={500} placeholder="Note for them (optional)" value={note} onChange={e => setNote(e.target.value)} />
      <div style={{ ...muted, marginTop: 6 }}>
        {a.assignedTo ? `Now with ${nameOf(a.assignedTo)}. ` : ''}This only decides who is reminded first. Anyone with the right role can still decide it.
      </div>
      {error && <div role="alert" style={{ marginTop: 6, fontSize: 12, color: 'var(--hf-danger-text)' }}>{error}</div>}
      <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
        <button style={btn(true)} disabled={save.isPending} onClick={() => { setError(null); save.mutate() }}>Save</button>
        <button style={btn()} onClick={() => setOpen(false)}>Cancel</button>
      </div>
    </div>
  )
}

// ── settings, closure days, time away ───────────────────────────────────────

function SettingsForm({ initial, onSaved }: { initial: EscalationSettings; onSaved: () => void }) {
  const { users } = useUsers()
  const [s, setS] = useState<EscalationSettings>(initial)
  const [error, setError] = useState<string | null>(null)
  const problem = settingsProblem(s)
  const save = useMutation({
    mutationFn: () => apiClient.put(`${BASE}/settings`, s),
    onSuccess: () => { setError(null); onSaved() },
    onError: e => setError(errMsg(e)),
  })
  const set = <K extends keyof EscalationSettings>(k: K, v: EscalationSettings[K]) => setS(p => ({ ...p, [k]: v }))
  const num = (k: 'reminder1Hours' | 'reminder2Hours' | 'escalateHours', label: string) => (
    <label style={{ fontSize: 13 }}>{label}
      <input style={{ ...field, display: 'block', width: 90, marginTop: 4 }} type="number" min={1} max={720} value={s[k]}
        onChange={e => set(k, Number(e.target.value))} />
      <span style={muted}>{hoursInWords(s[k], s)}</span>
    </label>
  )
  const toggleDay = (c: DayCode) => set('workDays', s.workDays.includes(c) ? s.workDays.filter(x => x !== c) : [...s.workDays, c])
  const toggleUser = (id: string) => set('escalationUserIds', s.escalationUserIds.includes(id) ? s.escalationUserIds.filter(x => x !== id) : [...s.escalationUserIds, id])

  return (
    <div style={{ display: 'grid', gap: 12 }}>
      <label style={{ fontSize: 13, fontWeight: 600 }}>
        <input type="checkbox" checked={s.enabled} onChange={e => set('enabled', e.target.checked)} /> Follow up requests that are waiting
      </label>
      <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
        {num('reminder1Hours', 'First reminder after')}
        {num('reminder2Hours', 'Second reminder after')}
        {num('escalateHours', 'Escalate after')}
      </div>
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', alignItems: 'center' }}>
        <label style={{ fontSize: 13 }}>Count waiting time
          <select style={{ ...field, display: 'block', marginTop: 4 }} value={s.clockMode} onChange={e => set('clockMode', e.target.value as EscalationSettings['clockMode'])}>
            <option value="BUSINESS_HOURS">Only in working hours</option>
            <option value="CALENDAR">Around the clock</option>
          </select>
        </label>
        {s.clockMode === 'BUSINESS_HOURS' && (
          <>
            <label style={{ fontSize: 13 }}>From
              <input style={{ ...field, display: 'block', marginTop: 4 }} type="time" value={s.workStart.slice(0, 5)} onChange={e => set('workStart', e.target.value)} />
            </label>
            <label style={{ fontSize: 13 }}>Until
              <input style={{ ...field, display: 'block', marginTop: 4 }} type="time" value={s.workEnd.slice(0, 5)} onChange={e => set('workEnd', e.target.value)} />
            </label>
          </>
        )}
      </div>
      {s.clockMode === 'BUSINESS_HOURS' && (
        <div>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {DAYS.map(d => (
              <label key={d.code} style={{ fontSize: 13 }}>
                <input type="checkbox" checked={s.workDays.includes(d.code)} onChange={() => toggleDay(d.code)} /> {d.short}
              </label>
            ))}
          </div>
          <label style={{ fontSize: 13, display: 'block', marginTop: 8 }}>
            <input type="checkbox" checked={s.skipPublicHolidays} onChange={e => set('skipPublicHolidays', e.target.checked)} /> Public holidays do not count
          </label>
        </div>
      )}
      <fieldset style={{ border: '1px solid var(--hf-border)', borderRadius: 8, padding: 10 }}>
        <legend style={{ fontSize: 12, fontWeight: 600 }}>When it escalates, tell</legend>
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', maxHeight: 120, overflow: 'auto' }}>
          {users.map(u => (
            <label key={u.id} style={{ fontSize: 13 }}>
              <input type="checkbox" checked={s.escalationUserIds.includes(u.id)} onChange={() => toggleUser(u.id)} /> {userName(u)}
            </label>
          ))}
        </div>
        <div style={{ ...muted, marginTop: 6 }}>If no one is chosen, or they are all away, the contracts administrators are told. The person who asked is told too.</div>
      </fieldset>
      <div style={muted}>
        Reminders go to the person it is assigned to, or to everyone who can approve it. Time before this is switched on does not count, so turning it on does not chase old requests.
        Nothing is ever approved automatically.
      </div>
      {(problem || error) && <div role="alert" style={{ fontSize: 12, color: 'var(--hf-danger-text)' }}>{problem ?? error}</div>}
      <div><button style={btn(true)} disabled={!!problem || save.isPending} onClick={() => { setError(null); save.mutate() }}>Save settings</button></div>
    </div>
  )
}

function ClosureDays({ isAdmin }: { isAdmin: boolean }) {
  const qc = useQueryClient()
  const [d, setD] = useState('')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { data: rows = [] } = useQuery<ClosureDay[]>({
    queryKey: ['contract-escalation', 'closures'],
    queryFn: async () => data<ClosureDay[]>(await apiClient.get(`${BASE}/closure-days`)) ?? [],
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ['contract-escalation', 'closures'] })
  const add = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/closure-days`, { day: d, reason: reason.trim() || null }),
    onSuccess: () => { setD(''); setReason(''); setError(null); refresh() }, onError: e => setError(errMsg(e)),
  })
  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`${BASE}/closure-days/${id}`),
    onSuccess: refresh, onError: e => setError(errMsg(e)),
  })
  return (
    <div>
      <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4 }}>Days the organisation is closed</div>
      <div style={{ ...muted, marginBottom: 8 }}>These do not count towards waiting time, on top of public holidays.</div>
      {rows.length === 0 && <div style={muted}>None coming up.</div>}
      {rows.map(c => (
        <div key={c.id} style={{ display: 'flex', gap: 10, alignItems: 'center', fontSize: 13, padding: '4px 0' }}>
          <span>{day(c.day)}</span><span style={muted}>{c.reason}</span>
          {isAdmin && <button style={{ ...btn(), padding: '3px 9px' }} disabled={remove.isPending} onClick={() => remove.mutate(c.id)}>Remove</button>}
        </div>
      ))}
      {isAdmin && (
        <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
          <input style={field} type="date" min={isoToday()} value={d} onChange={e => setD(e.target.value)} aria-label="Closed on" />
          <input style={{ ...field, flex: 1, minWidth: 140 }} maxLength={200} placeholder="Reason (optional)" value={reason} onChange={e => setReason(e.target.value)} />
          <button style={btn()} disabled={!d || add.isPending} onClick={() => { setError(null); add.mutate() }}>Add</button>
        </div>
      )}
      {error && <div role="alert" style={{ marginTop: 6, fontSize: 12, color: 'var(--hf-danger-text)' }}>{error}</div>}
    </div>
  )
}

function TimeAway({ isAdmin }: { isAdmin: boolean }) {
  const qc = useQueryClient()
  const { users, nameOf } = useUsers()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [who, setWho] = useState('')
  const [cover, setCover] = useState('')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { data: rows = [] } = useQuery<Absence[]>({
    queryKey: ['contract-escalation', 'absences'],
    queryFn: async () => data<Absence[]>(await apiClient.get(`${BASE}/absences`)) ?? [],
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ['contract-escalation', 'absences'] })
  const problem = from || to ? absenceProblem(from, to, cover, who) : null
  const add = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/absences`, { userId: who || null, from, to, delegateUserId: cover || null, reason: reason.trim() || null }),
    onSuccess: () => { setFrom(''); setTo(''); setWho(''); setCover(''); setReason(''); setError(null); refresh() }, onError: e => setError(errMsg(e)),
  })
  const cancel = useMutation({
    mutationFn: (id: string) => apiClient.delete(`${BASE}/absences/${id}`),
    onSuccess: refresh, onError: e => setError(errMsg(e)),
  })
  return (
    <div>
      <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4 }}>Out of office</div>
      <div style={{ ...muted, marginBottom: 8 }}>Reminders skip someone who is away and go to whoever covers for them, or to the other approvers.</div>
      {rows.length === 0 && <div style={muted}>No one is away or has time away coming up.</div>}
      {rows.map(a => (
        <div key={a.id} style={{ display: 'flex', gap: 10, alignItems: 'center', fontSize: 13, padding: '4px 0', flexWrap: 'wrap' }}>
          <strong>{nameOf(a.userId)}</strong><span>{day(a.from)} to {day(a.to)}</span>
          {a.delegateUserId && <span style={muted}>covered by {nameOf(a.delegateUserId)}</span>}
          <button style={{ ...btn(), padding: '3px 9px' }} disabled={cancel.isPending} onClick={() => cancel.mutate(a.id)}>Cancel</button>
        </div>
      ))}
      <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}>
        {isAdmin && (
          <label style={{ fontSize: 12 }}>Who
            <select style={{ ...field, display: 'block' }} value={who} onChange={e => setWho(e.target.value)}>
              <option value="">Me</option>
              {users.map(u => <option key={u.id} value={u.id}>{userName(u)}</option>)}
            </select>
          </label>
        )}
        <label style={{ fontSize: 12 }}>First day<input style={{ ...field, display: 'block' }} type="date" value={from} onChange={e => setFrom(e.target.value)} /></label>
        <label style={{ fontSize: 12 }}>Last day<input style={{ ...field, display: 'block' }} type="date" min={from || undefined} value={to} onChange={e => setTo(e.target.value)} /></label>
        <label style={{ fontSize: 12 }}>Covered by
          <select style={{ ...field, display: 'block' }} value={cover} onChange={e => setCover(e.target.value)}>
            <option value="">No one in particular</option>
            {users.map(u => <option key={u.id} value={u.id}>{userName(u)}</option>)}
          </select>
        </label>
        <input style={{ ...field, flex: 1, minWidth: 120 }} maxLength={200} placeholder="Reason (optional)" value={reason} onChange={e => setReason(e.target.value)} />
        <button style={btn()} disabled={!!problem || !from || !to || add.isPending} onClick={() => { setError(null); add.mutate() }}>Record</button>
      </div>
      {(problem || error) && <div role="alert" style={{ marginTop: 6, fontSize: 12, color: 'var(--hf-danger-text)' }}>{problem ?? error}</div>}
    </div>
  )
}

export function EscalationSettingsPanel() {
  const qc = useQueryClient()
  const isAdmin = usePermission('CONTRACTS_ADMIN')
  const [open, setOpen] = useState(false)
  const { data: settings } = useQuery<EscalationSettings | undefined>({
    queryKey: ['contract-escalation', 'settings'],
    queryFn: async () => data<EscalationSettings>(await apiClient.get(`${BASE}/settings`)),
  })
  return (
    <div style={card}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
        <div>
          <div style={{ fontWeight: 700 }}>Reminders and escalation</div>
          <div style={muted}>
            {settings?.enabled
              ? `On. Reminded after ${hoursInWords(settings.reminder1Hours, settings)} and ${hoursInWords(settings.reminder2Hours, settings)}; escalated after ${hoursInWords(settings.escalateHours, settings)}.`
              : 'Off. Waiting requests are not followed up.'}
          </div>
        </div>
        <button style={btn()} onClick={() => setOpen(o => !o)}>{open ? 'Close' : 'Settings'}</button>
      </div>
      {open && settings && (
        <div style={{ display: 'grid', gap: 18, marginTop: 14, paddingTop: 14, borderTop: '1px solid var(--hf-border)' }}>
          {isAdmin ? (
            <SettingsForm key={settings.enabledSince ?? 'new'} initial={settings} onSaved={() => qc.invalidateQueries({ queryKey: ['contract-escalation', 'settings'] })} />
          ) : <div style={muted}>Only a contracts administrator can change these settings.</div>}
          <ClosureDays isAdmin={isAdmin} />
          <TimeAway isAdmin={isAdmin} />
        </div>
      )}
    </div>
  )
}

// ── response times ──────────────────────────────────────────────────────────

function lastNinety(): { from: string; to: string } {
  const to = isoToday()
  const d = new Date(`${to}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() - 89)
  return { from: d.toISOString().slice(0, 10), to }
}

export function ResponseTimesPanel() {
  const [range, setRange] = useState(lastNinety)
  const [open, setOpen] = useState(false)
  const { data: report, isLoading, error } = useQuery<SlaReport | undefined>({
    queryKey: ['contract-escalation', 'sla', range.from, range.to],
    enabled: open,
    queryFn: async () => data<SlaReport>(await apiClient.get(`${BASE}/sla`, { params: range })),
  })
  const r = report?.result
  const approver = (id: string) => report?.approvers.find(x => x.userId === id)?.name ?? 'a colleague'
  const stat = (label: string, value: string) => (
    <div style={{ minWidth: 110 }}><div style={{ fontSize: 18, fontWeight: 700 }}>{value}</div><div style={muted}>{label}</div></div>
  )
  return (
    <div style={card}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontWeight: 700 }}>Response times</div>
          <div style={muted}>How long approvals take to be decided.</div>
        </div>
        <button style={btn()} onClick={() => setOpen(o => !o)}>{open ? 'Close' : 'Show'}</button>
      </div>
      {open && (
        <div style={{ marginTop: 12 }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 12 }}>
            <input style={field} type="date" aria-label="From" value={range.from} max={range.to} onChange={e => setRange(p => ({ ...p, from: e.target.value }))} />
            <span style={muted}>to</span>
            <input style={field} type="date" aria-label="To" value={range.to} min={range.from} onChange={e => setRange(p => ({ ...p, to: e.target.value }))} />
          </div>
          {isLoading && <div style={{ fontSize: 13 }}>Loading…</div>}
          {error && <div role="alert" style={{ fontSize: 12, color: 'var(--hf-danger-text)' }}>{errMsg(error)}</div>}
          {r && report && (
            <div style={{ display: 'grid', gap: 14 }}>
              <div style={muted}>
                Measured {report.workingClock ? 'in working hours' : 'around the clock'}. The target is {minutesInWords(report.targetHours * 60)}.
              </div>
              <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
                {stat('Asked for', String(r.requested))}
                {stat('Decided', String(r.decided))}
                {stat('Still waiting', String(r.waiting))}
                {stat('Average', minutesInWords(r.averageMinutes))}
                {stat('Typical (median)', minutesInWords(r.medianMinutes))}
                {stat('Slowest 1 in 10', minutesInWords(r.ninetiethPercentileMinutes))}
                {stat('Within target', r.decidedWithinTargetPercent == null ? '—' : `${r.decidedWithinTargetPercent}%`)}
              </div>
              <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
                {stat('Reminded', String(r.reminded))}
                {stat('Escalated', String(r.escalated))}
                {stat('Waiting past target', String(r.waitingPastTarget))}
                {stat('Sole-user exceptions', String(r.soleUserExceptions))}
              </div>
              {r.byAction.length > 0 && (
                <table style={{ width: '100%', fontSize: 13, borderCollapse: 'collapse' }}>
                  <thead><tr style={{ textAlign: 'left', ...muted }}><th>Action</th><th>Decided</th><th>Average</th><th>Escalated</th></tr></thead>
                  <tbody>{r.byAction.map(g => (
                    <tr key={g.key}><td>{ACTION_LABEL[g.key] ?? g.key}</td><td>{g.decided}</td><td>{minutesInWords(g.averageMinutes)}</td><td>{g.escalated}</td></tr>
                  ))}</tbody>
                </table>
              )}
              {r.byApprover.length > 0 && (
                <table style={{ width: '100%', fontSize: 13, borderCollapse: 'collapse' }}>
                  <thead><tr style={{ textAlign: 'left', ...muted }}><th>Decided by</th><th>Decided</th><th>Average</th><th>Escalated first</th></tr></thead>
                  <tbody>{r.byApprover.map(g => (
                    <tr key={g.key}><td>{approver(g.key)}</td><td>{g.decided}</td><td>{minutesInWords(g.averageMinutes)}</td><td>{g.escalated}</td></tr>
                  ))}</tbody>
                </table>
              )}
              {r.overdue.length > 0 && (
                <div>
                  <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4 }}>Waiting past the target</div>
                  {r.overdue.slice(0, 10).map(o => (
                    <div key={o.id} style={{ fontSize: 13 }}>{ACTION_LABEL[o.action] ?? o.action} · waited {minutesInWords(o.waitedMinutes)}</div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
