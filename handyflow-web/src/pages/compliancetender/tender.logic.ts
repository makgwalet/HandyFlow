// src/pages/compliancetender/tender.logic.ts
//
// Pure rules for the tender form and the tender page: what is editable, and what a form may not say.
export const CLOSED_STATUSES = ["AWARDED", "UNSUCCESSFUL", "WITHDRAWN"]
export const PREPARING_STATUSES = ["DRAFT", "IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT"]

/** Details can be corrected until a final outcome (mirrors Tender.updateDetails). */
export const canEditDetails = (status: string) => !CLOSED_STATUSES.includes(status)
/** The requirement matrix can be changed only while preparing (mirrors Tender.isPreparing). */
export const canEditMatrix = (status: string) => PREPARING_STATUSES.includes(status)

export interface TenderForm {
  name: string; tenderAuthority: string; authorityReferenceNumber: string
  closingDate: string; briefingDate: string; siteInspectionDate: string
  estimatedValue: string; industry: string; requiredClassOfWork: string; requiresPricing: boolean
}

export function validateTenderForm(f: TenderForm): Record<string, string> {
  const e: Record<string, string> = {}
  if (!f.name.trim()) e.name = "A tender needs a name"
  if (f.estimatedValue.trim() !== "") {
    const v = Number(f.estimatedValue.replace(/\s/g, "").replace(",", "."))
    if (!Number.isFinite(v) || v < 0) e.estimatedValue = "Enter an amount of zero or more"
  }
  // ISO dates compare as text
  if (f.closingDate && f.briefingDate && f.briefingDate > f.closingDate) e.briefingDate = "The briefing is after the closing date"
  if (f.closingDate && f.siteInspectionDate && f.siteInspectionDate > f.closingDate) e.siteInspectionDate = "The site inspection is after the closing date"
  return e
}

export function toTenderBody(f: TenderForm) {
  const text = (s: string) => s.trim() === "" ? null : s.trim()
  return {
    name: f.name.trim(), tenderAuthority: text(f.tenderAuthority), authorityReferenceNumber: text(f.authorityReferenceNumber),
    closingDate: text(f.closingDate), briefingDate: text(f.briefingDate), siteInspectionDate: text(f.siteInspectionDate),
    estimatedValue: f.estimatedValue.trim() === "" ? null : Number(f.estimatedValue.replace(/\s/g, "").replace(",", ".")),
    industry: text(f.industry), requiredClassOfWork: text(f.requiredClassOfWork), requiresPricing: f.requiresPricing,
  }
}

export function formFromTender(t: {
  name: string; tenderAuthority: string | null; authorityReferenceNumber: string | null; closingDate: string | null; briefingDate: string | null
  siteInspectionDate: string | null; estimatedValue: number | null; industry: string | null; requiredClassOfWork: string | null; requiresPricing?: boolean
}): TenderForm {
  return {
    name: t.name, tenderAuthority: t.tenderAuthority ?? "", authorityReferenceNumber: t.authorityReferenceNumber ?? "",
    closingDate: t.closingDate?.slice(0, 10) ?? "", briefingDate: t.briefingDate?.slice(0, 10) ?? "", siteInspectionDate: t.siteInspectionDate?.slice(0, 10) ?? "",
    estimatedValue: t.estimatedValue == null ? "" : String(t.estimatedValue), industry: t.industry ?? "", requiredClassOfWork: t.requiredClassOfWork ?? "", requiresPricing: !!t.requiresPricing,
  }
}

// ---- key personnel: an HR employee, or someone outside HR

export const EXTERNAL_TYPES: { value: string; label: string }[] = [
  { value: "DIRECTOR", label: "Director" }, { value: "SUBCONTRACTOR", label: "Subcontractor" },
  { value: "CONSULTANT", label: "Consultant" }, { value: "OTHER", label: "Other" },
]

export const personTypeLabel = (t: string | null | undefined) => EXTERNAL_TYPES.find(x => x.value === t)?.label ?? (t === "EMPLOYEE" ? "Employee" : "")

export interface PersonnelDraft { mode: "EMPLOYEE" | "EXTERNAL"; employeeId: string | null; role: string; type: string; name: string; organisation: string }

/** What is missing before the person can be added, in words; null when ready. */
export function personnelProblem(d: PersonnelDraft): string | null {
  if (!d.role.trim()) return "Say what role they have on this tender."
  if (d.mode === "EMPLOYEE") return d.employeeId ? null : "Choose an employee."
  if (!d.name.trim()) return "Enter the person's name."
  return null
}

export function personnelBody(d: PersonnelDraft) {
  if (d.mode === "EMPLOYEE") return { employeeId: d.employeeId, role: d.role.trim() }
  return { role: d.role.trim(), personType: d.type, externalName: d.name.trim(), externalOrganisation: d.organisation.trim() || null }
}

/** Things worth knowing before the tender is marked Submitted. The newest package is the one that counts. */
export function submitWarnings(packages: { versionNo: number; submissionReady: boolean; stale?: boolean; staleReasons?: string[] }[]): string[] {
  if (packages.length === 0) return ["No submission package has been built for this tender."]
  const latest = packages.reduce((a, b) => (b.versionNo > a.versionNo ? b : a))
  const out: string[] = []
  if (!latest.submissionReady) out.push(`The newest package (version ${latest.versionNo}) is a draft and is not marked ready to submit.`)
  if (latest.stale) out.push(`The newest package (version ${latest.versionNo}) is out of date. ${(latest.staleReasons ?? []).join(". ")}.`)
  return out
}
