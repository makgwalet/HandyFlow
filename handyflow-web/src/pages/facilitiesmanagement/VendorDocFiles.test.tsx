import { describe, it, expect, vi, beforeEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import VendorsTab from "./VendorsTab"

const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn(), delete: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }))
vi.mock("../../store/toast.store", () => ({ toast }))
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><VendorsTab /></QueryClientProvider>)

const vendor = { id: "v1", companyName: "Cool Air", serviceType: "HVAC", contactName: null, contactPhone: null, contactEmail: null, notes: null, active: true, onboardingStatus: "PENDING" }
const compliance = { eligible: false, problems: [], missing: [], lapsed: [], expiring: [], documents: [{ id: "d1", vendorId: "v1", docType: "TAX_CLEARANCE", reference: "TC-1", issuedBy: "SARS", issueDate: null, expiryDate: "2027-01-01", coverAmount: null, documentRef: null, status: "VALID", inForce: true, revokedReason: null }] }

function serve() {
  get.mockImplementation(async (u: string) => {
    if (u.endsWith("/vendors")) return { data: { content: [vendor], totalPages: 1, totalElements: 1 } }
    if (u.endsWith("/compliance")) return { data: compliance }
    if (u.endsWith("/evidence")) return { data: [] }
    return { data: {} }
  })
}

describe("vendor document files", () => {
  beforeEach(() => { cleanup(); get.mockReset(); post.mockReset(); toast.success.mockReset(); toast.error.mockReset() })

  async function openRecordDialog() {
    serve()
    wrap()
    fireEvent.click(await screen.findByText("Cool Air"))
    fireEvent.click(await screen.findByText("Record a document"))
  }
  const choose = (name = "tc.pdf") => fireEvent.change(screen.getByLabelText("Document file"), { target: { files: [new File(["x"], name, { type: "application/pdf" })] } })
  const fill = () => fireEvent.change(screen.getByLabelText(/^Expires/), { target: { value: "2027-06-01" } })

  it("attaches the chosen file to the document it has just recorded", async () => {
    await openRecordDialog()
    post.mockImplementation(async (u: string) => u.endsWith("/documents") ? { data: { id: "d9" } } : { data: {} })
    fill(); choose()
    fireEvent.click(screen.getByRole("button", { name: "Record" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/facilitiesmanagement/vendors/v1/documents/d9/evidence", expect.any(FormData)))
    expect(toast.success).toHaveBeenCalledWith("Document recorded with its file")
  })

  it("keeps the record and says so when only the file fails", async () => {
    await openRecordDialog()
    post.mockImplementation(async (u: string) => { if (u.endsWith("/documents")) return { data: { id: "d9" } }; throw new Error("too big") })
    fill(); choose()
    fireEvent.click(screen.getByRole("button", { name: "Record" }))
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(expect.stringMatching(/recorded, but its file was not attached/)))
  })

  it("records without a file when none is chosen", async () => {
    await openRecordDialog()
    post.mockResolvedValue({ data: { id: "d9" } })
    fill()
    fireEvent.click(screen.getByRole("button", { name: "Record" }))
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith("Document recorded"))
    expect(post).toHaveBeenCalledTimes(1)
  })

  it("lists a document's files from its own address", async () => {
    serve()
    wrap()
    fireEvent.click(await screen.findByText("Cool Air"))
    fireEvent.click(await screen.findByText("Files"))
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/facilitiesmanagement/vendors/v1/documents/d1/evidence"))
  })
})
