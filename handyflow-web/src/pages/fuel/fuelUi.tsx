// src/pages/fuel/fuelUi.tsx
//
// The Fuel module's visual kit: gradient KPI tiles, panels with a navy header, status pills, ring gauges
// and a step tracker. Everything reads theme tokens, so it follows light and dark mode with no extra work.
// Table, spacing and hover polish for the rest of the Fuel screens lives in styles/fuel.css (class "hf-fuel").
import type { CSSProperties, ElementType, ReactNode } from "react"
import { Check } from "lucide-react"

export type Tone = "blue" | "green" | "violet" | "amber" | "red" | "slate"

/** Fill gradient for solid tiles, and the matching soft/ink pair for pills. All values are theme tokens. */
const TONES: Record<Tone, { from: string; to: string; soft: string; ink: string; border: string }> = {
  blue:   { from: "var(--hf-primary)",          to: "var(--hf-info)",   soft: "var(--hf-info-soft)",    ink: "var(--hf-info-text-strong)",    border: "var(--hf-info-border)" },
  green:  { from: "var(--hf-success-solid-strong)", to: "var(--hf-success)", soft: "var(--hf-success-soft)", ink: "var(--hf-success-text-strong)", border: "var(--hf-success-border-subtle)" },
  violet: { from: "var(--hf-violet-solid-strong)",  to: "var(--hf-violet)", soft: "var(--hf-violet-soft)",  ink: "var(--hf-violet-text-strong)",  border: "var(--hf-violet-border)" },
  amber:  { from: "var(--hf-warning-solid-strong)", to: "var(--hf-warning)", soft: "var(--hf-warning-soft)", ink: "var(--hf-warning-text-strong)", border: "var(--hf-warning-border)" },
  red:    { from: "var(--hf-danger-hover)",     to: "var(--hf-danger)", soft: "var(--hf-danger-soft)",  ink: "var(--hf-danger-text-strong)",  border: "var(--hf-danger-border)" },
  slate:  { from: "var(--hf-primary-deep)",     to: "var(--hf-neutral-solid)", soft: "var(--hf-surface-sunken)", ink: "var(--hf-text-tertiary)", border: "var(--hf-border)" },
}

export const toneSoft = (t: Tone) => TONES[t].soft
export const toneInk = (t: Tone) => TONES[t].ink

// ── KPI tile ────────────────────────────────────────────────────────────────

export function KpiTile({ label, value, hint, icon: Icon, tone = "blue", onClick, trend }: {
  label: string; value: ReactNode; hint?: ReactNode; icon?: ElementType; tone?: Tone; onClick?: () => void
  /** A short series drawn as a sparkline in the tile corner. */
  trend?: number[]
}) {
  const t = TONES[tone]
  const style: CSSProperties = {
    position: "relative", overflow: "hidden", textAlign: "left", border: "none", borderRadius: 14, padding: "16px 18px", minHeight: 104,
    background: `linear-gradient(135deg, ${t.from}, ${t.to})`, color: "var(--hf-text-on-solid)", boxShadow: "var(--hf-shadow-sm)",
    cursor: onClick ? "pointer" : "default", font: "inherit", width: "100%",
  }
  const body = (
    <>
      <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12.5, fontWeight: 600, opacity: 0.92 }}>
        {Icon && <span aria-hidden="true" style={{ width: 26, height: 26, borderRadius: 8, background: "rgba(255,255,255,0.18)", display: "grid", placeItems: "center" }}><Icon size={15} /></span>}
        {label}
      </div>
      <div style={{ fontSize: 28, fontWeight: 800, lineHeight: 1.15, marginTop: 10, letterSpacing: "-0.01em" }}>{value}</div>
      {hint && <div style={{ fontSize: 12, marginTop: 4, opacity: 0.88 }}>{hint}</div>}
      {trend && trend.length > 1 && <Sparkline values={trend} style={{ position: "absolute", right: 12, top: 12, opacity: 0.8 }} />}
    </>
  )
  return onClick
    ? <button type="button" className="hf-fuel-lift" onClick={onClick} style={style}>{body}</button>
    : <div style={style}>{body}</div>
}

