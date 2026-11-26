// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"

const getMyPayRuns = vi.fn(); const getMyPayRun = vi.fn(); const approvePayRun = vi.fn(); const declinePayRun = vi.fn()
vi.mock("../../api/payrollBureauPortal.api", () => ({
  payrollBureauPortalApi: {
    getMyPayRuns: (...a: unknown[]) => getMyPayRuns(...a), getMyPayRun: (...a: unknown[]) => getMyPayRun(...a),
    approvePayRun: (...a: unknown[]) => approvePayRun(...a), declinePayRun: (...a: unknown[]) => declinePayRun(...a),
  },
}))
import { PortalPayRuns } from "./PortalPayRuns"

const run = (over: object = {}) => ({ id: "r1", payRunNumber: "PR-001", periodStart: "2026-11-01", periodEnd: "2026-11-30", payDate: "2026-11-27",
  status: "AWAITING_CLIENT", totalGross: 50000, totalPaye: 8000, totalUif: 356, totalSdl: 500, totalNet: 40000, employeeCount: 3,
  sentToClientAt: null, clientApprovedAt: null, clientDeclinedAt: null, clientDeclineReason: null, ...over })
const detail = (status = "AWAITING_CLIENT") => ({ run: run({ status }),
  payslips: [{ id: "p1", employeeName: "Ann Dube", employeeNumber: "E1", totalEarnings: 20000, payeAmount: 3000, uifEmployee: 178, totalDeductions: 3178, netPay: 16822 }],
  review: { previousRunNumber: "PR-000", blocking: 0, warnings: 1, findings: [{ severity: "WARN", code: "NET_CHANGE", employeeName: "Ann Dube", message: "Ann's net pay moved by 25%" }] } })

beforeEach(() => { [getMyPayRuns, getMyPayRun, approvePayRun, declinePayRun].forEach(f => f.mockReset()) })
afterEach(cleanup)

describe("client pay runs", () => {
  it("shows a run waiting for approval and its employees and checks", async () => {
    getMyPayRuns.mockResolvedValue([run()]); getMyPayRun.mockResolvedValue(detail())
    render(<PortalPayRuns clientId="c1" />)
    fireEvent.click(await screen.findByText("PR-001"))
    expect(await screen.findByText("Ann Dube")).toBeTruthy()
    expect(screen.getByText(/net pay moved by 25%/)).toBeTruthy()
    expect(screen.getByText("Approve this pay run")).toBeTruthy()
  })

  it("approves", async () => {
    getMyPayRuns.mockResolvedValue([run()]); getMyPayRun.mockResolvedValue(detail()); approvePayRun.mockResolvedValue(run({ status: "CLIENT_APPROVED" }))
    render(<PortalPayRuns clientId="c1" />)
    fireEvent.click(await screen.findByText("PR-001"))
    fireEvent.click(await screen.findByText("Approve this pay run"))
    await waitFor(() => expect(approvePayRun).toHaveBeenCalledWith("c1", "r1"))
  })

  it("needs a reason to decline", async () => {
    getMyPayRuns.mockResolvedValue([run()]); getMyPayRun.mockResolvedValue(detail()); declinePayRun.mockResolvedValue(run({ status: "DRAFT" }))
    render(<PortalPayRuns clientId="c1" />)
    fireEvent.click(await screen.findByText("PR-001"))
    fireEvent.click(await screen.findByText("Decline…"))
    const send = screen.getByText("Send back to the bureau") as HTMLButtonElement
    expect(send.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Overtime is wrong" } })
    expect(send.disabled).toBe(false)
    fireEvent.click(send)
    await waitFor(() => expect(declinePayRun).toHaveBeenCalledWith("c1", "r1", "Overtime is wrong"))
  })

  it("offers no approval for a finalised run", async () => {
    getMyPayRuns.mockResolvedValue([run({ status: "FINALISED" })]); getMyPayRun.mockResolvedValue(detail("FINALISED"))
    render(<PortalPayRuns clientId="c1" />)
    fireEvent.click(await screen.findByText("PR-001"))
    await screen.findByText("Ann Dube")
    expect(screen.queryByText("Approve this pay run")).toBeNull()
  })
})
