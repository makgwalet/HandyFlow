// What the vehicle detail panel shows: compliance traffic lights, document expiry wording, and the identity rows.
export const COMPLIANCE_LABEL: Record<string, string> = {
  VALID: "Valid", EXPIRING_SOON: "Expiring soon", DUE: "Due", EXPIRED: "Expired", MISSING: "Missing", NOT_APPLICABLE: "Not applicable",
}
/** green / amber / red / grey, by how urgent the status is. */
export const complianceTone = (s: string): "ok" | "warn" | "bad" | "none" =>
  s === "VALID" ? "ok" : s === "EXPIRING_SOON" || s === "DUE" ? "warn" : s === "EXPIRED" || s === "MISSING" ? "bad" : "none"

export function expiryText(days: number | null | undefined): string {
  if (days == null) return "No expiry"
  if (days < 0) return `Expired ${-days} ${days === -1 ? "day" : "days"} ago`
  if (days === 0) return "Expires today"
  return `${days} ${days === 1 ? "day" : "days"} left`
}

export const DOCUMENT_TYPE_LABEL: Record<string, string> = {
  LICENCE_DISC: "Licence disc", ROADWORTHY: "Roadworthy certificate", INSURANCE: "Insurance", REGISTRATION_CERTIFICATE: "Registration certificate",
  PURCHASE_AGREEMENT: "Purchase agreement", FINANCE_AGREEMENT: "Finance agreement", TRACKING_CERTIFICATE: "Tracking certificate",
  SERVICE_RECORD: "Service record", WARRANTY: "Warranty", INSPECTION: "Inspection", ACCIDENT: "Accident", OTHER: "Other",
}
/** Types whose expiry date is required by the server (it updates the vehicle's own expiry). */
export const EXPIRY_REQUIRED = new Set(["LICENCE_DISC", "ROADWORTHY", "INSURANCE"])

export function uploadProblem(type: string, file: File | null, expiryDate: string): string | null {
  if (!type) return "Choose the document type."
  if (!file) return "Choose a file."
  if (EXPIRY_REQUIRED.has(type) && !expiryDate) return `${DOCUMENT_TYPE_LABEL[type]} needs an expiry date.`
  return null
}

export interface Identity {
  vin?: string | null; engineNumber?: string | null; variant?: string | null; bodyType?: string | null; transmission?: string | null; driveType?: string | null
  axleCount?: number | null; gvmKg?: number | null; tareKg?: number | null; payloadKg?: number | null; gcmKg?: number | null
  tankCapacityLitres?: number | null; engineHours?: number | null; lifecycleStatus?: string | null
}
/** Identity rows in the order of the register; blanks are shown as "Not recorded" so gaps (engine number) are visible. */
export function identityRows(v: Identity): { label: string; value: string; missing: boolean }[] {
  const kg = (n?: number | null) => (n == null ? null : `${n.toLocaleString("en-ZA")} kg`)
  const rows: [string, string | null | undefined][] = [
    ["VIN / chassis number", v.vin], ["Engine number", v.engineNumber], ["Variant", v.variant], ["Body type", v.bodyType],
    ["Transmission", v.transmission], ["Drive type", v.driveType], ["Axles", v.axleCount == null ? null : String(v.axleCount)],
    ["GVM", kg(v.gvmKg)], ["Tare", kg(v.tareKg)], ["Payload", kg(v.payloadKg)], ["Gross combination mass", kg(v.gcmKg)],
    ["Tank capacity", v.tankCapacityLitres == null ? null : `${v.tankCapacityLitres} L`], ["Engine hours", v.engineHours == null ? null : String(v.engineHours)],
    ["Lifecycle", v.lifecycleStatus],
  ]
  return rows.map(([label, value]) => ({ label, value: value || "Not recorded", missing: !value }))
}
