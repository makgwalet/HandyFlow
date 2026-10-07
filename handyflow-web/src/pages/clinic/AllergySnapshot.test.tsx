import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
import AllergySnapshot from "./AllergySnapshot"

const show = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <AllergySnapshot consultationId="c1" /></QueryClientProvider>)

beforeEach(() => get.mockReset())
afterEach(cleanup)

describe("AllergySnapshot", () => {
  it("lists the allergies recorded when the consultation was signed", async () => {
    get.mockResolvedValue({ data: { data: { captured: true, capturedAt: "x", items: [{ allergen: "Penicillin", severity: "SEVERE", reaction: "Rash" }] } } })
    show()
    expect(await screen.findByText("Penicillin")).toBeTruthy()
    expect(screen.getByText(/\(severe\): Rash/)).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/clinic/consultations/c1/allergy-snapshot")
  })

  it("says so when none were recorded at the time", async () => {
    get.mockResolvedValue({ data: { data: { captured: true, capturedAt: "x", items: [] } } })
    show()
    expect(await screen.findByText("No allergies were recorded at the time.")).toBeTruthy()
  })

  it("shows nothing for a consultation signed before snapshots existed", async () => {
    get.mockResolvedValue({ data: { data: { captured: false, items: [] } } })
    const { container } = show()
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
})
