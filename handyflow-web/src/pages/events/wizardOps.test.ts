import { describe, it, expect, vi } from "vitest"
import { budgetTotal, buildPlan, capacityNote, emptyDraft, emptyLine, emptyTier, emptyVendor, firstBlockedStep, runPlan, stepProblem, tierPlaces, vendorBody, vendorProblem, visibleSteps } from "./wizardOps"

const all = { tickets: true, vendors: true, budget: true }
const good = () => { const d = emptyDraft(); d.event.title = "Summit"; d.event.startDatetime = "2026-12-01T08:00"; d.event.endDatetime = "2026-12-01T17:00"; return d }

describe("steps", () => {
  it("hides the optional steps from people who may not do them", () => {
    expect(visibleSteps(all)).toEqual(["details", "tickets", "vendors", "budget", "review"])
    expect(visibleSteps({ tickets: false, vendors: false, budget: true })).toEqual(["details", "budget", "review"])
    expect(visibleSteps({ tickets: false, vendors: false, budget: false })).toEqual(["details", "review"])
  })
  it("details need a title and dates; empty optional steps are fine", () => {
    expect(stepProblem("details", emptyDraft())).toMatch(/title/); expect(stepProblem("details", good())).toBeNull()
    for (const s of ["tickets", "vendors", "budget"] as const) expect(stepProblem(s, good())).toBeNull()
  })
  it("names the row that is wrong", () => {
    const d = good(); d.tiers = [{ ...emptyTier(), name: "GA", quantity: "10" }, emptyTier()]; expect(stepProblem("tickets", d)).toMatch(/^Ticket type 2: /)
    d.vendors = [{ ...emptyVendor(), companyName: "Sound", contactEmail: "bad" }]; expect(stepProblem("vendors", d)).toMatch(/^Vendor 1: .*email/)
    d.lines = [{ ...emptyLine(), description: "Hall" }]; expect(stepProblem("budget", d)).toMatch(/^Budget line 1: .*amount/)
  })
  it("sends review back to the first step that is not ready", () => {
    const d = good(); expect(firstBlockedStep(visibleSteps(all), d)).toBeNull(); d.lines = [emptyLine()]; expect(firstBlockedStep(visibleSteps(all), d)).toBe("budget")
  })
})

describe("totals and plan", () => {
  it("adds places and budget, and warns when over capacity", () => {
    const d = good(); d.event.venueCapacity = "100"; d.tiers = [{ ...emptyTier(), name: "A", quantity: "60" }, { ...emptyTier(), name: "B", quantity: "50" }]; d.lines = [{ ...emptyLine(), description: "x", plannedAmount: "1500.50" }, { ...emptyLine(), description: "y", plannedAmount: "500" }]
    expect(tierPlaces(d)).toBe(110); expect(budgetTotal(d)).toBe(2000.5); expect(capacityNote(d)).toMatch(/110 places.*100/); d.event.venueCapacity = ""; expect(capacityNote(d)).toBeNull()
  })
  it("builds the calls in order and leaves out steps the person may not do", () => {
    const d = good(); d.tiers = [{ ...emptyTier(), name: "GA", quantity: "10", price: "100" }]; d.vendors = [{ ...emptyVendor(), companyName: "Sound" }]; d.lines = [{ ...emptyLine(), description: "Hall", plannedAmount: "900" }]
    expect(buildPlan(d, all).map(c => c.path("E1"))).toEqual(["/api/v1/events/E1/tiers", "/api/v1/events/E1/vendors", "/api/v1/events/E1/budget/lines"])
    expect(buildPlan(d, { tickets: true, vendors: false, budget: false })).toHaveLength(1); expect(buildPlan(d, { tickets: false, vendors: false, budget: false })).toHaveLength(0)
  })
  it("sends optional vendor fields as null", () => {
    expect(vendorProblem({ ...emptyVendor(), companyName: "" })).toMatch(/company/); expect(vendorBody({ ...emptyVendor(), companyName: " Sound " })).toMatchObject({ companyName: "Sound", contactName: null, quotedAmount: null, notes: null })
  })
})

describe("runPlan", () => {
  const plan = ["a", "b", "c"].map(l => ({ label: l, path: (id: string) => `/${id}/${l}`, body: l }))
  it("stops at the first failure and resumes from it without repeating", async () => {
    const post = vi.fn().mockResolvedValueOnce({}).mockRejectedValueOnce(new Error("no")).mockResolvedValue({})
    const r1 = await runPlan(plan, "E", 0, post); expect(r1.done).toBe(1); expect(r1.error).toBeTruthy()
    const r2 = await runPlan(plan, "E", r1.done, post); expect(r2).toEqual({ done: 3 }); expect(post.mock.calls.map(c => c[0])).toEqual(["/E/a", "/E/b", "/E/b", "/E/c"])
  })
})
