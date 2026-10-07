import { describe, it, expect } from "vitest"
import { fmtR, fmtTimer, unwrap, padZero, QUICK_PROCEDURES } from "./consultationSession.shared"

describe("consultation session helpers", () => {
  it("pads to two digits", () => { expect(padZero(5)).toBe("05"); expect(padZero(12)).toBe("12") })
  it("formats the timer, hours only when needed", () => {
    expect(fmtTimer(0)).toBe("00:00")
    expect(fmtTimer(65)).toBe("01:05")
    expect(fmtTimer(3600)).toBe("01:00:00")
    expect(fmtTimer(3725)).toBe("01:02:05")
  })
  it("formats rand amounts with two decimals and treats missing as zero", () => {
    expect(fmtR(0)).toMatch(/^R\s0[.,]00$/)
    expect(fmtR(undefined as any)).toMatch(/^R\s0[.,]00$/)
    expect(fmtR(1234.5)).toMatch(/50$/)
  })
  it("unwraps a plain array, a data envelope or a page", () => {
    expect(unwrap({ data: [1] })).toEqual([1])
    expect(unwrap({ data: { data: [2] } })).toEqual([2])
    expect(unwrap({ data: { data: { content: [3] } } })).toEqual([3])
    expect(unwrap({ data: { data: {} } })).toEqual([])
  })
  it("keeps the quick procedures list", () => expect(QUICK_PROCEDURES.map(p => p.tariff)).toEqual(["0115", "0116", "0301", "0007", "4116"]))
})
