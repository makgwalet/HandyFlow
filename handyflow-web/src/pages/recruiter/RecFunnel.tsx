// The recruitment pipeline as a funnel: each band is as wide as the number of applications that reached that stage, with the number written in it,
// and the key beside it gives the stage name and the share of everyone who applied. With nothing to show it says so instead of drawing empty bands.
export type FunnelStage = { label: string; count: number }

const TONES = ['var(--hf-primary)', 'var(--hf-sky-solid-strong)', 'var(--hf-violet-solid-strong)', 'var(--hf-warning-solid-strong)', 'var(--hf-success-solid-strong)', 'var(--hf-orange-solid-strong)']
const W = 340, BAND = 42, MIN_SHARE = 0.3

export default function RecFunnel({ stages, caption }: { stages: FunnelStage[]; caption?: string }) {
  if (stages.length === 0) return null
  const received = stages[0].count
  if (received === 0) return <div style={{ color: 'var(--hf-text-muted)', fontSize: 12, padding: '12px 0' }}>No applications in this period, so there is no funnel to draw yet.</div>

  const share = (n: number) => Math.max(MIN_SHARE, Math.min(1, n / received))
  const height = stages.length * BAND
  const pct = (n: number) => `${Math.round((n / received) * 100)}%`

  return (
    <figure style={{ margin: 0, display: 'grid', gap: 12, justifyItems: 'center' }}>
      <svg width="100%" style={{ maxWidth: W }} viewBox={`0 0 ${W} ${height}`} role="img"
        aria-label={`Recruitment pipeline: ${stages.map(s => `${s.label} ${s.count}`).join(', ')}`}>
        {stages.map((s, i) => {
          const wTop = W * share(s.count)
          const wBottom = i + 1 < stages.length ? W * share(stages[i + 1].count) : wTop * 0.85
          const y = i * BAND, cx = W / 2
          const points = `${cx - wTop / 2},${y} ${cx + wTop / 2},${y} ${cx + wBottom / 2},${y + BAND} ${cx - wBottom / 2},${y + BAND}`
          return (
            <g key={s.label}>
              <polygon points={points} fill={TONES[Math.min(i, TONES.length - 1)]} stroke="var(--hf-surface)" strokeWidth="2" strokeLinejoin="round" />
              <text x={cx} y={y + BAND / 2 + 5} textAnchor="middle" fontSize="15" fontWeight="700" fill="var(--hf-text-on-solid)">{s.count}</text>
            </g>
          )
        })}
      </svg>
      <figcaption style={{ display: 'grid', gap: 6, fontSize: 12, width: '100%', maxWidth: W }}>
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
