import { describe, it, expect } from "vitest"
import { reportCounts, statusText, statusTone, stepStates } from "./reportsUi.logic"

describe("report status", () => {
  it("words and tones every status, and passes an unknown one through", () => {
    expect(statusText("FACTUAL_REVIEW")).toBe("In factual-accuracy review")
    expect(statusText("SUPERSEDED")).toBe("Replaced by a newer version")
    expect(statusText("ODD")).toBe("ODD")
    expect(statusTone("ISSUED")).toBe("success")
    expect(statusTone("ODD")).toBe("muted")
  })
  it("marks the steps before the current one done and the rest upcoming", () => {
    expect(stepStates("DRAFT")).toEqual(["current", "upcoming", "upcoming", "upcoming"])
    expect(stepStates("READY")).toEqual(["done", "done", "current", "upcoming"])
    expect(stepStates("ISSUED")).toEqual(["done", "done", "done", "current"])
    expect(stepStates("SUPERSEDED")).toEqual(["upcoming", "upcoming", "upcoming", "upcoming"])
  })
  it("counts reports by stage and open comments, leaving out replaced versions' comments", () => {
    const c = reportCounts([
      { status: "DRAFT", openItems: 0 }, { status: "FACTUAL_REVIEW", openItems: 3 }, { status: "READY", openItems: 0 },
      { status: "ISSUED", openItems: 0 }, { status: "SUPERSEDED", openItems: 2 },
    ])
    expect(c).toEqual({ total: 5, inReview: 1, ready: 1, issued: 1, openComments: 3 })
    expect(reportCounts([])).toEqual({ total: 0, inReview: 0, ready: 0, issued: 0, openComments: 0 })
  })
})
