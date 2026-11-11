import { describe, it, expect } from "vitest"
import { validateSettings, toForm, toRequest, rowCaveats, losesMoneyToServe, type FuelSettings } from "./costToServeOps"

const s: FuelSettings = { driverCostPerHour: 150, vehicleCostPerKm: 6.5, fixedCostPerDelivery: 40, overheadPctOfRevenue: 3, defaultTripHours: 2, poOverTolerancePct: 2, ratesConfigured: true }

describe("costToServeOps", () => {
  it("accepts the saved settings and round-trips them", () => {
    const f = toForm(s)
    expect(validateSettings(f)).toBeNull()
    expect(toRequest(f)).toEqual({ driverCostPerHour: 150, vehicleCostPerKm: 6.5, fixedCostPerDelivery: 40, overheadPctOfRevenue: 3, defaultTripHours: 2, poOverTolerancePct: 2 })
  })
  it("a blank rate means zero, not an error", () => {
    const f = { ...toForm(s), driverCostPerHour: "", vehicleCostPerKm: " " }
    expect(validateSettings(f)).toBeNull()
    expect(toRequest(f).driverCostPerHour).toBe(0); expect(toRequest(f).vehicleCostPerKm).toBe(0)
  })
  it("rejects negatives, out-of-range overhead, trip hours and tolerance", () => {
    const f = toForm(s)
    expect(validateSettings({ ...f, driverCostPerHour: "-1" })).toMatch(/Driver cost/)
    expect(validateSettings({ ...f, vehicleCostPerKm: "abc" })).toMatch(/Vehicle cost/)
    expect(validateSettings({ ...f, overheadPctOfRevenue: "101" })).toMatch(/Overhead/)
    expect(validateSettings({ ...f, defaultTripHours: "0" })).toMatch(/trip hours/)
    expect(validateSettings({ ...f, defaultTripHours: "" })).toMatch(/trip hours/)
    expect(validateSettings({ ...f, poOverTolerancePct: "11" })).toMatch(/tolerance/)
    expect(validateSettings({ ...f, poOverTolerancePct: "-1" })).toMatch(/tolerance/)
  })
  it("caveats say which part of a customer's figure is estimated", () => {
    expect(rowCaveats({ deliveries: 4, deliveriesWithoutDistance: 0, deliveriesWithEstimatedHours: 0 })).toEqual([])
    const c = rowCaveats({ deliveries: 4, deliveriesWithoutDistance: 2, deliveriesWithEstimatedHours: 1 })
    expect(c[0]).toMatch(/2 of 4.*no site distance/); expect(c[1]).toMatch(/1 of 4.*estimated driver hours/)
  })
  it("flags a customer that is profitable on margin but loses money once service is counted", () => {
    expect(losesMoneyToServe({ margin: 500, netMargin: -100 })).toBe(true)
    expect(losesMoneyToServe({ margin: 500, netMargin: 100 })).toBe(false)
    expect(losesMoneyToServe({ margin: -50, netMargin: -100 })).toBe(false)
  })
})
