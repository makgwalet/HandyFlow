import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))

import { useAuthStore } from "../../store/auth.store"
vi.mock("./companyZone", async orig => ({ ...(await orig<typeof import("./companyZone")>()), useCompanyZone: () => ({ zone: "Africa/Johannesburg", ready: true }) }))
import WeeklyHoursPanel from "./WeeklyHoursPanel"

const T = { warnAt: 40, limit: 45, ceiling: 55, minRestHours: 12 }
const report = (rows: unknown[], o: Record<string, unknown> = {}) => ({ data: { data: { weekStart: "2026-11-16", guardsRostered: 12, flagged: rows.length, thresholds: T, truncated: false, rows, ...o } } })
const row = (o: Record<string, unknown> = {}) => ({ guardId: "g1", guardName: "Thabo Mokoena", hours: 48, state: "OVER", shifts: 4, overlaps: false, restBreaches: [], ...o })
function show() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><WeeklyHoursPanel /></QueryClientProvider>)
}

describe("WeeklyHoursPanel", () => {
  beforeEach(() => { api.get.mockReset(); api.put.mockReset(); useAuthStore.setState({ user: { permissions: [] } } as never) })
  afterEach(() => cleanup())

  it("lists flagged guards with hours, status and problems", async () => {
    api.get.mockResolvedValue(report([row({ overlaps: true, restBreaches: [{ from: "2026-11-16T16:00:00Z", to: "2026-11-16T20:00:00Z", hours: 4 }] })]))
    show()
    expect(await screen.findByText("Thabo Mokoena")).toBeTruthy()
    expect(screen.getByText("48 h")).toBeTruthy()
    expect(screen.getByText("Over 45 h")).toBeTruthy()
    expect(screen.getByText("Double booked: two shifts overlap")).toBeTruthy()
    expect(screen.getByText(/Only 4 h rest/)).toBeTruthy()
    expect(screen.getByText("1 of 12 rostered guards needs attention.")).toBeTruthy()
    expect(api.get.mock.calls[0][0]).toBe("/api/v1/security/workforce/weekly-hours")
    expect(api.get.mock.calls[0][1].params.flaggedOnly).toBe(true)
  })

  it("says nobody needs attention when nobody is flagged", async () => {
    api.get.mockResolvedValue(report([], { flagged: 0 }))
    show()
    expect(await screen.findByText("None of the 12 rostered guards needs attention.")).toBeTruthy()
    expect(screen.queryByRole("table")).toBeNull()
  })

  it("shows an error, not a clean result, when loading fails", async () => {
    api.get.mockImplementation(() => Promise.reject(new Error("x")))
    show()
    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy())
    expect(screen.queryByText(/needs attention|need attention/)).toBeNull()
  })

  it("treats a malformed answer as an error", async () => {
    api.get.mockResolvedValue({ data: { data: { weekStart: "x" } } })
    show()
    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy())
  })

  it("going back a week asks for the earlier Monday; next is off on this week", async () => {
    api.get.mockResolvedValue(report([]))
    show()
    await screen.findByText(/None of the 12/)
    expect((screen.getByLabelText("Next week") as HTMLButtonElement).disabled).toBe(true)
    const first = api.get.mock.calls[0][1].params.week as string
    fireEvent.click(screen.getByLabelText("Previous week"))
    await waitFor(() => expect(api.get.mock.calls.length).toBe(2))
    const second = api.get.mock.calls[1][1].params.week as string
    expect(new Date(first).getTime() - new Date(second).getTime()).toBe(7 * 86400000)
    expect(new Date(first).getUTCDay()).toBe(1) // a Monday
    expect((screen.getByLabelText("Next week") as HTMLButtonElement).disabled).toBe(false)
  })

  it("'show all' asks for every rostered guard", async () => {
    api.get.mockResolvedValue(report([]))
    show()
    await screen.findByText(/None of the 12/)
    fireEvent.click(screen.getByLabelText("Show all rostered guards"))
    await waitFor(() => expect(api.get.mock.calls.at(-1)![1].params.flaggedOnly).toBe(false))
  })

  it("states the limits in use, from the server", async () => {
    api.get.mockResolvedValue(report([], { thresholds: { warnAt: 44, limit: 48, ceiling: 58, minRestHours: 10 } }))
    show()
    expect(await screen.findByText(/48 h ordinary limit, 58 h ceiling, 10 h rest/)).toBeTruthy()
  })

  it("names a guard the server could not name", async () => {
    api.get.mockResolvedValue(report([row({ guardName: null })]))
    show()
    expect(await screen.findByText("Unknown guard")).toBeTruthy()
  })

  const serveManager = (rows: unknown[], policies: unknown[] = [{ category: "DEFAULT", effectiveFrom: "2020-01-01", ordinaryWeekly: 45, maxOvertimeWeekly: 10, maxDaily: 12, minRestHours: 12 }, { category: "NIGHT", effectiveFrom: "2020-01-01", ordinaryWeekly: 48, maxOvertimeWeekly: 12, maxDaily: 12, minRestHours: 12 }]) => {
    useAuthStore.setState({ user: { permissions: ["SECURITY_MANAGE"] } } as never)
    api.get.mockImplementation((url: string) => url.includes("working-time-policies") ? Promise.resolve({ data: { data: policies } }) : Promise.resolve(report(rows)))
  }

  it("shows each guard's category and judges them by their own limit", async () => {
    api.get.mockResolvedValue(report([row({ category: "NIGHT", limit: 48, ceiling: 60, hours: 50 })]))
    show()
    expect(await screen.findByText("Over 48 h")).toBeTruthy()
    expect(screen.getByText("NIGHT")).toBeTruthy()
  })

  it("lists a day over the daily limit as a problem", async () => {
    api.get.mockResolvedValue(report([row({ state: "OK", hours: 30, dayBreaches: [{ day: "2026-11-17", hours: 14 }] })]))
    show()
    expect(await screen.findByText(/14 h rostered on Tue/)).toBeTruthy()
  })

  it("lets a manager change a guard's category and refreshes the list", async () => {
    serveManager([row({ category: "DEFAULT" })])
    api.put.mockResolvedValue({ data: {} })
    show()
    const sel = await screen.findByLabelText("Category for Thabo Mokoena") as HTMLSelectElement
    expect(sel.value).toBe("DEFAULT")
    await waitFor(() => expect(Array.from(sel.options).map(o => o.value)).toEqual(["DEFAULT", "NIGHT"]))
    fireEvent.change(sel, { target: { value: "NIGHT" } })
    await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1))
    expect(api.put.mock.calls[0][0]).toBe("/api/v1/security/guards/g1/working-time-category")
    expect(api.put.mock.calls[0][1]).toEqual({ category: "NIGHT" })
    await waitFor(() => expect(api.get.mock.calls.filter(c => String(c[0]).includes("weekly-hours")).length).toBeGreaterThan(1))
  })

  it("shows the server's reason when the category is refused", async () => {
    serveManager([row()])
    api.put.mockImplementation(() => Promise.reject({ response: { data: { message: "No policy exists for that category yet. Add one first" } } }))
    show()
    const sel = await screen.findByLabelText("Category for Thabo Mokoena") as HTMLSelectElement
    await waitFor(() => expect(sel.options.length).toBe(2))
    fireEvent.change(sel, { target: { value: "NIGHT" } })
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("Add one first"))
  })

  it("gives people without manage permission no way to change a category", async () => {
    api.get.mockResolvedValue(report([row()]))
    show()
    await screen.findByText("Thabo Mokoena")
    expect(screen.queryByLabelText(/Category for/)).toBeNull()
    expect(api.get.mock.calls.some(c => String(c[0]).includes("working-time-policies"))).toBe(false)
  })
})
