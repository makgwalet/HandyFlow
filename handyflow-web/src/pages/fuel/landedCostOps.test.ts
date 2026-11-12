import { describe, it, expect } from "vitest"
import { perLitre, flagText, leftOutNote, type LandedCostReport } from "./landedCostOps"

const rep = (o: Partial<LandedCostReport> = {}): LandedCostReport => ({ from: "a", to: "b", routesWithDeliveries: 3, routesLeftOut: 0, routes: [], byTanker: [], byDepot: [],
  total: { key: "t", label: "All", routes: 3, litres: 0, revenue: 0, productCost: 0, transportCost: 0, landedCost: 0, landedPerLitre: null, marginPerLitre: null, margin: 0 }, ...o })

describe("landedCostOps", () => {
  it("shows a per-litre figure to the cent and a dash, never zero, for an unknown", () => {
    expect(perLitre(18.25)).toBe("R18.25/L"); expect(perLitre(null)).toBe("-"); expect(perLitre(0)).toBe("R0.00/L")
  })
  it("names each flag in words and passes an unknown one through", () => {
    expect(flagText("NO_TRANSPORT_COST")).toBe("No vehicle or driver cost recorded"); expect(flagText("X")).toBe("X")
  })
  it("says how many routes are left out, with the right grammar, or nothing", () => {
    expect(leftOutNote(rep())).toBeNull()
    expect(leftOutNote(rep({ routesLeftOut: 1 }))).toMatch(/1 of 3 routes are left out .* its cost/)
    expect(leftOutNote(rep({ routesLeftOut: 1, routesWithDeliveries: 1 }))).toMatch(/1 of 1 route is left out/)
    expect(leftOutNote(rep({ routesLeftOut: 2 }))).toMatch(/2 of 3 routes are left out .* their cost/)
  })
})
