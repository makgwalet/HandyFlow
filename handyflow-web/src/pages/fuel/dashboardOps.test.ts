import { describe, expect, it } from "vitest"
import { asSummary, buildDashboard, deliveryStep, DELIVERY_STEPS, fmtLitres } from "./dashboardOps"

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
