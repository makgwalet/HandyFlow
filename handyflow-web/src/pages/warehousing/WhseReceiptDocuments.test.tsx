import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseReceiptDocuments, { type ReceiptDocument } from "./WhseReceiptDocuments"
import { documentTypeLabel, fileProblem, fileSize } from "./receiptDocuments"

const get = vi.fn()
const post = vi.fn()
let canUpload = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => canUpload }))

const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const doc = (over: Partial<ReceiptDocument> = {}): ReceiptDocument => ({ id: "E1", fileName: "delivery-note.pdf", contentType: "application/pdf", fileSizeBytes: 2048, evidenceType: "DELIVERY_NOTE",
  uploadedByName: "Thandi", createdAt: "2026-11-11T08:00:00Z", ...over })
const BASE = "/api/v1/warehousing/inbound-shipments/S1/evidence"

beforeEach(() => { get.mockReset(); post.mockReset(); canUpload = true; post.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("receipt document helpers", () => {
  it("a file must be present, not empty, under 20 MB and a usable type", () => {
    expect(fileProblem(null)).toMatch(/Choose a file/)
    expect(fileProblem({ name: "a.pdf", size: 0, type: "application/pdf" })).toMatch(/empty/)
    expect(fileProblem({ name: "a.pdf", size: 21 * 1024 * 1024, type: "application/pdf" })).toMatch(/20 MB/)
    expect(fileProblem({ name: "a.exe", size: 10, type: "application/x-msdownload" })).toMatch(/photo, PDF/)
    expect(fileProblem({ name: "a.jpg", size: 10, type: "image/jpeg" })).toBeNull()
    expect(fileProblem({ name: "a.xlsx", size: 10, type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" })).toBeNull()
  })
  it("labels and sizes read well", () => {
    expect(documentTypeLabel("DAMAGE_PHOTO")).toBe("Photo of damage")
    expect(documentTypeLabel("MYSTERY")).toBe("MYSTERY")
    expect(fileSize(512)).toBe("512 B")
    expect(fileSize(2048)).toBe("2 KB")
    expect(fileSize(3 * 1024 * 1024)).toBe("3.0 MB")
  })
})

describe("WhseReceiptDocuments", () => {
  it("lists what is attached", async () => {
    get.mockResolvedValue({ data: [doc(), doc({ id: "E2", fileName: "dent.jpg", evidenceType: "DAMAGE_PHOTO", contentType: "image/jpeg" })] })
    wrap(<WhseReceiptDocuments shipmentId="S1" />)
    expect(await screen.findByText("delivery-note.pdf")).toBeTruthy()
    expect(screen.getByText("dent.jpg")).toBeTruthy()
    expect(screen.getByText("Photo of damage", { selector: "span" })).toBeTruthy()
    expect(get).toHaveBeenCalledWith(BASE)
  })

  it("attaches a file with its type as a multipart upload", async () => {
    get.mockResolvedValue({ data: [] })
    wrap(<WhseReceiptDocuments shipmentId="S1" />)
    await screen.findByText("Nothing attached yet.")
    expect((screen.getByText("Attach") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("What is it"), { target: { value: "DAMAGE_PHOTO" } })
    const f = new File(["x"], "dent.jpg", { type: "image/jpeg" })
    fireEvent.change(screen.getByLabelText("File"), { target: { files: [f] } })
    fireEvent.click(screen.getByText("Attach"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, form, cfg] = post.mock.calls[0]
    expect(url).toBe(BASE)
    expect((form as FormData).get("evidenceType")).toBe("DAMAGE_PHOTO")
    expect(((form as FormData).get("file") as File).name).toBe("dent.jpg")
    expect(cfg).toEqual({ headers: { "Content-Type": "multipart/form-data" } })
  })

  it("an unusable file is flagged and cannot be attached", async () => {
    get.mockResolvedValue({ data: [] })
    wrap(<WhseReceiptDocuments shipmentId="S1" />)
    await screen.findByText("Nothing attached yet.")
    fireEvent.change(screen.getByLabelText("File"), { target: { files: [new File(["x"], "a.exe", { type: "application/x-msdownload" })] } })
    expect(await screen.findByText(/Attach a photo, PDF/)).toBeTruthy()
    expect((screen.getByText("Attach") as HTMLButtonElement).disabled).toBe(true)
  })

  it("a reader who cannot upload sees the list but no upload form", async () => {
    canUpload = false
    get.mockResolvedValue({ data: [doc()] })
    wrap(<WhseReceiptDocuments shipmentId="S1" />)
    await screen.findByText("delivery-note.pdf")
    expect(screen.queryByText("Attach")).toBeNull()
  })

  it("opening a document downloads it from the shipment's own address", async () => {
    get.mockImplementation(async (url: string) => (url.endsWith("/download") ? { data: new Blob(["pdf"]) } : { data: [doc()] }))
    ;(URL as unknown as { createObjectURL: () => string }).createObjectURL = () => "blob:x"
    ;(URL as unknown as { revokeObjectURL: () => void }).revokeObjectURL = () => {}
    wrap(<WhseReceiptDocuments shipmentId="S1" />)
    fireEvent.click(await screen.findByText("delivery-note.pdf"))
    await waitFor(() => expect(get).toHaveBeenCalledWith(`${BASE}/E1/download`, { responseType: "blob" }))
  })

  it("a failed download says so", async () => {
    get.mockImplementation(async (url: string) => { if (url.endsWith("/download")) throw new Error("boom"); return { data: [doc()] } })
    wrap(<WhseReceiptDocuments shipmentId="S1" />)
    fireEvent.click(await screen.findByText("delivery-note.pdf"))
    expect(await screen.findByText("Could not download that document.")).toBeTruthy()
  })
})
