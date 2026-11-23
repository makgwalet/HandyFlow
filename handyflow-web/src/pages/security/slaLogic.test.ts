import { describe, it, expect } from "vitest"
import {
  defaultRange, failureText, liveLine, minutesText, parseMinutes, percentText, rangeError, stateLabel, stateTone, targetError, targetLabel, SAVE_FAILED, MAX_MINUTES,
} from "./slaLogic"

describe("minutes", () => {
  it("reads naturally", () => {
    expect(minutesText(5)).toBe("5 min"); expect(minutesText(0)).toBe("0 min"); expect(minutesText(59)).toBe("59 min")
    expect(minutesText(60)).toBe("1 h"); expect(minutesText(90)).toBe("1 h 30 min"); expect(minutesText(120)).toBe("2 h")
    expect(minutesText(1500)).toBe("1 d 1 h"); expect(minutesText(2880)).toBe("2 d"); expect(minutesText(MAX_MINUTES)).toBe("30 d")
  })
  it("shows a dash when there is nothing", () => {
    expect(minutesText(null)).toBe("—"); expect(minutesText(undefined)).toBe("—"); expect(minutesText(NaN)).toBe("—")
  })
  it("parses whole minutes from 1 to 30 days only", () => {
    expect(parseMinutes(" 45 ")).toBe(45); expect(parseMinutes("43200")).toBe(43200)
    for (const bad of ["", "0", "-5", "1.5", "abc", "43201", "1e3"]) expect(parseMinutes(bad), bad).toBeNull()
  })
})

describe("target form", () => {
  it("accepts equal or longer resolve times", () => {
    expect(targetError("10", "120")).toBeNull(); expect(targetError("30", "30")).toBeNull()
  })
  it("explains each way it can be wrong", () => {
    expect(targetError("", "30")).toContain("whole minutes")
    expect(targetError("10", "0")).toContain("whole minutes")
    expect(targetError("60", "30")).toContain("cannot be shorter")
  })
  it("describes a target in words", () => {
    expect(targetLabel({ siteId: null, siteName: null, severity: "HIGH", acknowledgeMinutes: 10, resolveMinutes: 120 })).toBe("HIGH: acknowledge in 10 min, resolve in 2 h")
  })
})

describe("states", () => {
  it("words and tones", () => {
    expect(stateLabel("MET")).toBe("Met"); expect(stateLabel("BREACHED")).toBe("Breached"); expect(stateLabel("AT_RISK")).toBe("At risk")
    expect(stateLabel("PENDING")).toBe("On track"); expect(stateLabel("UNTRACKED")).toBe("No target")
    expect(stateTone("BREACHED")).toBe("bad"); expect(stateTone("AT_RISK")).toBe("warn"); expect(stateTone("MET")).toBe("good"); expect(stateTone("UNTRACKED")).toBe("neutral")
  })
  it("says what is overdue or left for each stage", () => {
    expect(liveLine({ state: "BREACHED", stage: "ACKNOWLEDGE", overdueMinutes: 20, remainingMinutes: null })).toBe("20 min overdue to acknowledge")
    expect(liveLine({ state: "BREACHED", stage: "RESOLVE", overdueMinutes: 90, remainingMinutes: null })).toBe("1 h 30 min overdue to resolve")
    expect(liveLine({ state: "AT_RISK", stage: "RESOLVE", overdueMinutes: null, remainingMinutes: 10 })).toBe("10 min left to resolve")
  })
})

describe("report", () => {
  it("shows a percentage or a dash", () => {
    expect(percentText(75)).toBe("75%"); expect(percentText(33.3)).toBe("33.3%"); expect(percentText(100)).toBe("100%"); expect(percentText(null)).toBe("—")
  })
  it("starts with the last 30 days including today", () => {
    expect(defaultRange(new Date(2026, 10, 23))).toEqual({ from: "2026-10-25", to: "2026-11-23" })
    expect(defaultRange(new Date(2026, 0, 5))).toEqual({ from: "2025-12-07", to: "2026-01-05" })
  })
  it("checks the range", () => {
    expect(rangeError("2026-11-01", "2026-11-30")).toBeNull()
    expect(rangeError("", "2026-11-30")).toContain("Choose")
    expect(rangeError("2026-11-30", "2026-11-01")).toContain("before")
    expect(rangeError("2026-01-01", "2027-01-02")).toContain("366")
    expect(rangeError("2026-01-01", "2027-01-01")).toBeNull() // 366 days inclusive
    expect(rangeError("2026-01-01", "2026-12-31")).toBeNull()
  })
})

describe("failures", () => {
  it("uses the server's words or a fallback", () => {
    expect(failureText({ response: { data: { message: "Only the whole company can do this." } } }, SAVE_FAILED)).toContain("whole company")
    expect(failureText(new Error("x"), SAVE_FAILED)).toBe(SAVE_FAILED)
    expect(failureText({ response: { data: { message: " " } } }, SAVE_FAILED)).toBe(SAVE_FAILED)
  })
})
