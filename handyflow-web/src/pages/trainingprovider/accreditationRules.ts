// Wording and checks for accreditation records. The server enforces every rule; these save a round trip.
// Nothing here says a provider is accredited because a number was typed in: only a recorded, verified approval counts.

export type Authority = { id: string; name: string; authorityType: string; notes: string | null; status: string; retiredReason: string | null }
export type Site = { id: string; name: string; siteType: string; address: string | null; province: string | null; contactPerson: string | null; phone: string | null; status: string; retiredReason: string | null }
export type Accreditation = {
  id: string; authorityId: string; authorityName: string; title: string; accreditationNumber: string | null
  status: string; effectiveStatus: string; submittedOn: string | null; approvalDate: string | null; expiryDate: string | null
  daysToExpiry: number | null; conditions: string | null; statusReason: string | null; verificationSource: string | null
  lastVerifiedOn: string | null; nextStatuses: string[]; detailsEditable: boolean; liveScopes: number
}
export type Scope = {
  id: string; accreditationId: string; siteId: string; siteName: string; courseId: string | null; programmeId: string | null
  programmeTitle: string; qualificationRef: string | null; nqfLevel: number | null; credits: number | null
  validFrom: string | null; validTo: string | null; notes: string | null; status: string; withdrawnReason: string | null
}
export type AccEvent = { id: string; fromStatus: string | null; toStatus: string; reason: string | null; overrideReason: string | null; actedBy: string | null; actedAt: string }
export type AccreditationDetail = { accreditation: Accreditation; scopes: Scope[]; history: AccEvent[] }

export const MIN_REASON = 10
export const MIN_SOURCE = 5

export const AUTHORITY_TYPES: Record<string, string> = {
  QCTO: "QCTO", SETA: "SETA", SAQA: "SAQA", DHET: "DHET", UMALUSI: "Umalusi", CHE: "CHE", PROFESSIONAL_BODY: "Professional body", OTHER: "Other",
}
export const SITE_TYPES: Record<string, string> = {
  CAMPUS: "Campus", DELIVERY_SITE: "Delivery site", ASSESSMENT_CENTRE: "Assessment centre", OTHER: "Other",
}

export const STATUS: Record<string, string> = {
  DRAFT: "Draft", APPLICATION_PREPARATION: "Preparing application", SUBMITTED: "Submitted", UNDER_REVIEW: "Under review",
  MORE_INFORMATION_REQUIRED: "More information required", CONDITIONAL_APPROVAL: "Approved with conditions", APPROVED: "Approved",
  REJECTED: "Rejected", EXPIRED: "Expired", SUSPENDED: "Suspended", WITHDRAWN: "Withdrawn", RENEWAL_PENDING: "Renewal pending",
}
export const statusLabel = (s: string) => STATUS[s] ?? s

export const APPROVAL_STATES = ["APPROVED", "CONDITIONAL_APPROVAL"]
export const NEEDS_REASON = ["REJECTED", "SUSPENDED", "WITHDRAWN", "MORE_INFORMATION_REQUIRED", "EXPIRED"]
export const isApproval = (s: string) => APPROVAL_STATES.includes(s)

/** Plain-words tone for a status badge. */
export function statusTone(s: string): "good" | "warn" | "bad" | "neutral" {
  if (s === "APPROVED") return "good"
  if (["CONDITIONAL_APPROVAL", "RENEWAL_PENDING", "MORE_INFORMATION_REQUIRED"].includes(s)) return "warn"
  if (["REJECTED", "EXPIRED", "SUSPENDED"].includes(s)) return "bad"
  return "neutral"
}

export const COVERAGE: Record<string, string> = {
  COVERED: "Covered", COVERED_WITH_CONDITIONS: "Covered, with conditions", COVERED_RENEWAL_PENDING: "Covered, renewal pending",
  NO_SCOPE: "No approved scope", SITE_NOT_COVERED: "Not covered at this site", SCOPE_WITHDRAWN: "Scope withdrawn",
  SCOPE_NOT_IN_FORCE: "Scope not in force on that date", NOT_APPROVED: "Accreditation not approved",
  SUSPENDED: "Accreditation suspended", EXPIRED: "Accreditation expired",
}
export const coverageLabel = (o: string) => COVERAGE[o] ?? o

/** "Expires in 40 days", "Expired 12 days ago", or a dash when nothing is recorded. */
export function expiryNote(days: number | null | undefined): string {
  if (days == null) return "No expiry recorded"
  if (days === 0) return "Expires today"
  if (days > 0) return `Expires in ${days} day${days === 1 ? "" : "s"}`
  const n = -days
  return `Expired ${n} day${n === 1 ? "" : "s"} ago`
}

