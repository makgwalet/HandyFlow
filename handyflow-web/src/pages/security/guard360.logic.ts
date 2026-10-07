// src/pages/security/guard360.logic.ts
//
// Pure rules for the Guard 360 page: PSiRA state, the latest screening per type, the standard guard-file checklist
// and the header numbers. No React, no network. Slice 2 moves the readiness verdict to the server; until then this
// only describes what is on file and never produces a pass/fail percentage.

export type Tone = "ok" | "warn" | "bad" | "info" | "neutral"

export interface EvidenceItem { id: string; fileName: string; label: string | null; sizeBytes: number; uploadedByName: string | null; createdAt: string }
export interface ScreeningItem {
  id: string; screeningType: string; reason: string; result: string
  conductedBy: string | null; conductedAt: string | null; nextDueAt: string | null
  reportRef: string | null; createdAt: string
  provider: string | null; requestedAt: string | null
  decision: string | null; decisionNote: string | null; decidedByName: string | null; decidedAt: string | null
  evidence: EvidenceItem[]
}
export interface ReadinessItem {
  key: string; label: string; required: boolean; state: string; detail: string
  validUntil: string | null; evidenceCount: number; met: boolean; screeningId: string | null
}
export interface Readiness { percent: number; ready: boolean; items: ReadinessItem[]; reasons: string[] }
export interface DocumentItem { id: string; category: string; fileUrl: string; fileName: string | null; notes: string | null; createdAt: string }
export interface ShiftItem { id: string; siteId: string; siteName: string | null; startAt: string; endAt: string; status: string }

export const DUE_SOON_DAYS = 30

/** Today's date in South African time, as yyyy-mm-dd. */
export function todayIso(now: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg", year: "numeric", month: "2-digit", day: "2-digit" }).format(now)
}

/** Whole days from `from` to `to` (both yyyy-mm-dd). Negative when `to` is earlier. */
export function daysBetween(from: string, to: string): number {
  const a = Date.UTC(+from.slice(0, 4), +from.slice(5, 7) - 1, +from.slice(8, 10))
  const b = Date.UTC(+to.slice(0, 4), +to.slice(5, 7) - 1, +to.slice(8, 10))
  return Math.round((b - a) / 86400000)
}

export interface ExpiryState { tone: Tone; label: string; days: number | null }

/** Expiry of a registration such as the PSiRA card. No date on file is a warning, not a pass. */
export function expiryState(expiry: string | null | undefined, today: string): ExpiryState {
  if (!expiry) return { tone: "warn", label: "No expiry date on file", days: null }
  const days = daysBetween(today, expiry.slice(0, 10))
  if (days < 0) return { tone: "bad", label: `Expired ${-days} day${-days === 1 ? "" : "s"} ago`, days }
  if (days === 0) return { tone: "warn", label: "Expires today", days }
  if (days <= DUE_SOON_DAYS) return { tone: "warn", label: `Expires in ${days} day${days === 1 ? "" : "s"}`, days }
  return { tone: "ok", label: `Valid, expires ${expiry.slice(0, 10)}`, days }
}

export const SCREENING_TYPES: { value: string; label: string }[] = [
  { value: "ID_VERIFICATION", label: "ID verification" },
  { value: "CRIMINAL_RECORD_CHECK", label: "Criminal record check" },
  { value: "REFERENCE_CHECK", label: "Reference check" },
  { value: "DRUG_TEST", label: "Drug test" },
  { value: "POLYGRAPH", label: "Polygraph" },
  { value: "PSYCHOMETRIC", label: "Psychometric" },
  { value: "CREDIT_CHECK", label: "Credit check" },
  { value: "QUALIFICATION_VERIFICATION", label: "Qualification verification" },
  { value: "OTHER", label: "Other" },
]
export const screeningLabel = (t: string) => SCREENING_TYPES.find(x => x.value === t)?.label ?? t.replace(/_/g, " ").toLowerCase()

