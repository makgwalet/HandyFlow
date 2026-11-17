import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import AutoAcceptSettings from "./AutoAcceptSettings"

const get = vi.fn(); const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><AutoAcceptSettings /></QueryClientProvider>)
}

describe("AutoAcceptSettings", () => {
  beforeEach(() => { get.mockReset(); put.mockReset(); put.mockResolvedValue({ data: {} }) })
  afterEach(cleanup)

  it("shows that it is off by default", async () => {
    get.mockImplementation(() => ok({ shiftsEnabled: false, gaugesEnabled: false, gaugeMaxDifferenceLitres: null }))
    mount()
    expect(await screen.findByText("Off: every import waits for a person")).toBeTruthy()
  })

  it("turns shifts on without needing a limit", async () => {
    get.mockImplementation(() => ok({ shiftsEnabled: false, gaugesEnabled: false, gaugeMaxDifferenceLitres: null }))
    mount()
    fireEvent.click(await screen.findByLabelText(/Accept clean pump\/POS shifts/))
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/import-auto-accept", { shiftsEnabled: true, gaugesEnabled: false, gaugeMaxDifferenceLitres: null }))
  })

  it("will not save gauges without a limit, then saves with one", async () => {
    get.mockImplementation(() => ok({ shiftsEnabled: false, gaugesEnabled: false, gaugeMaxDifferenceLitres: null }))
    mount()
    fireEvent.click(await screen.findByLabelText(/Accept gauge readings/))
    expect((screen.getByRole("button", { name: "Save" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Largest difference in litres"), { target: { value: "250" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/import-auto-accept", { shiftsEnabled: false, gaugesEnabled: true, gaugeMaxDifferenceLitres: 250 }))
  })

  it("treats an answer that is not a setting as off", async () => {
    get.mockImplementation(() => ok([]))
    mount()
    expect(await screen.findByText("Off: every import waits for a person")).toBeTruthy()
  })
})
