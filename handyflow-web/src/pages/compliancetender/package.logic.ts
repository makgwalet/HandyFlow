// src/pages/compliancetender/package.logic.ts
//
// Pure helpers for the submission package screen (ADR-005): turning what the person typed into a request, and turning what the server answered into words. No React, no network.
import type { BuildRequest, ComplianceDoc, LimitsRequest, PackagePlan, PlanIssue, SectionStatus } from "./package.api"

export interface SectionInfo { key: string; title: string; hint: string }

/** The sections this version knows, in default order. The server owns the catalogue; these labels are only for choosing and ordering before a preview exists. */
export const SECTIONS: SectionInfo[] = [
  { key: "COVER_LETTER", title: "Cover letter", hint: "Written for this tender" },
  { key: "COMPANY_PROFILE", title: "Company profile", hint: "From your company details, or customised" },
  { key: "COMPLIANCE", title: "Compliance response", hint: "Where you stand on each requirement" },
  { key: "KEY_PERSONNEL", title: "Key personnel", hint: "The people added to this tender" },
  { key: "PRICING", title: "Pricing schedule", hint: "Needs manage permission" },
  { key: "SUPPORTING_DOCUMENTS", title: "Supporting documents", hint: "Certificates and documents you choose" },
]

export interface LimitsDraft {
  name: string; allowed: string; maxFileMb: string; maxTotalMb: string; maxFiles: string; maxNameLength: string; zip: "" | "yes" | "no"
}
export const EMPTY_LIMITS: LimitsDraft = { name: "", allowed: "", maxFileMb: "", maxTotalMb: "", maxFiles: "", maxNameLength: "", zip: "" }

export type OutputMode = "COMBINED" | "NUMBERED_ZIP"

export interface PackageDraft {
  included: string[]                       // section keys, in package order
  coverLetter: string
  companyMode: "CURRENT" | "CUSTOM"
  companyText: string
  documentIds: string[]
  profileId: string | null
  limits: LimitsDraft
  pricingRequired: boolean
  pageNumbers: boolean                     // stamp "Page X of N" across the combined PDF
  compress: boolean                        // write the combined PDF with full compression
  outputMode: OutputMode                   // one merged PDF, or every file separate and numbered in a ZIP
}

export const INITIAL_DRAFT: PackageDraft = {
  included: SECTIONS.map(s => s.key), coverLetter: "", companyMode: "CURRENT", companyText: "", documentIds: [],
  profileId: null, limits: EMPTY_LIMITS, pricingRequired: false, pageNumbers: false, compress: false, outputMode: "COMBINED",
}

const MB = 1024 * 1024

/** A positive number typed as "12", "12.5" or "12,5"; null when blank, undefined when it is not a usable positive number. */
export function parsePositive(text: string): number | null | undefined {
  const t = text.trim().replace(",", ".")
  if (t === "") return null
  if (!/^\d+(\.\d+)?$/.test(t)) return undefined
  const n = Number(t)
  return n > 0 ? n : undefined
}

export type LimitsResult = { ok: true; value: LimitsRequest | null } | { ok: false; error: string }

/** Tender-specific limits as typed. Blank everywhere means no override; a half-valid entry is refused with a reason, never quietly dropped. */
export function parseLimits(d: LimitsDraft): LimitsResult {
  const anything = d.name.trim() || d.allowed.trim() || d.maxFileMb.trim() || d.maxTotalMb.trim() || d.maxFiles.trim() || d.maxNameLength.trim() || d.zip
  if (!anything) return { ok: true, value: null }
  const fileMb = parsePositive(d.maxFileMb), totalMb = parsePositive(d.maxTotalMb), files = parsePositive(d.maxFiles), nameLen = parsePositive(d.maxNameLength)
  if (fileMb === undefined) return { ok: false, error: "Maximum file size must be a number above zero." }
  if (totalMb === undefined) return { ok: false, error: "Maximum total size must be a number above zero." }
  if (files === undefined || (files !== null && !Number.isInteger(files))) return { ok: false, error: "Maximum number of files must be a whole number above zero." }
  if (nameLen === undefined || (nameLen !== null && !Number.isInteger(nameLen))) return { ok: false, error: "Maximum file name length must be a whole number above zero." }
  const extensions = d.allowed.split(/[,\s]+/).map(e => e.trim().toLowerCase().replace(/^\./, "")).filter(Boolean)
  return {
    ok: true,
    value: {
      name: d.name.trim() || "This tender",
      allowedExtensions: extensions.length ? Array.from(new Set(extensions)) : null,
      maxFileBytes: fileMb === null ? null : Math.round(fileMb * MB),
      maxTotalBytes: totalMb === null ? null : Math.round(totalMb * MB),
      maxFileCount: files, zipAllowed: d.zip === "" ? null : d.zip === "yes", maxFileNameLength: nameLen,
    },
  }
}

export type RequestResult = { ok: true; request: BuildRequest } | { ok: false; error: string }

