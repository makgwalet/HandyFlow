import { describe, expect, it } from "vitest"
import { fromLabel, isAsAt, reportHint, toLabel } from "./reports.logic"

describe("report date labels", () => {
  it("treats the balance sheet and trial balance as as-at reports", () => {
    expect(isAsAt("balance-sheet")).toBe(true)
    expect(isAsAt("trial-balance")).toBe(true)
    expect(isAsAt("profit-and-loss")).toBe(false)
  })

  it("keeps From and To for the profit and loss", () => {
    expect(fromLabel("profit-and-loss")).toBe("From")
    expect(toLabel("profit-and-loss")).toBe("To")
    expect(reportHint("profit-and-loss")).toBeNull()
  })

  it("asks for the financial year start and an as-at date on position reports", () => {
    for (const kind of ["balance-sheet", "trial-balance"] as const) {
      expect(fromLabel(kind)).toBe("Financial year starts")
      expect(toLabel(kind)).toBe("As at")
      expect(reportHint(kind)).toContain("Retained Earnings")
    }
  })
})
