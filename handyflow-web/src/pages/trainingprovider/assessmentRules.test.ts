import { describe, it, expect } from "vitest"
import { approvalNote, emptyInstrument, instrumentBody, instrumentProblem, reasonProblem, statusTone, updateBody } from "./assessmentRules"

const ok = { ...emptyInstrument, programmeVersionId: "v1", title: "Safety theory test" }
describe("instrument form", () => {
  it("needs a version, a title and a sensible mark", () => {
    expect(instrumentProblem(emptyInstrument, true)).toContain("version")
    expect(instrumentProblem({ ...ok, title: " " }, true)).toContain("title")
    expect(instrumentProblem({ ...ok, maxMark: "0" }, true)).toContain("maximum mark")
    expect(instrumentProblem({ ...ok, maxMark: "abc" }, true)).toContain("maximum mark")
    expect(instrumentProblem({ ...ok, maxMark: "50" }, true)).toBeNull()
    expect(instrumentProblem({ ...ok, maxMark: "" }, true)).toBeNull()
  })
  it("does not need a version when editing", () => {
    expect(instrumentProblem({ ...ok, programmeVersionId: "" }, false)).toBeNull()
  })
  it("sends blanks as null and a mark as a number", () => {
    expect(instrumentBody({ ...ok, maxMark: "50", guidance: " " })).toMatchObject({ maxMark: 50, guidance: null, moduleId: null, requiresModeration: true })
    expect(instrumentBody(ok).maxMark).toBeNull()
  })
  it("never sends a version on update", () => {
    expect("programmeVersionId" in updateBody(ok)).toBe(false)
  })
})
describe("wording", () => {
  it("needs a reason of ten characters", () => {
    expect(reasonProblem("short")).toContain("10")
    expect(reasonProblem("Replaced by the revised instrument")).toBeNull()
  })
  it("says who approved", () => {
    expect(approvalNote({ status: "DRAFT", approvedAt: null, approvalOverrideReason: null })).toContain("second person should")
    expect(approvalNote({ status: "APPROVED", approvedAt: "2026-11-25T10:00:00Z", approvalOverrideReason: null })).toContain("by a second person")
    expect(approvalNote({ status: "APPROVED", approvedAt: "2026-11-25T10:00:00Z", approvalOverrideReason: "Only person on site" })).toContain("by its author")
  })
  it("tones statuses", () => {
    expect(statusTone("APPROVED")).toBe("good")
    expect(statusTone("DRAFT")).toBe("warn")
    expect(statusTone("RETIRED")).toBe("neutral")
  })
})