export function toRequest(d: PackageDraft): RequestResult {
  const limits = parseLimits(d.limits)
  if (!limits.ok) return limits
  return {
    ok: true,
    request: {
      sectionKeys: d.included,
      coverLetterText: d.coverLetter.trim() || null,
      companyProfileText: d.companyMode === "CUSTOM" && d.companyText.trim() ? d.companyText.trim() : null,
      documentIds: d.documentIds,
      submissionProfileId: d.profileId,
      limits: limits.value,
      pricingRequired: d.pricingRequired,
      pageNumbers: d.pageNumbers && d.outputMode === "COMBINED",
      compress: d.compress && d.outputMode === "COMBINED",
      outputMode: d.outputMode,
    },
  }
}

/** The request for a screen that has not been touched; used only to give the preview query something to hold while the real one is being debounced. */
export const DEFAULT_REQUEST: BuildRequest = {
  sectionKeys: INITIAL_DRAFT.included, coverLetterText: null, companyProfileText: null, documentIds: [], submissionProfileId: null, limits: null, pricingRequired: false, pageNumbers: false, compress: false, outputMode: "COMBINED",
}

// ---- section choice and order

export function toggleSection(included: string[], key: string): string[] {
  if (included.includes(key)) return included.filter(k => k !== key)
  // switched back on, a section returns to its default place among the chosen ones rather than the end
  const rank = (k: string) => SECTIONS.findIndex(s => s.key === k)
  const next = [...included, key]
  return next.sort((a, b) => rank(a) - rank(b))
}

export function moveSection(included: string[], key: string, by: -1 | 1): string[] {
  const i = included.indexOf(key), j = i + by
  if (i < 0 || j < 0 || j >= included.length) return included
  const next = [...included]
  ;[next[i], next[j]] = [next[j], next[i]]
  return next
}

export function toggleDocument(ids: string[], id: string): string[] { return ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id] }

// ---- reading the server's answer

export const blocking = (issues: PlanIssue[]): PlanIssue[] => issues.filter(i => i.severity === "BLOCKING")
export const warnings = (issues: PlanIssue[]): PlanIssue[] => issues.filter(i => i.severity === "WARNING")

/** Blocking issues first, then warnings, each in the order the server gave them. */
export function feed(issues: PlanIssue[]): PlanIssue[] { return [...blocking(issues), ...warnings(issues)] }

/** Chosen sections that produced something, out of those chosen. Counts, not a score: nothing here is weighted. */
export function sectionsReady(plan: PackagePlan): { ready: number; chosen: number } {
  return { ready: plan.sections.filter(s => s.available).length, chosen: plan.sections.length }
}

export function headline(plan: PackagePlan): { tone: "ready" | "draft" | "blocked"; title: string; detail: string } {
  const b = blocking(plan.issues).length
  if (!plan.canBuild) return { tone: "blocked", title: "Not ready to build", detail: b === 1 ? "1 problem has to be fixed first." : `${b} problems have to be fixed first.` }
  if (plan.submissionReady) return { tone: "ready", title: "Ready to submit", detail: "Everything chosen is in and nothing is blocking." }
  const missing = plan.sections.filter(s => !s.available).length
  const reasons = plan.issues.some(i => i.code === "PRICING_REQUIRED") ? "pricing is required but missing" : missing === 1 ? "1 section is missing" : `${missing} sections are missing`
  return { tone: "draft", title: "Draft only", detail: `A draft can be built, but it is not ready to submit: ${reasons}.` }
}

export function sectionTone(s: SectionStatus | undefined): "ok" | "missing" | "unknown" { return !s ? "unknown" : s.available ? "ok" : "missing" }

