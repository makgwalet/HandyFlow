import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn(); const del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), delete: (...a: any[]) => del(...a) } }))
const perms: Record<string, boolean> = {}
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
vi.mock("./ReferralLetterModal", () => ({ default: ({ consultationId, onClose }: any) => <button onClick={onClose}>referral for {consultationId}</button> }))
vi.mock("./QuickActionsCard", () => ({ SickNoteModal: ({ list, onClose }: any) => <button onClick={onClose}>sick note for {list[0].id}</button>, default: () => null }))
import DocumentsTab from "./PatientDocumentsTab"

const items = [
  { origin: "STORED", id: "d1", docType: "LETTER", source: "UPLOADED", title: "Cardiologist letter", date: "2026-09-20", addedBy: "Sister Zodwa", sizeBytes: 2048, fileName: "letter.pdf", downloadPath: "/api/v1/clinic/patients/p1/documents/d1/file", removable: true },
  { origin: "STORED", id: "d2", docType: "SICK_NOTE", source: "ISSUED", title: "Medical certificate", date: "2026-10-07", downloadPath: "/api/v1/clinic/patients/p1/documents/d2/file", removable: true },
  { origin: "RECORD", id: "c1", docType: "VISIT_SUMMARY", source: "RECORD", title: "Visit summary: Cough", date: "2026-10-07", downloadPath: "/api/v1/clinic/consultations/c1/summary-pdf", removable: false }]
const patient: any = { id: "p1", firstName: "Liam", lastName: "Botha" }
const consultations: any[] = [{ id: "c1", consultedAt: "2026-10-07T08:00:00Z", chiefComplaint: "Cough" }]
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><DocumentsTab patient={patient} consultations={consultations} /></QueryClientProvider>)

afterEach(cleanup)
beforeEach(() => { get.mockReset(); post.mockReset(); del.mockReset(); for (const k of Object.keys(perms)) delete perms[k]
  perms.CLINIC_DOCUMENT_READ = true
  get.mockImplementation((url: string) => url.endsWith("/documents") ? Promise.resolve({ data: { data: { patientId: "p1", items } } }) : Promise.resolve({ data: new Blob(["x"]), headers: { "content-type": "application/pdf" } })) })

describe("documents register", () => {
  it("lists stored and generated documents with type, source and who added them", async () => {
    show()
    expect(await screen.findByText("Cardiologist letter")).toBeTruthy()
    expect(screen.getByText(/Letter · Uploaded by Sister Zodwa · 2 KB/)).toBeTruthy()
    expect(screen.getByText(/Sick note · Issued here/)).toBeTruthy()
    expect(screen.getByText(/Visit summary · From the record/)).toBeTruthy()
    expect(screen.getByText("3 documents")).toBeTruthy()
  })
  it("filters by kind", async () => {
    show(); await screen.findByText("Cardiologist letter")
    fireEvent.click(screen.getByRole("button", { name: "Letter (1)" }))
    expect(screen.queryByText("Medical certificate")).toBeNull()
    expect(screen.getByText("Cardiologist letter")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "All (3)" }))
    expect(screen.getByText("Medical certificate")).toBeTruthy()
  })
  it("shows nothing without the read permission and loads nothing", () => {
    delete perms.CLINIC_DOCUMENT_READ
    show()
    expect(screen.getByText(/do not have permission/)).toBeTruthy()
    expect(get).not.toHaveBeenCalled()
  })
  it("offers Download only with the download permission, and Remove only on stored files", async () => {
    perms.CLINIC_DOCUMENT_DOWNLOAD = true; perms.CLINIC_DOCUMENT_VOID = true
    show(); await screen.findByText("Cardiologist letter")
    expect(screen.getAllByRole("button", { name: /^Download / }).length).toBe(3)
    expect(screen.getAllByRole("button", { name: /^Remove / }).length).toBe(2)
    expect(screen.queryByRole("button", { name: "Remove Visit summary: Cough" })).toBeNull()
  })
  it("hides Download and Upload without the permissions", async () => {
    show(); await screen.findByText("Cardiologist letter")
    expect(screen.queryByRole("button", { name: /^Download / })).toBeNull()
    expect(screen.queryByRole("button", { name: /Upload document/ })).toBeNull()
  })
  it("uploads a file with its type, title and date", async () => {
    perms.CLINIC_DOCUMENT_CREATE = true
    post.mockResolvedValue({ data: {} })
    show()
    fireEvent.click(await screen.findByRole("button", { name: /Upload document/ }))
    const save = screen.getByRole("button", { name: "Upload" }) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    const file = new File(["%PDF-1.4"], "scan.pdf", { type: "application/pdf" })
    fireEvent.change(screen.getByLabelText("File"), { target: { files: [file] } })
    expect((screen.getByLabelText("Title") as HTMLInputElement).value).toBe("scan")
    fireEvent.change(screen.getByLabelText("Kind of document"), { target: { value: "PAPER_NOTES" } })
    fireEvent.change(screen.getByLabelText("Date on the document"), { target: { value: "2025-06-01" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0]
    expect(url).toBe("/api/v1/clinic/patients/p1/documents")
    expect((body as FormData).get("type")).toBe("PAPER_NOTES")
    expect((body as FormData).get("title")).toBe("scan")
    expect((body as FormData).get("date")).toBe("2025-06-01")
    expect((body as FormData).get("file")).toBeTruthy()
    await waitFor(() => expect(get.mock.calls.length).toBeGreaterThan(1))
  })
  it("shows the server's reason when an upload is refused", async () => {
    perms.CLINIC_DOCUMENT_CREATE = true
    post.mockRejectedValue({ response: { data: { message: "The file is not the type it says it is" } } })
    show()
    fireEvent.click(await screen.findByRole("button", { name: /Upload document/ }))
    fireEvent.change(screen.getByLabelText("File"), { target: { files: [new File(["%PDF"], "a.pdf", { type: "application/pdf" })] } })
    fireEvent.change(screen.getByLabelText("Kind of document"), { target: { value: "LETTER" } })
    fireEvent.change(screen.getByLabelText("Date on the document"), { target: { value: "2025-06-01" } })
    fireEvent.click(screen.getByRole("button", { name: "Upload" }))
    expect(await screen.findByText(/not the type it says/)).toBeTruthy()
  })
  it("will not remove a document without a reason", async () => {
    perms.CLINIC_DOCUMENT_VOID = true
    del.mockResolvedValue({ data: {} })
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Remove Cardiologist letter" }))
    const go = screen.getByRole("button", { name: "Remove" }) as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Wrong patient" } })
    fireEvent.click(go)
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/documents/d1", { params: { reason: "Wrong patient" } }))
  })
  it("issues a sick note for the visits and reloads the register afterwards", async () => {
    perms.CLINIC_SICK_NOTE_SIGN = true
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Issue sick note" }))
    const before = get.mock.calls.length
    fireEvent.click(screen.getByText("sick note for c1"))
    await waitFor(() => expect(get.mock.calls.length).toBeGreaterThan(before))
  })
  it("reports a failed load", async () => {
    get.mockRejectedValue(new Error("x")); show()
    expect(await screen.findByText(/could not be loaded/)).toBeTruthy()
  })
})
