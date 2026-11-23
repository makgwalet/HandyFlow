// src/pages/agriculture/agAudit.logic.ts
//
// The "Added by ... · Last changed by ..." line under a record's heading (GET /api/v1/agriculture/audit/{type}/{id}).

export type AuditType =
  | "animals" | "groups" | "health-events" | "breeding-records" | "inventory-items" | "crop-cycles"
  | "weights" | "mortality" | "movements" | "feed-records" | "scouting" | "harvests" | "input-applications"
export interface AuditInfo { createdByName: string | null; createdAt: string | null; updatedByName: string | null; updatedAt: string | null }

const day = (iso: string | null) => (iso ? iso.slice(0, 10) : "")

/**
 * "Added by Thandi on 2026-11-02 · Last changed by Sipho on 2026-11-20". Records from before names were kept say only when they
 * were added. The change part is left out when nobody is known or it happened on the day it was added by the same person.
 */
export function auditText(a: AuditInfo | null | undefined): string {
  if (!a) return ""
  const added = a.createdByName ? `Added by ${a.createdByName}${a.createdAt ? ` on ${day(a.createdAt)}` : ""}` : a.createdAt ? `Added ${day(a.createdAt)}` : ""
  const sameAsAdded = !!a.updatedByName && a.updatedByName === a.createdByName && day(a.updatedAt) === day(a.createdAt)
  const changed = a.updatedByName && !sameAsAdded ? `Last changed by ${a.updatedByName}${a.updatedAt ? ` on ${day(a.updatedAt)}` : ""}` : ""
  return [added, changed].filter(Boolean).join(" · ")
}

/** " · by Thandi" to append to a row's detail line, or nothing when nobody is recorded (older rows). */
export function byText(a: AuditInfo | null | undefined): string {
  return a?.createdByName ? ` · by ${a.createdByName}` : ""
}
