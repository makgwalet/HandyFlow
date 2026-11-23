import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, render, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))

import { useAuthStore } from "../../store/auth.store"
import { setCompanyZone } from "./companyZone"
import WeeklyHoursPanel from "./WeeklyHoursPanel"
import OccurrenceBookCard from "./OccurrenceBookCard"
import MonthlyPackCard from "./MonthlyPackCard"
import WorkingTimePolicyPanel from "./WorkingTimePolicyPanel"

/** The real zone hook, not a mock: a company in Auckland, where the date is ahead of South Africa's. */
const AUCKLAND = { timeZone: "Pacific/Auckland", abbreviation: "GMT+13", now: "x", today: "2026-12-01" }
let release: (v: unknown) => void = () => {}

function serve(zone: unknown) {
  api.get.mockImplementation((url: string) => {
    if (url.endsWith("/company-time-zone")) return new Promise(res => { release = () => res({ data: { data: zone } }) })
    if (url.includes("/working-time-policies")) return Promise.resolve({ data: { data: [] } })
    return Promise.resolve({ data: { data: {} } })
  })
}
const dataCalls = (frag: string) => api.get.mock.calls.filter(c => String(c[0]).includes(frag))
function show(node: React.ReactElement) {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{node}</QueryClientProvider>)
}

describe("pages follow the company zone", () => {
  beforeEach(() => {
    api.get.mockReset(); setCompanyZone(undefined)
    useAuthStore.setState({ user: { permissions: ["SECURITY_MANAGE"] } } as never)
    vi.useFakeTimers({ toFake: ["Date"] })
    vi.setSystemTime(new Date("2026-11-29T21:30:00Z")) // Sunday 23:30 in South Africa, Monday 10:30 in Auckland
  })
  afterEach(() => { vi.useRealTimers(); cleanup() })

  it("weekly hours waits for the zone, then asks for the company's week", async () => {
    serve(AUCKLAND); show(<WeeklyHoursPanel />)
    await waitFor(() => expect(dataCalls("/company-time-zone").length).toBe(1))
    expect(dataCalls("weekly-hours")).toHaveLength(0)
    release(null)
    await waitFor(() => expect(dataCalls("weekly-hours").length).toBeGreaterThan(0))
    expect(dataCalls("weekly-hours")[0][1].params.week).toBe("2026-11-30")
    expect(dataCalls("weekly-hours")).toHaveLength(1)
  })

  it("the occurrence book waits, then asks for the company's today", async () => {
    serve(AUCKLAND); show(<OccurrenceBookCard siteId="s1" />)
    await waitFor(() => expect(dataCalls("/company-time-zone").length).toBe(1))
    expect(dataCalls("occurrence-book")).toHaveLength(0)
    release(null)
    await waitFor(() => expect(dataCalls("occurrence-book").length).toBeGreaterThan(0))
    expect(dataCalls("occurrence-book")[0][1].params.date).toBe("2026-11-30")
  })

  it("the monthly pack asks for last month in the company's zone", async () => {
    vi.setSystemTime(new Date("2026-11-30T21:30:00Z")) // still November in South Africa, already 1 December in Auckland
    serve(AUCKLAND); show(<MonthlyPackCard siteId="s1" />)
    await waitFor(() => expect(dataCalls("/company-time-zone").length).toBe(1))
    expect(dataCalls("monthly-pack")).toHaveLength(0)
    release(null)
    await waitFor(() => expect(dataCalls("monthly-pack").length).toBeGreaterThan(0))
    expect(dataCalls("monthly-pack")[0][1].params.month).toBe("2026-11")
  })

  it("a South African company still gets South Africa's day", async () => {
    serve({ ...AUCKLAND, timeZone: "Africa/Johannesburg", abbreviation: "SAST" }); show(<OccurrenceBookCard siteId="s1" />)
    await waitFor(() => expect(dataCalls("/company-time-zone").length).toBe(1))
    release(null)
    await waitFor(() => expect(dataCalls("occurrence-book").length).toBeGreaterThan(0))
    expect(dataCalls("occurrence-book")[0][1].params.date).toBe("2026-11-29")
  })

  it("if the zone cannot be loaded the pages still work, on South Africa's day", async () => {
    api.get.mockImplementation((url: string) => url.endsWith("/company-time-zone") ? Promise.reject(new Error("x")) : Promise.resolve({ data: { data: {} } }))
    show(<OccurrenceBookCard siteId="s1" />)
    await waitFor(() => expect(dataCalls("occurrence-book").length).toBeGreaterThan(0))
    expect(dataCalls("occurrence-book")[0][1].params.date).toBe("2026-11-29")
  })

  it("an unusable zone from the server is ignored", async () => {
    serve({ ...AUCKLAND, timeZone: "Mars/Olympus" }); show(<OccurrenceBookCard siteId="s1" />)
    await waitFor(() => expect(dataCalls("/company-time-zone").length).toBe(1))
    release(null)
    await waitFor(() => expect(dataCalls("occurrence-book").length).toBeGreaterThan(0))
    expect(dataCalls("occurrence-book")[0][1].params.date).toBe("2026-11-29")
  })

  it("the policy form's earliest start date is the company's today", async () => {
    serve(AUCKLAND); const r = show(<WorkingTimePolicyPanel />)
    await waitFor(() => expect(dataCalls("/company-time-zone").length).toBe(1))
    release(null)
    const btn = await r.findByRole("button", { name: "Add policy" })
    await waitFor(() => expect(dataCalls("/company-time-zone").length).toBe(1))
    await new Promise(res => setTimeout(res, 20))
    btn.click()
    const date = (await r.findByLabelText("Starts on")) as HTMLInputElement
    await waitFor(() => expect(date.min).toBe("2026-11-30"))
  })
})
