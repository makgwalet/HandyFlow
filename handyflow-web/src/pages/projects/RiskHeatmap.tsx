// src/pages/projects/RiskHeatmap.tsx
// Open risks on a 5 × 5 probability × impact grid. Used on a project's Risks tab and on the Projects dashboard.
// Click a cell to list the risks in it.
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { card, muted, one } from './tabs/pmKit'

interface TopRisk { id: string; projectId: string; projectNumber: string | null; projectName: string | null; riskNumber: string | null; title: string
  probability: number; impact: number; rating: string; ownerName: string | null }
interface Heatmap { cells: number[][]; openRisks: number; top: TopRisk[] }

/** Same thresholds as the server's rating (score ≥ 15 red, ≥ 9 amber). */
export const scoreTone = (score: number) => score >= 15 ? 'var(--hf-danger)' : score >= 9 ? 'var(--hf-warning)' : 'var(--hf-success)'

export function RiskHeatmap({ projectId, onOpenProject, compact }: { projectId?: string; onOpenProject?: (id: string) => void; compact?: boolean }) {
  const [pick, setPick] = useState<{ p: number; i: number } | null>(null)
  const url = projectId ? `/api/v1/projects/${projectId}/risks/heatmap` : '/api/v1/projects/risk-heatmap'
  const { data, isLoading, isError } = useQuery<Heatmap>({ queryKey: ['pm-heatmap', projectId ?? 'all'], staleTime: 60_000, queryFn: async () => one<Heatmap>(await apiClient.get(url)) })
  if (isLoading) return <div style={muted}>Loading risks…</div>
  if (isError || !data) return <div style={muted}>The heat map is not available right now.</div>
  const size = compact ? 30 : 40
  const inCell = pick ? data.top.filter(r => r.probability === pick.p && r.impact === pick.i) : data.top
  return (
    <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap', alignItems: 'flex-start' }}>
      <div>
        <div style={{ display: 'flex' }}>
          <div style={{ writingMode: 'vertical-rl', transform: 'rotate(180deg)', fontSize: 10, fontWeight: 700, color: 'var(--hf-text-muted)', textAlign: 'center', marginRight: 4 }}>IMPACT →</div>
          <div role="grid" aria-label="Open risks by probability and impact">
            {[5, 4, 3, 2, 1].map(i => (
              <div key={i} role="row" style={{ display: 'flex', alignItems: 'center' }}>
                <span style={{ width: 14, fontSize: 10, color: 'var(--hf-text-faint)', textAlign: 'right', marginRight: 4 }}>{i}</span>
                {[1, 2, 3, 4, 5].map(p => {
                  const n = data.cells[i - 1]?.[p - 1] ?? 0, selected = pick?.p === p && pick?.i === i
                  return (
                    <button key={p} role="gridcell" onClick={() => setPick(selected || n === 0 ? null : { p, i })} aria-label={`Probability ${p}, impact ${i}: ${n} risk${n === 1 ? '' : 's'}`}
                      style={{ width: size, height: size, margin: 1.5, borderRadius: 6, border: selected ? '2px solid var(--hf-text)' : '1px solid transparent', cursor: n ? 'pointer' : 'default',
                        background: scoreTone(p * i), opacity: n ? 1 : 0.22, color: 'var(--hf-text-on-solid)', fontWeight: 800, fontSize: compact ? 12 : 14 }}>{n || ''}</button>
                  )
                })}
              </div>
            ))}
            <div style={{ display: 'flex', marginLeft: 18 }}>{[1, 2, 3, 4, 5].map(p => <span key={p} style={{ width: size + 3, textAlign: 'center', fontSize: 10, color: 'var(--hf-text-faint)' }}>{p}</span>)}</div>
            <div style={{ marginLeft: 18, fontSize: 10, fontWeight: 700, color: 'var(--hf-text-muted)', textAlign: 'center' }}>PROBABILITY →</div>
          </div>
        </div>
      </div>
      <div style={{ flex: 1, minWidth: 220 }}>
        <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 6 }}>
          {pick ? `Probability ${pick.p} × impact ${pick.i}` : `Top risks`} <span style={muted}>· {data.openRisks} open</span>
          {pick && <button onClick={() => setPick(null)} style={{ marginLeft: 8, fontSize: 11, background: 'none', border: 'none', color: 'var(--hf-primary-text)', cursor: 'pointer' }}>show top risks</button>}
        </div>
        {inCell.length === 0 ? <div style={muted}>{data.openRisks ? 'None in this cell among the top ten.' : 'No open risks.'}</div> : (
          <div style={{ display: 'grid', gap: 4 }}>
            {inCell.slice(0, compact ? 5 : 10).map(r => (
              <button key={r.id} disabled={!onOpenProject} onClick={() => onOpenProject?.(r.projectId)}
                style={{ ...card, padding: '6px 9px', display: 'flex', gap: 8, alignItems: 'center', textAlign: 'left', cursor: onOpenProject ? 'pointer' : 'default', color: 'var(--hf-text)' }}>
                <span style={{ minWidth: 26, height: 22, borderRadius: 5, background: scoreTone(r.probability * r.impact), color: 'var(--hf-text-on-solid)', fontSize: 11, fontWeight: 800, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>{r.probability * r.impact}</span>
                <span style={{ minWidth: 0, flex: 1 }}><span style={{ fontSize: 12.5, fontWeight: 600 }}>{r.riskNumber ? `${r.riskNumber} ` : ''}{r.title}</span>
                  <span style={{ display: 'block', fontSize: 11, color: 'var(--hf-text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {!projectId && r.projectNumber ? `${r.projectNumber} · ${r.projectName} · ` : ''}{r.ownerName ?? 'no owner'}</span></span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
