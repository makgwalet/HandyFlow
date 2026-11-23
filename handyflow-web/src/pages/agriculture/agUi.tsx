// src/pages/agriculture/agUi.tsx
// The few pieces every Agriculture list and form repeats. They use theme tokens and the .hf-ag stylesheet, so they follow
// light and dark mode and show a keyboard focus ring.
import type React from "react"
import { cloneElement, isValidElement, useId, useRef } from "react"
import { AG_ACCENT, AG_ACCENT_TEXT, lbl } from "./constants"
import { nextTabIndex } from "./agUi.logic"

/**
 * A label tied to its control, so a screen reader announces it and clicking the label focuses the field.
 * Pass exactly one control (input, select or textarea) as the child. `hint` and `error` sit under it.
 */
export function Field({ label, hint, error, children }: { label: string; hint?: string; error?: string | null; children: React.ReactElement<{ id?: string; "aria-describedby"?: string; "aria-invalid"?: boolean }> }) {
  const id = useId()
  const noteId = `${id}-note`
  const note = error || hint
  const control = isValidElement(children)
    ? cloneElement(children, { id: children.props.id ?? id, "aria-describedby": note ? noteId : undefined, "aria-invalid": error ? true : undefined })
    : children
  const controlId = isValidElement(children) ? (children.props.id ?? id) : id
  return (
    <div>
      <label htmlFor={controlId} style={lbl}>{label}</label>
      {control}
      {note && <p id={noteId} role={error ? "alert" : undefined} style={{ fontSize: 11, margin: "4px 0 0", color: error ? "var(--hf-danger-text)" : "var(--hf-text-faint)" }}>{note}</p>}
    </div>
  )
}

/** Shown when a list could not be loaded, so a failed request is not mistaken for "nothing registered yet". */
export function QueryError({ onRetry, what = "this list", onBack }: { onRetry: () => void; what?: string; onBack?: () => void }) {
  return (
    <div role="alert" style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap", margin: "6px 0", padding: "10px 12px", border: "1px solid var(--hf-danger)", borderRadius: 10, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }}>
      <span>Could not load {what}. Check your connection and try again.</span>
      <button type="button" onClick={onRetry} style={{ padding: "5px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }}>Try again</button>
      {onBack && <button type="button" onClick={onBack} style={{ padding: "5px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "transparent", color: "var(--hf-text)", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }}>Back</button>}
    </div>
  )
}

export function EmptyNote({ children }: { children: React.ReactNode }) {
  return <p style={{ color: "var(--hf-text-faint)", fontSize: 13, margin: "6px 0" }}>{children}</p>
}

/**
 * A tab bar that works from the keyboard and with a screen reader: Left and Right move between tabs (Home and End jump),
 * only the open tab is in the Tab order, and each tab says whether it is selected. The caller shows the matching panel.
 */
export function AgTabs<K extends string>({ tabs, active, onChange, label, size = "md", spacing = 18 }: {
  tabs: { key: K; label: string }[]; active: K; onChange: (key: K) => void; label: string; size?: "sm" | "md"; spacing?: number
}) {
  const id = useId()
  const buttons = useRef(new Map<K, HTMLButtonElement>())
  const onKey = (e: React.KeyboardEvent, index: number) => {
    const to = nextTabIndex(e.key, index, tabs.length)
    if (to === null) return
    e.preventDefault()
    const next = tabs[to]
    onChange(next.key)
    buttons.current.get(next.key)?.focus()
  }
  const pad = size === "sm" ? "9px 14px" : "10px 16px"
  return (
    <div role="tablist" aria-label={label} style={{ display: "flex", gap: 4, borderBottom: "1px solid var(--hf-border)", marginBottom: spacing, overflowX: "auto" }}>
      {tabs.map((t, i) => {
        const on = t.key === active
        return (
          <button key={t.key} id={`${id}-${t.key}`} type="button" role="tab" aria-selected={on} tabIndex={on ? 0 : -1}
            ref={el => { if (el) buttons.current.set(t.key, el); else buttons.current.delete(t.key) }}
            onClick={() => onChange(t.key)} onKeyDown={e => onKey(e, i)}
            style={{ padding: pad, border: "none", background: "none", cursor: "pointer", fontSize: size === "sm" ? 12.5 : 13, fontWeight: 600, whiteSpace: "nowrap",
              color: on ? AG_ACCENT_TEXT : "var(--hf-text-muted)", borderBottom: on ? `2px solid ${AG_ACCENT}` : "2px solid transparent", marginBottom: -1 }}>
            {t.label}
          </button>
        )
      })}
    </div>
  )
}
