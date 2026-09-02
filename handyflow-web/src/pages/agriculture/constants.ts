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

export const AG_ACCENT = "#166534" // green-800 — a farm-operations tone, distinct from every prior module's own accent

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
  ACTIVE: ["#DCFCE7", "#166534"],
  INACTIVE: ["#F1F5F9", "#64748B"],
  CLOSED: ["#F1F5F9", "#64748B"],
  SOLD: ["#EFF6FF", "#1D4ED8"],
  DECEASED: ["#FEF2F2", "#DC2626"],
  CULLED: ["#FEF2F2", "#DC2626"],
  TRANSFERRED_OUT: ["#F5F3FF", "#6D28D9"],
  COMPLETED: ["#DCFCE7", "#166534"],
  SCHEDULED: ["#FFFBEB", "#D97706"],
  DUE: ["#FFFBEB", "#D97706"],
  OVERDUE: ["#FEF2F2", "#DC2626"],
  PREGNANT_UNCONFIRMED: ["#FFFBEB", "#D97706"],
  CONFIRMED_PREGNANT: ["#EFF6FF", "#1D4ED8"],
  BORN: ["#DCFCE7", "#166534"],
  ABORTED: ["#FEF2F2", "#DC2626"],
  FAILED: ["#FEF2F2", "#DC2626"],
  NOT_PREGNANT: ["#F1F5F9", "#64748B"],
}

export function statusBadge(status: string | null | undefined) {
  const [bg, fg] = STATUS_COLORS[status ?? ""] ?? ["#F1F5F9", "#64748B"]
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
