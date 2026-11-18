import { describe, it, expect } from "vitest"
import {
  asList, errorText, requisitionProblem, requisitionTotal, requisitionBody, requisitionActions, emptyReqLine, budgetProblem, emptyBudget, utilisationTone,
  weightsProblem, DEFAULT_WEIGHTS, rfqProblem, emptyRfq, rfqBody, quoteProblem, quoteBody, overridesRecommendation, awardProblem, rfqActions, type ReqForm, type QuoteForm,
} from "./scmOps"

const req = (over: Partial<ReqForm> = {}): ReqForm => ({ type: "OPERATIONAL", title: "Gloves", businessReason: "", department: "Workshop", costCentre: "", projectRef: "", requiredByDate: "",
  lines: [{ itemName: "Gloves", quantity: "10", estimatedUnitCost: "25", vatRate: "" }], ...over })

describe("asList / errorText", () => {
  it("reads arrays, pages and wrappers", () => {
    expect(asList({ data: { data: [1, 2] } })).toEqual([1, 2])
    expect(asList({ data: { data: { content: [3] } } })).toEqual([3])
    expect(asList({ data: [4] })).toEqual([4])
    expect(asList({ data: { data: null } })).toEqual([])
    expect(asList(undefined)).toEqual([])
  })
  it("prefers the server's message", () => {
    expect(errorText({ response: { data: { message: "Budget exceeded" } }, message: "Request failed" })).toBe("Budget exceeded")
    expect(errorText(new Error("boom"))).toBe("boom")
    expect(errorText({}, "fallback")).toBe("fallback")
  })
})

describe("requisitions", () => {
  it("needs a title, a budget scope and valid lines", () => {
    expect(requisitionProblem(req())).toBeNull()
    expect(requisitionProblem(req({ title: " " }))).toContain("title")
    expect(requisitionProblem(req({ department: "", costCentre: "", projectRef: "" }))).toContain("budget check")
    expect(requisitionProblem(req({ lines: [] }))).toContain("at least one line")
    expect(requisitionProblem(req({ lines: [{ ...emptyReqLine(), itemName: "x", estimatedUnitCost: "" }] }))).toContain("Line 1")
    expect(requisitionProblem(req({ lines: [{ itemName: "x", quantity: "0", estimatedUnitCost: "1", vatRate: "" }] }))).toContain("quantity")
  })
  it("totals only complete lines", () => {
    expect(requisitionTotal([{ itemName: "a", quantity: "2", estimatedUnitCost: "10.5", vatRate: "" }, { itemName: "b", quantity: "x", estimatedUnitCost: "9", vatRate: "" }])).toBe(21)
  })
  it("builds the request body with numbers and nulls", () => {
    const b = requisitionBody(req({ costCentre: " CC1 ", businessReason: "" }))
    expect(b.costCentre).toBe("CC1"); expect(b.businessReason).toBeNull(); expect(b.lines[0]).toMatchObject({ quantity: 10, estimatedUnitCost: 25, vatRate: null })
  })
  it("offers the next steps for each status", () => {
    expect(requisitionActions("DRAFT", null, false, true)).toEqual(["submit", "cancel"])
    expect(requisitionActions("DRAFT", null, true, false)).toEqual([])
    expect(requisitionActions("PENDING_APPROVAL", null, true, false)).toEqual(["approve", "reject"])
    expect(requisitionActions("PENDING_APPROVAL", null, true, true)).toEqual(["approve", "reject", "withdraw"])
    expect(requisitionActions("APPROVED", "NOT_ORDERED", false, true)).toEqual(["convert", "cancel"])
    expect(requisitionActions("APPROVED", "PARTIALLY_ORDERED", false, true)).toEqual(["convert"])
    expect(requisitionActions("APPROVED", "ORDERED", false, true)).toEqual([])
    expect(requisitionActions("CANCELLED", null, true, true)).toEqual([])
  })
})

describe("budgets", () => {
  const ok = { ...emptyBudget(), name: "Workshop 2026", scopeValue: "Workshop", periodStart: "2026-01-01", periodEnd: "2026-12-31", amount: "100000" }
  it("validates", () => {
    expect(budgetProblem(ok)).toBeNull()
    expect(budgetProblem({ ...ok, name: "" })).toContain("name")
    expect(budgetProblem({ ...ok, scopeValue: "" })).toContain("covers")
    expect(budgetProblem({ ...ok, periodEnd: "2025-12-31" })).toContain("before")
    expect(budgetProblem({ ...ok, amount: "0" })).toContain("above zero")
  })
  it("tones", () => {
    expect(utilisationTone("EXCEEDED")).toBe("bad"); expect(utilisationTone("NEAR_LIMIT")).toBe("warn"); expect(utilisationTone("OK")).toBe("ok")
  })
})