export function Sparkline({ values, width = 84, height = 30, style }: { values: number[]; width?: number; height?: number; style?: CSSProperties }) {
  const max = Math.max(...values), min = Math.min(...values), span = max - min || 1
  const pts = values.map((v, i) => `${(i / (values.length - 1)) * width},${height - 3 - ((v - min) / span) * (height - 6)}`).join(" ")
  return (
    <svg aria-hidden="true" width={width} height={height} viewBox={`0 0 ${width} ${height}`} style={style}>
      <polyline points={pts} fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** Responsive strip for KPI tiles. */
export function KpiGrid({ children, min = 160 }: { children: ReactNode; min?: number }) {
  return <div style={{ display: "grid", gridTemplateColumns: `repeat(auto-fit, minmax(${min}px, 1fr))`, gap: 14 }}>{children}</div>
}

// ── Panel ───────────────────────────────────────────────────────────────────

export function Panel({ title, icon: Icon, action, children, pad = true, style }: {
  title: string; icon?: ElementType; action?: ReactNode; children: ReactNode; pad?: boolean; style?: CSSProperties
}) {
  return (
    <section style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14, boxShadow: "var(--hf-shadow-sm)", overflow: "hidden", display: "flex", flexDirection: "column", ...style }}>
      <header style={{ display: "flex", alignItems: "center", gap: 10, padding: "11px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)" }}>
        {Icon && <Icon size={16} aria-hidden="true" />}
        <h2 style={{ margin: 0, fontSize: 13.5, fontWeight: 700, flex: 1 }}>{title}</h2>
        {action}
      </header>
      <div style={{ padding: pad ? 16 : 0, flex: 1, minWidth: 0 }}>{children}</div>
    </section>
  )
}

/** A quiet link-style button for a Panel header (sits on the navy bar). */
export function PanelLink({ children, onClick }: { children: ReactNode; onClick: () => void }) {
  return <button type="button" onClick={onClick} style={{ background: "none", border: "none", color: "var(--hf-on-brand-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer", padding: 0 }}>{children}</button>
}

// ── Status pill ─────────────────────────────────────────────────────────────

const STATUS_TONE: Record<string, Tone> = {
  COMPLETED: "green", DELIVERED: "green", PAID: "green", POSTED: "green", APPROVED: "green", ACTIVE: "green", OK: "green", CLOSED: "slate",
  IN_TRANSIT: "amber", DISPATCHED: "blue", LOADING: "blue", ARRIVED: "violet", OFFLOADING: "violet", OPEN: "blue",
  SCHEDULED: "blue", PLANNED: "blue", PENDING: "amber", DRAFT: "slate",
  DECLINED: "red", APPLIED: "green", RECEIVED: "blue", CANCELLED: "red", REVERSED: "red", FAILED: "red", REJECTED: "red", OVERDUE: "red", LOW: "red", BLOCKED: "red",
}

export const toneForStatus = (status: string | null | undefined): Tone => STATUS_TONE[(status ?? "").toUpperCase()] ?? "slate"
const pretty = (s: string) => s.toLowerCase().replace(/_/g, " ").replace(/^\w/, c => c.toUpperCase())

export function StatusPill({ status, tone, label }: { status?: string | null; tone?: Tone; label?: string }) {
  const t = TONES[tone ?? toneForStatus(status)]
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 6, padding: "2px 10px", borderRadius: 999, fontSize: 11.5, fontWeight: 700, whiteSpace: "nowrap", background: t.soft, color: t.ink, border: `1px solid ${t.border}` }}>
      <span aria-hidden="true" style={{ width: 6, height: 6, borderRadius: 999, background: "currentColor" }} />
      {label ?? pretty(status ?? "unknown")}
    </span>
  )
}

// ── Ring gauge ──────────────────────────────────────────────────────────────

export function RingGauge({ percent, size = 112, stroke = 11, tone = "blue", label, sub }: {
  percent: number; size?: number; stroke?: number; tone?: Tone; label?: ReactNode; sub?: ReactNode
}) {
  const p = Math.min(100, Math.max(0, Number.isFinite(percent) ? percent : 0))
  const r = (size - stroke) / 2, c = 2 * Math.PI * r
  return (
    <div role="img" aria-label={`${Math.round(p)} percent`} style={{ position: "relative", width: size, height: size, flexShrink: 0 }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ transform: "rotate(-90deg)" }}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--hf-surface-sunken)" strokeWidth={stroke} />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={TONES[tone].to} strokeWidth={stroke} strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={c * (1 - p / 100)} style={{ transition: "stroke-dashoffset .6s ease" }} />
      </svg>
      <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center", textAlign: "center" }}>
        <div>
          <div style={{ fontSize: size / 4.2, fontWeight: 800, color: "var(--hf-text)", lineHeight: 1 }}>{label ?? `${Math.round(p)}%`}</div>
          {sub && <div style={{ fontSize: 10.5, color: "var(--hf-text-muted)", marginTop: 3 }}>{sub}</div>}
        </div>
      </div>
    </div>
  )
}

