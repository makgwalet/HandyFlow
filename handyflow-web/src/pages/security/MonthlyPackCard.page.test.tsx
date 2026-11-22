import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))

import MonthlyPackCard from "./MonthlyPackCard"

const pack = (o: Record<string, unknown> = {}) => ({ data: { data: {
  siteId: "s1", siteName: "Centurion Mall", month: "2026-10", from: "2026-10-01", to: "2026-10-31", inProgress: false, contractStatus: "ACTIVE",
  cover: { posts: 1, measured: true, postDays: 4, requiredGuardHours: 48, coveredGuardHours: 47, coveragePct: 97.9, postDaysShort: 1, postDaysUncovered: 0 },
  shifts: { COMPLETED: 20, MISSED: 2 },
  patrols: { rounds: 3, complete: 1, partial: 1, missed: 1, checkpointsExpected: 15, checkpointsScanned: 8, checkpointPct: 53.3 },
  incidents: { total: 3, resolved: 1, bySeverity: { HIGH: 2, LOW: 1 } },
  visitors: { total: 3, withVehicle: 1, byType: { VISITOR: 2, CONTRACTOR: 1 } },
  alarms: 1, occurrenceBook: { daysInPeriod: 31, daysSigned: 28 }, ...o } } })

function show() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MonthlyPackCard siteId="s1" /></QueryClientProvider>)
}

describe("MonthlyPackCard", () => {
  beforeEach(() => { api.get.mockReset() })
  afterEach(() => { cleanup(); delete document.body.dataset.printing; vi.restoreAllMocks() })

  it("shows last month's report with every figure, and asks the server for that month", async () => {
    api.get.mockResolvedValue(pack())
    show()
    expect((await screen.findAllByText(/47 of 48 required guard-hours covered \(97.9%\)/)).length).toBe(2) // screen + print copy
    expect(screen.getAllByText(/1 of 3 rounds complete/).length).toBe(2)
    expect(screen.getAllByText(/3 incidents \(2 High, 1 Low\), 1 resolved/).length).toBe(2)
    expect(screen.getAllByText(/Occurrence Book signed off on 28 of 31 days/).length).toBe(2)
    expect(api.get.mock.calls[0][0]).toBe("/api/v1/security/sites/s1/monthly-pack")
    expect(api.get.mock.calls[0][1].params.month).toMatch(/^\d{4}-\d{2}$/)
  })

  it("never claims cover when the site has no posts", async () => {
    api.get.mockResolvedValue(pack({ cover: { posts: 0, measured: false, postDays: 0, requiredGuardHours: 0, coveredGuardHours: 0, coveragePct: null, postDaysShort: 0, postDaysUncovered: 0 } }))
    show()
    expect((await screen.findAllByText(/cover is not measured/)).length).toBe(2)
    expect(screen.queryByText(/100/)).toBeNull()
  })

  it("marks a month in progress as month to date", async () => {
    api.get.mockResolvedValue(pack({ inProgress: true }))
    show()
    expect(await screen.findByText("Month to date")).toBeTruthy()
  })

  it("shows an error, not an empty report, when loading fails", async () => {
    api.get.mockImplementation(() => Promise.reject(new Error("x")))
    show()
    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy())
    expect(screen.queryByTestId("mp-print")).toBeNull()
    expect((screen.getByLabelText("Print this report") as HTMLButtonElement).disabled).toBe(true)
  })

  it("treats a malformed answer as an error", async () => {
    api.get.mockResolvedValue({ data: { data: { site: {} } } })
    show()
    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy())
  })

  it("going back a month asks for the earlier month; next is off on the latest", async () => {
    api.get.mockResolvedValue(pack())
    show()
    await screen.findAllByText(/required guard-hours/)
    const first = api.get.mock.calls[0][1].params.month as string
    fireEvent.click(screen.getByLabelText("Previous month"))
    await waitFor(() => expect(api.get.mock.calls.length).toBe(2))
    expect((api.get.mock.calls[1][1].params.month as string) < first).toBe(true)
    expect((screen.getByLabelText("Next month") as HTMLButtonElement).disabled).toBe(false)
  })

  it("printing marks this card, so the Occurrence Book copy on the same page stays off paper", async () => {
    api.get.mockResolvedValue(pack())
    let during: string | undefined
    vi.spyOn(window, "print").mockImplementation(() => { during = document.body.dataset.printing })
    show()
    await screen.findAllByText(/required guard-hours/)
    fireEvent.click(screen.getByLabelText("Print this report"))
    expect(during).toBe("mp")
  })

  it("the print copy carries the title, period and every row, and no personal detail", async () => {
    api.get.mockResolvedValue(pack())
    show()
    await screen.findAllByText(/required guard-hours/)
    const t = screen.getByTestId("mp-print").textContent ?? ""
    expect(t).toContain("Monthly service report — Centurion Mall — October 2026")
    expect(t).toContain("Period 2026-10-01 to 2026-10-31")
    for (const k of ["Cover", "Shifts", "Patrols", "Incidents", "Visitors", "Alarms", "Occurrence Book"]) expect(t).toContain(k)
    expect(t).toContain("20 completed, 2 missed.")
    expect(t).toMatch(/Printed .*SAST/)
  })
})
