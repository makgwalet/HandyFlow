// The recruitment pipeline as a funnel: each band is as wide as the number of applications that reached that stage, and the key beside it gives
// the count and the share of everyone who applied. The key carries the numbers, so the picture never has to be read on its own.
export type FunnelStage = { label: string; count: number }

const TONES = ['var(--hf-info)', 'var(--hf-sky)', 'var(--hf-violet)', 'var(--hf-indigo)', 'var(--hf-warning)', 'var(--hf-success)']
const W = 300, BAND = 44, GAP = 3, MIN_SHARE = 0.16

export default function RecFunnel({ stages, caption }: { stages: FunnelStage[]; caption?: string }) {
  if (stages.length === 0) return null
  const top = Math.max(stages[0].count, 1)
  const share = (n: number) => Math.max(MIN_SHARE, Math.min(1, n / top))
  const height = stages.length * (BAND + GAP)
  const pct = (n: number) => (stages[0].count === 0 ? '0%' : `${Math.round((n / stages[0].count) * 100)}%`)

  return (
    <figure style={{ margin: 0, display: 'flex', gap: 20, alignItems: 'center', flexWrap: 'wrap' }}>
      <svg width={W} height={height} viewBox={`0 0 ${W} ${height}`} role="img"
        aria-label={`Recruitment pipeline: ${stages.map(s => `${s.label} ${s.count}`).join(', ')}`}>
        {stages.map((s, i) => {
          const wTop = W * share(s.count)
          const wBottom = i + 1 < stages.length ? W * share(stages[i + 1].count) : wTop * 0.8
          const y = i * (BAND + GAP), cx = W / 2
          const points = `${cx - wTop / 2},${y} ${cx + wTop / 2},${y} ${cx + wBottom / 2},${y + BAND} ${cx - wBottom / 2},${y + BAND}`
          return <polygon key={s.label} points={points} fill={TONES[Math.min(i, TONES.length - 1)]} />
        })}
      </svg>
      <figcaption style={{ display: 'grid', gap: 6, fontSize: 12, minWidth: 200 }}>
        {caption && <div style={{ color: 'var(--hf-text-muted)' }}>{caption}</div>}
        {stages.map((s, i) => (
          <div key={s.label} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span aria-hidden style={{ width: 10, height: 10, borderRadius: 2, background: TONES[Math.min(i, TONES.length - 1)], flexShrink: 0 }} />
            <span style={{ flex: 1 }}>{s.label}</span>
            <strong>{s.count}</strong>
            <span style={{ color: 'var(--hf-text-muted)', minWidth: 40, textAlign: 'right' }}>{pct(s.count)}</span>
          </div>
        ))}
      </figcaption>
    </figure>
  )
}
