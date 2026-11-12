import { describe, it, expect } from "vitest"
import { validateEnquiry, validateClose, canQuote, who, type Enquiry } from "./enquiryOps"

const enq = (o: Partial<Enquiry> = {}): Enquiry => ({ id: "e1", enquiryNumber: "EQ-2026-00001", accountId: "a1", accountName: "Acme Mining", contactName: null, contactPhone: null, contactEmail: null,
  fuelType: "DIESEL", litres: 5000, requestedDate: null, source: "PHONE", status: "NEW", quoteOrderId: null, quoteOrderNumber: null, decisionReason: null, notes: null, createdAt: "2026-11-12T08:00:00Z", decidedAt: null, ...o })

describe("enquiryOps", () => {
  it("needs a customer or a name, a fuel and litres above zero", () => {
    expect(validateEnquiry("", " ", "DIESEL", "100")).toMatch(/account|who/)
    expect(validateEnquiry("a1", "", "", "100")).toMatch(/fuel/)
    expect(validateEnquiry("a1", "", "DIESEL", "0")).toMatch(/litres/)
    expect(validateEnquiry("a1", "", "DIESEL", "")).toMatch(/litres/)
    expect(validateEnquiry("", "Walk-in farmer", "DIESEL", "100")).toBeNull()
  })
  it("a reason must be at least five characters", () => {
    expect(validateClose("no")).toMatch(/5/)
    expect(validateClose("Price too high")).toBeNull()
  })
  it("only a new enquiry with an account can be quoted", () => {
    expect(canQuote(enq())).toBe(true)
    expect(canQuote(enq({ accountId: null }))).toBe(false)
    expect(canQuote(enq({ status: "QUOTED" }))).toBe(false)
  })
  it("names the customer, else the contact", () => {
    expect(who(enq())).toBe("Acme Mining")
    expect(who(enq({ accountName: null, contactName: "Walk-in farmer" }))).toBe("Walk-in farmer")
  })
})