// ── Step tracker ────────────────────────────────────────────────────────────

/** Horizontal stepper; steps before `current` are ticked, `current` is highlighted. */
export function StepTracker({ steps, current }: { steps: string[]; current: number }) {
  return (
    <ol style={{ display: "flex", alignItems: "flex-start", listStyle: "none", margin: 0, padding: 0 }}>
      {steps.map((s, i) => {
        const done = i < current, now = i === current
        return (
          <li key={s} aria-current={now ? "step" : undefined} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", position: "relative", minWidth: 0 }}>
            {i > 0 && <span aria-hidden="true" style={{ position: "absolute", top: 15, right: "50%", width: "100%", height: 2, background: i <= current ? "var(--hf-success)" : "var(--hf-border)" }} />}
            <span style={{
              position: "relative", zIndex: 1, width: 30, height: 30, borderRadius: 999, display: "grid", placeItems: "center", fontSize: 12, fontWeight: 700,
              background: done ? "var(--hf-success)" : now ? "var(--hf-surface)" : "var(--hf-surface-sunken)",
              color: done ? "var(--hf-text-on-solid)" : now ? "var(--hf-primary-text)" : "var(--hf-text-muted)",
              border: now ? "2px solid var(--hf-primary)" : "2px solid transparent",
            }}>{done ? <Check size={15} /> : i + 1}</span>
            <span style={{ marginTop: 6, fontSize: 11.5, fontWeight: now ? 700 : 500, color: now ? "var(--hf-text)" : "var(--hf-text-muted)", textAlign: "center" }}>{s}</span>
          </li>
        )
      })}
    </ol>
  )
}

// ── Bits ────────────────────────────────────────────────────────────────────

export function ProgressBar({ percent, tone = "blue" }: { percent: number; tone?: Tone }) {
  const p = Math.min(100, Math.max(0, Number.isFinite(percent) ? percent : 0))
  return (
    <div role="progressbar" aria-valuenow={Math.round(p)} aria-valuemin={0} aria-valuemax={100} style={{ height: 8, background: "var(--hf-surface-sunken)", borderRadius: 99, overflow: "hidden" }}>
      <div style={{ height: "100%", width: `${p}%`, borderRadius: 99, background: `linear-gradient(90deg, ${TONES[tone].from}, ${TONES[tone].to})`, transition: "width .5s ease" }} />
    </div>
  )
}

export function EmptyState({ icon: Icon, title, hint, action }: { icon?: ElementType; title: string; hint?: string; action?: ReactNode }) {
  return (
    <div style={{ textAlign: "center", padding: "32px 16px", color: "var(--hf-text-muted)" }}>
      {Icon && <Icon size={30} aria-hidden="true" style={{ color: "var(--hf-text-disabled)", marginBottom: 8 }} />}
      <div style={{ fontWeight: 700, color: "var(--hf-text-tertiary)", fontSize: 14 }}>{title}</div>
      {hint && <div style={{ fontSize: 12.5, marginTop: 4 }}>{hint}</div>}
      {action && <div style={{ marginTop: 12 }}>{action}</div>}
    </div>
  )
}

/** A tappable tile for the quick-actions grid. */
export function ActionTile({ label, icon: Icon, tone = "blue", onClick }: { label: string; icon: ElementType; tone?: Tone; onClick: () => void }) {
  const t = TONES[tone]
  return (
    <button type="button" className="hf-fuel-lift" onClick={onClick} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8, padding: "14px 8px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, cursor: "pointer", font: "inherit", color: "var(--hf-text-secondary)", fontSize: 12.5, fontWeight: 600 }}>
      <span aria-hidden="true" style={{ width: 36, height: 36, borderRadius: 10, display: "grid", placeItems: "center", background: t.soft, color: t.ink }}><Icon size={18} /></span>
      {label}
    </button>
  )
}
