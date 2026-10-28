import { describe, it, expect } from "vitest"
import { beneficiaryText, careerBody, careerProblem, careerSummary, emptyCareer, emptyRelative, fromRelative, relativeBody, relativeProblem, sharesTotal, tenureText, type CareerView, type RelativeView } from "./profile.logic"

const NOW = "2026-10-28"
const rel = (o: Partial<RelativeView>): RelativeView => ({
  id: "r1", employeeId: "e1", kind: "DEPENDANT", fullName: "Sipho", relationship: "CHILD", relationshipLabel: "Child", phone: null, email: null,
  dateOfBirth: "2020-01-01", age: 6, address: null, beneficiary: false, beneficiaryPercent: null, notes: null, updatedAt: "", ...o,
})
const ev = (o: Partial<CareerView>): CareerView => ({
  id: "c1", kind: "HIRED", kindLabel: "", eventDate: "2024-03-01", endDate: null, employer: null, jobTitle: null, department: null, fromValue: null, toValue: null,
  percentChange: null, note: null, payRelated: false, auto: true, createdAt: "", ...o,
})

describe("relativeProblem", () => {
  const kin = { ...emptyRelative("NEXT_OF_KIN"), fullName: "Thandi", relationship: "SPOUSE", phone: "0825550101" }
  it("passes a complete next of kin", () => expect(relativeProblem(kin, [], null, NOW)).toBeNull())
  it("needs name, relation and phone", () => {
    expect(relativeProblem({ ...kin, fullName: " " }, [], null, NOW)).toMatch(/full name/)
    expect(relativeProblem({ ...kin, relationship: "" }, [], null, NOW)).toMatch(/related/)
    expect(relativeProblem({ ...kin, phone: "" }, [], null, NOW)).toMatch(/phone/)
  })
  it("needs a dependant's date of birth, not in the future", () => {
    const d = { ...emptyRelative("DEPENDANT"), fullName: "Sipho" }
    expect(relativeProblem(d, [], null, NOW)).toMatch(/date of birth/)
    expect(relativeProblem({ ...d, dateOfBirth: "2026-10-29" }, [], null, NOW)).toMatch(/future/)
    expect(relativeProblem({ ...d, dateOfBirth: NOW }, [], null, NOW)).toBeNull()
  })
  it("keeps beneficiary shares within 100", () => {
    const d = { ...emptyRelative("DEPENDANT"), fullName: "B", dateOfBirth: "2020-01-01", beneficiary: true, beneficiaryPercent: "41" }
    const others = [rel({ id: "a", beneficiary: true, beneficiaryPercent: 60 })]
    expect(relativeProblem(d, others, null, NOW)).toMatch(/101%/)
    expect(relativeProblem({ ...d, beneficiaryPercent: "40" }, others, null, NOW)).toBeNull()
    expect(relativeProblem({ ...d, beneficiaryPercent: "100" }, others, "a", NOW)).toBeNull()
    expect(relativeProblem({ ...d, beneficiaryPercent: "101" }, [], null, NOW)).toMatch(/between 0 and 100/)
  })
  it("adds shares up leaving the edited line out", () => {
    const list = [rel({ id: "a", beneficiary: true, beneficiaryPercent: 60 }), rel({ id: "b", beneficiary: true, beneficiaryPercent: 30 }), rel({ id: "c" })]
    expect(sharesTotal(list)).toBe(90)
    expect(sharesTotal(list, "a")).toBe(30)
  })
})

describe("relative body", () => {
  it("trims, nulls blanks and drops a share for a non-beneficiary", () => {
    const b = relativeBody({ ...emptyRelative("NEXT_OF_KIN"), fullName: " Thandi ", relationship: "SPOUSE", phone: " 082 ", beneficiary: false, beneficiaryPercent: "50" })
    expect(b.fullName).toBe("Thandi"); expect(b.phone).toBe("082"); expect(b.email).toBeNull(); expect(b.beneficiaryPercent).toBeNull()
  })
  it("round trips", () => {
    const r = rel({ beneficiary: true, beneficiaryPercent: 25 })
    expect(relativeBody(fromRelative(r)).beneficiaryPercent).toBe(25)
    expect(beneficiaryText(r)).toBe("Beneficiary, 25%")
    expect(beneficiaryText(rel({ beneficiary: true }))).toBe("Beneficiary")
    expect(beneficiaryText(rel({}))).toBe("")
  })
})

