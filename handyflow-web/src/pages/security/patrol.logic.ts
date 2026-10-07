// src/pages/security/patrol.logic.ts
//
// Pure rules for the Patrols screens: status wording, the summary numbers, filters and the date presets. No React, no
// network. The server decides each round's status; this only presents it.
import type { Tone } from "./guard360.logic"
import { addDays, dayKey, dayStart } from "./schedule.logic"

export interface PatrolRound {
  id: string; shiftId: string; siteId: string; siteName: string | null; routeName: string | null; guardName: string | null
  roundNumber: number; status: string; expectedStartAt: string | null; expectedEndAt: string | null
  startedAt: string | null; completedAt: string | null; checkpointsExpected: number; checkpointsScanned: number
  offSchedule: boolean; offScheduleReason: string | null; acknowledged: boolean
}
export interface PatrolCheckpoint { id: string; name: string; sequence: number; scannedAt: string | null; method: string | null; scannedBy: string | null }
export interface PatrolDetail { round: PatrolRound; acknowledgementNote: string | null; checkpoints: PatrolCheckpoint[] }

export const STATUS_LABEL: Record<string, string> = { EXPECTED: "Not started", IN_PROGRESS: "In progress", COMPLETE: "Complete", PARTIAL: "Partial", MISSED: "Missed" }
export const STATUS_TONE: Record<string, Tone> = { EXPECTED: "neutral", IN_PROGRESS: "info", COMPLETE: "ok", PARTIAL: "warn", MISSED: "bad" }
export const STATUS_FILTERS = [{ value: "", label: "Any status" }, ...Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label }))]

/** A round that needs a supervisor's eye: missed or partial and not yet acknowledged. */
export const needsAttention = (r: PatrolRound) => (r.status === "MISSED" || r.status === "PARTIAL") && !r.acknowledged
export const canAcknowledge = (r: PatrolRound) => (r.status === "MISSED" || r.status === "PARTIAL") && !r.acknowledged

export const progress = (r: Pick<PatrolRound, "checkpointsScanned" | "checkpointsExpected">) =>
  r.checkpointsExpected > 0 ? Math.min(100, Math.round(r.checkpointsScanned / r.checkpointsExpected * 100)) : 0

/**
 * Totals over the rounds shown. The completion rate is over rounds whose window has finished (complete, partial, missed),
 * so rounds still to come do not drag it down. Null when none has finished.
 */
export function summarise(rounds: PatrolRound[]) {
  const c = (s: string) => rounds.filter(r => r.status === s).length
  const complete = c("COMPLETE"), partial = c("PARTIAL"), missed = c("MISSED")
  const finished = complete + partial + missed
  return {
    total: rounds.length, complete, partial, missed, inProgress: c("IN_PROGRESS"), notStarted: c("EXPECTED"),
    offSchedule: rounds.filter(r => r.offSchedule).length,
    needAttention: rounds.filter(needsAttention).length,
    completionRate: finished === 0 ? null : Math.round(complete / finished * 100),
  }
}

export function filterRounds(rounds: PatrolRound[], f: { search: string; attention: boolean }): PatrolRound[] {
  const q = f.search.trim().toLowerCase()
  return rounds.filter(r => (!f.attention || needsAttention(r))
    && (!q || (r.guardName ?? "").toLowerCase().includes(q) || (r.siteName ?? "").toLowerCase().includes(q) || (r.routeName ?? "").toLowerCase().includes(q)))
}

export const PRESETS = [{ id: "today", label: "Today", days: 1 }, { id: "7", label: "Last 7 days", days: 7 }, { id: "30", label: "Last 30 days", days: 30 }] as const

/** The server window for a preset: the last `days` South African days up to and including today. */
export function presetWindow(days: number, now: Date = new Date()): { from: Date; to: Date } {
  const today = dayKey(now)
  return { from: dayStart(addDays(today, -(days - 1))), to: dayStart(addDays(today, 1)) }
}

/** Checkpoints scanned versus the route's list: the names still to scan, in route order. */
export const unscanned = (cps: PatrolCheckpoint[]) => cps.filter(c => !c.scannedAt)
export const METHOD_LABEL: Record<string, string> = { QR: "QR", NFC: "NFC", BLE: "Bluetooth", GPS_PING: "GPS", MANUAL: "Manual" }