export function fmtSize(bytes: number): string {
  if (bytes >= 1024 * MB) return `${(bytes / (1024 * MB)).toFixed(1)} GB`
  if (bytes >= MB) return `${(bytes / MB).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

export const shortHash = (h: string): string => (h.length > 12 ? `${h.slice(0, 8)}…${h.slice(-4)}` : h)

export function sourceText(source: "GENERATED" | "ATTACHED" | "ORIGINAL"): string {
  return source === "GENERATED" ? "Generated" : source === "ATTACHED" ? "Merged in" : "Original kept"
}

// ---- dates

const DAY = 86_400_000
const startOfDay = (d: Date) => Date.UTC(d.getFullYear(), d.getMonth(), d.getDate())

/** Whole calendar days from `today` to an ISO date (yyyy-mm-dd); negative once it has passed; null with no date. */
export function daysUntil(iso: string | null | undefined, today: Date): number | null {
  if (!iso) return null
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  if (!m) return null
  return Math.round((Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])) - startOfDay(today)) / DAY)
}

export function closingText(iso: string | null | undefined, today: Date): { text: string; tone: "ok" | "soon" | "late" | "none" } {
  const n = daysUntil(iso, today)
  if (n === null) return { text: "No closing date set", tone: "none" }
  if (n < 0) return { text: n === -1 ? "Closed yesterday" : `Closed ${-n} days ago`, tone: "late" }
  if (n === 0) return { text: "Closes today", tone: "late" }
  if (n === 1) return { text: "Closes tomorrow", tone: "soon" }
  return { text: `${n} days remaining`, tone: n <= 7 ? "soon" : "ok" }
}

/** Whether a document will still be valid when the tender closes. The server's compliance section judges this too; this is the quick signal beside each document. */
export function documentState(doc: Pick<ComplianceDoc, "expiryDate">, closingIso: string | null | undefined, today: Date): { text: string; tone: "ok" | "warn" | "bad" | "none" } {
  const untilExpiry = daysUntil(doc.expiryDate, today)
  if (untilExpiry === null) return { text: "No expiry date", tone: "none" }
  if (untilExpiry < 0) return { text: "Expired", tone: "bad" }
  const untilClosing = daysUntil(closingIso, today)
  if (untilClosing !== null && untilExpiry < untilClosing) return { text: "Expires before closing", tone: "bad" }
  if (untilExpiry <= 30) return { text: `Expires in ${untilExpiry} days`, tone: "warn" }
  return { text: "Valid", tone: "ok" }
}

/** "12 Oct 2026, 14:05" in South African time; empty text for a bad value. */
export function fmtWhen(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ""
  return d.toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })
}

/** A starting cover letter. The page adds the letterhead, date, addressee and the "Re:" line itself, so this is only the body. */
export const COVER_LETTER_TEMPLATE = [
  "Dear Sir/Madam,",
  "",
  "We submit our tender for the above, together with the documents listed in this submission package.",
  "",
  "We confirm that:",
  "- our tender is valid for the period stated in the tender document;",
  "- we have read and accept the conditions of tender; and",
  "- the information supplied is true and correct.",
  "",
  "Please contact us if you need any clarification.",
  "",
  "Yours faithfully",
].join("\n")

/** The newest version, when it no longer matches the tender. Older versions are superseded, so only the newest matters for the warning. */
export function staleLatest<T extends { versionNo: number; stale?: boolean; staleReasons?: string[] }>(packages: T[]): T | null {
  if (packages.length === 0) return null
  const latest = packages.reduce((a, b) => (b.versionNo > a.versionNo ? b : a))
  return latest.stale ? latest : null
}

// ---- saved draft

/** A stable text form of the draft, used to tell whether anything changed since the last save. */
export const draftKey = (d: PackageDraft): string => JSON.stringify(d)

const isStrings = (v: unknown): v is string[] => Array.isArray(v) && v.every(x => typeof x === "string")

/** Turns whatever was saved back into a usable draft. Unknown or malformed parts fall back to the defaults, so an old or damaged draft never breaks the screen. */
export function hydrateDraft(stored: unknown): PackageDraft {
  const s = (stored && typeof stored === "object" ? stored : {}) as Record<string, unknown>
  const known = new Set(SECTIONS.map(x => x.key))
  const included = isStrings(s.included) ? s.included.filter(k => known.has(k)) : null
  const lim = (s.limits && typeof s.limits === "object" ? s.limits : {}) as Record<string, unknown>
  const limits = { ...EMPTY_LIMITS }
  for (const k of Object.keys(EMPTY_LIMITS) as (keyof LimitsDraft)[]) {
    if (typeof lim[k] === "string") (limits as Record<string, string>)[k] = lim[k] as string
  }
  if (limits.zip !== "" && limits.zip !== "yes" && limits.zip !== "no") limits.zip = ""
  return {
    included: included && included.length > 0 ? included : INITIAL_DRAFT.included,
    coverLetter: typeof s.coverLetter === "string" ? s.coverLetter : "",
    companyMode: s.companyMode === "CUSTOM" ? "CUSTOM" : "CURRENT",
    companyText: typeof s.companyText === "string" ? s.companyText : "",
    documentIds: isStrings(s.documentIds) ? s.documentIds : [],
    profileId: typeof s.profileId === "string" ? s.profileId : null,
    limits,
    pricingRequired: s.pricingRequired === true,
    pageNumbers: s.pageNumbers === true,
    compress: s.compress === true,
    outputMode: s.outputMode === "NUMBERED_ZIP" ? "NUMBERED_ZIP" : "COMBINED",
  }
}

export interface SaveState { kind: "idle" | "saving" | "saved" | "error"; at?: string; by?: string | null; error?: string }

export function saveStateText(s: SaveState): string {
  switch (s.kind) {
    case "saving": return "Saving…"
    case "error": return `Not saved. ${s.error ?? "Try again."}`
    case "saved": {
      const t = s.at ? new Date(s.at).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" }) : ""
      return `Draft saved${t ? ` at ${t}` : ""}${s.by ? ` by ${s.by}` : ""}`
    }
    default: return "Your choices are saved as you go"
  }
}

/** Adds the chosen documents to the ticked ones (never un-ticks anything) and lists what could not be chosen. */
export function applySuggestions(ids: string[], suggestions: { outcome: string; documentId: string | null; requirement: string; message: string }[]): { ids: string[]; added: number; problems: string[] } {
  const next = [...ids]
  let added = 0
  for (const s of suggestions) {
    if (s.outcome === "CHOSEN" && s.documentId && !next.includes(s.documentId)) { next.push(s.documentId); added++ }
  }
  const problems = suggestions.filter(s => s.outcome !== "CHOSEN").map(s => s.message)
  return { ids: next, added, problems }
}
