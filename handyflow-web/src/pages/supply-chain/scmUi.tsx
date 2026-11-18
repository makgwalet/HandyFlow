// src/pages/supply-chain/scmUi.tsx — small presentational pieces shared by the redesigned Supply Chain screens.
import type { ReactNode, ElementType } from "react"
import { Check } from "lucide-react"

export type Tone = "ok" | "warn" | "bad" | "info" | "neutral"
const TONE: Record<Tone, { bg: string; fg: string }> = {
  ok:      { bg: "var(--hf-success-soft-strong)", fg: "var(--hf-success-text-strong)" },
  warn:    { bg: "var(--hf-warning-soft-strong)", fg: "var(--hf-warning-text-deep)" },
  bad:     { bg: "var(--hf-danger-soft)",         fg: "var(--hf-danger-text)" },
  info:    { bg: "var(--hf-info-soft-strong)",    fg: "var(--hf-info-text)" },
  neutral: { bg: "var(--hf-surface-sunken)",      fg: "var(--hf-text-tertiary)" },
}
export const toneColors = (t: Tone) => TONE[t]

export function StatCard({ label, value, sub, icon: Icon, tone = "neutral", onClick }: { label: string; value: ReactNode; sub?: ReactNode; icon?: ElementType; tone?: Tone; onClick?: () => void }) {
  const c = TONE[tone]
  return (
    <div role={onClick ? "button" : undefined} tabIndex={onClick ? 0 : undefined} onClick={onClick} onKeyDown={e => { if (onClick && e.key === "Enter") onClick() }}
      style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "16px 18px", cursor: onClick ? "pointer" : "default", minWidth: 0 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
        <span style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.06em" }}>{label}</span>
        {Icon && <span style={{ width: 30, height: 30, borderRadius: 8, background: c.bg, display: "inline-flex", alignItems: "center", justifyContent: "center" }}><Icon size={15} style={{ color: c.fg }} /></span>}
      </div>
      <div style={{ fontSize: 24, fontWeight: 700, color: "var(--hf-text)" }}>{value}</div>
      {sub != null && <div style={{ fontSize: 12, color: "var(--hf-text-tertiary)", marginTop: 4 }}>{sub}</div>}
    </div>
  )
}

export function Panel({ title, right, children }: { title: ReactNode; right?: ReactNode; children: ReactNode }) {
  return (
    <section style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, minWidth: 0 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <h4 style={{ margin: 0, fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>{title}</h4>{right}
      </div>
      {children}
    </section>
  )
}

export function Pill({ tone, children }: { tone: Tone; children: ReactNode }) {
  const c = TONE[tone]
  return <span style={{ background: c.bg, color: c.fg, fontSize: 11, fontWeight: 700, padding: "2px 9px", borderRadius: 20, whiteSpace: "nowrap" }}>{children}</span>
}

/** Horizontal progress stepper. `current` -1 means the item left the path (nothing highlighted). */
export function Stepper({ steps, current }: { steps: readonly string[]; current: number }) {
  return (
    <ol aria-label="Progress" style={{ display: "flex", listStyle: "none", margin: 0, padding: 0, gap: 4, alignItems: "center" }}>
      {steps.map((s, i) => {
        const done = current > i, here = current === i
        return (
          <li key={s} aria-current={here ? "step" : undefined} style={{ display: "flex", alignItems: "center", gap: 6, flex: i < steps.length - 1 ? 1 : undefined }}>
            <span style={{ width: 22, height: 22, borderRadius: 11, display: "inline-flex", alignItems: "center", justifyContent: "center", fontSize: 11, fontWeight: 700,
              background: done || here ? "var(--hf-primary)" : "var(--hf-surface-sunken)", color: done || here ? "var(--hf-text-on-solid)" : "var(--hf-text-faint)" }}>
              {done ? <Check size={12} /> : i + 1}
            </span>
            <span style={{ fontSize: 12, fontWeight: here ? 700 : 500, color: here ? "var(--hf-text)" : "var(--hf-text-tertiary)" }}>{s}</span>
            {i < steps.length - 1 && <span style={{ flex: 1, height: 2, background: done ? "var(--hf-primary)" : "var(--hf-border)", minWidth: 12 }} />}
          </li>
        )
      })}
    </ol>
  )
}

export const grid = (min = 200): React.CSSProperties => ({ display: "grid", gridTemplateColumns: `repeat(auto-fit, minmax(${min}px, 1fr))`, gap: 12 })
