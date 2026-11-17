import { describe, expect, it } from "vitest"
import { buildDashboard, deliveryStep, DELIVERY_STEPS, fmtLitres } from "./dashboardOps"

const now = new Date(2026, 10, 17, 12, 0, 0)  // 17 Nov 2026, local
const tank = (o: any = {}) => ({ id: "t1", name: "Tank 1", fuelType: "DIESEL", capacityLitres: 10000, currentLitres: 8000, fillPercentage: 80, low: false, ...o })

describe("buildDashboard", () => {
  it("totals stock and capacity and reports the fill percentage", () => {
    const d = buildDashboard({ tanks: [tank(), tank({ id: "t2", currentLitres: 2000, capacityLitres: 10000, fillPercentage: 20 })], dispatches: [], deliveries: [], receipts: [], now })
    expect(d.totalStock).toBe(10000)
    expect(d.capacity).toBe(20000)
    expect(d.stockPercent).toBe(50)
  })

  it("has a zero fill percentage rather than NaN when there are no tanks", () => {
    const d = buildDashboard({ tanks: [], dispatches: [], deliveries: [], receipts: [], now })
    expect(d.stockPercent).toBe(0)
    expect(d.alerts).toEqual([])
  })

  it("counts only this month's dispatches and builds a per-day series", () => {
    const d = buildDashboard({
      tanks: [], deliveries: [], receipts: [], now,
      dispatches: [
        { dispatchedAt: new Date(2026, 10, 3, 9).toISOString(), litresDispensed: 100 },
        { dispatchedAt: new Date(2026, 10, 3, 15).toISOString(), litresDispensed: 50 },
        { dispatchedAt: new Date(2026, 9, 30, 9).toISOString(), litresDispensed: 999 },
      ],
    })
    expect(d.monthDispatchCount).toBe(2)
    expect(d.monthLitres).toBe(150)
    expect(d.dailyLitres).toHaveLength(17)
    expect(d.dailyLitres[2]).toBe(150)
  })

  it("raises alerts for low tanks, negative variance and overdue deliveries", () => {
    const d = buildDashboard({
      tanks: [tank({ low: true, currentLitres: 900, fillPercentage: 9 })],
      dispatches: [{ varianceLitres: -20 }, { varianceLitres: -2 }],
      deliveries: [{ id: "d1", status: "SCHEDULED", scheduledAt: new Date(2026, 10, 10).toISOString(), fuelType: "DIESEL" }],
      receipts: [], now,
    })
    expect(d.alerts.map(a => a.key)).toEqual(["low-t1", "variance", "overdue"])
    expect(d.alerts[0].severity).toBe("high")
    expect(d.alerts[1].title).toBe("1 negative variance reading")
  })

  it("lists scheduled and in-transit deliveries soonest first and ignores finished ones", () => {
    const d = buildDashboard({
      tanks: [], dispatches: [], receipts: [], now,
      deliveries: [
        { id: "b", status: "IN_TRANSIT", scheduledAt: "2026-11-18T08:00:00Z" },
        { id: "a", status: "SCHEDULED", scheduledAt: "2026-11-17T08:00:00Z" },
        { id: "c", status: "DELIVERED", scheduledAt: "2026-11-01T08:00:00Z" },
      ],
    })
    expect(d.pendingDeliveries.map((x: any) => x.id)).toEqual(["a", "b"])
    expect(d.inTransit).toBe(1)
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
