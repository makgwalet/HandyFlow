import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EventReportsTab from "./EventReportsTab"
import { closeOutHeadline, failedChecks, overBudget, owedSuppliers, ratePercent, visibleReports } from "./reportOps"

const get = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const co = (o: object = {}): any => ({ eventId: "e1", eventNumber: "EV-1", title: "Summit", status: "COMPLETED", attendance: { registered: 100, checkedIn: 80, notCheckedIn: 20, walkIns: 5, cancelled: 3, transferred: 1, attendanceRatePercent: 80 },
  budget: { budgetStatus: "APPROVED", planned: 100000, committed: 90000, invoiced: 95000, paid: 60000, forecast: 110000, variance: -10000, outstanding: 50000, costPerRegistered: 1100, costPerCheckedIn: 1375 },
  suppliers: [{ vendorId: "v1", vendorName: "Sound Co", commitmentCount: 1, committed: 1, invoiced: 1, paid: 0, outstanding: 20000 }, { vendorId: "v2", vendorName: "Paid Up", commitmentCount: 1, committed: 1, invoiced: 1, paid: 1, outstanding: 0 }],
  checks: [{ code: "A", label: "Checklist done", passed: true, detail: null }, { code: "B", label: "Invoices reconciled", passed: false, detail: "2 unreconciled" }], readyToClose: false, generatedAt: "x", ...o })

describe("reportOps", () => {
  it("lists reports by permission", () => {
    expect(visibleReports(false, false)).toEqual([]); expect(visibleReports(true, false).map(r => r.id)).toEqual(["guests", "no-shows", "check-ins"]); expect(visibleReports(false, true).map(r => r.id)).toContain("close-out")
  })
  it("summarises the close-out", () => {
    expect(failedChecks(co()).length).toBe(1); expect(closeOutHeadline(co())).toBe("1 thing still to sort out before closing."); expect(closeOutHeadline(co({ readyToClose: true }))).toMatch(/in order/)
    expect(overBudget(co())).toBe(true); expect(overBudget(co({ budget: { budgetStatus: "NOT_STARTED", variance: -1 } }))).toBe(false)
    expect(owedSuppliers(co()).map(s => s.vendorName)).toEqual(["Sound Co"]); expect(ratePercent(null)).toBe("–"); expect(ratePercent(80)).toBe("80.0%")
  })
})

describe("reports screen", () => {
  let clicked: string[]
  beforeEach(() => {
    get.mockReset(); perms = []; clicked = []
    get.mockImplementation(async (u: string) => u.includes("/reports/") ? { data: new Blob(["x"]) } : { data: { data: co() } })
    ;(URL as any).createObjectURL = () => "blob:x"; (URL as any).revokeObjectURL = () => {}
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) { clicked.push(this.download) })
  })
  afterEach(() => { cleanup(); vi.restoreAllMocks() })

  it("shows nothing to someone without access", () => { wrap(<EventReportsTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} />); expect(screen.getByText(/do not have access/)).toBeTruthy() })
  it("a PII-only user gets guest CSVs but no money and no close-out call", async () => {
    perms = ["EVENTS_EXPORT"]; wrap(<EventReportsTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} />)
    expect(screen.getByLabelText("Download Guest register")).toBeTruthy(); expect(screen.queryByLabelText("Download Budget vs actual")).toBeNull(); expect(get).not.toHaveBeenCalled()
  })
  it("a finance user sees the close-out, over-budget warning and who is owed", async () => {
    perms = ["EVENTS_BUDGET_READ"]; wrap(<EventReportsTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} />)
    await waitFor(() => screen.getByRole("status")); expect(screen.getByRole("status").textContent).toMatch(/1 thing still/)
    expect(screen.getByText(/Over budget by/)).toBeTruthy(); expect(screen.getByText(/Invoices reconciled/)).toBeTruthy(); expect(screen.getByText(/Sound Co/)).toBeTruthy(); expect(screen.queryByText(/Paid Up/)).toBeNull()
    expect(screen.queryByLabelText("Download Guest register")).toBeNull()
  })
  it("downloads a report", async () => {
    perms = ["EVENTS_MANAGE"]; wrap(<EventReportsTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} />)
    fireEvent.click(screen.getByLabelText("Download Guest register"))
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/events/e1/reports/guests.csv", { responseType: "blob" })); await waitFor(() => expect(clicked).toEqual(["guests-e1.csv"]))
  })
  it("shows the server's reason when a download is refused", async () => {
    perms = ["EVENTS_EXPORT"]; get.mockRejectedValueOnce({ response: { data: new Blob([JSON.stringify({ message: "No-shows are only available once the event is live" })]) } })
    wrap(<EventReportsTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} />)
    fireEvent.click(screen.getByLabelText("Download No-shows")); await waitFor(() => screen.getByText(/only available once the event is live/))
  })
})
