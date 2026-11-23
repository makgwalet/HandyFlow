// src/pages/agriculture/agUi.logic.ts
// Helpers for the Agriculture screens that are not components (kept apart so agUi.tsx stays hot-reloadable).
import type React from "react"

/** The props that make a div behave as a button: focusable, and Enter or Space opens it. */
export function rowButtonProps(onActivate: () => void, label: string) {
  return {
    role: "button" as const, tabIndex: 0, "aria-label": label, className: "hf-ag-row",
    onClick: onActivate,
    onKeyDown: (e: React.KeyboardEvent) => {
      if (e.target !== e.currentTarget) return   // a button inside the row handles its own keys
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onActivate() }
    },
  }
}


/** A form row: as many columns as fit, never fewer than 170px each (so a phone gets one). */
export const formGrid: React.CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 10 }

/** Which tab a key moves to: Left and Right wrap around, Home and End jump to the ends. null for any other key. */
export function nextTabIndex(key: string, index: number, count: number): number | null {
  if (count <= 0) return null
  if (key === "ArrowRight") return (index + 1) % count
  if (key === "ArrowLeft") return (index - 1 + count) % count
  if (key === "Home") return 0
  if (key === "End") return count - 1
  return null
}