describe("careerProblem", () => {
  const prior = { ...emptyCareer(), eventDate: "2018-01-01", endDate: "2023-12-31", employer: "Acme", jobTitle: "Clerk" }
  it("checks an earlier job", () => {
    expect(careerProblem(prior, "2024-03-01", NOW)).toBeNull()
    expect(careerProblem({ ...prior, employer: "" }, "2024-03-01", NOW)).toMatch(/employer/)
    expect(careerProblem({ ...prior, endDate: "" }, "2024-03-01", NOW)).toMatch(/left/)
    expect(careerProblem({ ...prior, endDate: "2017-01-01" }, "2024-03-01", NOW)).toMatch(/before starting/)
    expect(careerProblem({ ...prior, endDate: "2024-03-02" }, "2024-03-01", NOW)).toMatch(/joined this company/)
  })
  it("checks promotions, transfers and notes", () => {
    const base = { ...emptyCareer(), eventDate: "2025-06-01" }
    expect(careerProblem({ ...base, kind: "PROMOTION" }, "2024-03-01", NOW)).toMatch(/promoted to/)
    expect(careerProblem({ ...base, kind: "PROMOTION", jobTitle: "Manager" }, "2024-03-01", NOW)).toBeNull()
    expect(careerProblem({ ...base, kind: "TRANSFER" }, "2024-03-01", NOW)).toMatch(/department/)
    expect(careerProblem({ ...base, kind: "OTHER" }, "2024-03-01", NOW)).toMatch(/what happened/)
    expect(careerProblem({ ...base, kind: "PROMOTION", jobTitle: "M", eventDate: "2024-02-29" }, "2024-03-01", NOW)).toMatch(/before the person joined/)
    expect(careerProblem({ ...base, kind: "PROMOTION", jobTitle: "M", eventDate: "2027-10-29" }, "2024-03-01", NOW)).toMatch(/year ahead/)
  })
  it("sends only what the kind uses", () => {
    expect(careerBody({ ...prior, kind: "PROMOTION", jobTitle: "Manager" }).employer).toBeNull()
    expect(careerBody({ ...prior, kind: "PROMOTION", jobTitle: "Manager" }).endDate).toBeNull()
    expect(careerBody(prior).employer).toBe("Acme")
  })
})

describe("careerSummary", () => {
  it("words each kind", () => {
    expect(careerSummary(ev({ kind: "SALARY_CHANGED", fromValue: "30000", toValue: "32500", percentChange: 8.3 })).replace(/\s/g, " ")).toBe("R30 000 → R32 500 (+8.3%)")
    expect(careerSummary(ev({ kind: "SALARY_CHANGED", fromValue: "40000", toValue: "38000", percentChange: -5 }))).toContain("(-5%)")
    expect(careerSummary(ev({ kind: "TITLE_CHANGED", fromValue: "Clerk", toValue: "Senior Clerk" }))).toBe("Clerk → Senior Clerk")
    expect(careerSummary(ev({ kind: "MANAGER_CHANGED", fromValue: null, toValue: "Sam" }))).toBe("— → Sam")
    expect(careerSummary(ev({ kind: "EMPLOYMENT_TYPE_CHANGED", fromValue: "FIXED_TERM", toValue: "PERMANENT" }))).toBe("Fixed term → Permanent")
    expect(careerSummary(ev({ kind: "HIRED", jobTitle: "Clerk", department: "Finance" }))).toBe("Clerk, Finance")
    expect(careerSummary(ev({ kind: "PRIOR_EMPLOYMENT", jobTitle: "Clerk", employer: "Acme" }))).toBe("Clerk at Acme")
    expect(careerSummary(ev({ kind: "TERMINATED" }))).toBe("")
  })
})

describe("tenureText", () => {
  it("counts whole years and months", () => {
    expect(tenureText("2024-03-01", "2026-10-28")).toBe("2 years 7 months")
    expect(tenureText("2025-10-28", "2026-10-28")).toBe("1 year")
    expect(tenureText("2026-10-01", "2026-10-28")).toBe("less than a month")
    expect(tenureText("2026-11-01", "2026-10-28")).toBe("")
  })
})
