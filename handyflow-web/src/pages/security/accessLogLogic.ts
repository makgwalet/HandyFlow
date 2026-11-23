// src/pages/security/accessLogLogic.ts
import { getCompanyZone } from "./companyZone"
//
// Pure wording for the access log: who opened what sensitive material, and when. The server sends ids, names where it knows them, and a
// small metadata object (as text); this turns them into sentences. It never carries the sensitive data itself.
export interface AccessLogEntry {
  id: string; occurredAt: string; actorId: string | null; actorType: string; entityType: string; entityId: string; action: string
  metadata: string | null; actorName: string | null; guardName: string | null
}

export const KIND_OPTIONS: { value: string; label: string }[] = [
  { value: "", label: "Everything" },
  { value: "GUARD", label: "Guards" },
  { value: "COMPLAINT", label: "Complaints" },
  { value: "INCIDENT", label: "Incidents" },
  { value: "PAYROLL_PERIOD", label: "Payroll" },
  { value: "PRINCIPAL", label: "Close protection" },
  { value: "DECLINED_REGISTER", label: "Declined register" },
]

export const readEntries = (x: unknown): AccessLogEntry[] => (Array.isArray(x) ? x.filter(e => e && typeof e.id === "string" && typeof e.action === "string") : [])

export function meta(e: AccessLogEntry): Record<string, unknown> {
  try { const m = JSON.parse(e.metadata ?? "{}"); return m && typeof m === "object" && !Array.isArray(m) ? m : {} } catch { return {} }
}

export const isSupportSession = (e: AccessLogEntry) => meta(e).supportSession === true

/** The person, or why there is none. A missing actor on a support session means Handyflow support staff opened it. */
export function who(e: AccessLogEntry): string {
  if (e.actorName) return isSupportSession(e) ? `${e.actorName} (support session)` : e.actorName
  if (isSupportSession(e)) return "Support staff"
  return e.actorType === "SYSTEM" ? "System" : "A user no longer on file"
}

const EVIDENCE_KIND: Record<string, string> = {
  SCREENING_EVIDENCE: "screening evidence", COMPETENCY_EVIDENCE: "competency evidence", COMPLAINT_EVIDENCE: "complaint evidence", INCIDENT_EVIDENCE: "incident evidence",
}

/** "Downloaded screening evidence", "Exported payroll (CSV)" and so on. */
export function what(e: AccessLogEntry): string {
  const m = meta(e)
  switch (e.action) {
    case "PII_REVEALED": return `Revealed a guard's ${typeof m.field === "string" ? fieldLabel(m.field) : "protected detail"}${typeof m.reason === "string" && m.reason ? `. Reason: ${m.reason}` : ""}`
    case "EVIDENCE_DOWNLOADED": return `Downloaded ${EVIDENCE_KIND[String(m.kind)] ?? "evidence"}`
    case "EXPORTED": return `Exported payroll${typeof m.format === "string" ? ` (${m.format})` : ""}`
    case "PAY_STATEMENT_VIEWED": return "Opened a pay statement"
    case "VETTING_VIEWED": return m.kind === "COMPLIANCE_PDF" ? "Opened a vetting compliance PDF" : "Opened a vetting record"
    case "DECLINED_REGISTER_VIEWED": return "Opened the declined-engagement register"
    default: return e.action.toLowerCase().replace(/_/g, " ")
  }
}

const fieldLabel = (f: string) => ({ ID_NUMBER: "ID number", BANK_ACCOUNT: "bank account number", BANK_ACCOUNT_NUMBER: "bank account number" } as Record<string, string>)[f.toUpperCase()] ?? f.toLowerCase().replace(/_/g, " ")

const SHORT: Record<string, string> = { GUARD: "Guard", COMPLAINT: "Complaint", INCIDENT: "Incident", PAYROLL_PERIOD: "Payroll period", PRINCIPAL: "Principal", DECLINED_REGISTER: "Company register" }

/** The guard's name when the server knows it; otherwise the kind of record and the first part of its id. */
export function subject(e: AccessLogEntry): string {
  if (e.entityType === "GUARD" && e.guardName) return e.guardName
  const kind = SHORT[e.entityType] ?? e.entityType.toLowerCase().replace(/_/g, " ")
  return e.entityType === "DECLINED_REGISTER" ? kind : `${kind} ${e.entityId.slice(0, 8)}`
}

export function whenLabel(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? "" : d.toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", hour12: false, timeZone: getCompanyZone() })
}

export const emptyLine = (kind: string) => (kind ? "Nothing of this kind has been opened or downloaded yet." : "Nothing sensitive has been opened or downloaded yet.")
export const LIMIT_NOTE = "Shows the most recent 200 entries. Refused requests are not recorded."
