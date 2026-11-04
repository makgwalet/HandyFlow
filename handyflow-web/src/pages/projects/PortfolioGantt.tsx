// src/pages/projects/PortfolioGantt.tsx
// Every live and planned project on one timeline: a bar from start to planned end coloured by health, a tick at the baseline
// end (bars running past it are late), progress by tasks done, today line. Click a project to open it.
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { card, day, list, muted } from './tabs/pmKit'

interface P { id: string; projectNumber: string; name: string; clientName: string | null; status: string; health: string
  startDate: string | null; endDate: string | null; baselineEnd: string | null; taskCount: number; completedTaskCount: number }

const HEALTH = { GREEN: 'var(--hf-success)', AMBER: 'var(--hf-warning)', RED: 'var(--hf-danger)' } as Record<string, string>
const LIVE = ['PLANNING', 'PROPOSED', 'APPROVED', 'ACTIVE', 'ON_HOLD']
const DAY = 86_400_000
const parse = (d: string) => new Date(d + 'T00:00:00').getTime()

export function PortfolioGantt({ onOpen }: { onOpen: (id: string) => void }) {
  const [scope, setScope] = useState<'live' | 'all'>('live')
  const { data = [], isLoading } = useQuery<P[]>({ queryKey: ['pm-projects-gantt'], staleTime: 60_000,
    queryFn: async () => list<P>(await apiClient.get('/api/v1/projects', { params: { size: 300 } })) })
  const rows = useMemo(() => data.filter(p => p.startDate && p.endDate && (scope === 'all' || LIVE.includes(p.status)))
    .sort((a, b) => a.startDate!.localeCompare(b.startDate!)), [data, scope])
  const range = useMemo(() => {
    if (!rows.length) return null
    const ts = rows.flatMap(p => [parse(p.startDate!), parse(p.endDate!), p.baselineEnd ? parse(p.baselineEnd) : parse(p.endDate!)])
    const now = Date.now()
    const lo = new Date(Math.min(...ts, now)); lo.setDate(1)
    const hi = new Date(Math.max(...ts, now)); hi.setMonth(hi.getMonth() + 1, 0)
    return { lo: lo.getTime(), hi: hi.getTime() }
  }, [rows])

  if (isLoading) return null
  return (
    <div style={{ ...card, marginBottom: 20 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, gap: 10, flexWrap: 'wrap' }}>
        <div style={{ fontSize: 13, fontWeight: 700 }}>Portfolio timeline</div>
        <select value={scope} onChange={e => setScope(e.target.value as 'live' | 'all')} style={{ padding: '5px 9px', borderRadius: 8, border: '1px solid var(--hf-border)', fontSize: 12, background: 'var(--hf-surface)', color: 'var(--hf-text)' }} aria-label="Which projects">
          <option value="live">Planned and live</option><option value="all">All (including finished)</option></select>
      </div>
      {!range ? <div style={muted}>No projects with start and end dates.</div> : (() => {
        const span = Math.max(DAY, range.hi - range.lo)
        const pct = (t: number) => `${(t - range.lo) / span * 100}%`
        const months: { t: number; label: string }[] = []
        for (let d = new Date(range.lo); d.getTime() <= range.hi; d.setMonth(d.getMonth() + 1))
          months.push({ t: d.getTime(), label: d.toLocaleString('en-ZA', { month: 'short', ...(d.getMonth() === 0 || months.length === 0 ? { year: '2-digit' } : {}) }) })
        const step = Math.ceil(months.length / 12)
        return (
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(160px, 240px) 1fr', gap: 0 }}>
            <div />
            <div style={{ position: 'relative', height: 18, borderBottom: '1px solid var(--hf-border)' }}>
              {months.map((m, i) => i % step === 0 && <span key={m.t} style={{ position: 'absolute', left: pct(m.t), fontSize: 10, color: 'var(--hf-text-faint)', paddingLeft: 3, borderLeft: '1px solid var(--hf-border)' }}>{m.label}</span>)}
            </div>
            {rows.map(p => {
              const s = parse(p.startDate!), e = parse(p.endDate!) + DAY, b = p.baselineEnd ? parse(p.baselineEnd) + DAY : null
              const done = p.taskCount ? p.completedTaskCount / p.taskCount : 0
              const late = b != null && e > b
              return (
                <button key={p.id} onClick={() => onOpen(p.id)} title={`${p.projectNumber} ${p.name}: ${day(p.startDate)} – ${day(p.endDate)}${p.baselineEnd ? ` (baseline end ${day(p.baselineEnd)})` : ''} · ${Math.round(done * 100)}% of tasks done`}
                  style={{ display: 'contents', cursor: 'pointer' }}>
                  <span style={{ padding: '5px 8px 5px 0', fontSize: 12, textAlign: 'left', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', borderBottom: '1px solid var(--hf-border)', color: 'var(--hf-text)' }}>
                    <b>{p.projectNumber}</b> {p.name}</span>
                  <span style={{ position: 'relative', height: 28, borderBottom: '1px solid var(--hf-border)' }}>
                    <span style={{ position: 'absolute', left: pct(s), width: `calc(${pct(e)} - ${pct(s)})`, top: 7, height: 14, borderRadius: 4,
                      background: 'var(--hf-surface-sunken)', border: `1.5px solid ${HEALTH[p.health] ?? 'var(--hf-border)'}`, opacity: p.status === 'ON_HOLD' ? 0.55 : 1, overflow: 'hidden' }}>
                      <span style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: `${done * 100}%`, background: HEALTH[p.health] ?? 'var(--hf-info)', opacity: 0.85 }} />
                    </span>
                    {b != null && <span style={{ position: 'absolute', left: pct(b), top: 4, height: 20, width: 2, background: late ? 'var(--hf-danger)' : 'var(--hf-text-muted)' }} />}
                  </span>
                </button>
              )
            })}
            <div />
            <div style={{ position: 'relative', height: 0 }}>
              <span style={{ position: 'absolute', left: pct(Date.now()), bottom: 0, height: rows.length * 29 + 18, width: 0, borderLeft: '1.5px dashed var(--hf-danger)', pointerEvents: 'none' }} />
            </div>
          </div>
        )
      })()}
      <div style={{ ...muted, display: 'flex', gap: 14, flexWrap: 'wrap', marginTop: 8 }}>
        <span>Bar: start to planned end, filled by tasks done, outlined by health</span><span>| baseline end (red when the project now finishes after it)</span><span style={{ color: 'var(--hf-danger-text)' }}>┆ today</span>
      </div>
    </div>
  )
}
