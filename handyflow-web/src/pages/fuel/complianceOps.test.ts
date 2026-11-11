import { describe, it, expect } from "vitest"
import { validateItem, validateRenew, whenLabel, byUrgency, coverageText, activityLabel, validateEvidenceRef, ACTIVITIES, type ComplianceItem } from "./complianceOps"

const item = (o: Partial<ComplianceItem>): ComplianceItem => ({ id: "1", itemType: "LICENCE", title: "t", referenceNo: null, subjectType: "COMPANY", subjectId: null, subjectLabel: null, issuedOn: null, expiresOn: "2027-01-01", notes: null, status: "VALID", daysToExpiry: 100, ...o })

describe("complianceOps", () => {
  it("validates an item", () => {
    expect(validateItem(" ", "COMPANY", "", "2027-01-01", "")).toMatch(/title/)
    expect(validateItem("Roadworthy", "TANKER", "", "2027-01-01", "")).toMatch(/tanker/)
    expect(validateItem("Roadworthy", "TANKER", "t1", "", "")).toMatch(/expiry/)
    expect(validateItem("Roadworthy", "TANKER", "t1", "2026-01-01", "2026-06-01")).toMatch(/before/)
    expect(validateItem("Site licence", "COMPANY", "", "2027-01-01", "")).toBeNull()
  })
  it("renewal must extend", () => {
    expect(validateRenew("2026-12-01", "")).toMatch(/Enter/)
    expect(validateRenew("2026-12-01", "2026-12-01")).toMatch(/later/)
    expect(validateRenew("2026-12-01", "2027-12-01")).toBeNull()
  })
  it("says it in words", () => {
    expect(whenLabel(item({ status: "EXPIRED", daysToExpiry: -1 }))).toBe("expired 1 day ago")
    expect(whenLabel(item({ status: "EXPIRED", daysToExpiry: -12 }))).toBe("expired 12 days ago")
    expect(whenLabel(item({ status: "EXPIRING", daysToExpiry: 0 }))).toBe("expires today")
    expect(whenLabel(item({ status: "EXPIRING", daysToExpiry: 9 }))).toBe("9 days left")
  })
  it("orders by urgency", () => {
    expect([item({ id: "a", daysToExpiry: 50 }), item({ id: "b", daysToExpiry: -3 })].sort(byUrgency)[0].id).toBe("b")
  })
  it("coverage text, activity labels and the evidence reference check", () => {
    expect(coverageText({ valid: 2, expiring: 1, expired: 0 })).toBe("2 valid, 1 expiring, 0 expired")
    expect(activityLabel("WHOLESALE")).toBe("Wholesale")
    expect(ACTIVITIES).toContain("TRANSPORT")
    expect(validateEvidenceRef("  ")).toMatch(/file link/)
    expect(validateEvidenceRef("scans/a.pdf")).toBeNull()
  })
})
