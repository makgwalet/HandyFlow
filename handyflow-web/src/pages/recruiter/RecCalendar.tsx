// The interview calendar: one week at a time, South African time, every interview that still stands. A list under the grid says the same in words.
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'

type Item = { id: string; applicationId: string; candidate: string; jobTitle: string; type: string; scheduledAt: string; durationMinutes: number; outcome: string; interviewerName: string | null; location: string | null }
const URL = '/api/v1/recruiter/calendar'
const ZONE = 'Africa/Johannesburg'
const FIRST_HOUR = 7, LAST_HOUR = 19, ROW = 48
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const TYPE_WORD: Record<string, string> = { PHONE: 'Phone', VIDEO: 'Video', IN_PERSON: 'On site', TECHNICAL: 'Technical', PANEL: 'Panel' }
const TYPE_TONE: Record<string, { bg: string; border: string; text: string }> = {
  VIDEO: { bg: 'var(--hf-info-soft)', border: 'var(--hf-info-border)', text: 'var(--hf-info-text)' },
  IN_PERSON: { bg: 'var(--hf-success-soft)', border: 'var(--hf-success-border)', text: 'var(--hf-success-text)' },
  PHONE: { bg: 'var(--hf-violet-soft)', border: 'var(--hf-violet-border)', text: 'var(--hf-violet-text)' },
  TECHNICAL: { bg: 'var(--hf-warning-soft)', border: 'var(--hf-warning-border)', text: 'var(--hf-warning-text)' },
  PANEL: { bg: 'var(--hf-orange-soft)', border: 'var(--hf-orange-border)', text: 'var(--hf-orange-text)' },
}
const OUTCOME_WORD: Record<string, string> = { PENDING: 'Booked', PASSED: 'Passed', FAILED: 'Did not pass', NO_SHOW: 'Did not attend' }
const pad = (n: number) => String(n).padStart(2, '0')
const iso = (d: Date) => `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`
const addDays = (day: string, n: number) => { const d = new Date(`${day}T00:00:00Z`); d.setUTCDate(d.getUTCDate() + n); return iso(d) }
const todaySast = () => new Date().toLocaleDateString('en-CA', { timeZone: ZONE })
const mondayOf = (day: string) => { const d = new Date(`${day}T00:00:00Z`); return addDays(day, -((d.getUTCDay() + 6) % 7)) }
const dayOf = (isoTime: string) => new Date(isoTime).toLocaleDateString('en-CA', { timeZone: ZONE })
const minutesOf = (isoTime: string) => {
  const p = new Intl.DateTimeFormat('en-GB', { timeZone: ZONE, hour: '2-digit', minute: '2-digit', hour12: false }).formatToParts(new Date(isoTime))
  return Number(p.find(x => x.type === 'hour')!.value) % 24 * 60 + Number(p.find(x => x.type === 'minute')!.value)
}
const clock = (m: number) => `${pad(Math.floor(m / 60) % 24)}:${pad(m % 60)}`
const dayLabel = (day: string) => new Date(`${day}T12:00:00Z`).toLocaleDateString('en-ZA', { timeZone: 'UTC', weekday: 'short', day: 'numeric', month: 'short' })
const btn = { padding: '6px 10px', background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12, cursor: 'pointer' } as const

