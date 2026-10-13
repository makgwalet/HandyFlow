// Patient account: wording and tones for the claim status of each visit, and small money helpers.
export interface AccountVisit {
  consultationId: string; claimId?: string | null; visitDate?: string | null; chiefComplaint?: string | null
  claimStatus: string; schemeName?: string | null
  charged?: number | null; schemePortion?: number | null; patientPortion?: number | null
  schemeOutstanding?: number | null; paidAgainstVisit?: number | null
}
export interface AccountPayment { id: string; claimId?: string | null; paidAt?: string | null; method: string; amount: number; reference?: string | null }
export interface Account {
  patientId: string; totalCharged: number; schemeShare: number; patientShare: number; paidByPatient: number
  balanceOwing: number; schemeOutstanding: number; visitsNotBilled: number; visits: AccountVisit[]; payments: AccountPayment[]
  /** Money the patient has paid that no visit carries yet (patch 0190). */
  paidOnAccount?: number
}

type Tone = "ok" | "warn" | "info" | "muted" | "bad"
export const CLAIM: Record<string, { label: string; tone: Tone }> = {
  NOT_BILLED: { label: "Not billed yet", tone: "warn" },
  DRAFT: { label: "Claim not sent", tone: "muted" },
  SUBMITTED: { label: "Sent to scheme", tone: "info" },
  ACCEPTED: { label: "Scheme accepted", tone: "info" },
  PARTIAL: { label: "Scheme part-paid", tone: "warn" },
  PAID: { label: "Scheme paid", tone: "ok" },
  CLOSED: { label: "Closed", tone: "ok" },
  REJECTED: { label: "Scheme rejected", tone: "bad" },
  VOIDED: { label: "Voided", tone: "muted" },
}
export const claimStatus = (s: string) => CLAIM[s] ?? { label: s.toLowerCase().replace(/_/g, " "), tone: "muted" as Tone }

/** Rejected and voided claims are not owed, so their amounts are shown struck through rather than added up. */
export const isOwed = (s: string) => s !== "REJECTED" && s !== "VOIDED" && s !== "NOT_BILLED"

export const methodLabel = (m: string) => ({ CASH: "Cash", EFT: "EFT", CARD: "Card" } as Record<string, string>)[m] ?? m

/** What the patient still owes on one visit: their share less what has been linked to it. Nothing for a visit with no claim, or a rejected or voided one. */
export function owedOnVisit(v: AccountVisit): number {
  if (!v.claimId || !isOwed(v.claimStatus)) return 0
  return Math.max(0, Math.round(((v.patientPortion ?? 0) - (v.paidAgainstVisit ?? 0)) * 100) / 100)
}

/** Visits a payment can be put towards, oldest first (the order the server pays them in when none is chosen). */
export function payableVisits(acc: Account): AccountVisit[] {
  return acc.visits.filter(v => owedOnVisit(v) > 0).sort((a, b) => (a.visitDate ?? "").localeCompare(b.visitDate ?? ""))
}

/** The wording of a visit in the "pay towards" list: "12 Sep 2026 · cough · owes R 80.00". */
export function payTowardsLabel(v: AccountVisit, fmtDay: (d: string) => string, fmtR: (n: number) => string): string {
  return [v.visitDate ? fmtDay(v.visitDate) : "Visit", v.chiefComplaint, `owes ${fmtR(owedOnVisit(v))}`].filter(Boolean).join(" · ")
}

/** The note under the figures when payments are not linked to any visit. */
export function onAccountNote(acc: Account): string {
  const n = acc.paidOnAccount ?? 0
  return n > 0 ? `${n.toLocaleString("en-ZA", { minimumFractionDigits: 2 })} of what the patient has paid is not linked to a visit yet.` : ""
}
