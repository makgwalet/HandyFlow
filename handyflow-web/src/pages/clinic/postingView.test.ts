import { describe, it, expect } from "vitest"
import { bannerSummary, needsBankChoice, postBody, postingTitle, type Posting } from "./postingView"

const pay: Posting = { id: "1", claimId: "c", patientName: "Thandi", type: "SCHEME_PAYMENT_RECEIVED", amount: 500, occurredOn: "2026-10-13", status: "PENDING", needsBank: true }
const off: Posting = { id: "2", claimId: "c", type: "CLAIM_WRITTEN_OFF", amount: 80, occurredOn: "2026-10-13", status: "FAILED", reason: "No bad debt account", needsBank: false }

describe("postingView", () => {
  it("says nothing when nothing waits", () => expect(bannerSummary([])).toBe(""))
  it("counts payments needing a bank and failures", () => {
    const s = bannerSummary([pay, off])
    expect(s).toContain("2 items are not in the accounts yet")
    expect(s).toContain("1 scheme payment needs a bank account")
    expect(s).toContain("1 could not be posted")
  })
  it("titles with kind, amount and patient", () => {
    expect(postingTitle(pay)).toContain("Scheme payment")
    expect(postingTitle(pay)).toContain("Thandi")
    expect(postingTitle(off)).toContain("Write-off")
  })
  it("asks for a bank only when a scheme payment is included", () => {
    expect(needsBankChoice([off])).toBe(false)
    expect(postBody([pay], "").error).toMatch(/bank account/)
    expect(postBody([pay], "b1").body).toEqual({ bankAccountId: "b1", postingIds: ["1"] })
    expect(postBody([off], "b1").body).toEqual({ postingIds: ["2"] })
  })
})
