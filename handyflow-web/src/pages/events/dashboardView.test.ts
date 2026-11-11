import { describe, it, expect } from "vitest"
import { attentionTitle, barPercent, budgetStatusText, daysText, fillText, placesLeft, sortAttention, tierBody, tierProblem, tierState, bucketLabel } from "./dashboardView"

const item = (type: string, severity: string) => ({ type, severity, message: "m", eventId: "e", eventNumber: "EVT-1" })
const now = new Date("2026-11-11T10:00:00")

describe("dashboard wording", () => {
  it("puts warnings first and keeps the given order otherwise", () => {
    const out = sortAttention([item("A", "INFO"), item("B", "WARNING"), item("C", "INFO"), item("D", "WARNING")])
    expect(out.map(i => i.type)).toEqual(["B", "D", "A", "C"])
  })
  it("names attention types and falls back to the raw type", () => {
    expect(attentionTitle("BUDGET_NOT_APPROVED")).toBe("Budget not approved"); expect(attentionTitle("NEW_THING")).toBe("NEW_THING")
  })
  it("says days in words", () => {
    expect(daysText(0)).toBe("Today"); expect(daysText(1)).toBe("Tomorrow"); expect(daysText(12)).toBe("In 12 days")
    expect(daysText(-1)).toBe("Started yesterday"); expect(daysText(-3)).toBe("Started 3 days ago")
  })
  it("shows fill with or without a limit", () => {
    expect(fillText(120, 200, 60)).toBe("120 of 200 (60%)"); expect(fillText(5, null, null)).toBe("5 registered"); expect(fillText(7, 10, null)).toBe("7 of 10")
  })
  it("keeps bars within 0-100", () => {
    expect(barPercent(5, 10)).toBe(50); expect(barPercent(20, 10)).toBe(100); expect(barPercent(-1, 10)).toBe(0); expect(barPercent(5, 0)).toBe(0); expect(barPercent(NaN, 10)).toBe(0)
  })
  it("budget status text", () => { expect(budgetStatusText(null)).toBe("No budget"); expect(budgetStatusText("SUBMITTED")).toBe("Awaiting approval") })
  it("formats an arrival bucket and survives junk", () => { expect(bucketLabel("2026-11-11T08:15:00")).toBe("08:15"); expect(bucketLabel("nope")).toBe("") })
})

describe("ticket types", () => {
  const base = { active: true, available: 5, saleStart: null, saleEnd: null }
  it("works out what each type is doing now", () => {
    expect(tierState(base, now).label).toBe("On sale")
    expect(tierState({ ...base, active: false }, now).label).toBe("Off sale")
    expect(tierState({ ...base, available: 0 }, now).label).toBe("Sold out")
    expect(tierState({ ...base, saleStart: "2026-12-01T00:00:00" }, now).label).toBe("Not on sale yet")
    expect(tierState({ ...base, saleEnd: "2026-11-01T00:00:00" }, now).label).toBe("Sale ended")
  })
  it("counts places left only on tiers that are on sale", () => {
    expect(placesLeft([{ active: true, available: 5 }, { active: false, available: 100 }, { active: true, available: -2 }])).toBe(5)
  })
  it("checks the add form", () => {
    const ok = { name: "VIP", price: "250", quantity: "50", saleStart: "", saleEnd: "" }
    expect(tierProblem(ok)).toBeNull(); expect(tierProblem({ ...ok, price: "" })).toBeNull()
    expect(tierProblem({ ...ok, name: " " })).toMatch(/name/)
    expect(tierProblem({ ...ok, price: "12.345" })).toMatch(/Price/)
    for (const q of ["", "0", "1.5", "abc"]) expect(tierProblem({ ...ok, quantity: q })).toMatch(/Quantity/)
    expect(tierProblem({ ...ok, quantity: "1000001" })).toMatch(/1 000 000/)
    expect(tierProblem({ ...ok, saleStart: "2026-12-02T10:00", saleEnd: "2026-12-01T10:00" })).toMatch(/end after/)
  })
  it("builds the body: free when price is empty, seconds on dates", () => {
    expect(tierBody({ name: " VIP ", description: "", price: "", quantity: "50", saleStart: "2026-12-01T10:00", saleEnd: "" }))
      .toEqual({ name: "VIP", description: null, price: 0, quantity: 50, saleStart: "2026-12-01T10:00:00", saleEnd: null })
  })
})
