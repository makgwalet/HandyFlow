import { describe, it, expect } from "vitest"
import { UNKNOWN_PERSON, byLine, detailHeading, flagsOf, personText, liveTotalText, monthParam, overviewParams, readSnapshot, summarise, type DraftDetail } from "./draftViewLogic"

const draft = (o = {}) => ({ id: "d1", siteId: "s1", month: "2026-10", basis: "SCHEDULED", currency: "ZAR", status: "DRAFT" as const, totalCents: 1850000, inProgress: false, unpricedHours: 0, mixedVat: false,
  note: null, createdAt: "2026-11-01T08:00:00Z", approvedAt: null, voidedAt: null, voidReason: null, ...o })
const snap = (o = {}) => ({ siteId: "s1", siteName: "Mall", month: "2026-10", lines: [{ rateId: "r1", type: "HOURLY", rateCents: 18500, vatInclusive: false, hours: 100, daysInForce: 31, amountCents: 1850000 }], billedHours: 100, unpricedHours: 0, totalCents: 1850000, warnings: [], ...o })
const detail = (d = {}, s: unknown = snap()): DraftDetail => ({ draft: draft(d), snapshot: s })

describe("monthParam and overviewParams", () => {
  it("accepts only a real month", () => {
    for (const ok of ["2026-10", "2026-01", "2026-12", " 2026-10 "]) expect(monthParam(ok)).toBe(ok.trim())
    for (const bad of ["", "2026-13", "2026-00", "2026-1", "October", "2026-10-01", "26-10"]) expect(monthParam(bad)).toBeUndefined()
  })
  it("sends only what is set", () => {
    expect(overviewParams("", "")).toEqual({})
    expect(overviewParams("2026-10", "")).toEqual({ month: "2026-10" })
    expect(overviewParams("nonsense", "DRAFT")).toEqual({ status: "DRAFT" })
    expect(overviewParams("2026-09", "APPROVED")).toEqual({ month: "2026-09", status: "APPROVED" })
  })
})

describe("summarise", () => {
  it("counts each status and totals only the live ones", () => {
    const s = summarise([draft({ totalCents: 100 }), draft({ status: "APPROVED", totalCents: 250 }), draft({ status: "VOID", totalCents: 999999 })])
    expect(s).toMatchObject({ waiting: 1, approved: 1, voided: 1, liveByCurrency: [{ currency: "ZAR", cents: 350 }] })
  })
  it("never adds one currency to another", () => {
    const s = summarise([draft({ totalCents: 100 }), draft({ currency: "USD", totalCents: 200 })])
    expect(s.liveByCurrency).toEqual([{ currency: "ZAR", cents: 100 }, { currency: "USD", cents: 200 }])
    expect(liveTotalText(s)).toBe("R1.00 + USD 2.00")
  })
  it("says R0.00 for nothing", () => { expect(liveTotalText(summarise([]))).toBe("R0.00"); expect(liveTotalText(summarise([draft({ status: "VOID" })]))).toBe("R0.00") })
})

describe("flagsOf", () => {
  it("names each reason to look twice", () => {
    expect(flagsOf(draft())).toEqual([])
    expect(flagsOf(draft({ inProgress: true, unpricedHours: 1, mixedVat: true }))).toEqual(["Month was still in progress", "1 hour with no rate", "Mixed VAT"])
    expect(flagsOf(draft({ unpricedHours: 2.5 }))).toEqual(["2.5 hours with no rate"])
  })
})

describe("readSnapshot", () => {
  it("returns the frozen preview when it agrees with the draft", () => {
    const r = readSnapshot(detail())
    expect(r.problem).toBeNull()
    expect(r.preview?.totalCents).toBe(1850000)
  })
  it("says so when the copy cannot be read", () => {
    for (const bad of [null, "text", {}, { lines: "x", totalCents: 1 }, { lines: [], totalCents: "1" }]) {
      const r = readSnapshot(detail({}, bad))
      expect(r.preview).toBeNull()
      expect(r.problem).toMatch(/could not be read/)
    }
  })
  it("warns, and still shows the copy, when its total is not the draft's total", () => {
    const r = readSnapshot(detail({ totalCents: 1000 }))
    expect(r.preview).not.toBeNull()
    expect(r.problem).toMatch(/R18 500\.00 but the draft says R10\.00/)
    expect(r.problem).toMatch(/Do not approve/)
  })
  it("warns when the lines do not add up to the total", () => {
    const r = readSnapshot(detail({}, snap({ totalCents: 1850000, lines: [{ amountCents: 1000 }] })))
    expect(r.problem).toMatch(/lines add up to R10\.00/)
  })
  it("treats a line without an amount as nothing rather than crashing", () => {
    expect(() => readSnapshot(detail({ totalCents: 0 }, snap({ totalCents: 0, lines: [{}, null] })))).not.toThrow()
  })
  it("accepts an empty month (no lines, total 0)", () => {
    expect(readSnapshot(detail({ totalCents: 0 }, snap({ totalCents: 0, lines: [], billedHours: 0 }))).problem).toBeNull()
  })
})

describe("detailHeading", () => {
  it("names the basis", () => {
    expect(detailHeading({ basis: "ACTUAL" })).toBe("Figures as they were when this was saved (hours actually worked)")
    expect(detailHeading({ basis: "OTHER" })).toContain("(other)")
  })
})

describe("personText", () => {
  const people = { u1: "Thandi Nkosi" }
  it("gives the name for an id the server named", () => { expect(personText("u1", people)).toBe("Thandi Nkosi") })
  it("says so when the server sent names but not this one", () => { expect(personText("u2", people)).toBe(UNKNOWN_PERSON) })
  it("shows nothing when there is no id", () => {
    expect(personText(null, people)).toBeNull()
    expect(personText(undefined, people)).toBeNull()
    expect(personText("", people)).toBeNull()
  })
  it("shows nothing, not a guess, when the server sent no names at all", () => { expect(personText("u1", undefined)).toBeNull() })
  it("an empty list of names means everyone is gone, not that names are missing", () => { expect(personText("u1", {})).toBe(UNKNOWN_PERSON) })
})

describe("byLine", () => {
  it("names who did it", () => { expect(byLine("Saved", "Thandi Nkosi", "2 Oct 10:00")).toBe("Saved by Thandi Nkosi, 2 Oct 10:00") })
  it("is the old wording when nobody is named", () => { expect(byLine("Saved", null, "2 Oct 10:00")).toBe("Saved 2 Oct 10:00") })
})
