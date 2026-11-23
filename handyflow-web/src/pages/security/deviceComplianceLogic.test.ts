import { describe, it, expect } from "vitest"
import {
  formProblem, formToRequest, isChanged, missingText, orderItems, policyToForm, summaryText, violationLabel, missingLabel,
  type ComplianceReport, type PolicyView,
} from "./deviceComplianceLogic"

const policy = (o: Partial<PolicyView> = {}): PolicyView => ({ minAppVersion: null, minOsMajor: null, requireScreenLock: false, blockRooted: false, asksAnything: false, updatedAt: null, ...o })
const report = (o: Partial<ComplianceReport> = {}): ComplianceReport => ({ generatedAt: "2026-11-23T08:00:00Z", policy: policy({ asksAnything: true, requireScreenLock: true }), activeDevices: 4, compliant: 4, nonCompliant: 0, unknown: 0, items: [], ...o })

describe("policy form", () => {
  it("starts empty when there is no policy", () => {
    expect(policyToForm(null)).toEqual({ minAppVersion: "", minOsMajor: "", requireScreenLock: false, blockRooted: false })
    expect(policyToForm(policy())).toEqual({ minAppVersion: "", minOsMajor: "", requireScreenLock: false, blockRooted: false })
  })
  it("shows a stored policy", () => {
    expect(policyToForm(policy({ minAppVersion: "2.4", minOsMajor: 12, requireScreenLock: true, blockRooted: true })))
      .toEqual({ minAppVersion: "2.4", minOsMajor: "12", requireScreenLock: true, blockRooted: true })
  })
  it("sends blanks as null and numbers as numbers", () => {
    expect(formToRequest({ minAppVersion: "  ", minOsMajor: " ", requireScreenLock: false, blockRooted: false }))
      .toEqual({ minAppVersion: null, minOsMajor: null, requireScreenLock: false, blockRooted: false })
    expect(formToRequest({ minAppVersion: " 3.10.1 ", minOsMajor: "14", requireScreenLock: true, blockRooted: true }))
      .toEqual({ minAppVersion: "3.10.1", minOsMajor: 14, requireScreenLock: true, blockRooted: true })
  })
  it("accepts good versions", () => {
    for (const v of ["", " ", "2", "2.4", "3.10.1", "1.2.3.4"]) expect(formProblem({ minAppVersion: v, minOsMajor: "", requireScreenLock: false, blockRooted: false })).toBeNull()
  })
  it("refuses bad app versions", () => {
    for (const v of ["2.x", "v2", "1.2.3.4.5", "2..4", "2.", "two", "2.4-beta", "1".repeat(31)])
      expect(formProblem({ minAppVersion: v, minOsMajor: "", requireScreenLock: false, blockRooted: false }), v).toBe("The lowest app version must be numbers separated by dots, like 2.4 or 3.10.1.")
  })
  it("accepts 1 to 99 for the software version and refuses the rest", () => {
    const f = (os: string) => formProblem({ minAppVersion: "", minOsMajor: os, requireScreenLock: false, blockRooted: false })
    for (const v of ["", "1", "9", "14", "99"]) expect(f(v), v).toBeNull()
    for (const v of ["0", "00", "100", "-1", "1.5", "abc", "14a"]) expect(f(v), v).toBe("The lowest Android or iOS version must be a whole number from 1 to 99.")
  })
  it("knows when saving would change nothing", () => {
    expect(isChanged(policyToForm(null), null)).toBe(false)
    expect(isChanged(policyToForm(policy({ minAppVersion: "2.4" })), policy({ minAppVersion: "2.4" }))).toBe(false)
    expect(isChanged({ minAppVersion: " 2.4 ", minOsMajor: "", requireScreenLock: false, blockRooted: false }, policy({ minAppVersion: "2.4" }))).toBe(false)
  })
  it("sees each kind of change", () => {
    const base = policyToForm(policy({ minAppVersion: "2.4", minOsMajor: 12 }))
    const p = policy({ minAppVersion: "2.4", minOsMajor: 12 })
    expect(isChanged({ ...base, minAppVersion: "2.5" }, p)).toBe(true)
    expect(isChanged({ ...base, minAppVersion: "" }, p)).toBe(true)
    expect(isChanged({ ...base, minOsMajor: "13" }, p)).toBe(true)
    expect(isChanged({ ...base, minOsMajor: "" }, p)).toBe(true)
    expect(isChanged({ ...base, requireScreenLock: true }, p)).toBe(true)
    expect(isChanged({ ...base, blockRooted: true }, p)).toBe(true)
  })
})

describe("wording", () => {
  it("words each problem and each gap", () => {
    expect(violationLabel("APP_TOO_OLD")).toBe("App needs updating")
    expect(violationLabel("OS_TOO_OLD")).toBe("Phone software too old")
    expect(violationLabel("NO_SCREEN_LOCK")).toBe("No screen lock")
    expect(violationLabel("ROOTED")).toBe("Rooted or jailbroken")
    expect(violationLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW")
    expect(missingLabel("ROOT_STATUS")).toBe("root status")
    expect(missingLabel("X")).toBe("X")
  })
  it("lists what a phone has not reported", () => {
    expect(missingText([])).toBe("")
    expect(missingText(["APP_VERSION", "SCREEN_LOCK"])).toBe("Not reported: app version, screen lock")
  })
  it("summarises", () => {
    expect(summaryText(report({ policy: policy() }))).toBe("No requirements set yet, so every phone counts as compliant.")
    expect(summaryText(report({ activeDevices: 0, compliant: 0 }))).toBe("No active phones.")
    expect(summaryText(report())).toBe("4 of 4 active phones meet the policy.")
    expect(summaryText(report({ activeDevices: 1, compliant: 1 }))).toBe("1 of 1 active phone meets the policy.")
    expect(summaryText(report({ compliant: 2, nonCompliant: 2 }))).toBe("2 of 4 active phones meet the policy, 2 fall short.")
    expect(summaryText(report({ compliant: 1, nonCompliant: 1, unknown: 2 }))).toBe("1 of 4 active phones meet the policy, 1 fall short, 2 not reported enough to tell.")
    expect(summaryText(report({ compliant: 3, unknown: 1 }))).toBe("3 of 4 active phones meet the policy, 1 not reported enough to tell.")
  })
})

describe("ordering", () => {
  it("puts known failures first, then unknowns, each by name, without changing the input", () => {
    const items = [
      { status: "UNKNOWN", deviceName: "Alpha" }, { status: "NON_COMPLIANT", deviceName: "Zulu" },
      { status: "NON_COMPLIANT", deviceName: "Bravo" }, { status: "UNKNOWN", deviceName: null },
    ]
    const copy = [...items]
    expect(orderItems(items).map(i => i.deviceName)).toEqual(["Bravo", "Zulu", null, "Alpha"])
    expect(items).toEqual(copy)
  })
})
