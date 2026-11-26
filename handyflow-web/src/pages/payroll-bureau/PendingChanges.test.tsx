// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"

const getEmployeeChanges = vi.fn()
const decideEmployeeChange = vi.fn()
vi.mock("../../api/payrollBureau.api", () => ({
  payrollBureauApi: {
    getEmployeeChanges: (...a: unknown[]) => getEmployeeChanges(...a),
    decideEmployeeChange: (...a: unknown[]) => decideEmployeeChange(...a),
  },
}))
import { PendingChanges } from "./PayrollBureauPage"

const change = (over: object = {}) => ({
  id: "c1", payEmployeeId: "e1", employeeName: "Thandi Nkosi", status: "PENDING",
  prevGrossSalary: 20000, newGrossSalary: 25000, prevTravelAllowance: 0, newTravelAllowance: 0,
  prevPension: 0, newPension: 0, prevMedicalAid: 0, newMedicalAid: 0,
  prevBankName: "FNB", newBankName: "FNB", prevBankAccount: "123", newBankAccount: "999",
  prevBankBranch: "250655", newBankBranch: "250655",
  requestedBy: "u1", requestedAt: "2026-11-26T10:00:00Z", decidedBy: null, decidedAt: null, decisionNote: null, ...over,
})

beforeEach(() => { getEmployeeChanges.mockReset(); decideEmployeeChange.mockReset() })
afterEach(cleanup)

describe("pending payroll changes", () => {
  it("lists only the fields that changed, and says payroll keeps the old details", async () => {
    getEmployeeChanges.mockResolvedValue([change()])
    render(<PendingChanges clientId="cl1" refreshKey={0} onChanged={() => {}} />)
    expect(await screen.findByText("Thandi Nkosi")).toBeTruthy()
    expect(screen.getByText(/Account number: 123/)).toBeTruthy()
    expect(screen.getByText(/Gross salary:/)).toBeTruthy()
    expect(screen.queryByText(/Branch code/)).toBeNull()
    expect(screen.getByText(/Payroll keeps using the old details/)).toBeTruthy()
  })

  it("shows nothing when no change is waiting", async () => {
    getEmployeeChanges.mockResolvedValue([change({ status: "APPROVED" })])
    const { container } = render(<PendingChanges clientId="cl1" refreshKey={0} onChanged={() => {}} />)
    await waitFor(() => expect(getEmployeeChanges).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })

  it("approves and tells the page to reload", async () => {
    getEmployeeChanges.mockResolvedValue([change()])
    decideEmployeeChange.mockResolvedValue(change({ status: "APPROVED" }))
    const onChanged = vi.fn()
    render(<PendingChanges clientId="cl1" refreshKey={0} onChanged={onChanged} />)
    fireEvent.click(await screen.findByText("Approve"))
    await waitFor(() => expect(onChanged).toHaveBeenCalled())
    expect(decideEmployeeChange).toHaveBeenCalledWith("c1", "approve")
  })

  it("shows the server's reason when the same user tries to approve", async () => {
    getEmployeeChanges.mockResolvedValue([change()])
    decideEmployeeChange.mockRejectedValue({ response: { data: { message: "A different user must approve or reject this change." } } })
    render(<PendingChanges clientId="cl1" refreshKey={0} onChanged={() => {}} />)
    fireEvent.click(await screen.findByText("Approve"))
    expect((await screen.findByRole("alert")).textContent).toContain("A different user must approve")
  })
})
