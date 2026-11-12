import { describe, it, expect } from "vitest"
import { changeText, caveats, type GroupReport, type GroupRow } from "./groupReportOps"

const row = (o: Partial<GroupRow> = {}): GroupRow => ({ legalEntityId: "e1", name: "Acme", unassigned: false, deliveries: 0, litres: 0, revenue: 0, margin: 0, deliveriesWithoutCost: 0, previousDeliveries: 0, previousLitres: 0, previousRevenue: 0, previousMargin: 0, stockLitres: 0, stockValue: 0, tanksWithoutCost: 0, openVariances: 0, ...o })
const rep = (o: Partial<GroupReport> = {}): GroupReport => ({ from: "2026-10-14", to: "2026-11-12", previousFrom: "2026-09-14", previousTo: "2026-10-13", moneyShown: true, entities: [row()], total: row({ name: "Whole group" }), deliveriesLeftOut: 0, ...o })

describe("groupReportOps (6a)", () => {
  it("shows the change against the previous period", () => {
    expect(changeText(1125, 1000)).toBe("+12.5%")
    expect(changeText(920, 1000)).toBe("-8.0%")
    expect(changeText(500, 0)).toBe("new")
    expect(changeText(0, 0)).toBeNull()
    expect(changeText(null, 10)).toBeNull()
    expect(changeText(10, null)).toBeNull()
  })
  it("says what the figures leave out", () => {
    expect(caveats(rep())).toEqual([])
    const c = caveats(rep({ total: row({ deliveriesWithoutCost: 2, tanksWithoutCost: 1 }), deliveriesLeftOut: 1, entities: [row({ unassigned: true, legalEntityId: null, name: "Not assigned to a legal entity" })] }))
    expect(c).toHaveLength(4)
    expect(c[0]).toMatch(/2 deliveries have no cost at sale/); expect(c[1]).toMatch(/1 tank holds fuel with no known cost/); expect(c[2]).toMatch(/1 reversed delivery is left out/); expect(c[3]).toMatch(/no legal entity/)
  })
})
