// src/pages/marketing/ui.tsx
import { useState, type ButtonHTMLAttributes, type CSSProperties, type ReactNode } from 'react'

export const C = {
  primary: '#2563EB', navy: '#1B3A6B', ink: '#0F172A', text: '#475569', mute: '#94A3B8', line: '#E2E8F0',
  bg: '#F8FAFC', green: '#16A34A', red: '#DC2626', amber: '#D97706', purple: '#7C3AED', sky: '#0EA5E9',
}

export const fmtN = (n: number | null | undefined) => (n ?? 0).toLocaleString('en-ZA')
export const pct = (r: number | null | undefined, d = 1) => (r == null ? '—' : `${(r * 100).toFixed(d)}%`)
export const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
export const fmtDT = (d: any) => d ? new Date(d).toLocaleString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'
/** Relative change, e.g. 0.12 = +12%. Null when there is nothing to compare against. */
export const change = (cur?: number | null, prev?: number | null): number | null =>
  cur == null || prev == null || prev === 0 ? null : (cur - prev) / prev

export const inp: CSSProperties = { width: '100%', padding: '9px 12px', border: `1.5px solid ${C.line}`, borderRadius: 8, fontSize: 14, boxSizing: 'border-box', background: '#fff', outline: 'none' }
export const lbl: CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }

export function Card({ title, action, children, style, pad = 20 }: { title?: ReactNode; action?: ReactNode; children: ReactNode; style?: CSSProperties; pad?: number }) {
  return (
    <div style={{ background: '#fff', border: `1px solid ${C.line}`, borderRadius: 14, padding: pad, ...style }}>
      {(title || action) && (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
          <div style={{ fontWeight: 700, fontSize: 15, color: C.ink }}>{title}</div>{action}
        </div>)}
      {children}
    </div>
  )
}

export function Kpi({ label, value, delta, invert, sub }: { label: string; value: string; delta?: number | null; invert?: boolean; sub?: string }) {
  const good = delta == null ? true : (delta >= 0) !== !!invert
  return (
    <div style={{ background: '#fff', border: `1px solid ${C.line}`, borderRadius: 14, padding: '16px 18px' }}>
      <div style={{ fontSize: 12, color: C.mute, fontWeight: 600 }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: 800, color: C.ink, margin: '4px 0 2px', letterSpacing: '-0.02em' }}>{value}</div>
      {sub ? <div style={{ fontSize: 11, color: C.mute }}>{sub}</div>
        : delta != null ? <div style={{ fontSize: 11, fontWeight: 700, color: delta === 0 ? C.mute : good ? C.green : C.red }}>{delta >= 0 ? '↑' : '↓'} {Math.abs(delta * 100).toFixed(0)}% vs previous period</div>
        : <div style={{ fontSize: 11, color: C.mute }}>No earlier period to compare</div>}
    </div>
  )
}

export const Pill = ({ children, color, bg }: { children: ReactNode; color: string; bg: string }) => (
  <span style={{ display: 'inline-block', background: bg, color, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700, whiteSpace: 'nowrap' }}>{children}</span>
)

export function Btn({ variant = 'secondary', children, style, ...p }: { variant?: 'primary' | 'secondary' | 'danger'; children: ReactNode } & ButtonHTMLAttributes<HTMLButtonElement>) {
  const v = variant === 'primary' ? { background: C.primary, color: '#fff', border: 'none' }
    : variant === 'danger' ? { background: '#fff', color: C.red, border: `1px solid #FECACA` }
    : { background: '#fff', color: C.text, border: `1px solid ${C.line}` }
  return <button {...p} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '8px 16px', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: p.disabled ? 'not-allowed' : 'pointer', opacity: p.disabled ? 0.5 : 1, ...v, ...style }}>{children}</button>
}

export function Tabs({ tabs, active, onChange }: { tabs: { id: string; label: string; icon?: ReactNode }[]; active: string; onChange: (id: string) => void }) {
  return (
    <div style={{ display: 'flex', borderBottom: `1px solid ${C.line}`, overflowX: 'auto' }}>
      {tabs.map(t => (
        <button key={t.id} onClick={() => onChange(t.id)}
          style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '10px 18px', background: 'none', border: 'none', whiteSpace: 'nowrap', cursor: 'pointer', marginBottom: -1,
            borderBottom: `2px solid ${active === t.id ? C.primary : 'transparent'}`, color: active === t.id ? C.primary : C.text, fontWeight: active === t.id ? 700 : 500, fontSize: 14 }}>
          {t.icon}{t.label}
        </button>))}
    </div>
  )
}

export const Empty = ({ icon, title, text }: { icon?: ReactNode; title: string; text?: string }) => (
  <div style={{ textAlign: 'center', padding: '44px 20px', color: C.mute }}>
    <div style={{ opacity: 0.35, marginBottom: 10 }}>{icon}</div>
    <div style={{ fontWeight: 700, color: C.text, marginBottom: 4 }}>{title}</div>
    {text && <div style={{ fontSize: 13 }}>{text}</div>}
  </div>
)