export const SCREENING_REASONS: { value: string; label: string }[] = [
  { value: "ONBOARDING", label: "Onboarding" }, { value: "PERIODIC", label: "Periodic renewal" }, { value: "POST_INCIDENT", label: "After an incident" },
  { value: "RANDOM", label: "Random" }, { value: "CLIENT_REQUESTED", label: "Client requested" },
]

/** Server states for a readiness row, as a tone and a short label. */
export const READINESS_STATE: Record<string, { tone: Tone; label: string }> = {
  MET: { tone: "ok", label: "Met" }, EXPIRING: { tone: "warn", label: "Expiring" }, PENDING: { tone: "warn", label: "Pending" },
  INCOMPLETE: { tone: "warn", label: "Incomplete" }, EXPIRED: { tone: "bad", label: "Expired" }, FAILED: { tone: "bad", label: "Failed" },
  MISSING: { tone: "neutral", label: "Missing" },
}
export const readinessState = (s: string) => READINESS_STATE[s] ?? { tone: "neutral" as Tone, label: s.toLowerCase() }

/** Colour of the readiness ring: ready is green, high but blocked is amber, low is red. */
export function readinessTone(r: { percent: number; ready: boolean }): Tone {
  if (r.ready) return "ok"
  return r.percent >= 60 ? "warn" : "bad"
}

/** The standard guard file. This is a default list, not a legal requirement for every client. */
export const GUARD_FILE: { category: string; label: string }[] = [
  { category: "ID_COPY", label: "ID copy" },
  { category: "PSIRA_CERTIFICATE", label: "PSiRA certificate" },
  { category: "PROOF_OF_ADDRESS", label: "Proof of address" },
  { category: "POLICE_CLEARANCE", label: "Police clearance" },
  { category: "POPIA_CONSENT", label: "POPIA consent" },
]

export const DOCUMENT_CATEGORIES: { value: string; label: string }[] = [
  { value: "ID_COPY", label: "ID copy" },
  { value: "PROOF_OF_ADDRESS", label: "Proof of address" },
  { value: "PSIRA_CERTIFICATE", label: "PSiRA certificate" },
  { value: "BANK_CONFIRMATION", label: "Bank confirmation" },
  { value: "MATRIC_CERTIFICATE", label: "Matric certificate" },
  { value: "TRAINING_CERTIFICATE", label: "Training certificate" },
  { value: "FIREARM_COMPETENCY", label: "Firearm competency" },
  { value: "FIREARM_LICENSE", label: "Firearm licence" },
  { value: "DRIVERS_LICENSE", label: "Driver's licence" },
  { value: "MEDICAL_CERTIFICATE", label: "Medical certificate" },
  { value: "POLICE_CLEARANCE", label: "Police clearance" },
  { value: "FINGERPRINT_FORM", label: "Fingerprint form" },
  { value: "EMPLOYMENT_CONTRACT", label: "Employment contract" },
  { value: "POPIA_CONSENT", label: "POPIA consent" },
  { value: "PASSPORT_PHOTO", label: "Passport photo" },
]

export const categoryLabel = (c: string) => DOCUMENT_CATEGORIES.find(x => x.value === c)?.label ?? c.replace(/_/g, " ").toLowerCase()

export function fileChecklist(docs: DocumentItem[]): { category: string; label: string; present: boolean; count: number }[] {
  return GUARD_FILE.map(f => {
    const count = docs.filter(d => d.category === f.category).length
    return { ...f, present: count > 0, count }
  })
}

/** Share of started shifts that were completed, as a whole percent. Null when none have started. */
export function completionRate(started: number, completed: number): number | null {
  return started <= 0 ? null : Math.round((completed / started) * 100)
}

export function isUpcoming(s: ShiftItem, now: Date): boolean { return new Date(s.startAt).getTime() > now.getTime() }

export const SHIFT_TONE: Record<string, Tone> = { COMPLETED: "ok", ACTIVE: "info", SCHEDULED: "neutral", MISSED: "bad", CANCELLED: "neutral", PULLED: "warn" }
export const SEVERITY_TONE: Record<string, Tone> = { LOW: "neutral", MEDIUM: "info", HIGH: "warn", CRITICAL: "bad" }
