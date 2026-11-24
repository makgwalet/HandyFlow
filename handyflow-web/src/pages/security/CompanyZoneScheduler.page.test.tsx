// @vitest-environment jsdom
import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, render, waitFor } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true,
  usePermissions: (ps: string[], mode: 'any' | 'all' = 'any') => { const f = (() => true) as (p: string) => boolean; return mode === 'all' ? ps.every(f) : ps.some(f) } }))
import { setCompanyZone } from "./companyZone"
import SchedulerTab from "./SchedulerTab"

let release: () => void = () => {}
const rangeCalls = () => api.get.mock.calls.filter(c => String(c[0]).includes("/shifts/range"))

describe("SchedulerTab follows the company zone", () => {
  beforeEach(() => {
    api.get.mockReset(); setCompanyZone(undefined)
    vi.useFakeTimers({ toFake: ["Date"] })
    vi.setSystemTime(new Date("2026-11-29T21:30:00Z")) // Sunday 23:30 in South Africa, Monday 10:30 in Auckland
  })
  afterEach(() => { vi.useRealTimers(); cleanup() })

  function serve(zone: unknown) {
    api.get.mockImplementation((url: string) => {
      if (url.endsWith("/company-time-zone")) return new Promise(res => { release = () => res({ data: { data: zone } }) })
      return Promise.resolve({ data: { data: url.includes("/guards") ? { content: [] } : url.includes("/shifts/range") ? [] : { content: [] } } })
    })
  }
  const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><SchedulerTab /></MemoryRouter></QueryClientProvider>)

  it("waits for the zone, then asks for the company's week (from the day before, to the next Monday)", async () => {
    serve({ timeZone: "Pacific/Auckland", abbreviation: "GMT+13", now: "x", today: "2026-11-30" }); show()
    await waitFor(() => expect(api.get.mock.calls.some(c => String(c[0]).endsWith("/company-time-zone"))).toBe(true))
    expect(rangeCalls()).toHaveLength(0)
    release()
    await waitFor(() => expect(rangeCalls().length).toBeGreaterThan(0))
    const q = new URLSearchParams(String(rangeCalls()[0][0]).split("?")[1])
    expect(q.get("from")).toBe("2026-11-28T11:00:00.000Z") // Sunday 29 Nov 00:00 in Auckland
    expect(q.get("to")).toBe("2026-12-06T11:00:00.000Z")   // Monday 7 Dec 00:00 in Auckland
    expect(rangeCalls()).toHaveLength(1)
  })

  it("a South African company still gets South Africa's week", async () => {
    serve({ timeZone: "Africa/Johannesburg", abbreviation: "SAST", now: "x", today: "2026-11-29" }); show()
    await waitFor(() => expect(api.get.mock.calls.some(c => String(c[0]).endsWith("/company-time-zone"))).toBe(true))
    release()
    await waitFor(() => expect(rangeCalls().length).toBeGreaterThan(0))
    const q = new URLSearchParams(String(rangeCalls()[0][0]).split("?")[1])
    expect(q.get("from")).toBe("2026-11-21T22:00:00.000Z") // Sunday 22 Nov 00:00 SAST
  })
})
