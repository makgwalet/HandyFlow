import { describe, expect, it } from "vitest"
import { asRetail, asYard, isNotForYou, retailTile, yardTile, asSummary, buildDashboard, deliveryStep, DELIVERY_STEPS, fmtLitres } from "./dashboardOps"

const tank = (o: any = {}) => ({ id: "t1", name: "Tank 1", fuelType: "DIESEL", capacityLitres: 10000, currentLitres: 8000, fillPercentage: 80, low: false, ...o })

const summary = (o: any = {}) => ({ today: "2026-11-17", monthLitres: 0, monthDispatches: 0, monthReceipts: 0, dailyLitres: [], negativeVarianceReadings: 0,
  openDeliveries: 0, inTransitDeliveries: 0, overdueDeliveries: 0, nextDeliveries: [], ...o })

describe("buildDashboard", () => {
  it("totals stock and capacity and reports the fill percentage", () => {
    const d = buildDashboard({ tanks: [tank(), tank({ id: "t2", currentLitres: 2000, capacityLitres: 10000, fillPercentage: 20 })], summary: summary() })
    expect(d.totalStock).toBe(10000)
    expect(d.capacity).toBe(20000)
    expect(d.stockPercent).toBe(50)
  })

  it("has a zero fill percentage rather than NaN when there are no tanks", () => {
    const d = buildDashboard({ tanks: [], summary: summary() })
    expect(d.stockPercent).toBe(0)
    expect(d.alerts).toEqual([])
  })

  it("takes the month figures from the server summary", () => {
    const d = buildDashboard({ tanks: [], summary: summary({ monthLitres: "150.5", monthDispatches: 2, monthReceipts: 3, dailyLitres: [0, 0, 150.5] }) })
    expect(d.monthDispatchCount).toBe(2)
    expect(d.monthLitres).toBe(150.5)
    expect(d.monthReceipts).toBe(3)
    expect(d.dailyLitres).toEqual([0, 0, 150.5])
  })

  it("shows zeros, not a crash, while the summary is not loaded", () => {
    const d = buildDashboard({ tanks: [tank()], summary: null })
    expect(d.monthLitres).toBe(0)
    expect(d.dailyLitres).toEqual([])
    expect(d.openDeliveries).toBe(0)
    expect(d.alerts).toEqual([])
  })

  it("raises alerts for low tanks, negative dip variance and overdue deliveries", () => {
    const d = buildDashboard({
      tanks: [tank({ low: true, currentLitres: 900, fillPercentage: 9 })],
      summary: summary({ negativeVarianceReadings: 1, overdueDeliveries: 2 }),
    })
    expect(d.alerts.map(a => a.key)).toEqual(["low-t1", "variance", "overdue"])
    expect(d.alerts[0].severity).toBe("high")
    expect(d.alerts[1].title).toBe("1 negative variance reading")
    expect(d.alerts[1].section).toBe("tanks")
    expect(d.alerts[2].title).toBe("2 deliveries are overdue")
    expect(buildDashboard({ tanks: [], summary: summary({ overdueDeliveries: 1 }) }).alerts[0].title).toBe("1 delivery is overdue")
  })

  it("uses the server's counts for open and in-transit deliveries and keeps its soonest-first list", () => {
    const next = [{ id: "a", status: "SCHEDULED", fuelType: "DIESEL", litresOrdered: 5000, scheduledAt: "2026-11-17T08:00:00Z" }, { id: "b", status: "IN_TRANSIT", fuelType: "PETROL", litresOrdered: 3000, scheduledAt: "2026-11-18T08:00:00Z" }]
    const d = buildDashboard({ tanks: [], summary: summary({ openDeliveries: 14, inTransitDeliveries: 3, nextDeliveries: next }) })
    expect(d.openDeliveries).toBe(14)
    expect(d.inTransit).toBe(3)
    expect(d.pendingDeliveries.map((x: any) => x.id)).toEqual(["a", "b"])
  })
})

