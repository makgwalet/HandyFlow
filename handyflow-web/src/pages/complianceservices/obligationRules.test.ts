import { describe, it, expect } from "vitest"
import {
  describeDueRule, dueRuleProblem, describeCondition, operatorsFor, conditionProblem, conditionsClash,
  missingForReview, approveNeedsReason, approveBlocker, describeSpan,
} from "./obligationRules"

const none = { dueRuleType: "NONE", dueMonth: null, dueDay: null, dueOffsetDays: null, dueOffsetMonths: null }

describe("due rules", () => {
  it("describes each rule in words", () => {
    expect(describeDueRule({ ...none, dueRuleType: "ANNUAL_FIXED", dueMonth: 6, dueDay: 30 })).toBe("Every 30 June")
    expect(describeDueRule({ ...none, dueRuleType: "ANNUAL_AFTER_FYE", dueOffsetMonths: 4 })).toMatch(/4 months after the financial year end/)
    expect(describeDueRule({ ...none, dueRuleType: "BEFORE_EXPIRY", dueOffsetDays: 30 })).toBe("30 days before it expires")
    expect(describeDueRule(none)).toBe("No due date")
  })
  it("says what is missing", () => {
    expect(dueRuleProblem(none)).toBeNull()
    expect(dueRuleProblem({ ...none, dueRuleType: "ANNUAL_FIXED" })).toMatch(/month/)
    expect(dueRuleProblem({ ...none, dueRuleType: "ANNUAL_FIXED", dueMonth: 2, dueDay: 30 })).toMatch(/does not exist/)
    expect(dueRuleProblem({ ...none, dueRuleType: "ANNUAL_FIXED", dueMonth: 2, dueDay: 29 })).toBeNull()
    expect(dueRuleProblem({ ...none, dueRuleType: "MONTHLY_DAY", dueDay: 32 })).toMatch(/1 to 31/)
    expect(dueRuleProblem({ ...none, dueRuleType: "AFTER_EVENT", dueOffsetDays: -1 })).toMatch(/0 to 730/)
    expect(dueRuleProblem({ ...none, dueRuleType: "ANNIVERSARY", dueOffsetMonths: 3 })).toBeNull()
  })
})

describe("conditions", () => {
  it("describes them", () => {
    expect(describeCondition({ field: "ENTITY_TYPE", operator: "IN", value: "PTY_LTD,NPC" })).toBe("Entity type is Private company (Pty) Ltd or Non-profit company")
    expect(describeCondition({ field: "EMPLOYEE_COUNT", operator: "GTE", value: "50" })).toBe("At least 50 employees")
    expect(describeCondition({ field: "VAT_REGISTERED", operator: "IS_FALSE", value: null })).toBe("Is not VAT registered")
  })
  it("offers only the operators a field accepts", () => {
    expect(operatorsFor("EMPLOYEE_COUNT")).toEqual(["GTE", "LTE"])
    expect(operatorsFor("VAT_REGISTERED")).toEqual(["IS_TRUE", "IS_FALSE"])
    expect(operatorsFor("INDUSTRY")).toEqual(["IN"])
  })
  it("checks values", () => {
    expect(conditionProblem({ field: "INDUSTRY", operator: "IN", value: " " })).toMatch(/value/)
    expect(conditionProblem({ field: "EMPLOYEE_COUNT", operator: "GTE", value: "x" })).toMatch(/whole number/)
    expect(conditionProblem({ field: "VAT_REGISTERED", operator: "IS_TRUE", value: null })).toBeNull()
  })
  it("catches conditions that cannot all hold", () => {
    expect(conditionsClash([{ field: "EMPLOYEE_COUNT", operator: "GTE", value: "50" }, { field: "EMPLOYEE_COUNT", operator: "LTE", value: "10" }])).toMatch(/cannot both/)
    expect(conditionsClash([{ field: "EMPLOYEE_COUNT", operator: "GTE", value: "10" }, { field: "EMPLOYEE_COUNT", operator: "LTE", value: "50" }])).toBeNull()
    expect(conditionsClash([{ field: "VAT_REGISTERED", operator: "IS_TRUE", value: null }, { field: "VAT_REGISTERED", operator: "IS_FALSE", value: null }])).toMatch(/VAT/)
    expect(conditionsClash([])).toBeNull()
  })
})

describe("review", () => {
  const ok = { name: "A", legalSource: "Act s1", frequency: "ANNUAL", riskLevel: "HIGH", responsibleRole: "CLIENT", changeSummary: "" }
  it("lists what is missing", () => {
    expect(missingForReview(ok, 1)).toEqual([])
    expect(missingForReview({ ...ok, name: " ", legalSource: "" }, 1)).toEqual(["name", "legal source"])
    expect(missingForReview(ok, 2)).toEqual(["what changed"])
  })
  it("asks the author or sender for a reason to approve", () => {
    const v = { authoredBy: "u1", submittedBy: "u2" }
    expect(approveNeedsReason(v, "u1")).toBe(true)
    expect(approveNeedsReason(v, "u2")).toBe(true)
    expect(approveNeedsReason(v, "u3")).toBe(false)
    expect(approveNeedsReason(v, null)).toBe(false)
  })
  it("blocks approval without a date, or without a reason when needed", () => {
    expect(approveBlocker(false, "", "")).toMatch(/date/)
    expect(approveBlocker(false, "", "2026-12-01")).toBeNull()
    expect(approveBlocker(true, "short", "2026-12-01")).toMatch(/at least 15/)
    expect(approveBlocker(true, "Sole practitioner, no one else", "2026-12-01")).toBeNull()
  })
  it("describes when a version is in force", () => {
    expect(describeSpan(null, null)).toBe("Not yet in force")
    expect(describeSpan("2026-01-01", null)).toBe("In force from 2026-01-01")
    expect(describeSpan("2026-01-01", "2026-06-30")).toBe("In force 2026-01-01 to 2026-06-30")
  })
})
