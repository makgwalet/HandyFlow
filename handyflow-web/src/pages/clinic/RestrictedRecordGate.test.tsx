import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import RestrictedRecordGate from "./RestrictedRecordGate"
import { reasonProblem } from "./useRestriction"

const perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a), delete: (...a: any[]) => del(...a) } }))

const status = (o: object) => ({ data: { data: { restricted: true, category: "MENTAL_HEALTH", flaggedAt: null, canAccess: false, breakGlassUntil: null, ...o } } })
const show = () => render(<QueryClientProvider client={new QueryClient()}><RestrictedRecordGate patientId="p1"><div>CLINICAL CONTENT</div></RestrictedRecordGate></QueryClientProvider>)

afterEach(cleanup)
beforeEach(() => { perms.clear(); get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset() })

describe("reasonProblem", () => {
  it("needs 10 to 500 characters once trimmed", () => {
    expect(reasonProblem("short")).not.toBeNull()
    expect(reasonProblem("   short    ")).not.toBeNull()
    expect(reasonProblem("Emergency admission")).toBeNull()
    expect(reasonProblem("x".repeat(501))).not.toBeNull()
  })
})

describe("RestrictedRecordGate", () => {
  it("shows the content when the record is not restricted", async () => {
    get.mockResolvedValue(status({ restricted: false, category: null, canAccess: true }))
    show()
    expect(await screen.findByText("CLINICAL CONTENT")).toBeTruthy()
  })

  it("shows the content if the check itself fails (the server still refuses)", async () => {
    get.mockRejectedValue(new Error("down"))
    show()
    expect(await screen.findByText("CLINICAL CONTENT")).toBeTruthy()
  })

  it("hides a restricted record behind the gate", async () => {
    get.mockResolvedValue(status({}))
    show()
    expect(await screen.findByText(/This record is restricted/)).toBeTruthy()
    expect(screen.queryByText("CLINICAL CONTENT")).toBeNull()
  })

  it("refuses to open without a reason, then sends the trimmed reason", async () => {
    perms.add("CLINIC_BREAK_GLASS_VIEW")
    get.mockResolvedValue(status({}))
    post.mockResolvedValue({})
    show()
    const open = await screen.findByText("Open record for 60 minutes") as HTMLButtonElement
    expect(open.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "  Emergency admission  " } })
    expect(open.disabled).toBe(false)
    fireEvent.click(open)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/break-glass", { reason: "Emergency admission" }))
  })

  it("tells a person without the permission to ask the practice manager", async () => {
    get.mockResolvedValue(status({}))
    show()
    expect(await screen.findByText(/do not have permission to open restricted records/)).toBeTruthy()
    expect(screen.queryByText("Open record for 60 minutes")).toBeNull()
  })

  it("keeps a visible strip while the glass is broken", async () => {
    get.mockResolvedValue(status({ canAccess: true, breakGlassUntil: "2026-10-08T12:00:00Z" }))
    show()
    expect((await screen.findByRole("status")).textContent).toMatch(/every view is logged/)
    expect(screen.getByText("CLINICAL CONTENT")).toBeTruthy()
  })

  it("lets a manager restrict a record with a category and reason", async () => {
    perms.add("CLINIC_RESTRICTED_RECORD_MANAGE")
    get.mockResolvedValue(status({ restricted: false, category: null, canAccess: true }))
    put.mockResolvedValue({})
    show()
    fireEvent.click(await screen.findByText("Restrict this record"))
    fireEvent.change(screen.getByLabelText("Category"), { target: { value: "HIV" } })
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Patient asked for it" } })
    fireEvent.click(screen.getByText("Restrict"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/restriction", { category: "HIV", reason: "Patient asked for it" }))
  })

  it("does not offer restricting to people without the manage permission", async () => {
    get.mockResolvedValue(status({ restricted: false, category: null, canAccess: true }))
    show()
    await waitFor(() => expect(get).toHaveBeenCalled())
    await screen.findByText("CLINICAL CONTENT")
    expect(screen.queryByText("Restrict this record")).toBeNull()
  })
})
