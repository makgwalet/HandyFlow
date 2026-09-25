// src/pages/agriculture/constants.ts
//
// Module 7 (Agriculture — a tenant operating its own farms, distinct from
// every provider-module pattern this session has built: no external
// clients, no client portal. Structurally closest to earthmoving/fleet
// (tenant runs its own physical assets), confirmed against
// za.co.handyflow.platform.agriculture — 7 controllers for Farm
// Foundation + Livestock (Increment 1), Crops + Cost Reporting to follow
// as a later delivery matching the backend's own increment split.
//
// Central design decision carried through every history sub-tab in this
// module: every Livestock history entity (AgWeightRecord, AgHealthEvent,
// AgBreedingRecord, AgMovementRecord, AgMortalityRecord, AgFeedRecord)
// belongs to EITHER an animal OR a group, never both — enforced server-side
// via AgTrackingTarget.requireExactlyOne() plus a DB CHECK constraint. The
// six *HistoryTab components in this package are built ONCE and shared
// between AgAnimalDetail and AgGroupDetail via a targetType prop, rather
// than duplicated per target — the API paths and request shapes are
// identical apart from the /animals/ vs /groups/ prefix and which of
// animalId/groupId is populated.
import type React from "react"

// Module accent (green-800 in light mode). Two tokens because fills and text
// diverge in dark mode: fills stay dark enough for white labels, text gets
// lighter to stay readable on dark surfaces.
export const AG_ACCENT = "var(--hf-success-solid-strong)"      // backgrounds, borders
export const AG_ACCENT_TEXT = "var(--hf-success-text-strong)"  // text and icons

export const fmtDate = (d: string | null | undefined) => (d ? d : "—")

export const fmtMoney = (n: number | null | undefined) =>
  n == null ? "—" : new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n)

export const fmtDateTime = (iso: string | null | undefined) => {
  if (!iso) return "—"
  try {
    return new Date(iso).toLocaleString("en-ZA", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })
  } catch {
    return iso
  }
}

export const badgeStyle = (bg: string, fg: string): React.CSSProperties => ({
  display: "inline-block", fontSize: 10.5, fontWeight: 700, padding: "2px 8px",
  borderRadius: 20, background: bg, color: fg, whiteSpace: "nowrap",
})

export const STATUS_COLORS: Record<string, [string, string]> = {
  ACTIVE: ["var(--hf-success-soft-strong)", "var(--hf-success-text-strong)"],
  INACTIVE: ["var(--hf-surface-sunken)", "var(--hf-text-muted)"],
  CLOSED: ["var(--hf-surface-sunken)", "var(--hf-text-muted)"],
  SOLD: ["var(--hf-info-soft)", "var(--hf-info-text)"],
  DECEASED: ["var(--hf-danger-soft)", "var(--hf-danger-text)"],
  CULLED: ["var(--hf-danger-soft)", "var(--hf-danger-text)"],
  TRANSFERRED_OUT: ["var(--hf-violet-soft)", "var(--hf-violet-text)"],
  COMPLETED: ["var(--hf-success-soft-strong)", "var(--hf-success-text-strong)"],
  SCHEDULED: ["var(--hf-warning-soft)", "var(--hf-warning-text)"],
  DUE: ["var(--hf-warning-soft)", "var(--hf-warning-text)"],
  OVERDUE: ["var(--hf-danger-soft)", "var(--hf-danger-text)"],
  PREGNANT_UNCONFIRMED: ["var(--hf-warning-soft)", "var(--hf-warning-text)"],
  CONFIRMED_PREGNANT: ["var(--hf-info-soft)", "var(--hf-info-text)"],
  BORN: ["var(--hf-success-soft-strong)", "var(--hf-success-text-strong)"],
  ABORTED: ["var(--hf-danger-soft)", "var(--hf-danger-text)"],
  FAILED: ["var(--hf-danger-soft)", "var(--hf-danger-text)"],
  NOT_PREGNANT: ["var(--hf-surface-sunken)", "var(--hf-text-muted)"],
}

export function statusBadge(status: string | null | undefined) {
  const [bg, fg] = STATUS_COLORS[status ?? ""] ?? ["var(--hf-surface-sunken)", "var(--hf-text-muted)"]
  return badgeStyle(bg, fg)
}

// Shared sub-resource target — every Livestock history entity hangs off
// either an animal or a group. Every *HistoryTab / AgEvidenceTab component
// in this package takes { targetType, targetId } and builds its own API
// paths as `/api/v1/agriculture/${targetType === "animal" ? "animals" : "groups"}/${targetId}/...`.
export type AgTargetType = "animal" | "group"

export function targetBasePath(targetType: AgTargetType, targetId: string) {
  return `/api/v1/agriculture/${targetType === "animal" ? "animals" : "groups"}/${targetId}`
}
