import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import DriverDocuments from "./DriverDocuments"
import { docName, docTone, docsFor, uploadProblem, type DriverDoc } from "./driverDocuments.logic"

const get = vi.fn(), post = vi.fn()
let manage = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => manage }))
const doc = (o: Partial<DriverDoc>): DriverDoc => ({ id: "d", type: "OTHER", title: null, documentNumber: null, issueDate: null, expiryDate: null, version: 1, fileName: "f.pdf", expired: false, daysToExpiry: null, ...o })
const show = (group: "documents" | "medical") => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><DriverDocuments driverId="dr1" group={group} /></QueryClientProvider>)
beforeEach(() => { get.mockReset(); post.mockReset(); manage = true })
afterEach(cleanup)

describe("driver document rules", () => {
  const ok = { type: "application/pdf", size: 1000 }
  it("asks for what each kind needs", () => {
    expect(uploadProblem({ type: "TRAINING_CERTIFICATE", title: " ", issueDate: "", expiryDate: "" }, ok)).toBe("Name the course.")
    expect(uploadProblem({ type: "MEDICAL_CERTIFICATE", title: "", issueDate: "", expiryDate: "" }, ok)).toContain("expiry date")
    expect(uploadProblem({ type: "PRDP", title: "", issueDate: "2026-05-01", expiryDate: "2026-04-01" }, ok)).toContain("before the issue date")
    expect(uploadProblem({ type: "PRDP", title: "", issueDate: "", expiryDate: "" }, null)).toBe("Choose a file.")
    expect(uploadProblem({ type: "PRDP", title: "", issueDate: "", expiryDate: "" }, { type: "text/plain", size: 5 })).toContain("PDF, JPEG")
    expect(uploadProblem({ type: "PRDP", title: "", issueDate: "", expiryDate: "" }, ok)).toBeNull()
  })
  it("lists the tab's kinds, soonest expiry first, and names courses by title", () => {
    const rows = docsFor([doc({ id: "a", type: "PRDP", daysToExpiry: 200 }), doc({ id: "b", type: "ID_COPY" }), doc({ id: "c", type: "DRIVING_LICENCE", daysToExpiry: -3 }), doc({ id: "m", type: "MEDICAL_CERTIFICATE", daysToExpiry: 5 })], "documents")
    expect(rows.map(r => r.id)).toEqual(["c", "a", "b"])
    expect(docName(doc({ type: "TRAINING_CERTIFICATE", title: "Defensive driving" }))).toBe("Defensive driving"); expect(docName(doc({ type: "PRDP" }))).toBe("PrDP")
    expect(docTone({ daysToExpiry: -1 })).toBe("bad"); expect(docTone({ daysToExpiry: 29 })).toBe("warn"); expect(docTone({ daysToExpiry: 30 })).toBe("ok"); expect(docTone({ daysToExpiry: null })).toBe("none")
  })
})

describe("DriverDocuments", () => {
  it("shows the medical certificate and courses with their expiry", async () => {
    get.mockResolvedValue({ data: { data: [doc({ id: "m", type: "MEDICAL_CERTIFICATE", expiryDate: "2026-11-01", daysToExpiry: 12 }), doc({ id: "t", type: "TRAINING_CERTIFICATE", title: "Defensive driving", expiryDate: "2025-01-01", daysToExpiry: -40, expired: true })] } })
    show("medical")
    expect((await screen.findByLabelText("Medical certificate")).textContent).toContain("12 days left")
    expect(screen.getByLabelText("Defensive driving").textContent).toContain("Expired 40 days ago")
  })
  it("says so when nothing is on file", async () => {
    get.mockResolvedValue({ data: { data: [] } }); show("documents")
    expect((await screen.findByRole("status")).textContent).toContain("No documents on file yet")
  })
  it("uploads a training certificate with its course name", async () => {
    get.mockResolvedValue({ data: { data: [] } }); post.mockResolvedValue({ data: { data: {} } }); show("medical")
    await screen.findByRole("status")
    fireEvent.change(screen.getByLabelText("Kind of document"), { target: { value: "TRAINING_CERTIFICATE" } })
    fireEvent.change(screen.getByLabelText("Course"), { target: { value: "First aid" } })
    fireEvent.change(screen.getByLabelText("Document file"), { target: { files: [new File(["x"], "aid.pdf", { type: "application/pdf" })] } })
    fireEvent.click(screen.getByRole("button", { name: "Add" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0] as [string, FormData]
    expect(url).toBe("/api/v1/fleet/drivers/dr1/documents"); expect(body.get("type")).toBe("TRAINING_CERTIFICATE"); expect(body.get("title")).toBe("First aid")
  })
  it("keeps Add switched off until the form is valid, and hides it from read-only users", async () => {
    get.mockResolvedValue({ data: { data: [] } }); show("medical"); await screen.findByRole("status")
    expect((screen.getByRole("button", { name: "Add" }) as HTMLButtonElement).disabled).toBe(true)
    cleanup(); manage = false; show("medical"); await screen.findByRole("status"); expect(screen.queryByRole("button", { name: "Add" })).toBeNull()
  })
})
