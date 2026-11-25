import { describe, it, expect } from "vitest"
import { markProblem, ordered, progress, completeProblem, templateProblem, blankTemplate, templateBody, sampleProblem, templatesFor, resultLabel, statusLabel, type ReviewSummary, type ReviewItem, type Template } from "./qualityRules"

const sum = (o: Partial<ReviewSummary> = {}): ReviewSummary => ({ id: "r", clientId: "c", clientName: "Acme", subjectType: "CASE", subjectLabel: "A case", templateName: "T", round: 1, status: "IN_PROGRESS", sample: false, startedAt: "2026-11-01T00:00:00Z", completedAt: null, checksDone: 0, checksTotal: 2, failed: 0, actions: [], ...o })
const item = (o: Partial<ReviewItem> = {}): ReviewItem => ({ position: 1, text: "x", critical: false, result: "PASS", note: null, ...o })
const NOTE = "Checked every figure against the evidence"

describe("qualityRules", () => {
  it("asks for a reason on a failed or not-applicable check only", () => {
    expect(markProblem("PASS", "")).toBeNull()
    expect(markProblem("FAIL", "short")).not.toBeNull()
    expect(markProblem("NA", "")).not.toBeNull()
    expect(markProblem("FAIL", "The VAT number is wrong")).toBeNull()
  })
  it("puts sent-back work first, then work in progress, then approved", () => {
    const out = ordered([sum({ id: "a", status: "APPROVED" }), sum({ id: "p", status: "IN_PROGRESS" }), sum({ id: "w", status: "REWORK_REQUIRED" })])
    expect(out.map(r => r.id)).toEqual(["w", "p", "a"])
  })
  it("words progress with failures", () => {
    expect(progress({ checksDone: 1, checksTotal: 3, failed: 0 })).toBe("1 of 3 checked")
    expect(progress({ checksDone: 3, checksTotal: 3, failed: 2 })).toBe("3 of 3 checked, 2 failed")
  })
  it("will not approve with unmarked checks or a critical failure", () => {
    expect(completeProblem("APPROVED", [item(), item({ result: "PENDING" })], NOTE, false, "")).toMatch(/Mark every check/)
    expect(completeProblem("APPROVED", [item({ critical: true, result: "FAIL" })], NOTE, false, "")).toMatch(/critical/)
    expect(completeProblem("APPROVED", [item(), item({ result: "FAIL" })], NOTE, false, "")).toBeNull()
  })
  it("needs a failed check to send work back, and a summary either way", () => {
    expect(completeProblem("REWORK_REQUIRED", [item()], NOTE, false, "")).toMatch(/Fail at least one/)
    expect(completeProblem("REWORK_REQUIRED", [item({ result: "FAIL" })], "short", false, "")).toMatch(/summary/)
  })
  it("asks the person who prepared the work for a reason", () => {
    expect(completeProblem("APPROVED", [item()], NOTE, true, "")).toMatch(/prepared this work/)
    expect(completeProblem("APPROVED", [item()], NOTE, true, "No one else is in today")).toBeNull()
  })
  it("checks a checklist form", () => {
    expect(templateProblem(blankTemplate())).toMatch(/name/)
    expect(templateProblem({ ...blankTemplate(), name: "N" })).toMatch(/at least one/)
    const ok = { ...blankTemplate(), name: " N ", items: [{ text: " A ", critical: true }, { text: "  ", critical: false }] }
    expect(templateProblem(ok)).toBeNull()
    expect(templateBody(ok).items).toEqual([{ text: "A", critical: true }])
    expect(templateBody(ok).name).toBe("N")
  })
  it("checks a sample", () => {
    expect(sampleProblem("", "2026-11-30", 10, "t")).not.toBeNull()
    expect(sampleProblem("2026-11-30", "2026-11-01", 10, "t")).toMatch(/before/)
    expect(sampleProblem("2026-11-01", "2026-11-30", 0, "t")).toMatch(/rate/)
    expect(sampleProblem("2026-11-01", "2026-11-30", 10, "")).toMatch(/checklist/)
    expect(sampleProblem("2026-11-01", "2026-11-30", 10, "t")).toBeNull()
  })
  it("offers only switched-on checklists for the kind of work", () => {
    const t = (id: string, subjectType: string, active: boolean): Template => ({ id, name: id, subjectType, description: null, active, items: [] })
    expect(templatesFor([t("a", "CASE", true), t("b", "CASE", false), t("c", "QUOTE", true)], "CASE").map(x => x.id)).toEqual(["a"])
  })
  it("words results and statuses", () => {
    expect(resultLabel("PENDING")).toBe("Not marked")
    expect(resultLabel("NA")).toBe("Not applicable")
    expect(statusLabel("REWORK_REQUIRED")).toBe("Sent back for rework")
  })
})
