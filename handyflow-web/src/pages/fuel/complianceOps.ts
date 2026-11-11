// Stage 4B helpers.
export interface ComplianceItem {
  id: string; itemType: string; title: string; referenceNo: string | null; subjectType: string; subjectId: string | null; subjectLabel: string | null
  issuedOn: string | null; expiresOn: string; notes: string | null; status: "VALID" | "EXPIRING" | "EXPIRED"; daysToExpiry: number
}
export const TYPES = ["LICENCE", "CALIBRATION", "INSPECTION", "CERTIFICATE", "INSURANCE", "OTHER"]
export const SUBJECTS = ["COMPANY", "DEPOT", "TANK", "TANKER", "DRIVER", "OTHER"]
export const subjectNeedsPick = (s: string) => s === "DEPOT" || s === "TANK" || s === "TANKER" || s === "DRIVER"

export function validateItem(title: string, subjectType: string, subjectId: string, expiresOn: string, issuedOn: string): string | null {
  if (!title.trim()) return "Give it a title, for example Roadworthy certificate."
  if (subjectNeedsPick(subjectType) && !subjectId) return `Choose which ${subjectType.toLowerCase()} this is for.`
  if (!expiresOn) return "Enter the expiry date."
  if (issuedOn && expiresOn < issuedOn) return "The expiry date is before the issue date."
  return null
}

export function validateRenew(current: string, next: string): string | null {
  if (!next) return "Enter the new expiry date."
  if (next <= current) return `The new expiry must be later than the current one (${current}).`
  return null
}

export function whenLabel(i: Pick<ComplianceItem, "status" | "daysToExpiry">): string {
  if (i.status === "EXPIRED") return `expired ${Math.abs(i.daysToExpiry)} day${Math.abs(i.daysToExpiry) === 1 ? "" : "s"} ago`
  if (i.daysToExpiry === 0) return "expires today"
  return `${i.daysToExpiry} day${i.daysToExpiry === 1 ? "" : "s"} left`
}

/** Expired first, then expiring soonest. */
export const byUrgency = (a: ComplianceItem, b: ComplianceItem) => a.daysToExpiry - b.daysToExpiry
