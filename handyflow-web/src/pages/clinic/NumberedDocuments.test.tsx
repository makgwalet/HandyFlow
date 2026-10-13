import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const post = vi.fn()
const perms: Record<string, boolean> = {}
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
import NumberedDocuments from "./NumberedDocuments"

const RX = { id: "d1", number: "RX-2026-000001", type: "PRESCRIPTION", typeLabel: "Prescription", issuedAt: "2026-10-13T10:00:00Z", status: "ISSUED", copyKept: true, lastSentAt: null }
const OLD = { id: "d2", number: "SN-2026-000003", type: "SICK_NOTE", typeLabel: "Sick note", issuedAt: "2026-10-10T10:00:00Z", status: "ISSUED", copyKept: false }
const GONE = { id: "d3", number: "MR-2026-000002", type: "MEDICAL_REPORT", typeLabel: "Medical report", issuedAt: "2026-10-09T10:00:00Z", status: "VOIDED", voidReason: "Medical report replaced by an updated copy", copyKept: true }

beforeEach(() => {
  get.mockReset(); post.mockReset(); for (const k of Object.keys(perms)) delete perms[k]
  get.mockImplementation((url: string) => url.endsWith("/issued-documents") ? Promise.resolve({ data: { data: [RX, OLD, GONE] } }) : Promise.resolve({ data: new Blob(["%PDF"]) }))
  ;(URL as any).createObjectURL = vi.fn(() => "blob:x"); (URL as any).revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
})
afterEach(cleanup)

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><NumberedDocuments patientId="p1" /></QueryClientProvider>)

describe("NumberedDocuments (0203)", () => {
  it("lists each document with its number, whether it was sent, and withdrawn ones with the reason", async () => {
    show()
    expect(await screen.findByText("Prescription RX-2026-000001")).toBeTruthy()
    expect(screen.getAllByText(/Not sent/)).toHaveLength(2)
    expect(screen.getByText("WITHDRAWN")).toBeTruthy()
    expect(screen.getByText(/replaced by an updated copy/)).toBeTruthy()
    expect(screen.getByText(/copy not kept/)).toBeTruthy()
  })

  it("offers no buttons without the permissions", async () => {
    show()
    await screen.findByText("Prescription RX-2026-000001")
    expect(screen.queryByRole("button")).toBeNull()
  })

  it("downloads the kept copy, and offers sending only for a standing document whose copy was kept", async () => {
    perms.CLINIC_DOCUMENT_DOWNLOAD = true; perms.CLINIC_DOCUMENT_EMAIL = true
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Download RX-2026-000001" }))
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/issued-documents/d1/pdf", { responseType: "blob" }))
    expect(screen.queryByRole("button", { name: "Download SN-2026-000003" })).toBeNull()
    expect(screen.getAllByRole("button", { name: /Email .* to the patient/ })).toHaveLength(1)
  })

  it("sends a document when asked and says where it went", async () => {
    perms.CLINIC_DOCUMENT_EMAIL = true
    post.mockResolvedValue({ data: { data: { recipient: "patient@example.org", number: "RX-2026-000001" } } })
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Email RX-2026-000001 to the patient" }))
    expect((await screen.findByRole("status")).textContent).toBe("RX-2026-000001 sent to patient@example.org.")
    expect(post).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/issued-documents/d1/email")
  })

  it("shows nothing when the patient has no numbered documents", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    const { container } = show()
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
})
