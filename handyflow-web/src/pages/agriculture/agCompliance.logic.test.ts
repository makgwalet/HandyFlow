import { describe, it, expect } from "vitest"
import { MARKET_STATUSES, clearOn, formatDay, holdSummary, isMarketMovement, isMarketStatus, overrideProblem, overrideReasonToSend, periodProblem, withdrawalNote, produceLines, produceNote, overrideNote } from "./agCompliance.logic"

describe("which actions are held", () => {
  it("holds sale, cull and transfer out, not death or reinstating", () => {
    expect(MARKET_STATUSES).toEqual(["SOLD", "CULLED", "TRANSFERRED_OUT"])
    expect(isMarketStatus("SOLD")).toBe(true)
    expect(isMarketStatus("DECEASED")).toBe(false)
    expect(isMarketStatus("ACTIVE")).toBe(false)
  })
  it("holds SALE and TRANSFER_OUT movements only", () => {
    expect(isMarketMovement("SALE")).toBe(true)
    expect(isMarketMovement("TRANSFER_OUT")).toBe(true)
    expect(isMarketMovement("AREA_TRANSFER")).toBe(false)
    expect(isMarketMovement("FARM_TRANSFER")).toBe(false)
  })
})

describe("dates", () => {
  it("formats like the server", () => {
    expect(formatDay("2026-12-15")).toBe("15 Dec 2026")
    expect(formatDay("2026-01-05")).toBe("5 Jan 2026")
    expect(formatDay(null)).toBe("")
  })
  it("clears on treatment date plus days, across month and year ends", () => {
    expect(clearOn("2026-12-01", 14)).toBe("2026-12-15")
    expect(clearOn("2026-12-25", "10")).toBe("2027-01-04")
    expect(clearOn("2026-12-01", "")).toBeNull()
    expect(clearOn("2026-12-01", 0)).toBeNull()
    expect(clearOn("2026-12-01", 1.5)).toBeNull()
    expect(clearOn("", 5)).toBeNull()
  })
})

describe("period typed into a form", () => {
  it("accepts empty and 0 to 730 whole days", () => {
    expect(periodProblem("", "Withdrawal period")).toBe("")
    expect(periodProblem("0", "Withdrawal period")).toBe("")
    expect(periodProblem("730", "Withdrawal period")).toBe("")
  })
  it("refuses negatives, fractions, text and more than two years", () => {
    for (const v of ["-1", "1.5", "abc", "731"]) expect(periodProblem(v, "Withdrawal period")).toContain("Withdrawal period")
  })
})

describe("override", () => {
  it("needs a reason of three characters, only when something is held", () => {
    expect(overrideProblem(false, "")).toBe("")
    expect(overrideProblem(true, "")).not.toBe("")
    expect(overrideProblem(true, "  ok ")).not.toBe("")
    expect(overrideProblem(true, "Yes")).toBe("")
  })
  it("sends the trimmed reason only when held", () => {
    expect(overrideReasonToSend(true, "  Breeding sale ")).toBe("Breeding sale")
    expect(overrideReasonToSend(false, "left over text")).toBeNull()
    expect(overrideReasonToSend(true, "   ")).toBeNull()
  })
  it("summarises the hold", () => {
    expect(holdSummary({ held: true, asOf: "2026-12-03", treatedOn: "2026-12-01", clearOn: "2026-12-15", product: "Terramycin" })).toBe("Terramycin on 1 Dec 2026. Clear from 15 Dec 2026.")
    expect(holdSummary({ held: true, asOf: "2026-12-03", treatedOn: null, clearOn: "2026-12-15", product: null })).toBe("A treatment. Clear from 15 Dec 2026.")
  })
})

describe("health event row note", () => {
  it("shows only while the period is still running", () => {
    expect(withdrawalNote("2026-12-15", "2026-12-14")).toBe("Withdrawal until 15 Dec 2026")
    expect(withdrawalNote("2026-12-15", "2026-12-15")).toBe("")
    expect(withdrawalNote(null, "2026-12-15")).toBe("")
  })
})

describe("milk and egg holds", () => {
  const held = (clearOn: string, product: string | null = "Mastitis tube", treatedOn: string | null = "2026-12-01") => ({ held: true, asOf: "2026-12-02", treatedOn, clearOn, product })
  const clear = { held: false, asOf: "2026-12-02", treatedOn: null, clearOn: null, product: null }

  it("says nothing when neither is held, or before the answer arrives", () => {
    expect(produceLines({ milk: clear, eggs: clear })).toEqual([])
    expect(produceLines(undefined)).toEqual([])
  })
  it("names each held product with the day it clears and what held it", () => {
    expect(produceLines({ milk: held("2026-12-05"), eggs: clear })).toEqual(["Milk is held until 5 Dec 2026 (Mastitis tube on 1 Dec 2026)."])
    expect(produceLines({ milk: held("2026-12-05"), eggs: held("2026-12-09", null, null) })).toEqual([
      "Milk is held until 5 Dec 2026 (Mastitis tube on 1 Dec 2026).", "Eggs are held until 9 Dec 2026."])
  })
  it("row note shows each period only while it is running", () => {
    expect(produceNote("2026-12-05", "2026-12-09", "2026-12-02")).toBe("Milk until 5 Dec 2026 · Eggs until 9 Dec 2026")
    expect(produceNote("2026-12-05", "2026-12-09", "2026-12-05")).toBe("Eggs until 9 Dec 2026")
    expect(produceNote(null, undefined, "2026-12-05")).toBe("")
  })
})

describe("override note", () => {
  it("says nothing without a reason", () => {
    expect(overrideNote(null, "2026-12-15")).toBe("")
    expect(overrideNote("   ", "2026-12-15")).toBe("")
    expect(overrideNote(undefined, undefined)).toBe("")
  })
  it("gives the reason and the day the hold would have run to", () => {
    expect(overrideNote("sold for breeding", "2026-12-15")).toBe("Went ahead inside the withdrawal period (held until 15 Dec 2026): sold for breeding")
    expect(overrideNote(" before rain ", "2026-12-25", "pre-harvest interval")).toBe("Went ahead inside the pre-harvest interval (held until 25 Dec 2026): before rain")
  })
  it("leaves the day out when it is not known", () => {
    expect(overrideNote("urgent", null)).toBe("Went ahead inside the withdrawal period: urgent")
  })
})
