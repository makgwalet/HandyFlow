// Correct and resubmit (CLINIC-DEC-003/004). A claim that has left DRAFT is never edited: the old claim is voided and a linked
// draft is created for the same consultation. Mirrors ClaimLedgerRules.requireCorrectable and ClaimCorrectionRules on the server.
import { REASON_MAX, REASON_MIN, moneyHasMoved, type MoneyClaim } from "./claimMoney"

export const NOTES_MAX = 1000

export interface CorrectableLine { lineType: string; tariffCode?: string; icd10Code?: string; description: string; quantity: number; unitPrice: number }
export interface CorrectableClaim extends MoneyClaim {
  schemeName?: string; memberNumber?: string; dependentCode?: string; lines?: CorrectableLine[]
}

export interface SchemeDetails { schemeName: string; memberNumber: string; dependentCode: string }

/** A claim can be corrected until a payment, write-off or credit note stands against it. */
export function canCorrect(c: MoneyClaim): boolean {
  return ["DRAFT", "SUBMITTED", "ACCEPTED", "REJECTED"].includes(c.status) && !moneyHasMoved(c)
}

export function detailsOf(c: CorrectableClaim): SchemeDetails {
  return { schemeName: c.schemeName ?? "", memberNumber: c.memberNumber ?? "", dependentCode: c.dependentCode ?? "" }
}

export function detailsChanged(c: CorrectableClaim, d: SchemeDetails): boolean {
  const o = detailsOf(c)
  return o.schemeName.trim() !== d.schemeName.trim() || o.memberNumber.trim() !== d.memberNumber.trim() || o.dependentCode.trim() !== d.dependentCode.trim()
}

const blankToNull = (s: string) => (s.trim() ? s.trim() : null)

/**
 * The request body. Nothing is sent as `corrected` when the scheme details are unchanged: the server copies the claim as it stands.
 * When they changed, the whole claim is sent: new scheme details with the consultation and procedure lines as they are
 * (medicine lines are rebuilt by the server from the prescriptions).
 */
export function correctionBody(c: CorrectableClaim, d: SchemeDetails, reason: string) {
  if (!detailsChanged(c, d)) return { reason: reason.trim(), corrected: null }
  const lines = c.lines ?? []
  const consultation = lines.find(l => l.lineType === "CONSULTATION")
  return {
    reason: reason.trim(),
    corrected: {
      schemeName: blankToNull(d.schemeName),
      memberNumber: blankToNull(d.memberNumber),
      dependentCode: blankToNull(d.dependentCode),
      consultationTariffCode: consultation?.tariffCode ?? null,
      consultationIcd10Code: consultation?.icd10Code ?? null,
      consultationDescription: consultation?.description ?? null,
      consultationRate: consultation?.unitPrice ?? null,
      procedures: lines.filter(l => l.lineType === "PROCEDURE").map(l => ({
        tariffCode: l.tariffCode ?? null, icd10Code: l.icd10Code ?? null, description: l.description, quantity: l.quantity, unitPrice: l.unitPrice,
      })),
    },
  }
}

export function correctionProblem(reason: string): string | null {
  const t = reason.trim()
  if (t.length < REASON_MIN) return `Give a reason of at least ${REASON_MIN} characters`
  if (t.length > REASON_MAX) return `Keep the reason under ${REASON_MAX} characters`
  return null
}

export function notesProblem(notes: string): string | null {
  return notes.trim().length > NOTES_MAX ? `Keep the notes under ${NOTES_MAX} characters` : null
}
