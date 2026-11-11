import { describe, it, expect } from "vitest"
import { validateOrder, validateConfirm, validateLoad, validateLoading, loadVariance, groupByCustomer, statusLabel, type CreditCheck, type QueueLine } from "./orderOps"

const check = (o: Partial<CreditCheck> = {}): CreditCheck => ({ creditLimit: 100, invoicesOutstanding: 0, deliveredNotInvoiced: 0, otherOpenOrders: 0, thisOrder: 50, exposureAfter: 50, withinLimit: true, onCreditHold: false, ...o })

describe("orderOps", () => {
  it("validates a quotation", () => {
    expect(validateOrder("", "DIESEL", "10", "1", "")).toMatch(/account/)
    expect(validateOrder("a", "", "10", "1", "")).toMatch(/fuel type/)
    expect(validateOrder("a", "DIESEL", "0", "1", "")).toMatch(/Litres/)
    expect(validateOrder("a", "DIESEL", "10", "-1", "")).toMatch(/Price/)
    expect(validateOrder("a", "DIESEL", "10", "1", "2020-01-01")).toMatch(/past/)
    expect(validateOrder("a", "DIESEL", "10", "1", "2999-01-01")).toBeNull()
  })
  it("confirm needs a tank; over limit needs permission and a reason; a hold blocks outright", () => {
    expect(validateConfirm("", check(), false, "")).toMatch(/tank/)
    expect(validateConfirm("t", check(), false, "")).toBeNull()
    expect(validateConfirm("t", check({ onCreditHold: true }), true, "because")).toMatch(/hold/)
    expect(validateConfirm("t", check({ withinLimit: false }), false, "because")).toMatch(/permission/)
    expect(validateConfirm("t", check({ withinLimit: false }), true, "no")).toMatch(/reason/)
    expect(validateConfirm("t", check({ withinLimit: false }), true, "Prepaid by EFT")).toBeNull()
  })
  it("a load cannot exceed what is left", () => {
    expect(validateLoad("0", 100, "x")).toMatch(/greater/)
    expect(validateLoad("101", 100, "x")).toMatch(/Only/)
    expect(validateLoad("50", 100, "")).toMatch(/when/)
    expect(validateLoad("50", 100, "2026-11-12T08:00")).toBeNull()
    expect(validateLoading("")).not.toBeNull(); expect(validateLoading("10")).toBeNull()
  })
  it("flags a loading variance beyond 0.5% for review and ignores missing figures", () => {
    expect(loadVariance(null, 100)).toBeNull()
    expect(loadVariance(10000, 9980)!.needsReview).toBe(false)
    expect(loadVariance(10000, 9900)!.needsReview).toBe(true)
    expect(loadVariance(10000, 10100)!.diff).toBe(100)
  })
  it("groups the queue per customer with totals", () => {
    const l = (id: string, c: string | null, t: number) => ({ deliveryId: id, customerId: c, customerName: c, totalAmount: t } as QueueLine)
    const g = groupByCustomer([l("1", "A", 10), l("2", "B", 5), l("3", "A", 7), l("4", null, 1)])
    expect(g.map(x => [x.name, x.total])).toEqual([["A", 17], ["B", 5], ["No customer (walk-in)", 1]])
    expect(statusLabel("PART_DELIVERED")).toBe("Part delivered")
  })
})
