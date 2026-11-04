import { describe, it, expect } from "vitest"
import { actionsFor, brokerPolicyProblem, brokerPolicyRequest, clientProblem, daysToExpiry, emptyBrokerPolicy, emptyClient, emptyPolicy, expiryNote, paymentProblem, policyProblem, policyRequest, renewalDefaults, termProblem } from "./insurance.logic"

const at = (s: string) => new Date(s + "T09:30:00")

describe("what a policy may do", () => {
  it("walks a brokerage policy from quote to cover", () => {
    expect(actionsFor("QUOTE", true)).toEqual(["edit", "bind", "cancel"])
    expect(actionsFor("BOUND", true)).toContain("activate")
    expect(actionsFor("ACTIVE", true)).toEqual(["edit", "lapse", "renew", "cancel"])
    expect(actionsFor("LAPSED", true)).toEqual(["edit", "reinstate", "renew", "cancel"])
  })
  it("offers nothing on finished policies, and no quote steps internally", () => {
    for (const s of ["CANCELLED", "EXPIRED", "RENEWED"]) expect(actionsFor(s, true)).toEqual([])
    expect(actionsFor("QUOTE", false)).toEqual([])
  })
})

describe("running out", () => {
  it("counts whole days, ignoring the time of day", () => {
    expect(daysToExpiry("2026-12-04", at("2026-11-04"))).toBe(30)
    expect(daysToExpiry("2026-11-04", at("2026-11-04"))).toBe(0)
    expect(daysToExpiry("2026-11-01", at("2026-11-04"))).toBe(-3)
    expect(daysToExpiry(null)).toBeNull()
  })
  it("only warns about cover that is in force and close to the end", () => {
    expect(expiryNote("ACTIVE", "2026-12-04", at("2026-11-04"))).toEqual({ text: "Runs out in 30 days", tone: "warning" })
    expect(expiryNote("ACTIVE", "2026-11-10", at("2026-11-04"))?.tone).toBe("danger")
    expect(expiryNote("ACTIVE", "2026-11-05", at("2026-11-04"))?.text).toBe("Runs out in 1 day")
    expect(expiryNote("ACTIVE", "2026-11-04", at("2026-11-04"))?.text).toBe("Runs out today")
    expect(expiryNote("LAPSED", "2026-10-30", at("2026-11-04"))?.text).toBe("Ran out 5 days ago")
    expect(expiryNote("ACTIVE", "2027-06-01", at("2026-11-04"))).toBeNull()
    expect(expiryNote("CANCELLED", "2026-11-05", at("2026-11-04"))).toBeNull()
  })
})

describe("renewal", () => {
  it("starts the day after the old term and runs a year", () => {
    expect(renewalDefaults("2026-12-31", 500000, 1200)).toMatchObject({ startDate: "2027-01-01", expiryDate: "2027-12-31", sumInsured: "500000", premiumAmount: "1200", policyNumber: "" })
    expect(renewalDefaults("2027-02-28", null, null)).toMatchObject({ startDate: "2027-03-01", expiryDate: "2028-02-29", sumInsured: "" })
  })
  it("needs a number, a premium and dates in order", () => {
    const ok = { policyNumber: "P-2", premiumAmount: "0", sumInsured: "", startDate: "2027-01-01", expiryDate: "2027-12-31" }
    expect(termProblem(ok)).toBeNull()
    expect(termProblem({ ...ok, policyNumber: " " })).toMatch(/policy number/)
    expect(termProblem({ ...ok, premiumAmount: "" })).toMatch(/premium/)
    expect(termProblem({ ...ok, sumInsured: "-1" })).toMatch(/sum insured/)
    expect(termProblem({ ...ok, expiryDate: "2027-01-01" })).toMatch(/after the start/)
  })
})

describe("policy forms", () => {
  const ok = { ...emptyPolicy(), policyNumber: "P1", insurerName: "Santam", premiumAmount: "950", startDate: "2026-11-01", expiryDate: "2027-10-31" }
  it("checks the internal policy and trims what it sends", () => {
    expect(policyProblem(ok, true)).toBeNull()
    expect(policyProblem({ ...ok, policyNumber: "" }, true)).toMatch(/policy number/)
    expect(policyProblem({ ...ok, policyNumber: "" }, false)).toBeNull()
    expect(policyProblem({ ...ok, insurerName: " " }, true)).toMatch(/insurer/)
    expect(policyProblem({ ...ok, excessAmount: "-5" }, true)).toMatch(/excess/)
    expect(policyRequest({ ...ok, insurerName: " Santam ", assetReference: " " }, true)).toMatchObject({ insurerName: "Santam", assetReference: null, sumInsured: null, premiumAmount: 950, lineOfBusiness: "MOTOR" })
    expect(policyRequest(ok, false)).not.toHaveProperty("policyNumber")
  })
  it("lets a brokerage quote leave dates and money open", () => {
    const q = { ...emptyBrokerPolicy(), clientId: "c", insurerId: "i" }
    expect(brokerPolicyProblem(q, true)).toBeNull()
    expect(brokerPolicyRequest(q, true)).toMatchObject({ clientId: "c", insurerId: "i", premiumAmount: null, startDate: null })
    expect(brokerPolicyProblem({ ...q, clientId: "" }, true)).toMatch(/client/)
    expect(brokerPolicyProblem({ ...q, clientId: "" }, false)).toBeNull()
    expect(brokerPolicyProblem({ ...q, insurerId: "" }, false)).toMatch(/insurer/)
    expect(brokerPolicyProblem({ ...q, commissionRatePct: "101" }, true)).toMatch(/0 and 100/)
    expect(brokerPolicyProblem({ ...q, startDate: "2027-01-02", expiryDate: "2027-01-01" }, true)).toMatch(/after the start/)
    expect(brokerPolicyRequest(q, false)).not.toHaveProperty("clientId")
  })
})

describe("clients and payments", () => {
  it("needs a name and a sane email and commission", () => {
    expect(clientProblem({ ...emptyClient(), clientName: "Acme" })).toBeNull()
    expect(clientProblem(emptyClient())).toMatch(/name/)
    expect(clientProblem({ ...emptyClient(), clientName: "A", contactEmail: "nope" })).toMatch(/email/)
    expect(clientProblem({ ...emptyClient(), clientName: "A", defaultCommissionRatePct: "120" })).toMatch(/100/)
  })
  it("refuses a payment that is empty, zero or more than is owing", () => {
    expect(paymentProblem(100, "")).toMatch(/above 0/)
    expect(paymentProblem(100, "0")).toMatch(/above 0/)
    expect(paymentProblem(100, "100.01")).toMatch(/more than/)
    expect(paymentProblem(100, "100")).toBeNull()
    expect(paymentProblem(100, "40.5")).toBeNull()
  })
})
