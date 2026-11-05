import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup } from "@testing-library/react"

vi.mock("../../api/auditTrail.api", async (orig) => {
  const real: any = await orig()
  return { ...real, auditTrailApi: { soleApprover: vi.fn() } }
})
import { auditTrailApi } from "../../api/auditTrail.api"
import { SoleApproverReportPage, stepLabel, toCsv } from "./SoleApproverReportPage"

const m = auditTrailApi as any
const row = (over: any = {}): any => ({
  id: "e1", actorType: "STAFF", actorId: "u1", actorName: "Thabo Nkosi", action: "SOLE_APPROVER_SELF_APPROVAL",
  itemType: "JOURNAL", itemId: "j1", at: "2026-11-05T09:30:00Z",
  afterJson: JSON.stringify({ permission: "ACCOUNTING_ADMIN", detail: "journal=JE-0012" }), ...over,
})

afterEach(cleanup)
beforeEach(() => { vi.clearAllMocks() })

describe("Sole-approver report", () => {
  it("lists who did what, in plain words", async () => {
    m.soleApprover.mockResolvedValue([row(), row({ id: "e2", itemType: "AP_APPROVE_OWN_BILL", actorName: "Lerato M", afterJson: JSON.stringify({ permission: "AP_APPROVE", detail: "bill=B-0007" }) })])
    render(<SoleApproverReportPage />)
    expect(await screen.findByText("Thabo Nkosi")).toBeTruthy()
    expect(screen.getByText("Journal posted by its maker")).toBeTruthy()
    expect(screen.getByText("Supplier bill approved by whoever captured it")).toBeTruthy()
    expect(screen.getByText("journal=JE-0012")).toBeTruthy()
    expect(screen.getByText("Download CSV")).toBeTruthy()
  })

  it("says so when nothing was done without a second person", async () => {
    m.soleApprover.mockResolvedValue([])
    render(<SoleApproverReportPage />)
    expect(await screen.findByText("No step has been done without a second person.")).toBeTruthy()
  })

  it("explains a refusal instead of showing an empty list", async () => {
    m.soleApprover.mockRejectedValue({ response: { status: 403 } })
    render(<SoleApproverReportPage />)
    expect((await screen.findByRole("alert")).textContent).toContain("audit permission")
  })

  it("labels unknown step types readably and builds a CSV with a header and quoted cells", () => {
    expect(stepLabel("SOMETHING_NEW")).toBe("something new")
    const csv = toCsv([row({ afterJson: JSON.stringify({ permission: "P", detail: 'has "quotes"' }) })])
    const [head, line] = csv.split("\r\n")
    expect(head).toContain('"When","Who","Step"')
    expect(line).toContain('"has ""quotes"""')
  })
})
