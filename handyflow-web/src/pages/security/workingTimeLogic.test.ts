import { describe, it, expect } from "vitest"
import { categoriesOf, ceilingOf, dateText, draftError, emptyDraft, errorText, exceedsGeneral, firstFullWeek, normaliseCategory, num, statusOf, summaryOf, toRequest, validCategory, validPolicies, type Draft, type Policy } from "./workingTimeLogic"

const TODAY = "2026-11-22"
const d = (o: Partial<Draft> = {}): Draft => ({ ...emptyDraft(TODAY), ...o })
const pol = (o: Partial<Policy> = {}): Policy => ({ id: "x", category: "DEFAULT", effectiveFrom: "2026-01-01", warnAt: 40, ordinaryWeekly: 45, maxOvertimeWeekly: 10, maxDaily: 12, maxDailyOvertime: 3, minRestHours: 12, legalBasis: null, ...o })

describe("workingTimeLogic", () => {
  it("starts from the general figures with no legal basis needed", () => {
    expect(draftError(d(), TODAY)).toBeNull()
    expect(exceedsGeneral(d())).toBe(false)
    expect(ceilingOf(d())).toBe(55)
  })
  it("normalises and checks the category", () => {
    expect(normaliseCategory(" night shift ")).toBe("NIGHT_SHIFT")
    for (const ok of ["default", "Night Shift", "a1_b"]) expect(validCategory(ok), ok).toBe(true)
    for (const bad of ["", "  ", "a-b", "x".repeat(41), "naïve"]) expect(validCategory(bad), bad).toBe(false)
    expect(draftError(d({ category: "a-b" }), TODAY)).toContain("Category")
  })
  it("reads plain decimal numbers only", () => {
    expect(num("45")).toBe(45)
    expect(num(" 7.5 ")).toBe(7.5)
    for (const bad of ["", "abc", "-1", "1e3", "1,5", "4.555", "1234", "Infinity"]) expect(num(bad), bad).toBeNull()
  })
  it("will not start in the past or without a date", () => {
    expect(draftError(d({ effectiveFrom: "2026-11-21" }), TODAY)).toBe("A policy cannot start in the past.")
    expect(draftError(d({ effectiveFrom: "" }), TODAY)).toContain("date")
    expect(draftError(d({ effectiveFrom: TODAY }), TODAY)).toBeNull()
  })
  it("refuses impossible hours", () => {
    expect(draftError(d({ warnAt: "50" }), TODAY)).toContain("warning level")
    expect(draftError(d({ ordinaryWeekly: "0" }), TODAY)).toContain("Ordinary")
    expect(draftError(d({ ordinaryWeekly: "90", legalBasis: "x".repeat(20) }), TODAY)).toContain("at most 84")
    expect(draftError(d({ maxOvertimeWeekly: "41", legalBasis: "x".repeat(20) }), TODAY)).toContain("overtime")
    expect(draftError(d({ maxDaily: "25", legalBasis: "x".repeat(20) }), TODAY)).toContain("Daily hours")
    expect(draftError(d({ maxDailyOvertime: "13", maxDaily: "12", legalBasis: "x".repeat(20) }), TODAY)).toContain("Daily overtime")
    expect(draftError(d({ minRestHours: "25" }), TODAY)).toContain("Rest")
    expect(draftError(d({ minRestHours: "" }), TODAY)).toContain("every number")
  })
  it("needs a legal basis for each figure beyond the general ones", () => {
    for (const o of [{ ordinaryWeekly: "48" }, { maxOvertimeWeekly: "15" }, { maxDaily: "13" }, { maxDailyOvertime: "4" }, { minRestHours: "10" }] as Partial<Draft>[]) {
      expect(exceedsGeneral(d(o)), JSON.stringify(o)).toBe(true)
      expect(draftError(d(o), TODAY), JSON.stringify(o)).toContain("legal basis")
      expect(draftError(d({ ...o, legalBasis: "short" }), TODAY)).toContain("legal basis")
      expect(draftError(d({ ...o, legalBasis: "Collective agreement 7, clause 4" }), TODAY)).toBeNull()
    }
    expect(draftError(d({ ordinaryWeekly: "40", warnAt: "35", maxOvertimeWeekly: "5", maxDaily: "10", maxDailyOvertime: "2", minRestHours: "14" }), TODAY)).toBeNull()
    expect(draftError(d({ legalBasis: "x".repeat(501) }), TODAY)).toContain("too long")
  })
  it("sends trimmed, normalised values and numbers", () => {
    expect(toRequest(d({ category: " night ", legalBasis: "  Agreement  " }))).toEqual({
      category: "NIGHT", effectiveFrom: TODAY, warnAt: 40, ordinaryWeekly: 45, maxOvertimeWeekly: 10, maxDaily: 12, maxDailyOvertime: 3, minRestHours: 12, legalBasis: "Agreement" })
    expect(toRequest(d()).legalBasis).toBeNull()
  })
  it("accepts only a well-formed policy list", () => {
    expect(validPolicies([pol()])).toHaveLength(1)
    expect(validPolicies([])).toEqual([])
    for (const bad of [null, undefined, {}, "x", [null], [{ category: "A" }], [{ ...pol(), ordinaryWeekly: "45" }]]) expect(validPolicies(bad)).toBeNull()
  })
  it("lists Default first, then the others alphabetically, once each", () => {
    expect(categoriesOf([])).toEqual(["DEFAULT"])
    expect(categoriesOf([pol({ category: "NIGHT" }), pol({ category: "ARMED" }), pol({ category: "NIGHT" })])).toEqual(["DEFAULT", "ARMED", "NIGHT"])
  })
  it("says which policy is in force, replaced or starting later", () => {
    const all = [pol({ effectiveFrom: "2026-01-01" }), pol({ effectiveFrom: "2026-06-01" }), pol({ effectiveFrom: "2026-12-01" }), pol({ category: "NIGHT", effectiveFrom: "2026-03-01" })]
    expect(statusOf(all[0], all, TODAY)).toBe("Replaced")
    expect(statusOf(all[1], all, TODAY)).toBe("In force")
    expect(statusOf(all[2], all, TODAY)).toBe("Starts later")
    expect(statusOf(all[3], all, TODAY)).toBe("In force")
  })
  it("summarises a policy with the derived ceiling", () => {
    expect(summaryOf(pol())).toBe("45 h ordinary + 10 h overtime (ceiling 55 h), 12 h a day, 12 h rest")
    expect(summaryOf(pol({ ordinaryWeekly: 48, maxOvertimeWeekly: 12.5 }))).toContain("ceiling 60.5 h")
  })
  it("shows when a mid-week start first applies", () => {
    expect(firstFullWeek("2026-11-23")).toBe("2026-11-23") // Monday
    expect(firstFullWeek("2026-11-25")).toBe("2026-11-30") // Wednesday
    expect(firstFullWeek("2026-11-29")).toBe("2026-11-30") // Sunday
    expect(dateText("2026-12-01")).toBe("1 Dec 2026")
  })
  it("shows the server's reason, or a fallback", () => {
    expect(errorText({ response: { data: { message: "This category already has a policy" } } }, "fb")).toContain("already")
    for (const bad of [{}, null, { response: { data: { message: "" } } }, { response: { data: { message: 5 } } }, { response: { data: { message: "x".repeat(300) } } }]) expect(errorText(bad, "fb")).toBe("fb")
  })
})
