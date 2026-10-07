import { describe, it, expect } from "vitest"
import { checkpointState, contractDaysLeft, contractNote, formatAddress, hasPosition, unscannedCount } from "./site.logic"

const NOW = new Date(2026, 9, 7, 12, 0)

describe("formatAddress", () => {
  it("joins known parts in reading order and keeps unknown ones", () => {
    expect(formatAddress({ city: "Pretoria", street: "1 Main Rd", zone: "North" })).toBe("1 Main Rd, Pretoria, North")
  })
  it("is empty rather than invented", () => { expect(formatAddress(null)).toBe(""); expect(formatAddress({ city: "" })).toBe("") })
})

describe("contract wording", () => {
  it("counts whole days", () => { expect(contractDaysLeft("2026-10-17", NOW)).toBe(10); expect(contractDaysLeft("2026-10-07", NOW)).toBe(0); expect(contractDaysLeft("2026-10-05", NOW)).toBe(-2) })
  it("has no days without an end date", () => { expect(contractDaysLeft(null, NOW)).toBeNull(); expect(contractNote("ACTIVE", null, NOW)).toBe("No end date on record") })
  it("words each case", () => {
    expect(contractNote("ACTIVE", "2026-10-08", NOW)).toBe("1 day left")
    expect(contractNote("ACTIVE", "2026-10-07", NOW)).toBe("Ends today")
    expect(contractNote("EXPIRED", "2026-10-06", NOW)).toBe("Ended 1 day ago")
    expect(contractNote("TERMINATED", "2027-01-01", NOW)).toBe("Terminated")
  })
})

describe("checkpoints", () => {
  it("flags never scanned and quiet checkpoints", () => {
    expect(checkpointState({ scans30d: 0, lastScanAt: null })).toEqual({ label: "Never scanned", tone: "warn" })
    expect(checkpointState({ scans30d: 0, lastScanAt: "2026-08-01T10:00:00Z" }).label).toBe("No scans in 30 days")
    expect(checkpointState({ scans30d: 1, lastScanAt: "2026-10-01T10:00:00Z" })).toEqual({ label: "1 scan in 30 days", tone: "ok" })
    expect(checkpointState({ scans30d: 12, lastScanAt: "2026-10-01T10:00:00Z" }).label).toBe("12 scans in 30 days")
  })
  it("counts the quiet ones", () => {
    expect(unscannedCount([{ id: "a", name: "A", scans30d: 0, lastScanAt: null }, { id: "b", name: "B", scans30d: 3, lastScanAt: "x" }])).toBe(1)
  })
})

describe("hasPosition", () => {
  it("needs both coordinates", () => {
    expect(hasPosition({ latitude: -25.8, longitude: 28.1 })).toBe(true)
    expect(hasPosition({ latitude: null, longitude: 28.1 })).toBe(false)
  })
})
