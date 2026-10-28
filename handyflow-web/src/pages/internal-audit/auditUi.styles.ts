// Colours and layout helpers shared by the Internal Audit screens.
import type { CSSProperties } from "react"
import type { Group } from "./auditUi.logic"

export const GROUP_COLOR: Record<Group, string> = {
  completed: "var(--hf-success-text)", inProgress: "var(--hf-primary)", planned: "var(--hf-border-strong)", deferred: "var(--hf-text-faint)",
}
export const SEVERITY_COLOR: Record<string, string> = {
  CRITICAL: "var(--hf-danger-text)", HIGH: "var(--hf-orange-text-strong)", MEDIUM: "var(--hf-warning-text)", LOW: "var(--hf-success-text)",
}

export const filterField: CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12.5, background: "var(--hf-surface)", color: "var(--hf-text-secondary)" }

/** A responsive grid: as many columns of at least `min` pixels as fit. */
export const grid = (min: number, gap = 14): CSSProperties => ({ display: "grid", gridTemplateColumns: `repeat(auto-fit, minmax(${min}px, 1fr))`, gap })
