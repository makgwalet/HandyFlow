import { describe, it, expect } from "vitest"
import { attention, pct, type Overview } from "./overviewOps"

const o = (x: Partial<Overview> = {}): Overview => ({ today: "2026-11-11", stock: [], lowTanks: 0, quotes: 0, confirmedOrders: 0, partDeliveredOrders: 0, deliveredNotInvoicedCount: 0, deliveredNotInvoicedValue: 0, invoiceFailures: 0, customersOnCreditHold: 0, complianceExpired: 0, complianceExpiring: 0, loadsToday: 0, loadsTodayUnallocated: 0, purchaseOrdersAwaitingDelivery: 0, ...x })

describe("overviewOps", () => {
  it("nothing needs attention when all is well", () => { expect(attention(o())).toEqual([]) })
  it("expired paperwork comes first", () => {
    const a = attention(o({ lowTanks: 2, complianceExpired: 1, loadsTodayUnallocated: 3 }))
    expect(a.map(x => x.key)).toEqual(["expired", "unalloc", "low"])
    expect(a[0].section).toBe("compliance")
  })
  it("uses the right singular and plural", () => {
    expect(attention(o({ loadsTodayUnallocated: 1 }))[0].text).toMatch(/1 load today has/)
    expect(attention(o({ loadsTodayUnallocated: 2 }))[0].text).toMatch(/2 loads today have/)
  })
  it("percent handles zero", () => { expect(pct(5, 0)).toBe("–"); expect(pct(1, 4)).toBe("25.0%") })
})