describe("asSummary", () => {
  it("accepts the server's object and rejects anything else", () => {
    expect(asSummary(summary())).not.toBeNull()
    expect(asSummary([])).toBeNull()
    expect(asSummary(null)).toBeNull()
    expect(asSummary({ content: [] })).toBeNull()
    expect(asSummary({ monthDispatches: 1, dailyLitres: [] })?.nextDeliveries).toEqual([])
  })
})

describe("deliveryStep", () => {
  it("maps statuses to tracker positions", () => {
    expect(deliveryStep("SCHEDULED")).toBe(0)
    expect(deliveryStep("IN_TRANSIT")).toBe(1)
    expect(deliveryStep("DELIVERED")).toBe(DELIVERY_STEPS.length)
    expect(deliveryStep(undefined)).toBe(0)
  })
})

describe("fmtLitres", () => {
  it("rounds and groups", () => { expect(fmtLitres(1234.6).replace(/\s/g, " ")).toMatch(/^1.235 L$/) })
})

describe("retail and yard tiles", () => {
  it("reads the forecourt summary and refuses anything else", () => {
    expect(asRetail({ days: 7, shifts: 3, litres: "1200.5", sales: "25000", variance: "-10", flaggedShifts: 1, byFuel: [] })).toEqual({ days: 7, shifts: 3, litres: 1200.5, sales: 25000, variance: -10, flaggedShifts: 1 })
    expect(asRetail(null)).toBeNull(); expect(asRetail([])).toBeNull(); expect(asRetail({ message: "x" })).toBeNull()
  })
  it("shows takings green, and amber when shifts are flagged", () => {
    const ok = retailTile({ days: 7, shifts: 1, litres: 500, sales: 12345.6, variance: 0, flaggedShifts: 0 })
    expect(ok.tone).toBe("green"); expect(ok.value).toMatch(/^R.12.346$/); expect(ok.hint).toMatch(/1 shift$/)
    const bad = retailTile({ days: 7, shifts: 4, litres: 500, sales: 100, variance: -50, flaggedShifts: 2 })
    expect(bad.tone).toBe("amber"); expect(bad.hint).toContain("2 flagged")
  })
  it("says plainly when nothing was sold", () => expect(retailTile({ days: 7, shifts: 0, litres: 0, sales: 0, variance: 0, flaggedShifts: 0 }).hint).toBe("No shifts in the last 7 days"))
  it("reads the yard, counting overstays from the capacity view", () => {
    expect(asYard({ inYard: 6, arrivals: 2, departures: 1, bays: 10, free: 4 }, { overstays: [{ id: "a" }, { id: "b" }] })).toEqual({ inYard: 6, arrivals: 2, departures: 1, bays: 10, free: 4, overstays: 2 })
    expect(asYard({ inYard: 0, arrivals: 0, departures: 0, bays: null, free: null }, null)?.overstays).toBe(0)
    expect(asYard(null, null)).toBeNull(); expect(asYard({ message: "x" }, null)).toBeNull()
  })
  it("shows the yard violet, and red when a truck has overstayed", () => {
    const ok = yardTile({ inYard: 6, arrivals: 2, departures: 1, bays: 10, free: 4, overstays: 0 })
    expect(ok.tone).toBe("violet"); expect(ok.value).toBe("6 in the yard"); expect(ok.hint).toBe("4 of 10 bays free · 2 arriving · 1 leaving today")
    const bad = yardTile({ inYard: 6, arrivals: 0, departures: 0, bays: null, free: null, overstays: 1 })
    expect(bad.tone).toBe("red"); expect(bad.hint).toBe("1 overstaying · No bay limit set")
  })
  it("leaves a tile out only for a division or permission the person does not have", () => {
    for (const s of [401, 403, 404]) expect(isNotForYou(s)).toBe(true)
    for (const s of [500, 502, undefined]) expect(isNotForYou(s)).toBe(false)
  })
})
