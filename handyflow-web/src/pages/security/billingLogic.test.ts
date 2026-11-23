import { describe, it, expect } from "vitest"
import {
  acknowledgementText, approveBlock, billingErrorText, canVoid, createBlock, formatCents, hoursText, needsAcknowledgement, parseRandsToCents,
  periodText, rateBody, rateFormError, rateState, rateText, reasonError, MAX_HOURLY_CENTS, type RateForm,
} from "./billingLogic"

const form = (o: Partial<RateForm> = {}): RateForm => ({ type: "HOURLY", amount: "185", from: "2026-10-01", to: "", note: "", ...o })

describe("money", () => {
  it("formats whole cents without floating point", () => {
    expect(formatCents(0)).toBe("R0.00")
    expect(formatCents(5)).toBe("R0.05")
    expect(formatCents(18500)).toBe("R185.00")
    expect(formatCents(123456)).toBe("R1 234.56")
    expect(formatCents(1000000000)).toBe("R10 000 000.00")
    expect(formatCents(-250)).toBe("-R2.50")
    expect(formatCents(1999, "USD")).toBe("USD 19.99")
  })
  it("reads rand amounts into cents", () => {
    expect(parseRandsToCents("185")).toBe(18500)
    expect(parseRandsToCents("185.5")).toBe(18550)
    expect(parseRandsToCents("185,50")).toBe(18550)
    expect(parseRandsToCents(" 1 234.05 ")).toBe(123405)
    expect(parseRandsToCents("0.01")).toBe(1)
    expect(parseRandsToCents("0.1")).toBe(10)
  })
  it("refuses amounts that are not a positive rand amount", () => {
    for (const bad of ["", "0", "0.00", "-5", "12.345", "abc", "R185", "1e3", "1.2.3", ".5", "1234567890"]) expect(parseRandsToCents(bad), bad).toBeNull()
  })
  it("words hours", () => {
    expect(hoursText(1)).toBe("1 hour")
    expect(hoursText(2.5)).toBe("2.5 hours")
    expect(hoursText(0.333333)).toBe("0.33 hours")
    expect(hoursText(0)).toBe("0 hours")
  })
})

describe("rates", () => {
  it("words a rate by type", () => {
    expect(rateText({ type: "HOURLY", amountCents: 18500, currency: "ZAR" })).toBe("R185.00 per hour")
    expect(rateText({ type: "MONTHLY_FIXED", amountCents: 4200000, currency: "ZAR" })).toBe("R42 000.00 per month, fixed")
  })
  it("says whether a rate is in force today", () => {
    const t = "2026-10-15"
    expect(rateState({ effectiveFrom: "2026-10-01", effectiveTo: null }, t)).toBe("CURRENT")
    expect(rateState({ effectiveFrom: "2026-10-15", effectiveTo: "2026-10-15" }, t)).toBe("CURRENT")
    expect(rateState({ effectiveFrom: "2026-10-16", effectiveTo: null }, t)).toBe("FUTURE")
    expect(rateState({ effectiveFrom: "2026-09-01", effectiveTo: "2026-10-14" }, t)).toBe("ENDED")
  })
  it("words the period", () => {
    expect(periodText({ effectiveFrom: "2026-10-01", effectiveTo: null })).toBe("From 2026-10-01, no end date")
    expect(periodText({ effectiveFrom: "2026-10-01", effectiveTo: "2026-10-31" })).toBe("2026-10-01 to 2026-10-31")
  })
  it("accepts a sound form and refuses each fault with its own message", () => {
    expect(rateFormError(form())).toBeNull()
    expect(rateFormError(form({ amount: "" }))).toMatch(/amount in rand/)
    expect(rateFormError(form({ amount: "0" }))).toMatch(/amount in rand/)
    expect(rateFormError(form({ from: "" }))).toMatch(/first day/)
    expect(rateFormError(form({ from: "1 Oct" }))).toMatch(/first day/)
    expect(rateFormError(form({ to: "soon" }))).toMatch(/not a valid date/)
    expect(rateFormError(form({ to: "2026-09-30" }))).toMatch(/before the first day/)
    expect(rateFormError(form({ to: "2026-10-01" }))).toBeNull()
    expect(rateFormError(form({ note: "x".repeat(501) }))).toMatch(/too long/)
    expect(rateFormError(form({ note: "x".repeat(500) }))).toBeNull()
  })
  it("applies the same caps as the server, per type", () => {
    expect(MAX_HOURLY_CENTS).toBe(10_000_000)
    expect(rateFormError(form({ amount: "100000" }))).toBeNull()
    expect(rateFormError(form({ amount: "100000.01" }))).toMatch(/limit of R100 000.00 an hour/)
    expect(rateFormError(form({ type: "MONTHLY_FIXED", amount: "100000.01" }))).toBeNull()
    expect(rateFormError(form({ type: "MONTHLY_FIXED", amount: "10000000" }))).toBeNull()
    expect(rateFormError(form({ type: "MONTHLY_FIXED", amount: "10000000.01" }))).toMatch(/a month/)
  })
  it("builds the request body", () => {
    expect(rateBody(form({ amount: "185,50", to: "2026-12-31", note: " PO 4471 " }), true))
      .toEqual({ rateType: "HOURLY", amountCents: 18550, vatInclusive: true, effectiveFrom: "2026-10-01", effectiveTo: "2026-12-31", note: "PO 4471" })
    expect(rateBody(form(), false)).toMatchObject({ vatInclusive: false, effectiveTo: null, note: null })
  })
})

