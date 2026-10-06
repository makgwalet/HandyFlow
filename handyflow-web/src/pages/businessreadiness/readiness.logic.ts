// src/pages/businessreadiness/readiness.logic.ts
//
// Pure rules for showing a readiness assessment and editing the requirement catalogue. The judging itself is done by the server (the shared, tested evaluator); these only decide
// wording, order and what to send. Wording is factual: no score, no percentage, and a result is always a WORD so it reads without colour.
import type { ReadinessAssessment, ReadinessItem, ReadinessResult, ReadinessSummary, TrackedRequirement } from "./readiness.api"

export const RESULT_LABEL: Record<ReadinessResult, string> = {
  MET: "Met", MISSING: "Missing", EXPIRED: "Expired", PENDING: "Pending", NOT_EVALUATED: "Not checked", NOT_APPLICABLE: "Not applicable",
}

/** What the user ticked on a requirement, in words. */
export const MANUAL_LABEL: Record<string, string> = { PENDING_REVIEW: "Pending review", MET: "Met", MISSING: "Missing", NOT_APPLICABLE: "Not applicable" }

/** Worst first, so what needs doing is at the top. Met and not applicable go last. */
const ORDER: ReadinessResult[] = ["MISSING", "EXPIRED", "PENDING", "NOT_EVALUATED", "MET", "NOT_APPLICABLE"]

export function sortItems(items: ReadinessItem[]): ReadinessItem[] {
  return items.map((it, i) => ({ it, i })).sort((a, b) => ORDER.indexOf(a.it.result) - ORDER.indexOf(b.it.result) || a.i - b.i).map(x => x.it)
}

/** "15 Nov 2026" from an ISO date, without letting the viewer's time zone move the day. */
export function formatIsoDate(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  if (!m) return iso
  return new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]))).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" })
}

export function basisText(a: Pick<ReadinessAssessment, "asOf" | "asOfBasis">): string {
  return a.asOfBasis === "CLOSING_DATE" ? `Judged as of the closing date, ${formatIsoDate(a.asOf)}` : `No closing date is set, so judged as of today, ${formatIsoDate(a.asOf)}`
}

/** How many need doing something about: missing, expired or pending. Not-checked ones are counted separately, never as fine. */
export function attentionCount(s: ReadinessSummary): number { return s.missing + s.expired + s.pending }

export function headline(s: ReadinessSummary): string {
  if (s.total === 0) return "No requirements to check yet"
  const need = attentionCount(s)
  if (need > 0) return `${need} of ${s.total} need attention`
  if (s.notEvaluated > 0) return `${s.met} met; ${s.notEvaluated} can't be checked yet`
  return `Everything that can be checked is met (${s.met} of ${s.total})`
}

/** The counts worth showing as chips, each as words. Zero counts are left out. */
export function chips(s: ReadinessSummary): { key: string; text: string }[] {
  const c: { key: string; n: number; one: string; many?: string }[] = [
    { key: "missing", n: s.missing, one: "missing" }, { key: "expired", n: s.expired, one: "expired" }, { key: "pending", n: s.pending, one: "pending" },
    { key: "notEvaluated", n: s.notEvaluated, one: "not checked" }, { key: "met", n: s.met, one: "met" }, { key: "notApplicable", n: s.notApplicable, one: "not applicable" },
    { key: "expiringSoon", n: s.expiringSoon, one: "expiring soon" }, { key: "differ", n: s.differFromManualStatus, one: "differ from your status" },
  ]
  return c.filter(x => x.n > 0).map(x => ({ key: x.key, text: `${x.n} ${x.one}` }))
}

