import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: vi.fn() } }))
import ConsentTab from "./ConsentTab"

const EVENT = { id: "e1", consentType: "TREATMENT", action: "GRANTED", method: "WRITTEN", capturedByName: "Reception Sample", notes: null, createdAt: "2026-10-13T09:15:00Z" }

beforeEach(() => {
  get.mockReset()
  get.mockImplementation((url: string) => url.endsWith("/consent/history") ? Promise.resolve({ data: { data: [EVENT] } })
    : url.endsWith("/consent") ? Promise.resolve({ data: { data: [] } })
    : Promise.resolve({ data: new Blob(["%PDF"]) }))
  ;(URL as any).createObjectURL = vi.fn(() => "blob:x"); (URL as any).revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
})
afterEach(cleanup)

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <ConsentTab patient={{ id: "p1" } as any} /></QueryClientProvider>)

describe("ConsentTab consent record (0203)", () => {
  it("prints a numbered record of one consent decision from the history", async () => {
    show()
    fireEvent.click(screen.getByRole("button", { name: /History/ }))
    fireEvent.click(await screen.findByRole("button", { name: "Print record" }))
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/consent/e1/record-pdf", { responseType: "blob" }))
  })

  it("says why when the record cannot be made", async () => {
    get.mockImplementation((url: string) => url.endsWith("/consent/history") ? Promise.resolve({ data: { data: [EVENT] } })
      : url.endsWith("/consent") ? Promise.resolve({ data: { data: [] } })
      : Promise.reject({ response: { data: new Blob([JSON.stringify({ message: "You do not have permission to print or export documents from a restricted record." })]) } }))
    show()
    fireEvent.click(screen.getByRole("button", { name: /History/ }))
    fireEvent.click(await screen.findByRole("button", { name: "Print record" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/restricted record/)
  })
})
