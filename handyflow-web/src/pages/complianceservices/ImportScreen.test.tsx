// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ImportPage from "./ImportPage"
import { discardProblem, fileProblem, summary } from "./importRules"

const get = vi.fn()
const post = vi.fn()
let admin = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (p: string[]) => (p.length === 1 ? admin : true), usePermission: () => admin }))

const batch = (over: object = {}) => ({ id: "b1", kind: "CLIENTS", fileName: "clients.csv", status: "PREVIEWED", totalRows: 3, okRows: 1, duplicateRows: 1, rejectedRows: 1, importedRows: 0, createdAt: "2026-11-25T09:00:00Z", committedAt: null, discardNote: null, ...over })
const detail = (over: object = {}) => ({
  batch: batch(over), columns: ["name", "email", "phone", "notes"], rowsTruncated: false,
  rows: [
    { rowNumber: 3, fields: ["Bad", "nope", "", ""], status: "REJECTED", message: "The email address is not valid", targetId: null },
    { rowNumber: 4, fields: ["Acme", "", "", ""], status: "DUPLICATE", message: "A client with this name already exists", targetId: null },
    { rowNumber: 2, fields: ["Beta", "", "", ""], status: "OK", message: null, targetId: null },
  ],
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const chooseFile = (name = "clients.csv") => fireEvent.change(screen.getByLabelText("CSV file"), { target: { files: [new File(["name\nBeta"], name, { type: "text/csv" })] } })

beforeEach(() => { admin = true; get.mockReset(); post.mockReset(); get.mockResolvedValue({ data: { data: [] } }) })
afterEach(cleanup)

describe("import rules", () => {
  it("file and reason checks", () => {
    expect(fileProblem(null)).not.toBeNull()
    expect(fileProblem({ name: "a.xlsx", size: 10 })).not.toBeNull()
    expect(fileProblem({ name: "a.csv", size: 3_000_000 })).not.toBeNull()
    expect(fileProblem({ name: "A.CSV", size: 10 })).toBeNull()
    expect(discardProblem("short")).not.toBeNull()
    expect(summary(batch({ status: "COMMITTED", importedRows: 1 }) as never)).toContain("1 created")
  })
})

describe("import page", () => {
  it("refuses a non-CSV file without calling the server", async () => {
    wrap(<ImportPage />)
    chooseFile("clients.xlsx")
    fireEvent.click(screen.getByText("Check the file"))
    await waitFor(() => expect(document.body.textContent).toContain("Save the spreadsheet as a CSV"))
    expect(post).not.toHaveBeenCalled()
  })
  it("shows the preview with problems first and nothing is imported yet", async () => {
    post.mockResolvedValue({ data: { data: detail() } })
    wrap(<ImportPage />)
    chooseFile()
    fireEvent.click(screen.getByText("Check the file"))
    await waitFor(() => expect(document.body.textContent).toContain("1 ready, 1 already there, 1 with a problem"))
    const rows = Array.from(document.querySelectorAll("tbody tr")).map(r => r.textContent ?? "")
    expect(rows[0]).toContain("The email address is not valid")
    expect(post).toHaveBeenCalledTimes(1)
    expect(String(post.mock.calls[0][0])).toContain("/preview?kind=CLIENTS")
  })
  it("an administrator imports the ready rows", async () => {
    post.mockResolvedValueOnce({ data: { data: detail() } }).mockResolvedValueOnce({ data: { data: detail({ status: "COMMITTED", importedRows: 1 }) } })
    wrap(<ImportPage />)
    chooseFile(); fireEvent.click(screen.getByText("Check the file"))
    fireEvent.click(await screen.findByText("Import 1 row"))
    await waitFor(() => expect(post).toHaveBeenLastCalledWith("/api/v1/compliance-services/imports/b1/commit", {}))
    await waitFor(() => expect(document.body.textContent).toContain("1 created"))
  })
  it("someone who is not an administrator cannot import", async () => {
    admin = false
    post.mockResolvedValue({ data: { data: detail() } })
    wrap(<ImportPage />)
    chooseFile(); fireEvent.click(screen.getByText("Check the file"))
    await screen.findByText("An administrator imports the rows that are ready.")
    expect(screen.queryByText("Import 1 row")).toBeNull()
  })
  it("setting a file aside needs a reason", async () => {
    post.mockResolvedValue({ data: { data: detail() } })
    wrap(<ImportPage />)
    chooseFile(); fireEvent.click(screen.getByText("Check the file"))
    fireEvent.click(await screen.findByText("Set this file aside"))
    fireEvent.click(screen.getByText("Set aside"))
    await waitFor(() => expect(document.body.textContent).toContain("at least 10 characters"))
    expect(post).toHaveBeenCalledTimes(1)
  })
})
