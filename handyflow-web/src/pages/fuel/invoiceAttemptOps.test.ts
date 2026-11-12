import { describe, it, expect } from "vitest"
import { attemptAdvice, attemptHeadline, validateReleaseNote } from "./invoiceAttemptOps"

describe("invoiceAttemptOps", () => {
  it("headline and advice differ by state, and warn against re-invoicing a linked-failure", () => {
    expect(attemptHeadline({ status: "LINK_FAILED", invoiceNumber: "INV-1", deliveryCount: 2 })).toMatch(/INV-1 was created for 2 deliveries/)
    expect(attemptHeadline({ status: "STARTED", invoiceNumber: null, deliveryCount: 1 })).toMatch(/1 delivery never finished/)
    expect(attemptAdvice({ status: "LINK_FAILED" })).toMatch(/Do not invoice these again/)
    expect(attemptAdvice({ status: "STARTED" })).toMatch(/Check Invoicing/)
  })
  it("release note must be 5 to 300 characters", () => {
    expect(validateReleaseNote("  ab ")).toMatch(/5 to 300/)
    expect(validateReleaseNote("x".repeat(301))).toMatch(/5 to 300/)
    expect(validateReleaseNote("Checked Invoicing")).toBeNull()
  })
})
