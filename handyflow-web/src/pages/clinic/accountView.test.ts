import { describe, it, expect } from "vitest"
import { claimStatus, isOwed, methodLabel } from "./accountView"

describe("account wording", () => {
  it("names each claim state in plain words and shows an unknown one as words", () => {
    expect(claimStatus("NOT_BILLED").label).toBe("Not billed yet")
    expect(claimStatus("PARTIAL").label).toBe("Scheme part-paid")
    expect(claimStatus("ON_HOLD").label).toBe("on hold")
  })
  it("does not count rejected, voided or unbilled visits as owed", () => {
    expect(isOwed("ACCEPTED")).toBe(true); expect(isOwed("DRAFT")).toBe(true)
    expect(isOwed("REJECTED")).toBe(false); expect(isOwed("VOIDED")).toBe(false); expect(isOwed("NOT_BILLED")).toBe(false)
  })
  it("labels payment methods", () => { expect(methodLabel("EFT")).toBe("EFT"); expect(methodLabel("CASH")).toBe("Cash"); expect(methodLabel("VOUCHER")).toBe("VOUCHER") })
})

import { onAccountNote, owedOnVisit, payTowardsLabel, payableVisits, type Account, type AccountVisit } from "./accountView"
describe("what each visit still owes (0190)", () => {
  const v = (o: Partial<AccountVisit> = {}): AccountVisit => ({ consultationId: "c", claimId: "k", visitDate: "2026-09-01T08:00:00Z", claimStatus: "SUBMITTED", patientPortion: 100, paidAgainstVisit: 0, ...o })
  it("is the patient's share less what has been linked, never negative", () => {
    expect(owedOnVisit(v())).toBe(100)
    expect(owedOnVisit(v({ paidAgainstVisit: 40 }))).toBe(60)
    expect(owedOnVisit(v({ paidAgainstVisit: 150 }))).toBe(0)
    expect(owedOnVisit(v({ patientPortion: 10.1, paidAgainstVisit: 0.2 }))).toBe(9.9)
  })
  it("is nothing for an unbilled, rejected or voided visit", () => {
    expect(owedOnVisit(v({ claimId: null, claimStatus: "NOT_BILLED" }))).toBe(0)
    expect(owedOnVisit(v({ claimStatus: "REJECTED" }))).toBe(0)
    expect(owedOnVisit(v({ claimStatus: "VOIDED" }))).toBe(0)
  })
  it("lists only visits that owe, oldest first", () => {
    const acc = { visits: [v({ claimId: "b", visitDate: "2026-09-05T08:00:00Z" }), v({ claimId: "paid", paidAgainstVisit: 100 }), v({ claimId: "a", visitDate: "2026-09-02T08:00:00Z" })] } as Account
    expect(payableVisits(acc).map(x => x.claimId)).toEqual(["a", "b"])
  })
  it("labels a visit with its date, reason and what it owes", () => {
    expect(payTowardsLabel(v({ chiefComplaint: "Cough", paidAgainstVisit: 20 }), d => d.slice(0, 10), n => `R${n}`)).toBe("2026-09-01 · Cough · owes R80")
  })
  it("notes money that no visit carries", () => {
    expect(onAccountNote({ paidOnAccount: 0 } as Account)).toBe("")
    expect(onAccountNote({} as Account)).toBe("")
    expect(onAccountNote({ paidOnAccount: 150 } as Account)).toContain("not linked to a visit")
  })
})
