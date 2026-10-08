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