/** Warn when an approval is within the supplied number of days. The window is the caller's choice: nothing is fixed here. */
export const expiringWithin = (days: number | null | undefined, window: number): boolean => days != null && days >= 0 && days <= window

const long = (s: string | null | undefined, n: number) => (s ?? "").trim().length >= n

export type TransitionForm = {
  toStatus: string; reason: string; accreditationNumber: string; approvalDate: string; expiryDate: string
  conditions: string; verificationSource: string; verifiedOn: string; overrideReason: string
}
export const emptyTransition = (toStatus = ""): TransitionForm => ({
  toStatus, reason: "", accreditationNumber: "", approvalDate: "", expiryDate: "", conditions: "", verificationSource: "", verifiedOn: "", overrideReason: "",
})

/** Mirrors the server checks that do not need to know who created the record (the server decides the second-person rule). */
export function transitionProblem(f: TransitionForm, from: string, allowed: string[], today: string): string | null {
  if (!f.toStatus) return "Choose the new status"
  if (!allowed.includes(f.toStatus)) return `An accreditation cannot move from ${statusLabel(from).toLowerCase()} to ${statusLabel(f.toStatus).toLowerCase()}`
  if (NEEDS_REASON.includes(f.toStatus) && !long(f.reason, MIN_REASON)) return `Say why, in at least ${MIN_REASON} characters`
  if (isApproval(f.toStatus)) {
    if (!f.accreditationNumber.trim()) return "Enter the accreditation number given by the authority"
    if (!f.approvalDate) return "Enter the approval date shown on the authority's letter or register"
    if (f.approvalDate > today) return "The approval date cannot be in the future"
    if (f.expiryDate && f.expiryDate < f.approvalDate) return "The expiry date cannot be before the approval date"
    if (f.expiryDate && f.expiryDate < today) return "That approval has already expired. Record it as expired, or enter the renewed expiry date"
    if (!long(f.verificationSource, MIN_SOURCE)) return "Say where the approval was verified, for example the authority's letter or register"
    if (f.toStatus === "CONDITIONAL_APPROVAL" && !long(f.conditions, MIN_REASON)) return "A conditional approval needs its conditions written down"
  }
  return null
}

const blank = (s: string) => (s.trim() === "" ? null : s.trim())
export function transitionBody(f: TransitionForm) {
  return {
    toStatus: f.toStatus, reason: blank(f.reason), accreditationNumber: blank(f.accreditationNumber),
    approvalDate: f.approvalDate || null, expiryDate: f.expiryDate || null, conditions: blank(f.conditions),
    verificationSource: blank(f.verificationSource), verifiedOn: f.verifiedOn || null, overrideReason: blank(f.overrideReason),
  }
}

export function reasonProblem(reason: string): string | null {
  return long(reason, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`
}
export function verifyProblem(source: string): string | null {
  return long(source, MIN_SOURCE) ? null : "Say where it was checked, for example the authority's register"
}

export type ScopeForm = { siteId: string; programmeTitle: string; qualificationRef: string; nqfLevel: string; credits: string; validFrom: string; validTo: string; notes: string }
export const emptyScope: ScopeForm = { siteId: "", programmeTitle: "", qualificationRef: "", nqfLevel: "", credits: "", validFrom: "", validTo: "", notes: "" }

export function scopeProblem(f: ScopeForm): string | null {
  if (!f.siteId) return "Choose the site this scope applies to. Approval at one site never covers another"
  if (f.programmeTitle.trim().length < 3) return "Enter the programme or qualification as named in the approval"
  const nqf = f.nqfLevel.trim() === "" ? null : Number(f.nqfLevel)
  if (nqf !== null && (!Number.isInteger(nqf) || nqf < 1 || nqf > 10)) return "The NQF level is a whole number from 1 to 10"
  const cr = f.credits.trim() === "" ? null : Number(f.credits)
  if (cr !== null && (!Number.isInteger(cr) || cr < 0)) return "Credits must be a whole number, zero or more"
  if (f.validFrom && f.validTo && f.validTo < f.validFrom) return "The scope cannot end before it starts"
  return null
}
export function scopeBody(f: ScopeForm) {
  return {
    siteId: f.siteId, programmeTitle: f.programmeTitle.trim(), qualificationRef: blank(f.qualificationRef),
    nqfLevel: f.nqfLevel.trim() === "" ? null : Number(f.nqfLevel), credits: f.credits.trim() === "" ? null : Number(f.credits),
    validFrom: f.validFrom || null, validTo: f.validTo || null, notes: blank(f.notes),
  }
}

export function nameProblem(name: string, what: string): string | null {
  return name.trim().length >= 3 ? null : `Give the ${what} a name of at least 3 characters`
}