export default function RecCalendar() {
  const [monday, setMonday] = useState(() => mondayOf(todaySast()))
  const days = Array.from({ length: 7 }, (_, i) => addDays(monday, i))
  const q = useQuery<Item[]>({ queryKey: ['rec-calendar', monday], queryFn: async () => data(await apiClient.get(`${URL}?from=${monday}&to=${days[6]}`)), retry: false })
  const err = (q.error as { response?: { data?: { message?: string } } } | null)?.response?.data?.message
  const today = todaySast()
  const items = q.data ?? []
  const hours = Array.from({ length: LAST_HOUR - FIRST_HOUR }, (_, i) => FIRST_HOUR + i)
  const offGrid = items.filter(i => { const m = minutesOf(i.scheduledAt); return m < FIRST_HOUR * 60 || m >= LAST_HOUR * 60 })

  return (
    <div style={{ fontSize: 12 }}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 10 }}>
        <button style={btn} aria-label="Previous week" onClick={() => setMonday(addDays(monday, -7))}>‹</button>
        <button style={btn} onClick={() => setMonday(mondayOf(todaySast()))}>This week</button>
        <button style={btn} aria-label="Next week" onClick={() => setMonday(addDays(monday, 7))}>›</button>
        <strong style={{ fontSize: 14 }}>{dayLabel(days[0])} to {dayLabel(days[6])}</strong>
        <span style={{ marginLeft: 'auto', display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          {Object.keys(TYPE_TONE).map(t => (
            <span key={t} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <span aria-hidden style={{ width: 9, height: 9, borderRadius: 2, background: TYPE_TONE[t].bg, border: `1px solid ${TYPE_TONE[t].border}` }} />{TYPE_WORD[t]}
            </span>
          ))}
        </span>
      </div>
      {err && <div role="alert" style={{ color: 'var(--hf-danger-text)', marginBottom: 8 }}>{err}</div>}
      {q.isError && !err && <div role="alert" style={{ color: 'var(--hf-danger-text)', marginBottom: 8 }}>The calendar could not be loaded.</div>}

      <div style={{ overflowX: 'auto', border: '1px solid var(--hf-border)', borderRadius: 10, background: 'var(--hf-surface)' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '48px repeat(7, minmax(110px, 1fr))', minWidth: 48 + 7 * 110 }}>
          <div />
          {days.map(d => (
            <div key={d} style={{ padding: '6px 8px', fontWeight: 600, borderBottom: '1px solid var(--hf-border)', borderLeft: '1px solid var(--hf-border-subtle)',
              color: d === today ? 'var(--hf-primary-text)' : undefined, background: d === today ? 'var(--hf-info-soft)' : undefined }}>{dayLabel(d)}</div>
          ))}
          <div>{hours.map(h => <div key={h} style={{ height: ROW, textAlign: 'right', paddingRight: 6, color: 'var(--hf-text-muted)', fontSize: 11 }}>{pad(h)}:00</div>)}</div>
          {days.map(d => (
            <div key={d} style={{ position: 'relative', height: hours.length * ROW, borderLeft: '1px solid var(--hf-border-subtle)', background: d === today ? 'var(--hf-info-soft)' : undefined }}>
              {hours.map(h => <div key={h} style={{ height: ROW, borderTop: '1px solid var(--hf-border-subtle)' }} />)}
              {items.filter(i => dayOf(i.scheduledAt) === d && !offGrid.includes(i)).map(i => {
                const start = minutesOf(i.scheduledAt)
                const top = ((start - FIRST_HOUR * 60) / 60) * ROW
                const height = Math.max(24, (i.durationMinutes / 60) * ROW - 2)
                const tone = TYPE_TONE[i.type] ?? TYPE_TONE.VIDEO
                return (
                  <div key={i.id} title={`${i.candidate}, ${i.jobTitle}. ${clock(start)} to ${clock(start + i.durationMinutes)}. ${TYPE_WORD[i.type] ?? i.type}${i.interviewerName ? `, with ${i.interviewerName}` : ''}${i.location ? `, ${i.location}` : ''}`}
                    style={{ position: 'absolute', left: 3, right: 3, top, height, overflow: 'hidden', borderRadius: 6, padding: '2px 5px', fontSize: 11,
                      background: tone.bg, border: `1px solid ${tone.border}`, color: tone.text, opacity: i.outcome === 'PENDING' ? 1 : 0.7 }}>
                    <strong>{i.candidate}</strong><br />{clock(start)} {i.jobTitle}
                  </div>
                )
              })}
            </div>
          ))}
        </div>
      </div>

      <div style={{ marginTop: 14 }}>
        <strong>This week in words</strong>
        {q.isLoading && <div style={{ color: 'var(--hf-text-muted)', marginTop: 6 }}>Loading...</div>}
        {q.data && items.length === 0 && <div style={{ color: 'var(--hf-text-muted)', marginTop: 6 }}>No interviews this week.</div>}
        {items.map(i => {
          const start = minutesOf(i.scheduledAt)
          return (
            <div key={i.id} style={{ display: 'flex', gap: 10, flexWrap: 'wrap', padding: '6px 0', borderBottom: '1px solid var(--hf-border-subtle)' }}>
              <span style={{ minWidth: 120 }}>{dayLabel(dayOf(i.scheduledAt))}, {clock(start)}</span>
              <span style={{ flex: 1, minWidth: 180 }}><strong>{i.candidate}</strong> <span style={{ color: 'var(--hf-text-muted)' }}>for {i.jobTitle}</span></span>
              <span>{TYPE_WORD[i.type] ?? i.type}{i.interviewerName ? `, with ${i.interviewerName}` : ''}{i.location ? `, ${i.location}` : ''}</span>
              <span style={{ color: 'var(--hf-text-muted)' }}>{OUTCOME_WORD[i.outcome] ?? i.outcome}</span>
            </div>
          )
        })}
        {offGrid.length > 0 && <div style={{ color: 'var(--hf-text-muted)', marginTop: 6 }}>{offGrid.length} interview{offGrid.length === 1 ? ' is' : 's are'} outside {pad(FIRST_HOUR)}:00 to {pad(LAST_HOUR)}:00 and only show in this list.</div>}
      </div>
    </div>
  )
}