describe("invoice drafts", () => {
  const d = (o = {}) => ({ status: "DRAFT" as const, inProgress: false, totalCents: 1000, unpricedHours: 0, mixedVat: false, ...o })
  it("blocks approval in the server's order", () => {
    expect(approveBlock(d())).toBeNull()
    expect(approveBlock(d({ status: "APPROVED" }))).toMatch(/Only a draft/)
    expect(approveBlock(d({ status: "VOID", inProgress: true }))).toMatch(/Only a draft/)
    expect(approveBlock(d({ inProgress: true, totalCents: 0 }))).toMatch(/still in progress/)
    expect(approveBlock(d({ totalCents: 0 }))).toMatch(/bills nothing/)
    expect(approveBlock(d({ totalCents: -1 }))).toMatch(/bills nothing/)
  })
  it("asks for a tick only for real doubts, and says which", () => {
    expect(needsAcknowledgement(d())).toBe(false)
    expect(needsAcknowledgement(d({ unpricedHours: 0.5 }))).toBe(true)
    expect(needsAcknowledgement(d({ mixedVat: true }))).toBe(true)
    expect(acknowledgementText(d({ unpricedHours: 2 }))).toBe("I have checked this: 2 hours with no contract rate.")
    expect(acknowledgementText(d({ mixedVat: true }))).toBe("I have checked this: a mix of VAT-inclusive and VAT-exclusive rates.")
    expect(acknowledgementText(d({ unpricedHours: 1, mixedVat: true }))).toContain("1 hour with no contract rate and a mix")
  })
  it("lets a draft or an approved draft be voided, never a void one", () => {
    expect(canVoid({ status: "DRAFT" })).toBe(true)
    expect(canVoid({ status: "APPROVED" })).toBe(true)
    expect(canVoid({ status: "VOID" })).toBe(false)
  })
  it("needs a reason of 5 to 500 characters", () => {
    expect(reasonError("")).toMatch(/at least 5/)
    expect(reasonError("  abcd ")).toMatch(/at least 5/)
    expect(reasonError("abcde")).toBeNull()
    expect(reasonError("x".repeat(500))).toBeNull()
    expect(reasonError("x".repeat(501))).toMatch(/too long/)
  })
  it("says a month already has a live draft, but not a void one", () => {
    expect(createBlock("2026-10", [])).toBeNull()
    expect(createBlock("2026-10", [{ month: "2026-10", status: "VOID" }])).toBeNull()
    expect(createBlock("2026-10", [{ month: "2026-09", status: "DRAFT" }])).toBeNull()
    expect(createBlock("2026-10", [{ month: "2026-10", status: "DRAFT" }])).toBe("There is already an invoice draft for this month. Void it to make a new one.")
    expect(createBlock("2026-10", [{ month: "2026-10", status: "APPROVED" }])).toBe("There is already an approved invoice draft for this month. Void it to make a new one.")
  })
})

describe("errors", () => {
  it("prefers the server's message", () => expect(billingErrorText({ response: { status: 409, data: { message: "Already there" } } }, "x")).toBe("Already there"))
  it("explains 403 and 404 without the server's words", () => {
    expect(billingErrorText({ response: { status: 403, data: {} } }, "x")).toMatch(/permission/)
    expect(billingErrorText({ response: { status: 404 } }, "x")).toMatch(/not available/)
  })
  it("falls back", () => { expect(billingErrorText(new Error("n"), "Could not save")).toBe("Could not save"); expect(billingErrorText(null, "F")).toBe("F") })
})