export function Switch({ on, onChange, disabled }: { on: boolean; onChange?: (v: boolean) => void; disabled?: boolean }) {
  return (
    <button type="button" role="switch" aria-checked={on} disabled={disabled} onClick={() => onChange?.(!on)}
      style={{ width: 40, height: 22, borderRadius: 99, border: 'none', padding: 2, background: on ? C.primary : '#CBD5E1', cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.55 : 1, transition: 'background .15s', flexShrink: 0 }}>
      <span style={{ display: 'block', width: 18, height: 18, borderRadius: '50%', background: '#fff', transform: on ? 'translateX(18px)' : 'none', transition: 'transform .15s' }} />
    </button>
  )
}

export function LineChart({ labels, series, height = 230 }: { labels: string[]; series: { name: string; color: string; values: number[] }[]; height?: number }) {
  const [hover, setHover] = useState<number | null>(null)
  const W = 720, H = height, pl = 42, pr = 12, pt = 12, pb = 26
  const rawMax = Math.max(1, ...series.flatMap(s => s.values))
  const mag = Math.pow(10, Math.floor(Math.log10(rawMax)))
  const max = [1, 2, 5, 10].map(m => m * mag).find(m => m >= rawMax)!
  const n = labels.length
  const x = (i: number) => pl + (n <= 1 ? 0 : (i * (W - pl - pr)) / (n - 1))
  const y = (v: number) => pt + (H - pt - pb) * (1 - v / max)
  const step = Math.max(1, Math.ceil(n / 7))
  const onMove = (e: React.MouseEvent<SVGSVGElement>) => {
    const r = e.currentTarget.getBoundingClientRect()
    const px = ((e.clientX - r.left) / r.width) * W
    setHover(Math.max(0, Math.min(n - 1, Math.round(((px - pl) / (W - pl - pr)) * (n - 1)))))
  }
  return (
    <div style={{ position: 'relative' }}>
      <div style={{ display: 'flex', gap: 16, marginBottom: 6, fontSize: 12, color: C.text }}>
        {series.map(s => <span key={s.name} style={{ display: 'flex', alignItems: 'center', gap: 5 }}><span style={{ width: 8, height: 8, borderRadius: '50%', background: s.color }} />{s.name}</span>)}
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: 'auto', display: 'block' }} onMouseMove={onMove} onMouseLeave={() => setHover(null)}>
        {[0, 0.25, 0.5, 0.75, 1].map(f => (
          <g key={f}><line x1={pl} x2={W - pr} y1={y(max * f)} y2={y(max * f)} stroke="#EEF2F7" />
            <text x={pl - 6} y={y(max * f) + 3} fontSize="10" fill={C.mute} textAnchor="end">{Math.round(max * f).toLocaleString('en-ZA')}</text></g>))}
        {labels.map((l, i) => i % step === 0 && <text key={i} x={x(i)} y={H - 8} fontSize="10" fill={C.mute} textAnchor="middle">{l}</text>)}
        {series.map(s => {
          const pts = s.values.map((v, i) => `${x(i)},${y(v)}`)
          return (
            <g key={s.name}>
              {n > 1 && <path d={`M${x(0)},${y(0)} L${pts.join(' L')} L${x(n - 1)},${y(0)} Z`} fill={s.color} opacity="0.07" />}
              <polyline points={pts.join(' ')} fill="none" stroke={s.color} strokeWidth="2.2" strokeLinejoin="round" />
            </g>)
        })}
        {hover != null && <line x1={x(hover)} x2={x(hover)} y1={pt} y2={H - pb} stroke="#CBD5E1" strokeDasharray="3 3" />}
        {hover != null && series.map(s => <circle key={s.name} cx={x(hover)} cy={y(s.values[hover])} r="3.5" fill="#fff" stroke={s.color} strokeWidth="2" />)}
      </svg>
      {hover != null && (
        <div style={{ position: 'absolute', top: 24, left: `${Math.min(78, Math.max(2, (x(hover) / W) * 100 - 8))}%`, background: C.ink, color: '#fff', borderRadius: 8, padding: '7px 10px', fontSize: 11, pointerEvents: 'none', lineHeight: 1.6 }}>
          <div style={{ fontWeight: 700 }}>{labels[hover]}</div>
          {series.map(s => <div key={s.name}><span style={{ color: s.color }}>●</span> {s.name}: {fmtN(s.values[hover])}</div>)}
        </div>)}
    </div>
  )
}

export function Donut({ slices, center, sub }: { slices: { label: string; value: number; color: string }[]; center: string; sub: string }) {
  const total = slices.reduce((a, s) => a + s.value, 0) || 1
  const r = 62, c = 2 * Math.PI * r
  let acc = 0
  return (
    <svg viewBox="0 0 180 180" width={180} height={180}>
      <circle cx="90" cy="90" r={r} fill="none" stroke="#F1F5F9" strokeWidth="22" />
      {slices.map(s => {
        const len = (s.value / total) * c
        const el = <circle key={s.label} cx="90" cy="90" r={r} fill="none" stroke={s.color} strokeWidth="22" strokeDasharray={`${len} ${c - len}`} strokeDashoffset={-acc} transform="rotate(-90 90 90)" />
        acc += len
        return el
      })}
      <text x="90" y="88" textAnchor="middle" fontSize="22" fontWeight="800" fill={C.ink}>{center}</text>
      <text x="90" y="106" textAnchor="middle" fontSize="10" fill={C.mute}>{sub}</text>
    </svg>
  )
}