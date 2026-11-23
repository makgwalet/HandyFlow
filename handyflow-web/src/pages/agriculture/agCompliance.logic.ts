// src/pages/agriculture/agCompliance.logic.ts
//
// Withdrawal periods (animals) and pre-harvest intervals (crops). The server decides what is held and refuses a sale, cull,
// transfer out or harvest while something is; these helpers only let the screen say so before the person presses Save, and
// ask for the reason that overrides it. Same rule as the server: clear from treatment date + days.
import { addDays } from "./agCrops.logic"

export interface WithdrawalStatus {
  held: boolean; asOf: string; treatedOn: string | null; clearOn: string | null; product: string | null
}

/** Animal statuses that send meat to market. A death or a return to active is never held. */
export const MARKET_STATUSES = ["SOLD", "CULLED", "TRANSFERRED_OUT"] as const
export const isMarketStatus = (s: string): boolean => (MARKET_STATUSES as readonly string[]).includes(s)
/** Movement types that take an animal or head off the farm. */
export const isMarketMovement = (t: string): boolean => t === "SALE" || t === "TRANSFER_OUT"

export const MAX_PERIOD_DAYS = 730
export const MIN_REASON = 3

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
/** 2026-12-15 -> "15 Dec 2026" (the same wording the server uses in its messages). */
export function formatDay(iso: string | null | undefined): string {
  if (!iso) return ""
  const [y, m, d] = iso.split("-").map(Number)
  if (!y || !m || !d) return iso
  return `${d} ${MONTHS[m - 1]} ${y}`
}

/** First day clear of a treatment: date + days. Null when no period is given. */
export function clearOn(date: string, days: string | number | null | undefined): string | null {
  if (!date || days === "" || days == null) return null
  const n = Number(days)
  if (!Number.isInteger(n) || n <= 0) return null
  return addDays(date, n)
}

/** A period typed into a form: empty, or a whole number of days from 0 to 730. */
export function periodProblem(days: string, what: string): string {
  if (days.trim() === "") return ""
  const n = Number(days)
  if (!Number.isInteger(n) || n < 0 || n > MAX_PERIOD_DAYS) return `${what} must be a whole number of days from 0 to ${MAX_PERIOD_DAYS}.`
  return ""
}

/** "Terramycin on 1 Dec 2026. Clear from 15 Dec 2026." */
export function holdSummary(s: WithdrawalStatus): string {
  const what = s.product ? s.product : "A treatment"
  return `${what}${s.treatedOn ? ` on ${formatDay(s.treatedOn)}` : ""}. Clear from ${formatDay(s.clearOn)}.`
}

/** The message to show when the screen is about to do something the server will hold, or "" when nothing is held. */
export function overrideProblem(held: boolean, reason: string): string {
  if (!held) return ""
  return reason.trim().length >= MIN_REASON ? "" : `Give a reason (at least ${MIN_REASON} characters) to go ahead during the withdrawal period.`
}

/** The reason to send: only when something is held, trimmed; otherwise null so a stale reason is never sent. */
export function overrideReasonToSend(held: boolean, reason: string): string | null {
  return held && reason.trim() ? reason.trim() : null
}

/** A health event's row text: "Withdrawal until 15 Dec 2026" while it is still running, otherwise "". */
export function withdrawalNote(clearOnDate: string | null | undefined, today: string): string {
  return clearOnDate && clearOnDate > today ? `Withdrawal until ${formatDay(clearOnDate)}` : ""
}

/** Milk and egg holds for an animal or group on a day (the server decides; see AgWithdrawalService). */
export interface ProduceWithdrawal { milk: WithdrawalStatus; eggs: WithdrawalStatus }

/** One sentence per held product: "Milk is held until 15 Dec 2026 (Mastitis tube on 11 Dec 2026)." Empty when nothing is held. */
export function produceLines(p: ProduceWithdrawal | undefined): string[] {
  if (!p) return []
  const line = (what: string, verb: string, s: WithdrawalStatus) =>
    s?.held ? `${what} ${verb} held until ${formatDay(s.clearOn)}${s.product || s.treatedOn ? ` (${s.product ?? "treatment"}${s.treatedOn ? ` on ${formatDay(s.treatedOn)}` : ""})` : ""}.` : ""
  return [line("Milk", "is", p.milk), line("Eggs", "are", p.eggs)].filter(Boolean)
}

/** A health event's row text for milk and eggs while their periods are still running: "Milk until 15 Dec 2026 · Eggs until 18 Dec 2026". */
export function produceNote(milkClearOn: string | null | undefined, eggClearOn: string | null | undefined, today: string): string {
  const parts: string[] = []
  if (milkClearOn && milkClearOn > today) parts.push(`Milk until ${formatDay(milkClearOn)}`)
  if (eggClearOn && eggClearOn > today) parts.push(`Eggs until ${formatDay(eggClearOn)}`)
  return parts.join(" · ")
}

/**
 * The line shown on a record that went ahead during a hold: "Went ahead inside the withdrawal period (held until 15 Dec 2026): sold for breeding".
 * Empty when no reason is stored. {what} is "withdrawal period" (animals) or "pre-harvest interval" (crops).
 */
export function overrideNote(reason: string | null | undefined, heldUntil: string | null | undefined, what: "withdrawal period" | "pre-harvest interval" = "withdrawal period"): string {
  if (!reason || !reason.trim()) return ""
  return `Went ahead inside the ${what}${heldUntil ? ` (held until ${formatDay(heldUntil)})` : ""}: ${reason.trim()}`
}
