// @vitest-environment jsdom
import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const guard = (over: Record<string, unknown> = {}) => ({ id: "g1", firstName: "Ayanda", lastName: "Zulu", fullName: "Ayanda Zulu", psiraNumber: "P-1", idNumber: "900***", phone: "0821234567",
  grade: "C", active: true, status: "ACTIVE", statusNote: null, statusChangedAt: null, psiraExpiryDate: "2027-01-01", notes: null, photoUrl: null, createdAt: "2026-01-01T00:00:00Z",
  employeeCode: "G1", cpVettingTier: null, bankName: null, bankAccountNumber: null, bankBranchCode: null, ...over })
const row = (g: unknown) => ({ guard: g, psiraState: "VALID", psiraDaysLeft: 200, screeningStatus: "CLEARED", lastActivityAt: null })
const rows = [row(guard()), row(guard({ id: "g2", fullName: "Bafana Khumalo", firstName: "Bafana", lastName: "Khumalo", employeeCode: "G2" }))]
const dir = { rows, totalElements: 2, page: 0, size: 25, counts: { total: 2, byStatus: { ACTIVE: 2, ON_LEAVE: 0, SUSPENDED: 0, UNDER_INVESTIGATION: 0, TERMINATED: 0 }, psiraExpired: 0, psiraExpiring: 0, screeningFlagged: 0 } }
const get = vi.fn<(u: string) => Promise<unknown>>(() => Promise.resolve({ data: { data: dir } }))
const post = vi.fn<(...a: unknown[]) => Promise<unknown>>(() => Promise.resolve({ data: { data: {} } }))
const perms = new Set<string>(["SECURITY_MANAGE"])
vi.mock("../../api/client", () => ({ apiClient: { get: (u: string) => get(u), post: (...a: unknown[]) => post(...a), patch: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
import GuardsTab from "./GuardsTab"

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><GuardsTab /></MemoryRouter></QueryClientProvider>)
afterEach(() => { cleanup(); get.mockClear(); post.mockReset(); post.mockResolvedValue({ data: { data: {} } }); perms.clear(); perms.add("SECURITY_MANAGE"); vi.restoreAllMocks() })

describe("Guard app access (bulk)", () => {
  it("sends the selected ids once confirmed, then shows temporary PINs and a download", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true)
    post.mockResolvedValue({ data: { data: { provisioned: 1, skipped: 1, failed: 0, moreRemaining: false, results: [
      { guardId: "g1", fullName: "Ayanda Zulu", employeeCode: "G1", phone: "0821234567", outcome: "PROVISIONED", temporaryPin: "482915" },
      { guardId: "g2", fullName: "Bafana Khumalo", employeeCode: "G2", outcome: "SKIPPED_ALREADY_ENROLLED" }] } } })
    show()
    await screen.findByText("Ayanda Zulu")
    fireEvent.click(screen.getByLabelText("Select all on this page"))
    fireEvent.click(screen.getByText("Give app access"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/security/guards/app-access/bulk", { guardIds: ["g1", "g2"], resetExisting: false }))
    expect(await screen.findByText("1 given access, 1 skipped.")).toBeTruthy()
    expect(screen.getByText("482915")).toBeTruthy()
    expect(screen.getByText("Already has access")).toBeTruthy()
    expect(screen.getByText("Download PIN sheet")).toBeTruthy()
    expect(screen.getByRole("note").textContent).toMatch(/shown once/)
  })

  it("does nothing when the confirmation is declined", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(false)
    show()
    await screen.findByText("Ayanda Zulu")
    fireEvent.click(screen.getByLabelText("Select all on this page"))
    fireEvent.click(screen.getByText("Give app access"))
    expect(post).not.toHaveBeenCalled()
  })

  it("is not offered without SECURITY_MANAGE", async () => {
    perms.clear()
    show()
    await screen.findByText("Ayanda Zulu")
    fireEvent.click(screen.getByLabelText("Select all on this page"))
    expect(screen.queryByText("Give app access")).toBeNull()
  })

  it("keeps the batches that worked when a later one fails", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true)
    post.mockRejectedValue({ response: { data: { message: "Too many requests" } } })
    show()
    await screen.findByText("Ayanda Zulu")
    fireEvent.click(screen.getByLabelText("Select all on this page"))
    fireEvent.click(screen.getByText("Give app access"))
    expect(await screen.findByText("Too many requests")).toBeTruthy()
    expect(screen.queryByRole("dialog", { name: "App access result" })).toBeNull()
  })
})

describe("Guard app access (single)", () => {
  const open = async () => { show(); await screen.findByText("Ayanda Zulu"); fireEvent.click(screen.getAllByTitle("Edit")[0]) }

  it("the edit form hints at the ID number on file; the add form shows an example", async () => {
    await open()
    const edit = await screen.findByPlaceholderText("900*** (leave blank to keep)")
    expect((edit as HTMLInputElement).value).toBe("")
    cleanup()
    show()
    await screen.findByText("Ayanda Zulu")
    fireEvent.click(screen.getByText("Add Guard"))
    expect(await screen.findByPlaceholderText("8501015026088")).toBeTruthy()
  })

  it("blank PIN: asks the server to generate one and shows it once", async () => {
    post.mockResolvedValue({ data: { data: { guardId: "g1", fullName: "Ayanda Zulu", temporaryPin: "739104" } } })
    await open()
    fireEvent.click(await screen.findByText("Give app access"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/security/guards/g1/enrol", {}))
    expect(await screen.findByText("739104")).toBeTruthy()
  })

  it("a typed PIN is sent as given and nothing is shown back", async () => {
    post.mockResolvedValue({ data: { data: { guardId: "g1", fullName: "Ayanda Zulu", temporaryPin: null } } })
    await open()
    fireEvent.change(await screen.findByLabelText("PIN (optional)"), { target: { value: "482915" } })
    fireEvent.click(screen.getByText("Give app access"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/security/guards/g1/enrol", { pin: "482915" }))
    expect(screen.queryByText(/Temporary PIN\./)).toBeNull()
  })

  it("a half-typed PIN cannot be submitted", async () => {
    await open()
    fireEvent.change(await screen.findByLabelText("PIN (optional)"), { target: { value: "123" } })
    expect((screen.getByText("Give app access") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByText("Give app access"))
    expect(post).not.toHaveBeenCalled()
  })
})
