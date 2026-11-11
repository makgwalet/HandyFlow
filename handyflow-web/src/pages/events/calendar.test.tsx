import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EventsCalendarTab from "./EventsCalendarTab"
import { byDay, daysCovered, monthCount, monthGrid, shiftMonth } from "./calendarOps"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const ev = (o: object = {}): any => ({ id: "e1", title: "Summit", status: "PUBLISHED", venueName: "Hall", startDatetime: "2026-12-03T09:00:00", endDatetime: "2026-12-03T17:00:00", ...o })

describe("calendarOps", () => {
  it("builds six Monday-first weeks", () => {
    const g = monthGrid(2026, 11, "2026-12-03")        // 1 Dec 2026 is a Tuesday
    expect(g).toHaveLength(42); expect(g[0].date).toBe("2026-11-30"); expect(g[0].inMonth).toBe(false); expect(g[1].date).toBe("2026-12-01"); expect(g[1].inMonth).toBe(true)
    expect(g.find(c => c.isToday)?.date).toBe("2026-12-03")
  })
  it("moves across year ends", () => { expect(shiftMonth(2026, 11, 1)).toEqual({ year: 2027, month: 0 }); expect(shiftMonth(2026, 0, -1)).toEqual({ year: 2025, month: 11 }) })
  it("covers every day of a multi-day event, and one day when the end is wrong", () => {
    expect(daysCovered(ev({ endDatetime: "2026-12-05T10:00:00" }))).toEqual(["2026-12-03", "2026-12-04", "2026-12-05"])
    expect(daysCovered(ev({ endDatetime: "2026-12-01T10:00:00" }))).toEqual(["2026-12-03"]); expect(daysCovered(ev({ endDatetime: null }))).toEqual(["2026-12-03"])
    expect(daysCovered(ev({ startDatetime: "2026-12-30T20:00:00", endDatetime: "2027-01-02T01:00:00" }))).toEqual(["2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02"])
  })
  it("sorts a day by start time and can hide cancelled", () => {
    const m = byDay([ev({ id: "b", startDatetime: "2026-12-03T14:00:00" }), ev({ id: "a" }), ev({ id: "c", status: "CANCELLED" })], true)
    expect(m["2026-12-03"].map(e => e.id)).toEqual(["a", "b"]); expect(byDay([ev({ status: "CANCELLED" })], false)["2026-12-03"]).toHaveLength(1)
    expect(monthCount(byDay([ev({ endDatetime: "2026-12-05T10:00:00" })], false), 2026, 11)).toBe(1)
  })
})

describe("calendar screen", () => {
  beforeEach(() => { get.mockReset(); vi.useFakeTimers({ toFake: ["Date"] }); vi.setSystemTime(new Date("2026-12-03T10:00:00")); get.mockResolvedValue({ data: { data: { content: [ev(), ev({ id: "e2", title: "Gala", startDatetime: "2026-12-10T18:00:00", endDatetime: "2026-12-10T22:00:00" }), ev({ id: "e3", title: "Old", status: "CANCELLED", startDatetime: "2026-12-10T08:00:00", endDatetime: null })], totalElements: 3 } } }) })
  afterEach(() => { cleanup(); vi.useRealTimers() })
  const wrap = (open = vi.fn()) => { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><EventsCalendarTab onSelectEvent={open} /></QueryClientProvider>); return open }

  it("places events on their days and lists today's", async () => {
    wrap(); await waitFor(() => screen.getByLabelText("2026-12-03"))
    await waitFor(() => expect(within(screen.getByLabelText("2026-12-03")).getByText("Summit")).toBeTruthy())
    expect(within(screen.getByLabelText("2026-12-10")).getByText("Gala")).toBeTruthy(); expect(within(screen.getByLabelText("2026-12-10")).queryByText("Old")).toBeNull()
    expect(within(screen.getByRole("region", { name: "Selected day" })).getByText(/09:00/)).toBeTruthy()
  })
  it("shows cancelled events when asked and opens one", async () => {
    const open = wrap(); await waitFor(() => screen.getByText("Gala")); fireEvent.click(screen.getByLabelText("Hide cancelled"))
    await waitFor(() => within(screen.getByLabelText("2026-12-10")).getByText("Old")); fireEvent.click(within(screen.getByLabelText("2026-12-10")).getByText("Gala"))
    expect(open).toHaveBeenCalledWith("e2", "Gala")
  })
  it("navigates months", async () => {
    wrap(); await waitFor(() => screen.getByText("December 2026")); fireEvent.click(screen.getByLabelText("Next month")); expect(screen.getByText("January 2027")).toBeTruthy(); fireEvent.click(screen.getByText("Today")); expect(screen.getByText("December 2026")).toBeTruthy()
  })
})
