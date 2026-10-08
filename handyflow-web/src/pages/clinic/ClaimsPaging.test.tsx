import { describe, it, expect, vi, afterEach, beforeEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ClaimsTab from "./ClaimsTab"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))

const claim = (i: number) => ({
  id: `c${i}`, status: "DRAFT", patientName: `Patient ${i}`, practitionerName: "Dr A",
  grossAmount: 100, schemePortion: 100, patientPortion: 0, lines: [], createdAt: "2026-10-01T08:00:00Z",
})

beforeEach(() => {
  get.mockImplementation(async (url: string) => {
    if (url.includes("/claims/summary")) return { data: { data: { total: 30, outstanding: 3000, paid: 0, rejected: 0 } } }
    if (url.includes("/claims/page")) {
      const page = Number(new URL("http://x" + url).searchParams.get("page"))
      const n = page === 0 ? 25 : 5
      return { data: { data: { content: Array.from({ length: n }, (_, i) => claim(page * 25 + i)), total: 30, page, size: 25 } } }
    }
    return { data: { data: [] } }
  })
})
afterEach(() => { cleanup(); get.mockReset() })

const mount = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ClaimsTab /></QueryClientProvider>)

describe("claims paging", () => {
  it("asks for the first 25 and shows the total from the summary, not the page", async () => {
    mount()
    expect(await screen.findByText("Showing 1-25 of 30")).toBeTruthy()
    expect(screen.getByText("30")).toBeTruthy()
    expect(get.mock.calls.some(c => String(c[0]).includes("/claims/page?page=0&size=25"))).toBe(true)
  })

  it("Next loads the second page", async () => {
    mount()
    fireEvent.click(await screen.findByText("Next"))
    expect(await screen.findByText("Showing 26-30 of 30")).toBeTruthy()
    await waitFor(() => expect((screen.getByText("Next") as HTMLButtonElement).disabled).toBe(true))
  })
})
