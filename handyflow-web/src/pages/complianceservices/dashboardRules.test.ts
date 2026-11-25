import { describe, it, expect } from "vitest"
import { tiles, shares, weekLabel, monthLabel, stageLabel, QUOTE_STAGE_LABELS, weeksNote, billingNote, type Kpis } from "./dashboardRules"

const kpis = (o: Partial<Kpis> = {}): Kpis => ({ activeClients: 4, overdueDeadlines: 0, deadlinesNext14: 3, documentsToReview: 1, documentsExpiring: 0, openCases: 0, regulatoryOpen: 0, quotesOut: 2, quotesOutValue: 3000, activeAgreements: 5, billsToRaise: 0, billsToRaiseValue: 0, ...o })

describe("dashboardRules", () => {
  it("is red only when something is overdue, and amber only when something needs doing", () => {
    const calm = tiles(kpis())
    expect(calm.find(t => t.key === "overdue")?.tone).toBe("ok")
    expect(calm.find(t => t.key === "cases")?.tone).toBe("neutral")
    const busy = tiles(kpis({ overdueDeadlines: 2, openCases: 1, documentsExpiring: 2, regulatoryOpen: 1, billsToRaise: 1, billsToRaiseValue: 500 }))
    expect(busy.find(t => t.key === "overdue")?.tone).toBe("bad")
    expect(busy.find(t => t.key === "cases")?.tone).toBe("warn")
    expect(busy.find(t => t.key === "documents")?.value).toBe("3")
    expect(busy.find(t => t.key === "bills")?.tone).toBe("warn")
  })
  it("sends each tile to the place that deals with it", () => {
    expect(Object.fromEntries(tiles(kpis()).map(t => [t.key, t.to]))).toMatchObject({ overdue: "queue", regulatory: "regulatory", quotes: "quotes", bills: "billing", agreements: "agreements", clients: "clients" })
  })
  it("sizes bars against the longest, keeping small non-zero values visible", () => {
    expect(shares([0, 5, 10])).toEqual([0, 0.5, 1])
    expect(shares([1, 1000])[0]).toBe(0.03)
    expect(shares([0, 0])).toEqual([0, 0])
    expect(shares([])).toEqual([])
  })
  it("labels weeks, months and stages", () => {
    expect(weekLabel("2026-11-25")).toBe("25 Nov")
    expect(monthLabel("2027-01")).toBe("Jan")
    expect(stageLabel(QUOTE_STAGE_LABELS, "PENDING_APPROVAL")).toBe("Awaiting approval")
    expect(stageLabel(QUOTE_STAGE_LABELS, "ODD_ONE")).toBe("odd one")
  })
  it("writes the notes under the charts", () => {
    expect(weeksNote(0, [{ start: "2026-11-25", count: 1 }, { start: "2026-12-02", count: 2 }])).toBe("3 deadlines in the next 8 weeks")
    expect(weeksNote(2, [{ start: "2026-11-25", count: 1 }])).toBe("1 deadline in the next 8 weeks, and 2 already overdue")
    expect(billingNote(0, 0)).toMatch(/Nothing waiting/)
    expect(billingNote(1200, 2)).toMatch(/2 bills from earlier months/)
  })
})
