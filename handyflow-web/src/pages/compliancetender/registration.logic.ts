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
