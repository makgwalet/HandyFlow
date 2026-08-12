// src/pages/accountant-portal/portal-theme.ts
//
// Shared design tokens for the accountant client portal. Every portal
// page should pull from here instead of hardcoding hex values inline —
// the four pages this replaces each independently reimplemented very
// nearly the same palette (navy #1B3A6B, teal #0D9488, the status colors)
// with small, accidental drifts between them. One source of truth means
// a future fifth portal page (or a rebrand) is a one-file change, not a
// four-file hunt.
//
// Palette is NOT invented fresh — #1B3A6B and #0D9488 are HandyFlow's
// existing brand navy/teal, already used in the login page's logo mark
// and throughout the PDF generators (AccountingReportPdfService,
// CreativePdfGenerator, etc.). This extends the existing identity
// consistently rather than introducing a new one for just the portal.

export const color = {
  // Brand
  navy: "#1B3A6B",
  navyDark: "#132C52",
  teal: "#0D9488",

  // Semantic status — matches the status colors already used across
  // fee notes, document requests, and deadlines, unified into one place
  amber: "#D97706",
  amberBg: "#FFFBEB",
  red: "#DC2626",
  redBg: "#FEF2F2",
  green: "#166534",
  greenBg: "#DCFCE7",
  blue: "#1D4ED8",
  blueBg: "#EFF6FF",

  // Neutrals
  ink: "#0F172A",       // primary text
  slate: "#475569",     // secondary text
  muted: "#64748B",     // tertiary text / labels
  faint: "#94A3B8",     // placeholder / disabled text
  border: "#E2E8F0",
  borderLight: "#F1F5F9",
  surface: "#FFFFFF",
  canvas: "#F8FAFC",
} as const

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  pill: 999,
} as const

export const space = (n: number) => `${n * 4}px`

export const shadow = {
  // Barely-there elevation for resting cards — the previous pages used
  // a flat 1px border with zero shadow, which reads as "wireframe" more
  // than "designed." This is intentionally subtle, not a drop-shadow —
  // a financial document list should feel calm, not showy.
  card: "0 1px 2px rgba(15, 23, 42, 0.04), 0 1px 3px rgba(15, 23, 42, 0.06)",
  cardHover: "0 4px 12px rgba(15, 23, 42, 0.08), 0 2px 4px rgba(15, 23, 42, 0.04)",
  modal: "0 24px 64px rgba(15, 23, 42, 0.24)",
} as const

export const type = {
  // Display face pairing: system-ui alone (what all four pages used
  // before) has no real personality and no weight contrast — every
  // piece of text on the page competed at the same visual volume. This
  // keeps system-ui as the workhorse (it's genuinely the right choice
  // for a dense, data-heavy utility surface — no web font loading cost,
  // renders instantly, feels native) but establishes real hierarchy
  // through weight, size, and letter-spacing instead of relying on a
  // second typeface to do that work.
  family: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif",
  mono: "'SF Mono', 'Roboto Mono', ui-monospace, monospace",
} as const

// Semantic status → color mapping, used across fee notes, documents,
// requests, and deadlines. Previously each page defined its own
// STATUS_COLORS-shaped constant independently (confirmed near-identical
// but not byte-identical across files) — centralizing here means they
// can't silently drift.
export const statusTone: Record<string, { color: string; bg: string }> = {
  DRAFT:     { color: color.muted, bg: color.borderLight },
  SENT:      { color: color.blue,  bg: color.blueBg },
  BILLED:    { color: color.blue,  bg: color.blueBg },
  PENDING:   { color: color.amber, bg: color.amberBg },
  PARTIAL:   { color: color.blue,  bg: color.blueBg },
  PAID:      { color: color.green, bg: color.greenBg },
  COMPLETE:  { color: color.green, bg: color.greenBg },
  FILED:     { color: color.green, bg: color.greenBg },
  OVERDUE:   { color: color.red,   bg: color.redBg },
  CANCELLED: { color: color.muted, bg: color.borderLight },
}
