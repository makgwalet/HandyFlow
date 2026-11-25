// Small helpers for recording time and reading the profitability report. The server decides what is allowed and does the sums.

export type TimeEntry = { id: string; clientId: string; clientName: string | null; userId: string; workDate: string; minutes: number; serviceCode: string | null; kind: string; billable: boolean; note: string | null; status: string; voidReason: string | null; createdAt: string }
export type Bucket = { key: string; label: string; minutes: number; billableMinutes: number; reworkMinutes: number; unplannedMinutes: number }
export type TimeSummary = { from: string; to: string; minutes: number; billableMinutes: number; reworkMinutes: number; unplannedMinutes: number; byClient: Bucket[]; byDay: Bucket[] }
export type CostRate = { id: string; userId: string; hourlyCost: number; effectiveFrom: string; setBy: string; setAt: string }
export type ClientLine = { clientId: string; clientName: string; minutes: number; billableMinutes: number; reworkMinutes: number; unplannedMinutes: number; unratedMinutes: number; costComplete: boolean; labourCost: number; invoiced: number; collected: number; margin: number; marginPct: number | null }
export type ServiceLine = { serviceCode: string | null; minutes: number; unratedMinutes: number; labourCost: number }
export type PersonLine = { userId: string; minutes: number; billableMinutes: number; unratedMinutes: number; utilisationPct: number | null; labourCost: number }
export type ProfitReport = { from: string; to: string; minutes: number; unratedMinutes: number; labourCost: number; invoiced: number; collected: number; clients: ClientLine[]; services: ServiceLine[]; people: PersonLine[]; basis: string }

export const MAX_ENTRY_MINUTES = 720
export const MIN_REASON = 10

export const KINDS: [string, string][] = [["PLANNED", "Planned work"], ["REWORK", "Rework"], ["UNPLANNED", "Unplanned work"]]
export const kindLabel = (k: string) => KINDS.find(x => x[0] === k)?.[1] ?? k

/**
 * Reads how long a person says they worked. "1:30", "1h 30m", "1h30", "45m", "1.5h" and "2h" are understood; a bare number is minutes.
 * Returns null when it cannot be read, or is not between 1 minute and 12 hours.
 */
export function parseDuration(text: string): number | null {
  const t = text.trim().toLowerCase().replace(/\s+/g, "")
  if (t === "") return null
  let minutes: number | null = null
  let m: RegExpMatchArray | null
  if ((m = t.match(/^(\d{1,2}):(\d{2})$/))) minutes = Number(m[1]) * 60 + Number(m[2])
  else if ((m = t.match(/^(\d+(?:\.\d+)?)h$/))) minutes = Math.round(Number(m[1]) * 60)
  else if ((m = t.match(/^(\d+)h(\d+)m?$/))) minutes = Number(m[1]) * 60 + Number(m[2])
  else if ((m = t.match(/^(\d+)m$/))) minutes = Number(m[1])
  else if ((m = t.match(/^(\d+)$/))) minutes = Number(m[1])
  if (minutes == null || !Number.isFinite(minutes) || minutes < 1 || minutes > MAX_ENTRY_MINUTES) return null
  return minutes
}

/** 90 -> "1h 30m", 45 -> "45m", 120 -> "2h". */
export function formatMinutes(minutes: number): string {
  const h = Math.floor(minutes / 60), m = minutes % 60
  return h === 0 ? `${m}m` : m === 0 ? `${h}h` : `${h}h ${m}m`
}

export const money = (n: number) => `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
export const pct = (n: number | null) => (n == null ? "—" : `${n.toFixed(1)}%`)

export type EntryForm = { clientId: string; workDate: string; duration: string; serviceCode: string; kind: string; billable: boolean; note: string }

/** What is wrong with the form before it is sent, or empty. */
export function entryProblem(f: EntryForm, today: string): string {
  if (!f.clientId) return "Choose the client"
  if (!f.workDate) return "Say which day the work was done"
  if (f.workDate > today) return "That day has not happened yet"
  if (parseDuration(f.duration) == null) return "Say how long it took, for example 1h 30m or 45m (between 1 minute and 12 hours)"
  if (f.note.length > 1000) return "Keep the note under 1000 characters"
  return ""
}

export const reasonProblem = (reason: string): string => (reason.trim().length >= MIN_REASON ? "" : `Say why, in at least ${MIN_REASON} characters`)

/** The first day of the month and today, as dates, for the report's default period. */
export function monthToDate(today: string): { from: string; to: string } {
  return { from: `${today.slice(0, 8)}01`, to: today }
}

/** Local date as YYYY-MM-DD. */
export function isoDay(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0")
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** A client's line is a loss when its margin is below nothing, and cannot be judged when some hours have no cost rate. */
export function marginNote(l: Pick<ClientLine, "costComplete" | "margin" | "invoiced">): string {
  if (!l.costComplete) return "Some hours have no cost rate"
  if (l.invoiced <= 0) return "Nothing invoiced"
  return l.margin < 0 ? "Loss" : ""
}
