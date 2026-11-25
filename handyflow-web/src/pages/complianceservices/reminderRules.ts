// Wording and checks for the daily digest settings. The server decides every rule; this only gives the person an early, readable answer.

export type Recipient = { userId: string; name: string; email: string | null; adminItems: boolean }
export type Settings = { enabled: boolean; minSeverity: string; maxItems: number; recipients: Recipient[]; updatedAt: string | null }
export type Preview = { userId: string; name: string; adminItems: boolean; itemCount: number; subject: string; body: string }
export type RunResult = { handedOver: number; nothingDue: number; noAddress: number; failed: number; alreadyDone: number }
export type LogEntry = { runDate: string; userId: string; name: string; itemCount: number; outcome: string; outcomeText: string; detail: string | null; at: string }
export type Staff = { id: string; firstName?: string; lastName?: string; email?: string }

export const MIN_ITEMS = 5
export const MAX_ITEMS = 100
export const MAX_RECIPIENTS = 25
export const LEVELS: [string, string][] = [["HIGH", "Urgent only"], ["MEDIUM", "Urgent and soon"], ["LOW", "Everything in the queue"]]
export const levelLabel = (l: string) => LEVELS.find(x => x[0] === l)?.[1] ?? l

export const staffName = (s: Staff) => [s.firstName, s.lastName].filter(Boolean).join(" ") || s.email || "Unnamed person"

export type Form = { enabled: boolean; minSeverity: string; maxItems: string; recipients: { userId: string; adminItems: boolean }[] }
export const formOf = (s: Settings): Form => ({ enabled: s.enabled, minSeverity: s.minSeverity, maxItems: String(s.maxItems), recipients: s.recipients.map(r => ({ userId: r.userId, adminItems: r.adminItems })) })

export function problem(f: Form): string | null {
  const n = Number(f.maxItems)
  if (!Number.isInteger(n) || n < MIN_ITEMS || n > MAX_ITEMS) return `A digest lists between ${MIN_ITEMS} and ${MAX_ITEMS} items`
  if (f.recipients.length > MAX_RECIPIENTS) return `A digest can go to at most ${MAX_RECIPIENTS} people`
  if (f.enabled && f.recipients.length === 0) return "Choose at least one person before switching the digest on"
  return null
}
export const body = (f: Form) => ({ enabled: f.enabled, minSeverity: f.minSeverity, maxItems: Number(f.maxItems), recipients: f.recipients })

export const same = (a: Form, b: Form) => JSON.stringify(a) === JSON.stringify(b)

/** People not yet chosen, for the picker. */
export const available = (staff: Staff[], f: Form) => staff.filter(s => !f.recipients.some(r => r.userId === s.id))

export function runText(r: RunResult): string {
  const parts: string[] = []
  if (r.handedOver) parts.push(`${r.handedOver} handed to the notification service`)
  if (r.nothingDue) parts.push(`${r.nothingDue} had nothing due`)
  if (r.alreadyDone) parts.push(`${r.alreadyDone} already had today's`)
  if (r.noAddress) parts.push(`${r.noAddress} could not be found`)
  if (r.failed) parts.push(`${r.failed} failed`)
  return parts.length ? parts.join(", ") : "Nobody to send to"
}
