// src/pages/invoicing/ui.tsx
//
// Small building blocks shared by the billing screens, written against the theme tokens: the section tabs, headline
// tiles, filter pills, search box, sortable headers, pager, dialog, form field, buttons and the loading, empty and
// error states. Kept together because they are only used by these screens.
import { useEffect, useId, useRef, type CSSProperties, type ElementType, type ReactNode } from "react"
import { NavLink } from "react-router-dom"
import { AlertCircle, ChevronDown, ChevronLeft, ChevronRight, ChevronUp, Search, X } from "lucide-react"
import { toneColor, type ChipTone } from "../../components/ui/Chip"
import type { SortDir } from "./billing.logic"

export const SECTIONS = [
  { to: "/quotes", label: "Quotes" }, { to: "/invoices", label: "Invoices" }, { to: "/recurring", label: "Recurring" },
  { to: "/retainers", label: "Retainers" }, { to: "/credit-notes", label: "Credit notes" },
]

/** The tab strip across the top of every billing screen. Each tab is its own route, so each can be linked to. */
export function BillingNav() {
  return (
    <nav aria-label="Billing sections" style={{ display: "flex", gap: 4, flexWrap: "wrap", marginBottom: 20, borderBottom: "1px solid var(--hf-border)" }}>
      {SECTIONS.map(s => (
        <NavLink key={s.to} to={s.to} end={false}
          style={({ isActive }) => ({
            padding: "9px 14px", fontSize: 13.5, fontWeight: 600, textDecoration: "none", marginBottom: -1,
            color: isActive ? "var(--hf-primary-text)" : "var(--hf-text-muted)",
            borderBottom: `2px solid ${isActive ? "var(--hf-primary)" : "transparent"}`,
          })}>
          {s.label}
        </NavLink>
      ))}
    </nav>
  )
}

export const panel: CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }
export const fieldStyle: CSSProperties = { width: "100%", padding: "9px 11px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text-primary)" }

export interface KpiProps { label: string; value: ReactNode; hint?: string; tone?: ChipTone; icon?: ElementType; onClick?: () => void }
export function Kpis({ items }: { items: KpiProps[] }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 12, marginBottom: 18 }}>
      {items.map(k => <Kpi key={k.label} {...k} />)}
    </div>
  )
}
function Kpi({ label, value, hint, tone = "neutral", icon: Icon, onClick }: KpiProps) {
  const c = tone === "neutral" ? "var(--hf-text-secondary)" : toneColor(tone)
  const body = (
    <>
      {Icon && <div style={{ width: 36, height: 36, borderRadius: 10, background: "var(--hf-surface-sunken)", color: c, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}><Icon size={17} /></div>}
      <div style={{ minWidth: 0, textAlign: "left" }}>
        <div style={{ fontSize: 21, fontWeight: 800, color: c, lineHeight: 1.15 }}>{value}</div>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 3 }}>{label}</div>
        {hint && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 1 }}>{hint}</div>}
      </div>
    </>
  )
  const style: CSSProperties = { ...panel, padding: "13px 15px", display: "flex", gap: 12, alignItems: "center", font: "inherit", color: "inherit" }
  return onClick
    ? <button type="button" onClick={onClick} aria-label={`${label}: ${typeof value === "string" || typeof value === "number" ? value : ""}`} style={{ ...style, cursor: "pointer" }}>{body}</button>
    : <div style={style}>{body}</div>
}

export function FilterPills({ label, options, value, onChange }: { label: string; options: { value: string; label: string; count?: number }[]; value: string; onChange: (v: string) => void }) {
  return (
    <div role="group" aria-label={label} style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
      {options.map(o => {
        const on = o.value === value
        return (
          <button key={o.value} type="button" aria-pressed={on} onClick={() => onChange(o.value)}
            style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: "pointer", border: `1px solid ${on ? "var(--hf-primary)" : "var(--hf-border)"}`,
              background: on ? "var(--hf-primary)" : "var(--hf-surface)", color: on ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
            {o.label}{o.count != null && <span style={{ opacity: 0.75 }}> {o.count}</span>}
          </button>
        )
      })}
    </div>
  )
}

