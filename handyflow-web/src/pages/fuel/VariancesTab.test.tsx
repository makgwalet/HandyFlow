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
    expect(screen.getAllByText(/Dan/).length).toBeGreaterThan(0)
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

  it("adds up what is short and over, and shows where variances cluster by driver, repeats marked", async () => {
    get.mockResolvedValue({ data: { data: { toleranceSet: true, variances: [v(), v({ id: "v2", receiptNumber: "DR-002", varianceLitres: -300 }), v({ id: "v3", receiptNumber: "DR-003", driverName: "Eve", vehicleReg: "CB 9", varianceLitres: 50 }), v({ id: "v4", receiptNumber: "DR-004", status: "CLOSED", decision: "WRITTEN_OFF", varianceLitres: -80 })] } } })
    mount()
    const totals = await screen.findByLabelText("Variance totals")
    const flat = (totals.textContent ?? "").replace(/\s/g, " ")
    expect(flat).toContain("Short, not closed500 L")
    expect(flat).toContain("3 (3 open")
    const rows = screen.getAllByRole("row").map(r => r.textContent)
    expect(rows[1]).toContain("Dan"); expect(rows[1]).toContain("Repeats")
    expect(rows[2]).toContain("Eve"); expect(rows[2]).not.toContain("Repeats")
    fireEvent.change(screen.getByLabelText("Group by"), { target: { value: "vehicle" } })
    expect(screen.getAllByRole("row").map(r => r.textContent).some(t => t?.includes("CB 9"))).toBe(true)
  })

  it("filters by status and search", async () => {
    get.mockResolvedValue({ data: { data: { toleranceSet: true, variances: [v(), v({ id: "v4", receiptNumber: "DR-004", status: "CLOSED", decision: "EXPLAINED" })] } } })
    mount()
    await screen.findByLabelText("Variance totals")
    fireEvent.change(screen.getByLabelText("Status"), { target: { value: "CLOSED" } })
    expect(screen.queryByLabelText("Close DR-001")).toBeNull()
    fireEvent.change(screen.getByLabelText("Search variances"), { target: { value: "zzz" } })
    expect(screen.getByText("No variances match.")).toBeTruthy()
  })

  it("opens the load's timeline from a variance", async () => {
    mount()
    fireEvent.click(await screen.findByLabelText("Timeline for DR-001"))
    await waitFor(() => expect(get.mock.calls.some(c => c[0] === "/api/v1/fuel/deliveries/d1/execution")).toBe(true))
  })
})
