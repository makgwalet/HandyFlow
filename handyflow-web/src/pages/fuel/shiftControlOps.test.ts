import { describe, it, expect } from "vitest"
import { asControls, onlyAcceptLog, amountProblem, repeatProblem, overrideText, repeatSummary, acceptLogLine, NO_CONTROLS } from "./shiftControlOps"

describe("shiftControlOps", () => {
  it("accepts only a real controls answer", () => {
    expect(asControls(null)).toBe(NO_CONTROLS)
    expect(asControls({ id: "x" })).toBe(NO_CONTROLS)
    const c = asControls({ overrides: [{ scope: "DEPOT", key: "d1", label: "Main", amount: 20 }, { nope: 1 }], policy: { repeatCount: 3, repeatDays: 7 } })
    expect(c.overrides).toHaveLength(1); expect(c.policy.repeatCount).toBe(3)
  })
  it("keeps only rows shaped like log entries", () => {
    expect(onlyAcceptLog([{ id: "x" }, { id: "l1", shiftNumber: "SH-1", attendantName: "Thabo" }])).toHaveLength(1)
    expect(onlyAcceptLog({})).toEqual([])
  })
  it("checks amounts and the repeat rule", () => {
    expect(amountProblem("")).toBeTruthy(); expect(amountProblem("-1")).toBeTruthy(); expect(amountProblem("abc")).toBeTruthy()
    expect(amountProblem("0")).toBeNull(); expect(amountProblem("12,50")).toBeNull()
    expect(repeatProblem("1", "7")).toBeTruthy(); expect(repeatProblem("3", "0")).toBeTruthy(); expect(repeatProblem("3", "400")).toBeTruthy(); expect(repeatProblem("3", "7")).toBeNull()
  })
  it("describes overrides, the rule and a log line in plain words", () => {
    expect(overrideText({ scope: "DEPOT", key: "d1", label: "Main depot", amount: 20 })).toBe("Main depot: cash variance tolerance R 20.00")
    expect(overrideText({ scope: "PRODUCT", key: "DIESEL", label: "DIESEL", amount: 500 })).toContain("gauge difference up to")
    expect(repeatSummary({ repeatCount: null, repeatDays: null })).toContain("Off")
    expect(repeatSummary({ repeatCount: 3, repeatDays: 7 })).toBe("Flagged when one attendant closes 3 shifts outside tolerance within 7 days")
    expect(acceptLogLine({ id: "l", shiftId: "s", shiftNumber: "SH-1", attendantName: "Thabo", varianceAmount: -2, toleranceApplied: 5, actor: "SYSTEM", acceptedAt: "2026-11-17T10:30:00Z" }))
      .toBe("SH-1 · Thabo · variance R -2.00 against a tolerance of R 5.00 · accepted by the system 2026-11-17 10:30")
  })
})
