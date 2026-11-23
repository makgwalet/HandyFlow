import { abbreviationIn, dateTimeIn, dayIn, getCompanyZone, timeIn } from "./companyZone"
import { printCss } from "./printSection"

// Occurrence Book: wording and small rules. The server decides what is in the book; this only words it.

export type ObKind = "ON_DUTY" | "OFF_DUTY" | "PATROL" | "GATE_IN" | "GATE_OUT" | "INCIDENT" | "ALARM" | "NOTE" | "HANDOVER" | "CORRECTION"

export interface ObLine { at: string; kind: ObKind | string; text: string | null; guardId: string | null; guardName: string | null; refId: string | null }
export interface ObSignoff { signedBy: string | null; signedByName: string | null; signedAt: string; note: string | null }
export interface ObDay { siteId: string; siteName: string; date: string; lines: ObLine[]; truncated: boolean; signoff?: ObSignoff | null }

export const KIND_LABEL: Record<string, string> = {
  ON_DUTY: "On duty", OFF_DUTY: "Off duty", PATROL: "Patrol", GATE_IN: "Gate in", GATE_OUT: "Gate out",
  INCIDENT: "Incident", ALARM: "Alarm", NOTE: "Note", HANDOVER: "Handover", CORRECTION: "Correction",
}

export type Tone = "ok" | "warn" | "bad" | "info" | "accent" | "neutral"
export const KIND_TONE: Record<string, Tone> = {
  INCIDENT: "bad", ALARM: "bad", CORRECTION: "warn", HANDOVER: "accent", NOTE: "info",
  ON_DUTY: "ok", OFF_DUTY: "neutral", PATROL: "neutral", GATE_IN: "neutral", GATE_OUT: "neutral",
}

export const MIN_TEXT = 3
export const MAX_TEXT = 2000

export function kindLabel(k: string): string { return KIND_LABEL[k] ?? k }

/** Today as YYYY-MM-DD in the company's zone (South Africa unless the company has set another). The name is kept for existing callers. */
export function todaySast(now: Date = new Date()): string { return dayIn(getCompanyZone(), now) }

/** Move a YYYY-MM-DD date by whole days, without any timezone drift. */
export function shiftDay(date: string, days: number): string {
  const [y, m, d] = date.split("-").map(Number)
  const t = new Date(Date.UTC(y, m - 1, d + days))
  return t.toISOString().slice(0, 10)
}

export function canGoNext(date: string, now: Date = new Date()): boolean { return date < todaySast(now) }

export function timeText(iso: string): string {
  return timeIn(getCompanyZone(), iso)
}

export function dayTitle(date: string): string {
  const [y, m, d] = date.split("-").map(Number)
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString("en-ZA", { weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "UTC" })
}

/** A problem with the text of a new entry, or null when it is fine. */
export function entryProblem(text: string): string | null {
  const t = text.trim()
  if (t.length < MIN_TEXT) return `Write at least ${MIN_TEXT} characters.`
  if (t.length > MAX_TEXT) return `Keep it under ${MAX_TEXT} characters.`
  return null
}

export function emptyText(date: string, today: string): string {
  return date === today ? "Nothing has been recorded at this site yet today." : "Nothing was recorded at this site on this day."
}

/** A stable reference so a double click or a retry cannot add the entry twice. */
export function newClientRef(): string {
  const c = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto
  return c?.randomUUID ? c.randomUUID() : `web-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

export function entryBody(kind: string, text: string, clientRef: string) {
  return { kind, text: text.trim(), clientRef }
}

/** Entry types that may be added: a signed day takes corrections only. */
export function allowedKinds(signed: boolean): string[] { return signed ? ["CORRECTION"] : ["NOTE", "HANDOVER", "CORRECTION"] }

/** The kind to use: the chosen one if allowed, else the first allowed. */
export function effectiveKind(chosen: string, signed: boolean): string {
  const a = allowedKinds(signed)
  return a.includes(chosen) ? chosen : a[0]
}

export function canSign(canManage: boolean, signed: boolean): boolean { return canManage && !signed }

export function signedText(s: ObSignoff): string {
  const when = dateTimeIn(getCompanyZone(), s.signedAt)
  return `Signed off by ${s.signedByName?.trim() || "a supervisor"} on ${when}.`
}

export function signWarning(date: string, today: string): string {
  return date === today
    ? "Today is not over. Once signed, anything added later today has to be a Correction."
    : "Once signed, only Corrections can be added to this day."
}

export function signBody(note: string): { note?: string } {
  const n = note.trim()
  return n ? { note: n } : {}
}

/** The heading of a printed day. */
export function printTitle(siteName: string, date: string): string { return `Daily Occurrence Book — ${siteName} — ${dayTitle(date)}` }

/** The line a printed page carries about sign-off. An unsigned day says so plainly. */
export function printSignLine(signoff: ObSignoff | null | undefined): string {
  return signoff ? signedText(signoff) + (signoff.note ? ` Note: ${signoff.note}` : "") : "NOT SIGNED OFF."
}

export function printedAt(now: Date = new Date()): string {
  const zone = getCompanyZone()
  return "Printed " + dateTimeIn(zone, now) + " (" + abbreviationIn(zone, now) + ")"
}

/** Print rules: only this card shows on paper when its print button was used (see printSection). */
export const PRINT_CSS = printCss("ob-print", "ob")
