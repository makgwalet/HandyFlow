import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ServicesTab from "./ServicesTab"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: vi.fn() } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const rec = (i: number) => ({ id: `s${i}`, type: i % 2 ? "REPAIR" : "SERVICE", description: `Job ${String(i).padStart(2, "0")}`, serviceDate: "2026-09-10", odometerAtService: 40000 + i, nextServiceKm: null, cost: 1000, supplier: i === 7 ? "Midas Rosebank" : "Workshop", invoiceRef: `INV${i}` })
beforeEach(() => {
  get.mockReset()
  get.mockImplementation((url: string) => /vehicles\/v1\/services/.test(url) ? ok(Array.from({ length: 30 }, (_, i) => rec(i)))
    : ok({ content: [{ id: "v1", registration: "ABC123MP", make: "Toyota", model: "Hilux", dueForService: false, currentOdometer: 50000, serviceIntervalKm: 15000 }] }))
})
afterEach(cleanup)

describe("service history", () => {
  it("shows 25 records at a time and searches them", async () => {
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ServicesTab /></QueryClientProvider>)
    await screen.findByRole("option", { name: /ABC123MP/ }); fireEvent.change(screen.getByRole("combobox"), { target: { value: "v1" } })
    await waitFor(() => expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–25 of 30"))
    fireEvent.click(screen.getByRole("button", { name: "Next" })); expect(screen.getByLabelText("Showing").textContent).toBe("Showing 26–30 of 30")
    fireEvent.change(screen.getByLabelText("Search service history"), { target: { value: "midas" } })
    expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–1 of 1"); expect(screen.getByText("Job 07")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Search service history"), { target: { value: "zzz" } }); expect(screen.getByText("No service records match.")).toBeTruthy()
  })
})