describe("RFQs", () => {
  it("weights must be whole numbers adding to 100", () => {
    expect(weightsProblem(DEFAULT_WEIGHTS)).toBeNull()
    expect(weightsProblem({ ...DEFAULT_WEIGHTS, price: 60 })).toContain("add up to 110")
    expect(weightsProblem({ ...DEFAULT_WEIGHTS, price: 50.5 })).toContain("whole number")
    expect(weightsProblem({ ...DEFAULT_WEIGHTS, price: -5, leadTime: 70 })).toContain("whole number")
  })
  it("validates the RFQ and builds the body", () => {
    const f = { ...emptyRfq(), title: "Tyres", lines: [{ itemName: "Tyre", quantity: "8", estimatedUnitCost: "" }], supplierIds: ["s1"], closesAt: "2026-12-01T10:00" }
    expect(rfqProblem(f)).toBeNull()
    expect(rfqProblem({ ...f, title: "" })).toContain("title")
    expect(rfqProblem({ ...f, weights: { ...DEFAULT_WEIGHTS, bbbee: 0 } })).toContain("90")
    const b = rfqBody(f)
    expect(b.lines[0].estimatedUnitCost).toBeNull(); expect(b.supplierIds).toEqual(["s1"]); expect(b.closesAt).toMatch(/^2026-12-01T/)
  })
  const q = (over: Partial<QuoteForm> = {}): QuoteForm => ({ supplierId: "s1", quoteReference: "", validUntil: "", leadTimeDays: "7", warrantyMonths: "", paymentTermsDays: "", deliveryCost: "", otherCharges: "",
    lines: [{ rfqLineId: "l1", noBid: false, quantity: "8", unitPrice: "900", discountPct: "" }], ...over })
  it("a quotation needs a supplier, a lead time and at least one priced line", () => {
    expect(quoteProblem(q())).toBeNull()
    expect(quoteProblem(q({ supplierId: "" }))).toContain("supplier")
    expect(quoteProblem(q({ leadTimeDays: "" }))).toContain("lead time")
    expect(quoteProblem(q({ lines: [{ rfqLineId: "l1", noBid: true, quantity: "", unitPrice: "", discountPct: "" }] }))).toContain("at least one line")
    expect(quoteProblem(q({ lines: [{ rfqLineId: "l1", noBid: false, quantity: "8", unitPrice: "", discountPct: "" }] }))).toContain("unit price")
  })
  it("no-bid lines carry no price", () => {
    const b = quoteBody(q({ lines: [{ rfqLineId: "l1", noBid: false, quantity: "8", unitPrice: "900", discountPct: "5" }, { rfqLineId: "l2", noBid: true, quantity: "", unitPrice: "", discountPct: "" }] }))
    expect(b.lines[0]).toMatchObject({ noBid: false, unitPrice: 900, discountPct: 5 })
    expect(b.lines[1]).toMatchObject({ noBid: true, unitPrice: null })
    expect(b.deliveryCost).toBe(0)
  })
  it("choosing another quotation than the recommended one is an override", () => {
    expect(overridesRecommendation("a", "b")).toBe(true)
    expect(overridesRecommendation("a", "a")).toBe(false)
    expect(overridesRecommendation("a", null)).toBe(false)
    expect(awardProblem("", "why")).toContain("Choose"); expect(awardProblem("a", " ")).toContain("why"); expect(awardProblem("a", "Best value")).toBeNull()
  })
  it("offers the right actions per status", () => {
    expect(rfqActions("DRAFT", false)).toEqual(["publish", "cancel"])
    expect(rfqActions("OPEN", false)).toContain("close")
    expect(rfqActions("CLOSED", false)).toContain("award")
    expect(rfqActions("AWARDED", false)).toEqual(["create-po"])
    expect(rfqActions("AWARDED", true)).toEqual([])
    expect(rfqActions("CANCELLED", false)).toEqual([])
  })
})
