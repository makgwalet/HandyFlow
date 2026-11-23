// Emailing the monthly report: rules and wording. The server enforces every rule again; this only avoids pointless clicks.
export interface Recipient { id: string; name: string; email: string }
export const MAX_RECIPIENTS = 5

/** Exactly one address, the same shape the server accepts. */
export function validEmail(raw: string): boolean {
  const e = raw.trim()
  return e.length <= 254 && /^[^\s@<>,;"']+@[^\s@<>,;"'.]+(\.[^\s@<>,;"'.]+)+$/.test(e)
}

export function canAdd(name: string, email: string, recipients: Recipient[]): boolean {
  if (name.trim().length === 0 || name.trim().length > 100 || !validEmail(email)) return false
  if (recipients.length >= MAX_RECIPIENTS) return false
  const e = email.trim().toLowerCase()
  return !recipients.some(r => r.email === e)
}

/** Why sending is not possible right now, or null when it is. */
export function sendBlock(o: { reportLoaded: boolean; inProgress: boolean; contactSet: boolean; recipients: number; busy: boolean }): string | null {
  if (!o.reportLoaded) return "The report has not loaded."
  if (o.inProgress) return "Only a finished month can be emailed."
  if (!o.contactSet) return "Set the contact address for report questions first."
  if (o.recipients === 0) return "Add a recipient first."
  if (o.busy) return "Sending…"
  return null
}

export function confirmText(monthTitle: string, n: number): string {
  return `Email the ${monthTitle} report to ${n} ${n === 1 ? "recipient" : "recipients"}?`
}

/** The server only queues the mail; it cannot confirm delivery, so neither do we. */
export function sentText(n: number, monthTitle: string, failed = 0): string {
  const base = `Queued for ${n} ${n === 1 ? "recipient" : "recipients"} (${monthTitle}). Delivery is not confirmed.`
  return failed > 0 ? `${base} ${failed} ${failed === 1 ? "address was" : "addresses were"} refused by the mail service; see the audit log.` : base
}

/** The monitored contact printed at the end of every emailed report. */
export interface Contact { name: string | null; email: string; phone: string | null }
export function validPhone(raw: string): boolean {
  const p = raw.trim()
  return p === "" || /^[0-9+()\- ]{5,40}$/.test(p)
}
export function canSaveContact(name: string, email: string, phone: string): boolean {
  return validEmail(email) && name.trim().length <= 100 && validPhone(phone)
}
export function validContact(c: unknown): c is Contact {
  return !!c && typeof c === "object" && typeof (c as Contact).email === "string" && (c as Contact).email.length > 0
}

export function errorText(e: unknown, fallback: string): string {
  const m = (e as { response?: { data?: { message?: unknown } } })?.response?.data?.message
  return typeof m === "string" && m.length > 0 && m.length < 200 ? m : fallback
}
