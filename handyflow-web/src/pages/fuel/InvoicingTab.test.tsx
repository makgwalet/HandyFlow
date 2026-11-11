import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import InvoicingTab from "./InvoicingTab"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const line = (o: any = {}) => ({ deliveryId: "d1", receiptNumber: "FDR-2026-00001", customerId: "c1", customerName: "Gold Mine Ltd", fuelType: "DIESEL", litresDelivered: 1000, pricePerLitre: 22.5, totalAmount: 22500, deliveredAt: "2026-11-11T10:00:00Z", invoiceState: "NONE", invoiceError: null, ...o })
let queue: any[]
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><InvoicingTab /></QueryClientProvider>)
}

describe("InvoicingTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); perms = ["FUEL_MANAGE"]
    queue = [line(), line({ deliveryId: "d2", receiptNumber: "FDR-2026-00002", invoiceState: "FAILED", invoiceError: "That customer was not found." }),
      line({ deliveryId: "d3", receiptNumber: "FDR-2026-00003", customerId: null, customerName: null })]
    get.mockImplementation(async () => ({ data: { data: queue } }))
    post.mockResolvedValue({ data: { data: { invoiceId: "i1", invoiceNumber: "INV-00042", deliveries: 2 } } })
  })
  afterEach(() => cleanup())

  it("lists deliveries per customer, keeps failures visible with the reason, and cannot invoice a walk-in", async () => {
    mount()
    await waitFor(() => screen.getByText("FDR-2026-00001"))
    expect(screen.getByText(/That customer was not found/)).toBeTruthy()
    expect(screen.getByText(/No customer \(walk-in\)/)).toBeTruthy()
    expect(screen.queryByLabelText("Select FDR-2026-00003")).toBeNull()
  })

  it("creates one draft invoice for the ticked deliveries", async () => {
    mount()
    await waitFor(() => screen.getByText("FDR-2026-00001"))
    const btn = screen.getByText(/Create draft invoice/) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.click(screen.getByLabelText("Select FDR-2026-00001"))
    fireEvent.click(screen.getByLabelText("Select FDR-2026-00002"))
    fireEvent.click(screen.getByText(/Create draft invoice \(2\)/))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/invoicing/invoice", { deliveryIds: ["d1", "d2"] }))
    await waitFor(() => expect(screen.getByRole("status").textContent).toMatch(/INV-00042/))
  })

  it("shows the server's reason when invoicing fails", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "The invoice could not be created: boom. The deliveries stay in the queue." } } })
    mount()
    await waitFor(() => screen.getByText("FDR-2026-00001"))
    fireEvent.click(screen.getByLabelText("Select FDR-2026-00001"))
    fireEvent.click(screen.getByText(/Create draft invoice \(1\)/))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/stay in the queue/))
  })

  it("has no invoice button without FUEL_MANAGE and says when nothing is waiting", async () => {
    perms = []; mount()
    await waitFor(() => screen.getByText("FDR-2026-00001"))
    expect(screen.queryByText(/Create draft invoice/)).toBeNull()
    cleanup(); queue = []; mount()
    await waitFor(() => screen.getByText(/Nothing waiting/))
  })
})
