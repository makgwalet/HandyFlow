import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EnquiriesTab from "./EnquiriesTab"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const enq = (o: any = {}) => ({ id: "e1", enquiryNumber: "EQ-2026-00001", accountId: "a1", accountName: "Acme Mining", contactName: null, contactPhone: null, contactEmail: null,
  fuelType: "DIESEL", litres: 5000, requestedDate: null, source: "PHONE", status: "NEW", quoteOrderId: null, quoteOrderNumber: null, decisionReason: null, notes: null, createdAt: "2026-11-12T08:00:00Z", decidedAt: null, ...o })
let rows: any[]

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><EnquiriesTab /></QueryClientProvider>)
}

describe("EnquiriesTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); perms = ["FUEL_MANAGE"]
    rows = [enq(), enq({ id: "e2", enquiryNumber: "EQ-2026-00002", accountId: null, accountName: null, contactName: "Walk-in farmer" })]
    post.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.endsWith("/enquiries")) return { data: { data: rows } }
      if (u.endsWith("/customer-accounts")) return { data: { data: [{ id: "a1", customerName: "Acme Mining" }] } }
      return { data: { data: [] } }
    })
  })
  afterEach(() => cleanup())

  it("lists enquiries with who asked, and says when there is no account yet", async () => {
    mount()
    await waitFor(() => screen.getByText("Acme Mining"))
    expect(screen.getByText("Walk-in farmer")).toBeTruthy()
    expect(screen.getByText("No customer account yet")).toBeTruthy()
    expect(screen.getByText("Enquiries (2 open)")).toBeTruthy()
  })

  it("quotes an enquiry that has an account, and cannot quote one that has none", async () => {
    mount()
    await waitFor(() => screen.getAllByText("Quote"))
    const buttons = screen.getAllByText("Quote") as HTMLButtonElement[]
    expect(buttons[0].disabled).toBe(false)
    expect(buttons[1].disabled).toBe(true)
    fireEvent.click(buttons[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/enquiries/e1/quote", {}))
  })

  it("records a new enquiry", async () => {
    mount()
    await waitFor(() => screen.getByText("New enquiry"))
    fireEvent.click(screen.getByText("New enquiry"))
    fireEvent.click(screen.getByText("Save enquiry"))
    expect(screen.getByRole("alert").textContent).toMatch(/account|who/)
    await waitFor(() => screen.getByRole("option", { name: "Acme Mining" }))
    fireEvent.change(screen.getByLabelText("Customer account"), { target: { value: "a1" } })
    fireEvent.change(screen.getByLabelText("Litres"), { target: { value: "8000" } })
    fireEvent.click(screen.getByText("Save enquiry"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/enquiries", expect.objectContaining({ accountId: "a1", fuelType: "DIESEL", litres: 8000, source: "PHONE" })))
  })

  it("closing as lost needs a reason", async () => {
    mount()
    await waitFor(() => screen.getAllByText("Lost"))
    fireEvent.click(screen.getAllByText("Lost")[0])
    fireEvent.click(screen.getByText("Mark as lost"))
    expect(screen.getByRole("alert").textContent).toMatch(/5 characters/)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Price too high" } })
    fireEvent.click(screen.getByText("Mark as lost"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/enquiries/e1/lost", { reason: "Price too high" }))
  })

  it("a read-only user sees enquiries but no buttons", async () => {
    perms = ["FUEL_READ"]
    mount()
    await waitFor(() => screen.getByText("Acme Mining"))
    expect(screen.queryByText("Quote")).toBeNull()
    expect(screen.queryByText("New enquiry")).toBeNull()
  })

  it("a refusal from the server is shown", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "Link this enquiry to a customer account before quoting." } } })
    mount()
    await waitFor(() => screen.getAllByText("Quote"))
    fireEvent.click(screen.getAllByText("Quote")[0])
    await waitFor(() => screen.getByText("Link this enquiry to a customer account before quoting."))
  })
})
