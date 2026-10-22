import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import CaseEvidence from "./CaseEvidence"
import { MAX_BYTES, MAX_PER_CASE, extensionOf, fileProblem, removeProblem, sizeText, type CaseFile } from "./erEvidence.logic"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const file = (over: Partial<CaseFile> = {}): CaseFile => ({
  id: "f1", fileName: "witness.pdf", contentType: "application/pdf", fileSizeBytes: 2048, evidenceType: "STATEMENT", uploadedByName: "Hanna", createdAt: "2026-10-20T08:00:00Z", ...over,
})
const pdf = (name = "witness.pdf", size = 1000) => new File([new Uint8Array(size)], name, { type: "application/pdf" })

beforeEach(() => { get.mockReset(); post.mockReset() })
afterEach(cleanup)

describe("file checks", () => {
  it("accepts a good file and explains each refusal", () => {
    const f = { name: "a.pdf", size: 1000 }
    expect(fileProblem(f, "STATEMENT", 0, true)).toBe("")
    expect(fileProblem(f, "STATEMENT", 0, false)).toMatch(/closed/)
    expect(fileProblem(f, "", 0, true)).toMatch(/kind/)
    expect(fileProblem(null, "EMAIL", 0, true)).toMatch(/Choose the file/)
    expect(fileProblem({ name: "a.pdf", size: 0 }, "EMAIL", 0, true)).toMatch(/empty/)
    expect(fileProblem({ name: "a.pdf", size: MAX_BYTES + 1 }, "EMAIL", 0, true)).toMatch(/too large/)
    expect(fileProblem({ name: "a.pdf", size: MAX_BYTES }, "EMAIL", 0, true)).toBe("")
    expect(fileProblem(f, "EMAIL", MAX_PER_CASE, true)).toMatch(/at most 50/)
  })
  it("refuses pages, scripts and programs", () => {
    for (const n of ["x.html", "x.svg", "x.js", "x.exe", "x"]) expect(fileProblem({ name: n, size: 10 }, "EMAIL", 0, true)).toMatch(/not accepted/)
    expect(fileProblem({ name: "PHOTO.JPG", size: 10 }, "PHOTO", 0, true)).toBe("")
  })
  it("reads extensions, sizes and reasons", () => {
    expect(extensionOf("a.b.PDF")).toBe("pdf")
    expect(extensionOf("noext")).toBe("")
    expect(sizeText(500)).toBe("500 B")
    expect(sizeText(2048)).toBe("2 KB")
    expect(sizeText(3 * 1024 * 1024)).toBe("3.0 MB")
    expect(removeProblem(" ")).toMatch(/reason/)
    expect(removeProblem("x".repeat(301))).toMatch(/too long/)
    expect(removeProblem("Wrong case")).toBe("")
  })
})

describe("CaseEvidence", () => {
  it("lists the files with their kind, size and who added them", async () => {
    get.mockResolvedValue({ data: { data: [file()] } })
    wrap(<CaseEvidence caseId="c1" canManage open />)
    expect(await screen.findByText("witness.pdf")).toBeTruthy()
    expect(screen.getByText(/Statement · 2 KB/)).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/hr/employee-relations/cases/c1/evidence")
  })

  it("leaves the section out when the files cannot be read", async () => {
    get.mockRejectedValue({ response: { status: 403 } })
    const { container } = wrap(<CaseEvidence caseId="c1" canManage open />)
    await waitFor(() => expect(get).toHaveBeenCalled())
    await waitFor(() => expect(container.textContent).not.toMatch(/Evidence files/))
  })

  it("uploads the chosen file with its kind", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    post.mockResolvedValue({ data: {} })
    wrap(<CaseEvidence caseId="c1" canManage open />)
    await screen.findByText(/No files are attached/)
    expect((screen.getByRole("button", { name: "Add the file" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Kind/), { target: { value: "EMAIL" } })
    fireEvent.change(screen.getByLabelText(/^File/), { target: { files: [pdf("mail.pdf")] } })
    fireEvent.click(screen.getByRole("button", { name: "Add the file" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0]
    expect(url).toBe("/api/v1/hr/employee-relations/cases/c1/evidence")
    expect((body as FormData).get("kind")).toBe("EMAIL")
    expect(((body as FormData).get("file") as File).name).toBe("mail.pdf")
    expect(await screen.findByText("The file was added to the case.")).toBeTruthy()
  })

  it("will not offer a page as evidence", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    wrap(<CaseEvidence caseId="c1" canManage open />)
    await screen.findByText(/No files are attached/)
    fireEvent.change(screen.getByLabelText(/Kind/), { target: { value: "DOCUMENT" } })
    fireEvent.change(screen.getByLabelText(/^File/), { target: { files: [pdf("page.html")] } })
    expect(screen.getByRole("alert").textContent).toMatch(/not accepted/)
    expect((screen.getByRole("button", { name: "Add the file" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("shows the server's message when the upload is refused", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    post.mockRejectedValue({ response: { data: { message: "The file does not look like a .pdf file." } } })
    wrap(<CaseEvidence caseId="c1" canManage open />)
    await screen.findByText(/No files are attached/)
    fireEvent.change(screen.getByLabelText(/Kind/), { target: { value: "EMAIL" } })
    fireEvent.change(screen.getByLabelText(/^File/), { target: { files: [pdf()] } })
    fireEvent.click(screen.getByRole("button", { name: "Add the file" }))
    expect(await screen.findByText("The file does not look like a .pdf file.")).toBeTruthy()
  })

  it("takes a file off with a reason", async () => {
    get.mockResolvedValue({ data: { data: [file()] } })
    post.mockResolvedValue({ data: {} })
    wrap(<CaseEvidence caseId="c1" canManage open />)
    fireEvent.click(await screen.findByRole("button", { name: "Remove witness.pdf" }))
    expect((screen.getByRole("button", { name: "Take off the case" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Why is the file/), { target: { value: "Uploaded to the wrong case" } })
    fireEvent.click(screen.getByRole("button", { name: "Take off the case" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/employee-relations/cases/c1/evidence/f1/remove", { reason: "Uploaded to the wrong case" }))
  })

  it("is read-only for someone who cannot manage cases, and for a closed case", async () => {
    get.mockResolvedValue({ data: { data: [file()] } })
    wrap(<CaseEvidence caseId="c1" canManage={false} open />)
    await screen.findByText("witness.pdf")
    expect(screen.queryByRole("button", { name: "Add the file" })).toBeNull()
    expect(screen.queryByRole("button", { name: /^Remove/ })).toBeNull()
    cleanup()
    wrap(<CaseEvidence caseId="c1" canManage open={false} />)
    await screen.findByText("witness.pdf")
    expect(screen.queryByRole("button", { name: "Add the file" })).toBeNull()
    expect(screen.queryByRole("button", { name: /^Remove/ })).toBeNull()
  })

  it("downloads a file", async () => {
    get.mockImplementation((url: string) => url.endsWith("/download") ? Promise.resolve({ data: new Blob(["x"]) }) : Promise.resolve({ data: { data: [file()] } }))
    URL.createObjectURL = vi.fn(() => "blob:x")
    URL.revokeObjectURL = vi.fn()
    wrap(<CaseEvidence caseId="c1" canManage open />)
    fireEvent.click(await screen.findByRole("button", { name: "Download witness.pdf" }))
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/hr/employee-relations/cases/c1/evidence/f1/download", { responseType: "blob" }))
  })
})
