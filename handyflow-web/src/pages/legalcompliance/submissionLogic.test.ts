import { describe, it, expect } from "vitest"
import { canSend, formProblem, replyValid, toggleGrant, grantsChanged, dueText, label } from "./submissionLogic"

const BODY = "The supplier contract has no data-processing clause."

describe("submissionLogic", () => {
  it("can send only when access is active and SUBMIT was granted", () => {
    expect(canSend("ACTIVE", ["SUMMARY", "SUBMIT"])).toBe(true)
    expect(canSend("ACTIVE", ["SUMMARY"])).toBe(false)
    expect(canSend("SUSPENDED", ["SUBMIT"])).toBe(false)
    expect(canSend("ENDED", ["SUBMIT"])).toBe(false)
  })
  it("form problems mirror the server limits", () => {
    expect(formProblem({ title: "abcd", body: BODY, respondBy: "" }, "2026-11-20")).toContain("title")
    expect(formProblem({ title: "A title", body: "too short", respondBy: "" }, "2026-11-20")).toContain("Describe")
    expect(formProblem({ title: "A title", body: "x".repeat(4001), respondBy: "" }, "2026-11-20")).toContain("too long")
    expect(formProblem({ title: "A title", body: BODY, respondBy: "2026-11-20" }, "2026-11-20")).toContain("after today")
    expect(formProblem({ title: "A title", body: BODY, respondBy: "2026-11-21" }, "2026-11-20")).toBeNull()
    expect(formProblem({ title: "  A title ", body: BODY, respondBy: "" }, "2026-11-20")).toBeNull()
  })
  it("accepting needs no note; declining and completing need ten characters", () => {
    expect(replyValid("accept", "")).toBe(true)
    expect(replyValid("decline", "no")).toBe(false)
    expect(replyValid("decline", "          ")).toBe(false)
    expect(replyValid("complete", "Done and filed away")).toBe(true)
  })
  it("toggles grants without duplicates", () => {
    expect(toggleGrant(["SUMMARY"], "SUBMIT", true)).toEqual(["SUMMARY", "SUBMIT"])
    expect(toggleGrant(["SUMMARY", "SUBMIT"], "SUBMIT", true)).toEqual(["SUMMARY", "SUBMIT"])
    expect(toggleGrant(["SUMMARY", "SUBMIT"], "SUBMIT", false)).toEqual(["SUMMARY"])
    expect(grantsChanged(["SUMMARY", "SUBMIT"], ["SUBMIT", "SUMMARY"])).toBe(false)
    expect(grantsChanged(["SUMMARY"], ["SUMMARY", "SUBMIT"])).toBe(true)
  })
  it("due text and labels", () => {
    expect(dueText(null, false)).toBeNull()
    expect(dueText("2026-12-01", false)).toBe("Reply wanted by 2026-12-01")
    expect(dueText("2026-11-01", true)).toBe("Reply was wanted by 2026-11-01")
    expect(label("INFORMATION_REQUEST")).toBe("Information request")
  })
})
