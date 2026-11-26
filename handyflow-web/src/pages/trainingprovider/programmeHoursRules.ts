// Wording and checks for optional programme workplace hours. A programme need not record any, and none are assumed. Hours are recorded only
// with the source they come from. The server enforces every rule; these save a round trip. Logged hours are never compared to a pass.

export type HoursView = {
  id: string; programmeVersionId: string; hours: number; covers: string | null; sourceType: string; sourceReference: string; sourceVersionDate: string | null
  sourceSection: string; effectiveDate: string; verifiedBy: string; verifiedOn: string; status: string; withdrawnReason: string | null; updatedAt: string
}
export type VersionHours = { programmeVersionId: string; versionNo: number; versionStatus: string; editable: boolean; current: HoursView | null; history: HoursView[] }

export const MIN_REASON = 10
export const SOURCE_TYPES: Record<string, string> = {
  QUALIFICATION_DOCUMENT: "Qualification document", CURRICULUM: "Curriculum", AUTHORITY_LETTER: "Letter from an authority", ACCREDITATION_DOCUMENT: "Accreditation document",
  FUNDER_AGREEMENT: "Funder agreement", PROVIDER_POLICY: "Provider's own policy", OTHER: "Other",
}
export const NONE_NOTE = "This programme version records no workplace hours. That does not mean none are needed; it means none have been recorded here."
export const SOURCE_NOTE = "Record hours only as a source states them. Say where the figure comes from, which section or page, and who checked it."

export type HoursForm = {
  hours: string; covers: string; sourceType: string; sourceReference: string; sourceVersionDate: string; sourceSection: string; effectiveDate: string; verifiedBy: string; verifiedOn: string
}
export const emptyHours = (): HoursForm => ({ hours: "", covers: "", sourceType: "", sourceReference: "", sourceVersionDate: "", sourceSection: "", effectiveDate: "", verifiedBy: "", verifiedOn: "" })
export const hoursForm = (h: HoursView): HoursForm => ({
  hours: String(h.hours), covers: h.covers ?? "", sourceType: h.sourceType, sourceReference: h.sourceReference, sourceVersionDate: h.sourceVersionDate ?? "",
  sourceSection: h.sourceSection, effectiveDate: h.effectiveDate, verifiedBy: h.verifiedBy, verifiedOn: h.verifiedOn,
})

export function hoursProblem(f: HoursForm, today: string): string | null {
  const n = f.hours.trim() === "" ? NaN : Number(f.hours)
  if (Number.isNaN(n) || n <= 0 || n > 20000) return "Hours must be more than 0 and no more than 20000"
  if (!/^\d+(\.\d{1,2})?$/.test(f.hours.trim())) return "Hours can have at most two decimal places"
  if (!SOURCE_TYPES[f.sourceType]) return "Say what kind of source the hours come from"
  if (f.sourceReference.trim().length < 3) return "Give the document or reference the hours come from"
  if (f.sourceSection.trim().length < 1) return "Give the section or page where the hours are stated"
  if (!f.effectiveDate) return "Give the date the source says the hours take effect"
  if (f.verifiedBy.trim().length < 2) return "Say who checked the source"
  if (!f.verifiedOn) return "Give the day it was checked"
  if (f.verifiedOn > today) return "The check cannot be dated in the future"
  if (f.sourceVersionDate && f.sourceVersionDate > f.verifiedOn) return "The source's own date cannot be after the day it was checked"
  return null
}

export const hoursBody = (f: HoursForm) => ({
  hours: Number(f.hours), covers: f.covers.trim() || null, sourceType: f.sourceType, sourceReference: f.sourceReference.trim(), sourceVersionDate: f.sourceVersionDate || null,
  sourceSection: f.sourceSection.trim(), effectiveDate: f.effectiveDate, verifiedBy: f.verifiedBy.trim(), verifiedOn: f.verifiedOn,
})
export const withdrawProblem = (reason: string): string | null => (reason.trim().length >= MIN_REASON ? null : `Give a reason of at least ${MIN_REASON} characters`)
export const sourceLine = (h: HoursView): string =>
  `${h.hours} workplace hours. Source: ${SOURCE_TYPES[h.sourceType] ?? h.sourceType}, ${h.sourceReference}, ${h.sourceSection}${h.sourceVersionDate ? `, dated ${h.sourceVersionDate}` : ""}; effective ${h.effectiveDate}; checked by ${h.verifiedBy} on ${h.verifiedOn}.`