/** One line per thing to point out about an item, beyond its result and detail. */
export function itemNotes(it: ReadinessItem): string[] {
  const out: string[] = []
  if (it.differsFromManualStatus) {
    out.push(it.manualStatus === "MET" ? `You marked this met, but the evidence says ${RESULT_LABEL[it.result].toLowerCase()}`
      : `You marked this missing, but the evidence says it is met`)
  }
  if (it.expiringSoon && it.expiresOn) out.push(`Expires ${formatIsoDate(it.expiresOn)}, within 30 days of the date it is judged on`)
  if (it.newerVersionAvailable) out.push("A newer version of this tracked requirement exists; this tender is judged on the version it was linked to")
  return out
}

// ---- the requirement catalogue --------------------------------------------------------------------------------------------

/** What satisfies a requirement, in words, for the catalogue table. */
export function ruleText(r: Pick<TrackedRequirement, "satisfiedByAuthority" | "satisfiedByRegistrationType" | "evidenceType">): string {
  const reg = [r.satisfiedByAuthority, r.satisfiedByRegistrationType].filter(x => x && x.trim()).join(" · ")
  const doc = r.evidenceType && r.evidenceType.trim() ? r.evidenceType.trim() : ""
  const parts: string[] = []
  if (reg) parts.push(`Registration: ${reg}`)
  if (doc) parts.push(`Document: ${doc}`)
  return parts.length ? parts.join("; ") : "No rule set, so it is not checked"
}

export interface RequirementForm { code: string; name: string; appliesTo: string; evidenceType: string; authority: string; registrationType: string; required: boolean }

export const emptyForm = (): RequirementForm => ({ code: "", name: "", appliesTo: "", evidenceType: "", authority: "", registrationType: "", required: true })

export const formFrom = (r: TrackedRequirement): RequirementForm => ({
  code: r.code, name: r.name, appliesTo: r.appliesTo ?? "", evidenceType: r.evidenceType ?? "", authority: r.satisfiedByAuthority ?? "", registrationType: r.satisfiedByRegistrationType ?? "", required: r.required,
})

/** The first problem with the form, or null. The code is only needed when creating (a new version keeps the code). */
export function formProblem(f: RequirementForm, creating: boolean): string | null {
  if (creating && !f.code.trim()) return "Enter a code, for example CSD_ACTIVE."
  if (creating && !/^[A-Za-z0-9_]+$/.test(f.code.trim())) return "The code can only have letters, numbers and underscores."
  if (creating && f.code.trim().length > 60) return "The code can be at most 60 characters."
  if (!f.name.trim()) return "Enter a name for the requirement."
  if (f.authority.trim().length > 20) return "The registration authority can be at most 20 characters, for example CSD or CIDB."
  if (f.registrationType.trim().length > 60) return "The registration type can be at most 60 characters."
  if (f.evidenceType.trim().length > 60) return "The document type can be at most 60 characters."
  if (f.appliesTo.trim().length > 60) return "'Applies to' can be at most 60 characters."
  return null
}

const nullIfBlank = (s: string) => (s.trim() === "" ? null : s.trim())

/** Creating: a blank part is simply not set. */
export function createBody(f: RequirementForm) {
  return { code: f.code.trim().toUpperCase(), name: f.name.trim(), appliesTo: nullIfBlank(f.appliesTo), evidenceType: nullIfBlank(f.evidenceType), required: f.required,
    satisfiedByAuthority: nullIfBlank(f.authority), satisfiedByRegistrationType: nullIfBlank(f.registrationType) }
}

/**
 * A new version: the rule fields are sent as STRINGS even when blank, because on the server a missing value means "keep the current rule" and only a blank string clears it. So
 * emptying a box here really clears it.
 */
export function newVersionBody(f: RequirementForm) {
  return { name: f.name.trim(), appliesTo: nullIfBlank(f.appliesTo), evidenceType: nullIfBlank(f.evidenceType), required: f.required,
    satisfiedByAuthority: f.authority.trim(), satisfiedByRegistrationType: f.registrationType.trim() }
}

/** The server's own message when it refuses, else a plain fallback. */
export function errorText(e: unknown): string {
  const m = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
  return m || "Couldn't save. Try again."
}
