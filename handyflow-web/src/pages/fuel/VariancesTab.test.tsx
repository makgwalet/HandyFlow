import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import VariancesTab from "./VariancesTab"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const v = (o: any = {}) => ({ id: "v1", deliveryId: "d1", receiptNumber: "DR-001", driverName: "Dan", vehicleReg: "CA 123", loadedLitres: 10000, deliveredLitres: 9800, varianceLitres: -200, variancePct: 2, tolerancePct: 0.5, status: "OPEN", detectedAt: "2026-11-11T10:00:00Z", ...o })
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><VariancesTab /></QueryClientProvider>)
}

describe("VariancesTab", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); perms = ["FUEL_VARIANCE_REVIEW"]; post.mockResolvedValue({ data: {} }); get.mockResolvedValue({ data: { data: { toleranceSet: true, variances: [v()] } } }) })
  afterEach(cleanup)

  it("says plainly when no tolerance is set, so nothing is raised", async () => {
    get.mockResolvedValue({ data: { data: { toleranceSet: false, variances: [] } } })
    mount()
    expect((await screen.findByRole("status")).textContent).toMatch(/no variances are being raised/)
  })
  it("shows the shortfall, the tolerance and the driver", async () => {
    mount()
    expect(await screen.findByText(/200 L short/)).toBeTruthy()
    expect(screen.getByText(/tolerance 0.5%/)).toBeTruthy()
    expect(screen.getByText(/Dan/)).toBeTruthy()
  })
  it("needs a note to close, and sends the decision with it", async () => {
    mount()
    fireEvent.click(await screen.findByLabelText("Close DR-001"))
    fireEvent.click(screen.getAllByText("Close variance").find(b => b.tagName === "BUTTON")!)
    expect((await screen.findByRole("alert")).textContent).toMatch(/5 characters/)
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Decision"), { target: { value: "WRITTEN_OFF" } })
    fireEvent.change(screen.getByLabelText("Note *"), { target: { value: "Spillage at the depot" } })
    fireEvent.click(screen.getAllByText("Close variance").find(b => b.tagName === "BUTTON")!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/variances/v1/close", { decision: "WRITTEN_OFF", note: "Spillage at the depot" }))
  })
  it("investigates an open variance", async () => {
    mount()
    fireEvent.click(await screen.findByLabelText("Investigate DR-001"))
    fireEvent.change(screen.getByLabelText("Note *"), { target: { value: "Checking the meter" } })
    fireEvent.click(screen.getByText("Start investigating"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/variances/v1/investigate", { note: "Checking the meter" }))
  })
  it("shows no actions without the review right, and none on a closed variance", async () => {
    get.mockResolvedValue({ data: { data: { toleranceSet: true, variances: [v({ status: "CLOSED", decision: "EXPLAINED", note: "Meter drift" })] } } })
    mount()
    await screen.findByText(/Closed: Explained/)
    expect(screen.queryByLabelText("Close DR-001")).toBeNull()
    cleanup(); perms = []; get.mockResolvedValue({ data: { data: { toleranceSet: true, variances: [v()] } } })
    mount()
    await screen.findByText(/200 L short/)
    expect(screen.queryByLabelText("Close DR-001")).toBeNull()
  })
})
