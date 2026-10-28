// Look shared by the Internal Audit screens: stat tiles, the progress ring, severity bars, status pills, a filter bar and a pager.

import type { ReactNode } from "react"
import { donutArcs, groupTone, type Group, type Page, type Tone, type TrendView } from "./auditUi.logic"
import { GROUP_COLOR, filterField } from "./auditUi.styles"

const TONE: Record<Tone, { color: string; bg: string }> = {
  info: { color: "var(--hf-info-text)", bg: "var(--hf-info-soft)" },
  warning: { color: "var(--hf-warning-text-strong)", bg: "var(--hf-warning-soft)" },
  success: { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  muted: { color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)" },
}

export function Pill({ label, tone = "muted" }: { label: string; tone?: Tone }) {
  const c = TONE[tone]
  return <span style={{ fontSize: 11, fontWeight: 700, padding: "3px 10px", borderRadius: 20, background: c.bg, color: c.color, whiteSpace: "nowrap" }}>{label}</span>
}
export const StagePill = ({ label, group }: { label: string; group: Group }) => <Pill label={label} tone={groupTone(group)} />

export function Panel({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, minWidth: 0 }}>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, color: "var(--hf-text)" }}>{title}</h3>
        {action}
      </header>
      {children}
    </section>
  )
}

export function Stat({ label, value, sub, tone, onClick, trend }: { label: string; value: ReactNode; sub?: string; tone?: "danger" | "warning"; onClick?: () => void; trend?: TrendView | null }) {
  const color = tone === "danger" ? "var(--hf-danger-text)" : tone === "warning" ? "var(--hf-warning-text)" : "var(--hf-text)"
  const body = (
    <>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 6 }}>{label}</div>
      <div style={{ fontSize: 28, fontWeight: 800, color, lineHeight: 1.1 }}>{value}</div>
      {sub && <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)", marginTop: 6 }}>{sub}</div>}
      {trend && (
        <div title={trend.spoken} style={{ fontSize: 11.5, marginTop: 6, fontWeight: 600, color: trend.tone === "good" ? "var(--hf-success-text)" : trend.tone === "bad" ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>
          <span aria-hidden>{trend.arrow} </span>{trend.text}
        </div>
      )}
    </>
  )
  const box = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "14px 16px", textAlign: "left" as const, minWidth: 0 }
  return onClick
    ? <button type="button" onClick={onClick} style={{ ...box, cursor: "pointer", font: "inherit" }}>{body}</button>
    : <div style={box}>{body}</div>
}

/** A ring split by group, with the figure in the middle. Labelled for screen readers by the caller's text beside it. */
export function Ring({ parts, centre, caption, size = 150 }: { parts: { key: Group; value: number }[]; centre: string; caption?: string; size?: number }) {
  const r = 52, c = 2 * Math.PI * r
  const arcs = donutArcs(parts.map(p => ({ key: p.key, value: p.value, color: GROUP_COLOR[p.key] })), c)
  return (
    <svg width={size} height={size} viewBox="0 0 140 140" role="img" aria-label={`${centre}${caption ? `, ${caption}` : ""}`}>
      <circle cx="70" cy="70" r={r} fill="none" stroke="var(--hf-surface-sunken)" strokeWidth="14" />
      {arcs.map(a => (
        <circle key={a.key} cx="70" cy="70" r={r} fill="none" stroke={a.color} strokeWidth="14"
          strokeDasharray={`${a.length} ${c - a.length}`} strokeDashoffset={a.offset} transform="rotate(-90 70 70)" />
      ))}
      <text x="70" y={caption ? 68 : 75} textAnchor="middle" fontSize="24" fontWeight="800" fill="var(--hf-text)">{centre}</text>
      {caption && <text x="70" y="88" textAnchor="middle" fontSize="10" fill="var(--hf-text-muted)">{caption}</text>}
    </svg>
  )
}

export function Meter({ pct, color = "var(--hf-primary)", label }: { pct: number; color?: string; label: string }) {
  const v = Math.max(0, Math.min(100, pct))
  return (
    <div role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={v}
      style={{ height: 8, background: "var(--hf-surface-sunken)", borderRadius: 99, overflow: "hidden" }}>
      <div style={{ width: `${v}%`, height: "100%", background: color, borderRadius: 99 }} />
    </div>
  )
}

export function Legend({ items }: { items: { label: string; value: number; color: string }[] }) {
  return (
    <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8, fontSize: 13 }}>
      {items.map(i => (
        <li key={i.label} style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span aria-hidden style={{ width: 10, height: 10, borderRadius: 99, background: i.color }} />
          <span style={{ color: "var(--hf-text-secondary)", flex: 1 }}>{i.label}</span>
          <strong style={{ color: "var(--hf-text)" }}>{i.value}</strong>
        </li>
      ))}
    </ul>
  )
}

// ── Filters and paging ────────────────────────────────────────────────────────────────────────────


export function FilterBar({ children }: { children: ReactNode }) {
  return <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 12 }}>{children}</div>
}

export function Pager<T>({ page, onPage }: { page: Page<T>; onPage: (n: number) => void }) {
  if (page.total === 0) return null
  const btn = (n: number, label: string, disabled: boolean) => (
    <button type="button" key={label} disabled={disabled} onClick={() => onPage(n)} aria-label={label}
      style={{ ...filterField, cursor: disabled ? "default" : "pointer", opacity: disabled ? 0.45 : 1 }}>{label === "Previous page" ? "‹" : "›"}</button>
  )
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 10, fontSize: 12, color: "var(--hf-text-muted)" }}>
      <span>Showing {page.from}–{page.to} of {page.total}</span>
      <span style={{ display: "flex", gap: 6, alignItems: "center" }}>
        {btn(page.page - 1, "Previous page", page.page <= 1)}
        <span>Page {page.page} of {page.pages}</span>
        {btn(page.page + 1, "Next page", page.page >= page.pages)}
      </span>
    </div>
  )
}