export function SearchBox({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  return (
    <label style={{ position: "relative", display: "inline-flex", alignItems: "center", minWidth: 220, flex: "1 1 220px", maxWidth: 340 }}>
      <Search size={14} aria-hidden="true" style={{ position: "absolute", left: 10, color: "var(--hf-text-faint)" }} />
      <input type="search" aria-label={placeholder} placeholder={placeholder} value={value} onChange={e => onChange(e.target.value)} style={{ ...fieldStyle, paddingLeft: 30 }} />
    </label>
  )
}

export function Th({ children, sortKey, active, dir, onSort, align }: { children?: ReactNode; sortKey?: string; active?: string; dir?: SortDir; onSort?: (k: string) => void; align?: "right" }) {
  const base: CSSProperties = { textAlign: align ?? "left", padding: "10px 14px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em", whiteSpace: "nowrap" }
  if (!sortKey || !onSort) return <th style={base}>{children}</th>
  const on = active === sortKey
  return (
    <th style={base} aria-sort={on ? (dir === "asc" ? "ascending" : "descending") : "none"}>
      <button type="button" onClick={() => onSort(sortKey)} style={{ all: "unset", cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 3, color: on ? "var(--hf-text-secondary)" : "inherit" }}>
        {children}{on && (dir === "asc" ? <ChevronUp size={12} /> : <ChevronDown size={12} />)}
      </button>
    </th>
  )
}
export const td: CSSProperties = { padding: "12px 14px", fontSize: 13, color: "var(--hf-text-secondary)", verticalAlign: "middle" }

export function TableShell({ children }: { children: ReactNode }) {
  return <div style={{ ...panel, overflow: "hidden" }}><div style={{ overflowX: "auto" }}><table style={{ width: "100%", borderCollapse: "collapse" }}>{children}</table></div></div>
}
export const trStyle = (clickable = true): CSSProperties => ({ borderTop: "1px solid var(--hf-border-subtle)", cursor: clickable ? "pointer" : "default" })

export function Pager({ page, pages, from, to, total, onPage }: { page: number; pages: number; from: number; to: number; total: number; onPage: (p: number) => void }) {
  if (total === 0) return null
  const b: CSSProperties = { display: "inline-flex", alignItems: "center", padding: "5px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer" }
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 12, fontSize: 12, color: "var(--hf-text-muted)", flexWrap: "wrap", gap: 8 }}>
      <span>Showing {from} to {to} of {total}</span>
      {pages > 1 && (
        <span style={{ display: "inline-flex", gap: 6, alignItems: "center" }}>
          <button type="button" aria-label="Previous page" disabled={page === 0} onClick={() => onPage(page - 1)} style={{ ...b, opacity: page === 0 ? 0.5 : 1 }}><ChevronLeft size={14} /></button>
          <span>Page {page + 1} of {pages}</span>
          <button type="button" aria-label="Next page" disabled={page >= pages - 1} onClick={() => onPage(page + 1)} style={{ ...b, opacity: page >= pages - 1 ? 0.5 : 1 }}><ChevronRight size={14} /></button>
        </span>
      )}
    </div>
  )
}

export function StateBox({ icon: Icon, title, text, action, tone = "neutral" }: { icon: ElementType; title: string; text?: string; action?: ReactNode; tone?: "neutral" | "bad" }) {
  return (
    <div style={{ ...panel, padding: "48px 24px", textAlign: "center" }}>
      <Icon size={32} aria-hidden="true" style={{ color: tone === "bad" ? "var(--hf-danger-text)" : "var(--hf-text-disabled)", marginBottom: 10 }} />
      <div role={tone === "bad" ? "alert" : undefined} style={{ fontWeight: 700, color: tone === "bad" ? "var(--hf-danger-text)" : "var(--hf-text-tertiary)" }}>{title}</div>
      {text && <div style={{ fontSize: 13, color: "var(--hf-text-faint)", marginTop: 4 }}>{text}</div>}
      {action && <div style={{ marginTop: 16 }}>{action}</div>}
    </div>
  )
}
export const Loading = ({ text }: { text: string }) => <div style={{ ...panel, padding: 48, textAlign: "center", color: "var(--hf-text-faint)" }}>{text}</div>
export const LoadError = ({ onRetry }: { onRetry?: () => void }) => (
  <StateBox icon={AlertCircle} tone="bad" title="This could not be loaded" text="Check your connection and try again."
    action={onRetry && <Btn variant="secondary" onClick={onRetry}>Try again</Btn>} />
)

export function Notice({ tone, children }: { tone: "bad" | "warn" | "ok" | "info"; children: ReactNode }) {
  const c = { bad: ["var(--hf-danger-soft)", "var(--hf-danger-border)", "var(--hf-danger-text)"], warn: ["var(--hf-warning-soft)", "var(--hf-warning-border-strong)", "var(--hf-warning-text-deep)"],
    ok: ["var(--hf-success-soft)", "var(--hf-success-border-subtle)", "var(--hf-success-text-strong)"], info: ["var(--hf-info-soft)", "var(--hf-info-border)", "var(--hf-info-text)"] }[tone]
  return <div role={tone === "bad" ? "alert" : "status"} style={{ display: "flex", gap: 8, alignItems: "flex-start", padding: "10px 12px", background: c[0], border: `1px solid ${c[1]}`, borderRadius: 8, fontSize: 13, color: c[2] }}>{children}</div>
}

