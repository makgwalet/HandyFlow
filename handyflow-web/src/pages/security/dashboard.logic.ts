// src/pages/security/dashboard.logic.ts
//
// Pure rules for the Security landing dashboard: the shape the server returns and how its figures are worded and toned.
// No React, no network. What needs attention, and in what order, is decided by the server.
import type { ChipTone } from "../../components/ui/Chip"

export interface AttentionItem { code: string; level: "DANGER" | "WARNING" | "INFO"; title: string; detail: string; count: number; section: string }
export interface ActiveShiftRow { id: string; guardId: string; guardName: string | null; siteId: string | null; siteName: string | null; startAt: string; endAt: string; actualStartAt: string | null; minutesLate: number | null }
export interface OpenIncidentRow { id: string; title: string; severity: string; status: string; siteId: string | null; siteName: string | null; createdAt: string }
export interface SecurityDashboard {
  asOf: string; activeSites: number; openAlarms: number
  shifts: { onDuty: number; scheduledToday: number; notStarted: number; missedToday: number; completedToday: number }
  workforce: { totalGuards: number; activeGuards: number; psiraExpired: number; psiraExpiring: number; competenciesExpired: number; competenciesExpiring: number }
  incidents: { open: number; unacknowledged: number; criticalOpen: number; last7Days: number }
  complaints: { open: number; urgent: number }
  gate: { onSite: number; overstayed: number; enteredToday: number }
  attention: AttentionItem[]; activeShifts: ActiveShiftRow[]; openIncidents: OpenIncidentRow[]
}

export const LEVEL_TONE: Record<string, ChipTone> = { DANGER: "bad", WARNING: "warn", INFO: "info" }
export const LEVEL_LABEL: Record<string, string> = { DANGER: "Urgent", WARNING: "Check", INFO: "Soon" }
export const SEVERITY_TONE: Record<string, ChipTone> = { LOW: "neutral", MEDIUM: "info", HIGH: "warn", CRITICAL: "bad" }

/** "Good morning" / "Good afternoon" / "Good evening" in the viewer's local time. */
export function greeting(now: Date = new Date()): string {
  const h = now.getHours()
  return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening"
}

/** The tone of a count that should be zero: nothing is calm, anything is the given tone. */
export const toneForCount = (n: number, whenNonZero: ChipTone): ChipTone => (n > 0 ? whenNonZero : "ok")

/** "Late by 22 min", "On time". A shift with no clock-in reads as "Not clocked in". */
export function punctualityLabel(minutesLate: number | null, actualStartAt: string | null): string {
  if (!actualStartAt) return "Not clocked in"
  if (minutesLate == null || minutesLate <= 15) return "On time"
  return `Late by ${minutesLate} min`
}
export const isLate = (minutesLate: number | null) => minutesLate != null && minutesLate > 15

/** "3 of 11 shifts done" style progress, never dividing by zero. */
export function shiftProgress(s: SecurityDashboard["shifts"]): string {
  if (s.scheduledToday === 0) return "No shifts scheduled today"
  return `${s.onDuty} on duty, ${s.completedToday} of ${s.scheduledToday} done today`
}

/** The most serious level present, for the headline: "Urgent", "Check" or "All clear". */
export function headline(items: AttentionItem[]): { tone: ChipTone; text: string } {
  if (items.some(i => i.level === "DANGER")) return { tone: "bad", text: `${items.filter(i => i.level === "DANGER").length} urgent` }
  if (items.some(i => i.level === "WARNING")) return { tone: "warn", text: `${items.filter(i => i.level === "WARNING").length} to check` }
  if (items.length > 0) return { tone: "info", text: `${items.length} coming up` }
  return { tone: "ok", text: "All clear" }
}
