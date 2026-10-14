import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance patch F10: supplier credit notes are captured, approved when large, and applied to bills.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
import { SupplierCreditNotesTab } from "./SupplierCreditNotesTab"

const note = (o: any) => ({ id: "c1", creditNoteNumber: "CN-100", supplierName: "Acme", billId: null, billNumber: null,
  creditDate: "2026-10-10", reason: "Returned goods", amount: 1000, vatAmount: 150, totalAmount: 1150,
  allocated: 0, available: 1150, status: "ISSUED", rejectionReason: null, ...o })
const bill = (o: any) => ({ id: "b1", supplierName: "Acme", billNumber: "INV-1", dueDate: "2026-10-01", status: "APPROVED",
  totalAmount: 800, paidAmount: 0, creditedAmount: 0, balance: 800, category: "OTHER", ...o })
let notes: any[] = [], bills: any[] = []
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SupplierCreditNotesTab /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["AP_READ", "AP_MANAGE", "AP_APPROVE", "AP_PAY"])
  get.mockReset(); post.mockReset()
  notes = [note({}), note({ id: "c2", creditNoteNumber: "CN-200", status: "PENDING_APPROVAL", available: 0, totalAmount: 6000 })]
  bills = [bill({}), bill({ id: "b2", billNumber: "INV-2", dueDate: "2026-10-20", totalAmount: 500, balance: 500 }), bill({ id: "b3", supplierName: "Sasol", billNumber: "S-1" })]
  get.mockImplementation((url: string) => Promise.resolve({ data: { data:
    url.includes("credit-notes") ? notes : url.includes("known-names") ? ["Acme", "Sasol"] : { content: bills } } }))
  post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("SupplierCreditNotesTab", () => {
  it("lists credit notes with what is left of each", async () => {
    show()
    expect(await screen.findByText("CN-100")).toBeTruthy()
    expect(screen.getByText("Issued")).toBeTruthy()
    expect(screen.getByText("Waiting for approval")).toBeTruthy()
  })

  it("approves a credit note that is waiting", async () => {
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Approve credit note CN-200" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/ap/credit-notes/c2/approve"))
  })

  it("shows the server's refusal when you captured the credit note yourself", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "You cannot approve your own request" } } })
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Approve credit note CN-200" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/cannot approve your own/)
  })

  it("hides approve and apply from people without those permissions", async () => {
    perms = new Set(["AP_READ"])
    show()
    await screen.findByText("CN-100")
    expect(screen.queryByRole("button", { name: /Approve credit note/ })).toBeNull()
    expect(screen.queryByRole("button", { name: /Apply credit note/ })).toBeNull()
    expect(screen.queryByRole("button", { name: /Capture credit note/ })).toBeNull()
  })

  it("rejects only with a reason", async () => {
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Reject credit note CN-200" }))
    const send = screen.getByRole("button", { name: "Reject" }) as HTMLButtonElement
    expect(send.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Why is it rejected"), { target: { value: "Not our goods" } })
    fireEvent.click(send)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/ap/credit-notes/c2/reject", { reason: "Not our goods" }))
  })

  it("applies credit to the oldest bills first and sends the lines", async () => {
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Apply credit note CN-100 to bills" }))
    const dlg = await screen.findByRole("dialog", { name: "Apply credit to bills" })
    await within(dlg).findByLabelText("Amount to apply to INV-1")
    expect(within(dlg).queryByLabelText("Amount to apply to S-1")).toBeNull() // another supplier's bill
    fireEvent.click(within(dlg).getByRole("button", { name: "Oldest first" }))
    expect((within(dlg).getByLabelText("Amount to apply to INV-1") as HTMLInputElement).value).toBe("800")
    expect((within(dlg).getByLabelText("Amount to apply to INV-2") as HTMLInputElement).value).toBe("350")
    fireEvent.click(within(dlg).getByRole("button", { name: "Apply credit" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/ap/credit-notes/c1/allocate",
      { lines: [{ billId: "b1", amount: 800 }, { billId: "b2", amount: 350 }] }))
  })

  it("will not apply more than a bill owes", async () => {
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Apply credit note CN-100 to bills" }))
    const dlg = await screen.findByRole("dialog", { name: "Apply credit to bills" })
    fireEvent.change(await within(dlg).findByLabelText("Amount to apply to INV-2"), { target: { value: "500.01" } })
    expect(within(dlg).getByRole("alert").textContent).toMatch(/still owes/)
    expect((within(dlg).getByRole("button", { name: "Apply credit" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("captures a credit note against a bill", async () => {
    show()
    fireEvent.click(await screen.findByRole("button", { name: /Capture credit note/ }))
    const dlg = await screen.findByRole("dialog", { name: "Capture supplier credit note" })
    const send = within(dlg).getByRole("button", { name: "Capture" }) as HTMLButtonElement
    expect(send.disabled).toBe(true)
    await waitFor(() => expect(within(dlg).getByText(/Acme - INV-1/)).toBeTruthy())
    fireEvent.change(within(dlg).getByLabelText("Bill it is for (optional)"), { target: { value: "b1" } })
    fireEvent.change(within(dlg).getByLabelText("Supplier's credit note number"), { target: { value: "CN-300" } })
    fireEvent.change(within(dlg).getByLabelText("What is it for"), { target: { value: "Damaged goods" } })
    fireEvent.change(within(dlg).getByLabelText("Amount (before VAT)"), { target: { value: "100" } })
    fireEvent.change(within(dlg).getByLabelText("VAT"), { target: { value: "15" } })
    expect(send.disabled).toBe(false)
    fireEvent.click(send)
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0]
    expect(url).toBe("/api/v1/ap/credit-notes")
    expect(body).toMatchObject({ creditNoteNumber: "CN-300", billId: "b1", supplierName: null, amount: 100, vatAmount: 15, reason: "Damaged goods" })
  })

  it("will not capture VAT above the amount", async () => {
    show()
    fireEvent.click(await screen.findByRole("button", { name: /Capture credit note/ }))
    const dlg = await screen.findByRole("dialog", { name: "Capture supplier credit note" })
    fireEvent.change(within(dlg).getByLabelText("Amount (before VAT)"), { target: { value: "100" } })
    fireEvent.change(within(dlg).getByLabelText("VAT"), { target: { value: "101" } })
    expect(within(dlg).getByRole("alert").textContent).toMatch(/VAT cannot be more/)
  })
})
