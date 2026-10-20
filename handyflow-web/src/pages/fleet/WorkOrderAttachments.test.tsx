import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WorkOrderAttachments, { type WorkOrderAttachment } from "./WorkOrderAttachments"
import { attachmentProblem, fileSizeText, suggestedAttachmentType } from "./workOrders.logic"

const get = vi.fn(), post = vi.fn(), del = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a), delete: (...a: Args) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const files: WorkOrderAttachment[] = [
  { id: "a1", type: "QUOTATION", description: "Brake pads", fileName: "quote.pdf", contentType: "application/pdf", sizeBytes: 250_000, uploadedAt: "2026-10-01T08:00:00Z" },
  { id: "a2", type: "PHOTO", description: null, fileName: "pads.jpg", contentType: "image/jpeg", sizeBytes: 2_500_000, uploadedAt: "2026-10-02T08:00:00Z" },
]
const show = (status = "OPEN", list = files) => render(<QueryClientProvider client={new QueryClient()}><WorkOrderAttachments orderId="w1" status={status} attachments={list} /></QueryClientProvider>)
const pdf = (size = 1000) => new File([new Uint8Array(size)], "invoice.pdf", { type: "application/pdf" })
const choose = (f: File) => fireEvent.change(screen.getByLabelText("Attachment file"), { target: { files: [f] } })

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE", "FLEET_ADMIN"]); for (const m of [get, post, del]) m.mockReset()
  post.mockResolvedValue({ data: { data: {} } }); del.mockResolvedValue({ data: { data: null } }); get.mockResolvedValue({ data: new Blob(["x"]) })
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
  Object.assign(URL, { createObjectURL: vi.fn(() => "blob:x"), revokeObjectURL: vi.fn() })
})
afterEach(() => { cleanup(); vi.restoreAllMocks() })

describe("attachment rules", () => {
  it("checks type, file kind, size and the 50 limit before uploading", () => {
    const f = { name: "a.pdf", type: "application/pdf", size: 1000 }
    expect(attachmentProblem("", f, 0)).toMatch(/what this file is/); expect(attachmentProblem("INVOICE", null, 0)).toMatch(/Choose a file/)
    expect(attachmentProblem("INVOICE", { ...f, type: "application/zip" }, 0)).toMatch(/PDF/); expect(attachmentProblem("INVOICE", { ...f, size: 16 * 1024 * 1024 }, 0)).toMatch(/16.0 MB.*15 MB/)
    expect(attachmentProblem("INVOICE", { ...f, size: 0 }, 0)).toMatch(/empty/); expect(attachmentProblem("INVOICE", f, 50)).toMatch(/at most 50/); expect(attachmentProblem("INVOICE", f, 49)).toBeNull()
  })
  it("words sizes and suggests the usual type for the stage", () => {
    expect([500, 250_000, 2_500_000].map(fileSizeText)).toEqual(["500 B", "244 KB", "2.4 MB"])
    expect(["OPEN", "QUOTED", "IN_PROGRESS", "COMPLETED", "ON_HOLD"].map(suggestedAttachmentType)).toEqual(["QUOTATION", "QUOTATION", "PHOTO", "INVOICE", ""])
  })
})

describe("WorkOrderAttachments", () => {
  it("lists the files with type, size and note", () => {
    show(); expect(screen.getByText("Attachments (2)")).toBeTruthy()
    expect(screen.getByLabelText("quote.pdf").textContent).toContain("Quotation"); expect(screen.getByLabelText("quote.pdf").textContent).toContain("Brake pads"); expect(screen.getByLabelText("pads.jpg").textContent).toContain("2.4 MB")
  })
  it("preselects the usual type and uploads the file with its type and note", async () => {
    show("COMPLETED"); expect((screen.getByLabelText("Attachment type") as HTMLSelectElement).value).toBe("INVOICE")
    choose(pdf()); fireEvent.change(screen.getByLabelText("Attachment note"), { target: { value: "Final invoice" } }); fireEvent.click(screen.getByText("Attach"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0] as [string, FormData]
    expect(url).toBe("/api/v1/fleet/work-orders/w1/attachments"); expect(body.get("type")).toBe("INVOICE"); expect(body.get("description")).toBe("Final invoice"); expect((body.get("file") as File).name).toBe("invoice.pdf")
  })
  it("blocks a bad file with the reason, and shows a server refusal", async () => {
    show(); choose(new File(["x"], "x.zip", { type: "application/zip" }))
    expect((screen.getByText("Attach") as HTMLButtonElement).disabled).toBe(true); expect(screen.getByText(/Attach a PDF/)).toBeTruthy()
    post.mockRejectedValue({ response: { data: { message: "File content does not match its type" } } })
    choose(pdf()); fireEvent.click(screen.getByText("Attach"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/does not match/)
  })
  it("downloads a file through the API as a blob", async () => {
    show(); fireEvent.click(screen.getByLabelText("Download quote.pdf"))
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/fleet/work-orders/w1/attachments/a1/download", { responseType: "blob" }))
    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalled())
  })
  it("deletes only after a confirm, and only for admins", async () => {
    show(); fireEvent.click(screen.getByLabelText("Delete quote.pdf")); expect(del).not.toHaveBeenCalled()
    fireEvent.click(screen.getByLabelText("Confirm delete quote.pdf")); await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/fleet/work-orders/w1/attachments/a1"))
    cleanup(); perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); show(); expect(screen.queryByLabelText("Delete quote.pdf")).toBeNull(); expect(screen.getByLabelText("Download quote.pdf")).toBeTruthy()
  })
  it("read-only users can download but not attach", () => {
    perms = new Set(["FLEET_READ"]); show(); expect(screen.queryByText("Attach")).toBeNull(); expect(screen.getByLabelText("Download pads.jpg")).toBeTruthy()
  })
  it("says so when there are no files", () => { show("OPEN", []); expect(screen.getByText(/No files yet/)).toBeTruthy() })
})
