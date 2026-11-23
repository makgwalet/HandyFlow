// src/pages/agriculture/agCalendar.logic.ts
//
// The farm calendar screen's date arithmetic, grouping and labels. The server (AgCalendarRules / GET /farms/{id}/calendar) decides
// what is on the calendar; this only picks the window to ask for and lays the answer out by day. Dates are YYYY-MM-DD strings.
import { addDays } from "./agCrops.logic"

export interface CalendarItem {
  date: string; kind: string; title: string; detail: string | null
  targetType: "ANIMAL" | "GROUP" | "CROP_CYCLE"; targetId: string; overdue: boolean
}

export type CalendarView = "day" | "week"

/** The Monday on or before this day (weeks run Monday to Sunday). */
export function weekStart(iso: string): string {
  const d = new Date(`${iso}T00:00:00Z`)
  const back = (d.getUTCDay() + 6) % 7
  return addDays(iso, -back)
}

export function windowFor(view: CalendarView, anchor: string): { from: string; to: string } {
  if (view === "day") return { from: anchor, to: anchor }
  const from = weekStart(anchor)
  return { from, to: addDays(from, 6) }
}

/** Previous or next day or week. */
export function step(view: CalendarView, anchor: string, direction: 1 | -1): string {
  return addDays(anchor, direction * (view === "day" ? 1 : 7))
}

const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

/** "Mon 23 Nov" */
export function dayLabel(iso: string): string {
  const d = new Date(`${iso}T00:00:00Z`)
  return `${WEEKDAYS[d.getUTCDay()].slice(0, 3)} ${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`
}

/** The heading over the window: "Mon 23 Nov" for a day, "23 Nov – 29 Nov 2026" for a week. */
export function windowTitle(view: CalendarView, from: string, to: string): string {
  if (view === "day") return `${dayLabel(from)} ${from.slice(0, 4)}`
  const f = new Date(`${from}T00:00:00Z`), t = new Date(`${to}T00:00:00Z`)
  return `${f.getUTCDate()} ${MONTHS[f.getUTCMonth()]} – ${t.getUTCDate()} ${MONTHS[t.getUTCMonth()]} ${to.slice(0, 4)}`
}

export interface DayGroup { date: string; items: CalendarItem[] }

/** One group per day from `from` to `to`, empty days included, items in the order the server sent them. */
export function groupByDay(items: CalendarItem[], from: string, to: string): DayGroup[] {
  const out: DayGroup[] = []
  for (let d = from; d <= to; d = addDays(d, 1)) out.push({ date: d, items: items.filter(i => i.date === d) })
  return out
}

export type KindFilter = "all" | "health" | "withdrawals" | "breeding" | "crops"
export const KIND_FILTERS: { value: KindFilter; label: string }[] = [
  { value: "all", label: "Everything" }, { value: "health", label: "Health" }, { value: "withdrawals", label: "Withdrawals" },
  { value: "breeding", label: "Births" }, { value: "crops", label: "Crops" },
]
const GROUPS: Record<Exclude<KindFilter, "all">, string[]> = {
  health: ["HEALTH_DUE", "HEALTH_SCHEDULED"],
  withdrawals: ["WITHDRAWAL_END"],
  breeding: ["BIRTH_DUE"],
  crops: ["PLANTING", "HARVEST", "SCOUTING_FOLLOWUP"],
}

export function filterItems(items: CalendarItem[], filter: KindFilter): CalendarItem[] {
  return filter === "all" ? items : items.filter(i => GROUPS[filter].includes(i.kind))
}

const KIND_LABELS: Record<string, string> = {
  HEALTH_DUE: "Health due", HEALTH_SCHEDULED: "Treatment", WITHDRAWAL_END: "Withdrawal ends", BIRTH_DUE: "Birth",
  PLANTING: "Planting", HARVEST: "Harvest", SCOUTING_FOLLOWUP: "Scouting",
}
export const kindLabel = (kind: string) => KIND_LABELS[kind] ?? kind.toLowerCase().replace(/_/g, " ")

/** How many items need doing and are late, for the line under the heading. */
export function overdueCount(items: CalendarItem[]): number {
  return items.filter(i => i.overdue).length
}

/** The line under the heading: how many things, and how many are overdue. */
export function summaryLine(items: CalendarItem[]): string {
  if (items.length === 0) return "Nothing due in this period."
  const late = overdueCount(items)
  return `${items.length} ${items.length === 1 ? "item" : "items"}${late > 0 ? `, ${late} overdue` : ""}.`
}
