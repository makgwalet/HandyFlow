// src/pages/compliancetender/registration.logic.ts
//
// A registration is only in force if its status says so AND its expiry date has not passed. The nightly job flips the status, but until it has run (or if the
// date was typed in wrongly) an "Active" registration with a past expiry must not be shown, counted or offered as valid.
import { daysUntil } from "./package.logic"

export function effectiveStatus(r: { status: string; expiryDate: string | null }, today: Date): string {
  if (r.status === "ACTIVE") {
    const n = daysUntil(r.expiryDate, today)
    if (n !== null && n < 0) return "EXPIRED"
  }
  return r.status
}

export interface ChipSpec { text: string; tone: "ok" | "warn" | "bad" | "neutral" }

/** "Expires in 12 days", "Expired 40 days ago", "No expiry". Warns from 30 days out, matching the server's expiring-soon window. */
export function expiryChip(expiryDate: string | null | undefined, today: Date): ChipSpec {
  const n = daysUntil(expiryDate, today)
  if (n === null) return { text: "No expiry", tone: "neutral" }
  if (n < 0) return { text: n === -1 ? "Expired yesterday" : `Expired ${-n} days ago`, tone: "bad" }
  if (n === 0) return { text: "Expires today", tone: "bad" }
  if (n === 1) return { text: "Expires tomorrow", tone: "bad" }
  return { text: `Expires in ${n} days`, tone: n <= 30 ? "warn" : "ok" }
}

/** Document state for the vault: expired beats everything, then unverified (which also means it cannot satisfy a tender requirement), then verified. */
export function documentState(d: { verified: boolean; expiryDate: string | null }, today: Date): ChipSpec {
  const n = daysUntil(d.expiryDate, today)
  if (n !== null && n < 0) return { text: "Expired", tone: "bad" }
  if (!d.verified) return { text: "Not verified", tone: "warn" }
  if (n !== null && n <= 30) return { text: "Verified · expiring", tone: "warn" }
  return { text: "Verified", tone: "ok" }
}

/** Countdown for a compliance deadline. */
export function deadlineChip(dueDate: string | null | undefined, today: Date): ChipSpec {
  const n = daysUntil(dueDate, today)
  if (n === null) return { text: "No date", tone: "neutral" }
  if (n < 0) return { text: n === -1 ? "1 day overdue" : `${-n} days overdue`, tone: "bad" }
  if (n === 0) return { text: "Due today", tone: "bad" }
  if (n === 1) return { text: "Due tomorrow", tone: "bad" }
  return { text: `Due in ${n} days`, tone: n <= 3 ? "bad" : n <= 14 ? "warn" : "ok" }
}

/** Form rules for a registration. Returns field -> message; empty means the form can be saved. */
export function validateRegistration(f: { registrationType: string; issuedDate: string; expiryDate: string; status: string }, today: Date): Record<string, string> {
  const e: Record<string, string> = {}
  if (!f.registrationType.trim()) e.registrationType = "Registration type is required"
  if (f.expiryDate && f.issuedDate && f.expiryDate < f.issuedDate) e.expiryDate = "Expiry date cannot be before issued date"
  else if (f.expiryDate && f.status === "ACTIVE") {
    const n = daysUntil(f.expiryDate, today)
    if (n !== null && n < 0) e.expiryDate = "This date has passed. Set the status to Expired, or enter the renewed expiry date."
  }
  return e
}
