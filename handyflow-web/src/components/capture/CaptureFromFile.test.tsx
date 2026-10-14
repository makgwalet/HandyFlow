import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import CaptureFromFile from "./CaptureFromFile"

const api = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
afterEach(cleanup)
beforeEach(() => api.post.mockReset())

const pdf = () => new File(["%PDF-1.4"], "inv.pdf", { type: "application/pdf" })
const cap = { textFound: true, supplierName: "ACME", documentNumber: "I1", documentDate: null, dueDate: null, total: 10, vatAmount: null, supplierVatNumber: null, notes: ["Check it."] }

describe("CaptureFromFile", () => {
  it("sends the pdf, hands the fields back and shows the notes", async () => {
    api.post.mockResolvedValue({ data: { data: cap } })
    const onRead = vi.fn()
    render(<CaptureFromFile onRead={onRead} />)
    fireEvent.change(screen.getByLabelText("Invoice or receipt PDF"), { target: { files: [pdf()] } })
    await waitFor(() => expect(onRead).toHaveBeenCalledWith(cap))
    expect(api.post.mock.calls[0][0]).toBe("/api/v1/capture/extract")
    expect(screen.getByText("Check it.")).toBeTruthy()
  })
  it("does not fill anything when no text was found", async () => {
    api.post.mockResolvedValue({ data: { data: { ...cap, textFound: false, notes: ["This is a photo or scan."] } } })
    const onRead = vi.fn()
    render(<CaptureFromFile onRead={onRead} />)
    fireEvent.change(screen.getByLabelText("Invoice or receipt PDF"), { target: { files: [pdf()] } })
    await waitFor(() => expect(screen.getByText("This is a photo or scan.")).toBeTruthy())
    expect(onRead).not.toHaveBeenCalled()
  })
  it("refuses a non-pdf without calling the server", async () => {
    render(<CaptureFromFile onRead={vi.fn()} />)
    fireEvent.change(screen.getByLabelText("Invoice or receipt PDF"), { target: { files: [new File(["x"], "a.jpg", { type: "image/jpeg" })] } })
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/Only PDF/))
    expect(api.post).not.toHaveBeenCalled()
  })
})
