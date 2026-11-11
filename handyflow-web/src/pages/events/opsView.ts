// src/pages/events/opsView.ts
//
// Rules and wording for the run sheet, checklists and readiness screens. The server holds the real rules (room clashes, only a live event can start a
// session, who may tick items); these keep obviously wrong requests from being sent and say each state in plain words.

export const SESSION_TYPES = ["KEYNOTE", "SESSION", "BREAKOUT", "PANEL", "WORKSHOP", "BREAK", "MEAL", "REGISTRATION", "ENTERTAINMENT", "SETUP", "BREAKDOWN", "OTHER"]
export const CHECKLIST_PHASES = ["PRE_EVENT", "SETUP", "EVENT_DAY", "BREAKDOWN", "POST_EVENT", "CONTINGENCY"]

const words = (c: string) => { const s = c.toLowerCase().replace(/_/g, " "); return s.charAt(0).toUpperCase() + s.slice(1) }
export const typeText = words
export const phaseText = (p: string) => p === "CONTINGENCY" ? "Contingency plans" : words(p)

export interface Session {
  id: string; title: string; description: string | null; sessionType: string; room: string | null; speaker: string | null; startAt: string; endAt: string
  status: string; actualStart: string | null; actualEnd: string | null; delayMinutes: number | null; notes: string | null
}

export const sessionStatusText = (s: string) => ({ SCHEDULED: "Scheduled", IN_PROGRESS: "In progress", COMPLETED: "Done", CANCELLED: "Cancelled" } as Record<string, string>)[s] ?? s
/** Completed and cancelled sessions are history. */
export const canEditSession = (s: string) => s === "SCHEDULED" || s === "IN_PROGRESS"
export const canStartSession = (s: string, eventStatus?: string) => s === "SCHEDULED" && eventStatus === "LIVE"
export const canCompleteSession = (s: string) => s === "IN_PROGRESS"
export const canCancelSession = (s: string) => s === "SCHEDULED" || s === "IN_PROGRESS"
/** A session that never ran can be deleted; one that ran is part of the record. */
export const canDeleteSession = (s: string) => s === "SCHEDULED" || s === "CANCELLED"

export function sessionProblem(f: { title: string; sessionType: string; startAt: string; endAt: string }): string | null {
  if (!f.title.trim()) return "Give the session a title."
  if (!SESSION_TYPES.includes(f.sessionType)) return "Choose a session type."
  if (!f.startAt || !f.endAt) return "Set when it starts and ends."
  if (f.endAt <= f.startAt) return "It must end after it starts."
  return null
}

const dt = (v: string) => (v ? (v.length === 16 ? `${v}:00` : v) : null)
const nz = (v: string | undefined) => ((v ?? "").trim() === "" ? null : (v as string).trim())
export function sessionBody(f: { title: string; description?: string; sessionType: string; room?: string; speaker?: string; startAt: string; endAt: string; notes?: string }) {
  return { title: f.title.trim(), description: nz(f.description), sessionType: f.sessionType, room: nz(f.room), speaker: nz(f.speaker), startAt: dt(f.startAt), endAt: dt(f.endAt), notes: nz(f.notes) }
}

/** "We're running late": whole minutes, later is positive, earlier negative; the server accepts -120 to +240 and not zero. */
export function shiftProblem(minutes: string): string | null {
  if (!/^-?\d+$/.test(minutes.trim())) return "Enter a whole number of minutes."
  const n = Number(minutes)
  if (n === 0) return "Enter how many minutes to move the programme by."
  if (n < -120 || n > 240) return "Between 120 minutes earlier and 240 minutes later."
  return null
}

export function lateText(minutes: number): string {
  if (!minutes) return "On time"
  return minutes > 0 ? `Running ${minutes} min late` : `Running ${-minutes} min early`
}

/** Clock time of a venue wall-clock timestamp, e.g. "09:30". Takes the text as sent (no timezone shifting). */
export const clock = (iso: string | null | undefined) => (iso ? iso.slice(11, 16) : "")

export function delayText(delay: number | null): string {
  if (delay == null) return ""
  if (delay === 0) return "started on time"
  return delay > 0 ? `started ${delay} min late` : `started ${-delay} min early`
}

export interface ChecklistItem {
  id: string; phase: string; title: string; description: string | null; assignedTo: string | null; dueAt: string | null; required: boolean; done: boolean
  doneByName: string | null; doneAt: string | null; sortOrder: number; overdue: boolean
}

export function checklistProblem(f: { phase: string; title: string }): string | null {
  if (!CHECKLIST_PHASES.includes(f.phase)) return "Choose a phase."
  if (!f.title.trim()) return "Give the item a title."
  return null
}
export function checklistBody(f: { phase: string; title: string; description?: string; assignedTo?: string; dueAt?: string; required?: boolean }) {
  return { phase: f.phase, title: f.title.trim(), description: nz(f.description), assignedTo: nz(f.assignedTo), dueAt: dt(f.dueAt ?? ""), required: f.required !== false, sortOrder: null }
}

/** Items grouped by phase in the order an event moves through them; empty phases are left out. */
export function groupByPhase(items: ChecklistItem[]): { phase: string; items: ChecklistItem[] }[] {
  return CHECKLIST_PHASES.map(phase => ({ phase, items: items.filter(i => i.phase === phase).sort((a, b) => a.sortOrder - b.sortOrder) })).filter(g => g.items.length > 0)
}

/** For a contingency plan "done" means reviewed; for everything else it means done. */
export const doneWord = (phase: string, done: boolean) => (phase === "CONTINGENCY" ? (done ? "Reviewed" : "Not reviewed") : done ? "Done" : "To do")

export function progressText(p: { total: number; done: number; requiredOpen: number }): string {
  if (p.total === 0) return "Nothing yet"
  return `${p.done} of ${p.total} done${p.requiredOpen > 0 ? `, ${p.requiredOpen} required still open` : ""}`
}

export function templateText(r: { added: number; skipped: number }): string {
  if (r.added === 0) return r.skipped > 0 ? "Everything in the starter list is already there." : "Nothing to add."
  return `Added ${r.added} item${r.added === 1 ? "" : "s"}${r.skipped ? `; ${r.skipped} already existed` : ""}.`
}
