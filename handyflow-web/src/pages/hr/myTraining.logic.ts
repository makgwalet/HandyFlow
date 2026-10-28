// src/pages/hr/myTraining.logic.ts
//
// An employee's own training. The server decides each certificate's state; these turn it into words.
export type CertState = "VALID" | "EXPIRING" | "EXPIRED" | "REVOKED"

export interface MyBooking {
  id: string; courseTitle: string; startDate: string | null; endDate: string | null; venue: string | null
  status: string; statusLabel: string; score: number | null; passed: boolean | null
}
export interface MyCertificate {
  id: string; courseTitle: string; number: string; issueDate: string | null; expiryDate: string | null; state: CertState; daysLeft: number | null
}
export interface MyTraining { bookings: MyBooking[]; certificates: MyCertificate[] }

export const STATE_LABEL: Record<CertState, string> = { VALID: "Valid", EXPIRING: "Expiring soon", EXPIRED: "Expired", REVOKED: "Withdrawn" }

/** What to tell the person about when a certificate runs out. */
export function expiryNote(c: Pick<MyCertificate, "state" | "expiryDate" | "daysLeft">): string {
  if (c.state === "REVOKED") return "This certificate was withdrawn."
  if (!c.expiryDate) return "Does not expire."
  if (c.state === "EXPIRED") return `Expired on ${c.expiryDate}. Ask your manager or HR about refresher training.`
  if (c.daysLeft === 0) return `Expires today (${c.expiryDate}).`
  if (c.daysLeft === 1) return `Expires tomorrow (${c.expiryDate}).`
  return c.state === "EXPIRING" ? `Expires in ${c.daysLeft} days (${c.expiryDate}).` : `Valid until ${c.expiryDate}.`
}

/** A withdrawn certificate has no PDF. */
export const canDownload = (c: Pick<MyCertificate, "state">) => c.state !== "REVOKED"

/** Sessions still to come first (soonest first), then the past ones (latest first). */
export function orderBookings(rows: readonly MyBooking[], today: string): MyBooking[] {
  const upcoming = rows.filter(r => (r.startDate ?? "") >= today && r.status === "ENROLLED").sort((a, b) => (a.startDate ?? "").localeCompare(b.startDate ?? ""))
  const rest = rows.filter(r => !upcoming.includes(r)).sort((a, b) => (b.startDate ?? "").localeCompare(a.startDate ?? ""))
  return [...upcoming, ...rest]
}
