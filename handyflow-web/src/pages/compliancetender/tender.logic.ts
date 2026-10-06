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
  estimatedValue: string; industry: string; requiredClassOfWork: string
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
    industry: text(f.industry), requiredClassOfWork: text(f.requiredClassOfWork),
  }
}

export function formFromTender(t: {
  name: string; tenderAuthority: string | null; authorityReferenceNumber: string | null; closingDate: string | null; briefingDate: string | null
  siteInspectionDate: string | null; estimatedValue: number | null; industry: string | null; requiredClassOfWork: string | null
}): TenderForm {
  return {
    name: t.name, tenderAuthority: t.tenderAuthority ?? "", authorityReferenceNumber: t.authorityReferenceNumber ?? "",
    closingDate: t.closingDate?.slice(0, 10) ?? "", briefingDate: t.briefingDate?.slice(0, 10) ?? "", siteInspectionDate: t.siteInspectionDate?.slice(0, 10) ?? "",
    estimatedValue: t.estimatedValue == null ? "" : String(t.estimatedValue), industry: t.industry ?? "", requiredClassOfWork: t.requiredClassOfWork ?? "",
  }
}