type BtnVariant = "primary" | "secondary" | "success" | "danger" | "ghost"
const VARIANT: Record<BtnVariant, CSSProperties> = {
  primary: { background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "1px solid transparent" },
  secondary: { background: "var(--hf-surface)", color: "var(--hf-text-secondary)", border: "1px solid var(--hf-border)" },
  success: { background: "var(--hf-success)", color: "var(--hf-text-on-solid)", border: "1px solid transparent" },
  danger: { background: "var(--hf-danger)", color: "var(--hf-text-on-solid)", border: "1px solid transparent" },
  ghost: { background: "transparent", color: "var(--hf-primary-text)", border: "1px solid transparent" },
}
export function Btn({ variant = "secondary", icon: Icon, children, small, ...rest }: { variant?: BtnVariant; icon?: ElementType; small?: boolean } & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const off = rest.disabled
  return (
    <button type="button" {...rest}
      style={{ display: "inline-flex", alignItems: "center", gap: 6, padding: small ? "5px 10px" : "8px 14px", borderRadius: 8, fontSize: small ? 12 : 13, fontWeight: 600, whiteSpace: "nowrap",
        cursor: off ? "not-allowed" : "pointer", ...VARIANT[variant], ...(off ? { opacity: 0.55 } : {}), ...rest.style }}>
      {Icon && <Icon size={small ? 12 : 14} aria-hidden="true" />}{children}
    </button>
  )
}

/** A modal dialog: labelled, closes on Escape or the backdrop, and puts focus inside when it opens. */
export function Dialog({ title, subtitle, onClose, children, footer }: { title: string; subtitle?: string; onClose: () => void; children: ReactNode; footer: ReactNode }) {
  const id = useId()
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const first = ref.current?.querySelector<HTMLElement>("input, select, textarea, button")
    first?.focus()
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose() }
    document.addEventListener("keydown", onKey)
    return () => document.removeEventListener("keydown", onKey)
  }, [onClose])
  return (
    <div onMouseDown={e => { if (e.target === e.currentTarget) onClose() }}
      style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, padding: 16 }}>
      <div ref={ref} role="dialog" aria-modal="true" aria-labelledby={id}
        style={{ background: "var(--hf-surface)", borderRadius: 14, padding: 24, width: "100%", maxWidth: 460, boxShadow: "0 20px 60px rgba(0,0,0,0.25)", maxHeight: "90vh", overflowY: "auto" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 16, gap: 8 }}>
          <div>
            <h2 id={id} style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text-primary)" }}>{title}</h2>
            {subtitle && <p style={{ margin: "3px 0 0", fontSize: 13, color: "var(--hf-text-muted)" }}>{subtitle}</p>}
          </div>
          <button type="button" aria-label="Close" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex", padding: 2 }}><X size={18} /></button>
        </div>
        <div style={{ display: "grid", gap: 14 }}>{children}</div>
        <div style={{ display: "flex", gap: 10, marginTop: 20, justifyContent: "flex-end" }}>{footer}</div>
      </div>
    </div>
  )
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: (id: string) => ReactNode }) {
  const id = useId()
  return (
    <div>
      <label htmlFor={id} style={{ display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }}>{label}</label>
      {children(id)}
      {hint && <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "4px 0 0" }}>{hint}</p>}
    </div>
  )
}

/** Totals block: subtotal, VAT and total. */
export function Totals({ rows, total, totalLabel = "Total" }: { rows: [string, string][]; total: string; totalLabel?: string }) {
  return (
    <div style={{ minWidth: 240 }}>
      {rows.map(([l, v]) => <div key={l} style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", fontSize: 13, color: "var(--hf-text-muted)" }}><span>{l}</span><span>{v}</span></div>)}
      <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 0 0", fontSize: 16, fontWeight: 800, color: "var(--hf-text-primary)", borderTop: "1px solid var(--hf-border)", marginTop: 4 }}><span>{totalLabel}</span><span>{total}</span></div>
    </div>
  )
}

export function Meter({ percent, colour, label }: { percent: number; colour: string; label: string }) {
  return (
    <div role="progressbar" aria-label={label} aria-valuenow={Math.round(percent)} aria-valuemin={0} aria-valuemax={100}
      style={{ height: 6, borderRadius: 3, background: "var(--hf-surface-sunken)", overflow: "hidden" }}>
      <div style={{ height: "100%", width: `${percent}%`, background: colour, borderRadius: 3 }} />
    </div>
  )
}

export function Facts({ rows }: { rows: [string, ReactNode][] }) {
  return (
    <dl style={{ margin: 0, display: "grid", gap: 6 }}>
      {rows.map(([k, v]) => (
        <div key={k} style={{ display: "flex", justifyContent: "space-between", gap: 12, fontSize: 13 }}>
          <dt style={{ color: "var(--hf-text-muted)" }}>{k}</dt><dd style={{ margin: 0, fontWeight: 600, color: "var(--hf-text-primary)", textAlign: "right" }}>{v}</dd>
        </div>
      ))}
    </dl>
  )
}
export const sectionTitle: CSSProperties = { fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.06em", margin: "0 0 10px" }
