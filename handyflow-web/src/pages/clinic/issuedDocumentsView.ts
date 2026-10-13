// The register of numbered documents (CLINIC-DEC-024). Withdrawing keeps the record; the public check then says the document is withdrawn.
export interface IssuedRow {
  id: string; number: string; type: string; typeLabel: string; patientId?: string | null; patientName?: string | null
  issuerName?: string | null; issuedAt: string; status: string; voidedAt?: string | null; voidReason?: string | null
}

export const VOID_REASON_MIN = 5
export const VOID_REASON_MAX = 500

export const TYPES: { id: string; label: string }[] = [
  { id: "", label: "All documents" }, { id: "SICK_NOTE", label: "Sick notes" }, { id: "REFERRAL", label: "Referral letters" },
  { id: "LETTER", label: "Letters" }, { id: "PRESCRIPTION", label: "Prescriptions" }, { id: "MEDICAL_REPORT", label: "Medical reports" }, { id: "CONSENT_FORM", label: "Consent forms" },
]
export const STATUSES = [{ id: "", label: "Any status" }, { id: "ISSUED", label: "Issued" }, { id: "VOIDED", label: "Withdrawn" }]

export const statusLabel = (s: string) => (s === "VOIDED" ? "Withdrawn" : s === "ISSUED" ? "Issued" : s)

export const canVoid = (r: Pick<IssuedRow, "status">) => r.status === "ISSUED"

export function voidProblem(reason: string): string | null {
  const t = reason.trim()
  if (t.length < VOID_REASON_MIN) return `Give a reason of at least ${VOID_REASON_MIN} characters`
  if (t.length > VOID_REASON_MAX) return `Keep the reason under ${VOID_REASON_MAX} characters`
  return null
}

export function listParams(type: string, status: string, q: string): Record<string, string> {
  const p: Record<string, string> = {}
  if (type) p.type = type
  if (status) p.status = status
  if (q.trim()) p.q = q.trim()
  return p
}
