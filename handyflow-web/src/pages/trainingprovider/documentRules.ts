// Wording and checks for the provider's own learner documents. The server enforces every rule and fixes the document wording.
// These are the provider's records. They are never an authority's certificate.

export type LearnerDocument = {
  id: string; documentNo: string; docType: string; enrolmentId: string; enrolmentNo: string | null; learnerId: string; learnerName: string; programmeTitle: string
  versionNo: number | null; issuedOn: string; title: string; body: string; disclaimer: string; status: string; voidReason: string | null
  handedOverOn: string | null; handedOverTo: string | null; handedOverMethod: string | null; completionStillStands: boolean
}

export const MIN_REASON = 10
export const DOC_TYPES: Record<string, string> = { COMPLETION_LETTER: "Completion letter", PARTICIPATION_STATEMENT: "Participation statement" }
export const METHODS: Record<string, string> = { IN_PERSON: "In person", EMAIL: "Email", COLLECTED: "Collected by the learner", POST: "Post" }
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")

export const DOCUMENT_NOTE = "These are the provider's own records. They are not a certificate or statement of results from the QCTO, SAQA or any other authority."

export function docTone(d: Pick<LearnerDocument, "status">): "good" | "warn" | "bad" | "neutral" {
  return d.status === "VOID" ? "neutral" : "good"
}

/** One line on where a document stands. */
export function documentLine(d: Pick<LearnerDocument, "status" | "issuedOn" | "handedOverOn" | "handedOverTo" | "handedOverMethod" | "voidReason">): string {
  if (d.status === "VOID") return `Voided. Reason: ${d.voidReason ?? "—"}`
  if (d.handedOverOn) return `Issued ${d.issuedOn}. Handed over to ${d.handedOverTo} on ${d.handedOverOn} (${label(METHODS, d.handedOverMethod).toLowerCase()})`
  return `Issued ${d.issuedOn}. Not yet handed over`
}

/** A warning when a letter's completion has since been withdrawn. */
export const standingWarning = (d: Pick<LearnerDocument, "docType" | "status" | "completionStillStands">): string | null =>
  d.docType === "COMPLETION_LETTER" && d.status === "ISSUED" && !d.completionStillStands
    ? "The completed outcome this letter rests on has been withdrawn. Void the letter with a reason." : null

export type HandOverForm = { handedOverOn: string; handedOverTo: string; method: string }
export const emptyHandOver = (today: string): HandOverForm => ({ handedOverOn: today, handedOverTo: "", method: "" })

export function handOverProblem(f: HandOverForm, issuedOn: string, today: string): string | null {
  if (!f.handedOverOn) return "Enter the day it was handed over"
  if (f.handedOverOn > today) return "The hand-over day cannot be in the future"
  if (f.handedOverOn < issuedOn) return "The hand-over day cannot be before the document was issued"
  if (f.handedOverTo.trim().length < 2) return "Say who it was handed over to"
  if (!METHODS[f.method]) return "Choose how it was handed over"
  return null
}
export const handOverBody = (f: HandOverForm) => ({ handedOverOn: f.handedOverOn, handedOverTo: f.handedOverTo.trim(), method: f.method })
export const voidProblem = (r: string): string | null => (r.trim().length >= MIN_REASON ? null : `Say why, in at least ${MIN_REASON} characters`)

/** Types that can still be issued for an enrolment: not already live, and a letter only when a completed outcome stands. */
export function issuableTypes(live: Pick<LearnerDocument, "docType" | "status">[], completedStands: boolean): string[] {
  const has = new Set(live.filter(d => d.status === "ISSUED").map(d => d.docType))
  return Object.keys(DOC_TYPES).filter(t => !has.has(t) && (t !== "COMPLETION_LETTER" || completedStands))
}
