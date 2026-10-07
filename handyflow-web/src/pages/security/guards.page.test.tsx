import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const guard = (over: any = {}) => ({ id: "g1", firstName: "Ayanda", lastName: "Zulu", fullName: "Ayanda Zulu", psiraNumber: "P-1", idNumber: "900***", phone: "082 000 0001",
  grade: "C", active: true, status: "ACTIVE", statusNote: null, statusChangedAt: null, psiraExpiryDate: "2027-01-01", notes: null, photoUrl: null, createdAt: "2026-01-01T00:00:00Z",
  employeeCode: "G1", cpVettingTier: null, bankName: null, bankAccountNumber: null, bankBranchCode: null, ...over })
const row = (g: any, over: any = {}) => ({ guard: g, psiraState: "VALID", psiraDaysLeft: 200, screeningStatus: "CLEARED", lastActivityAt: null, ...over })
const result = (rows: any[], total = rows.length) => ({ rows, totalElements: total, page: 0, size: 25,
  counts: { total, byStatus: { ACTIVE: total - 1, ON_LEAVE: 1, SUSPENDED: 0, UNDER_INVESTIGATION: 0, TERMINATED: 0 }, psiraExpired: 1, psiraExpiring: 2, screeningFlagged: 0, screeningPending: 0, screeningUnscreened: 0 } })
const first = result([row(guard()), row(guard({ id: "g2", fullName: "Bafana Khumalo", firstName: "Bafana", lastName: "Khumalo" }), { psiraState: "EXPIRED", psiraDaysLeft: -3, screeningStatus: "FLAGGED" })], 60)
const get = vi.fn((_url: string) => Promise.resolve({ data: { data: first } })), patch = vi.fn((..._a: any[]) => Promise.resolve({ data: {} }))
vi.mock("../../api/client", () => ({ apiClient: { get: (u: string) => get(u), patch: (...a: any[]) => patch(...a), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
import GuardsTab from "./GuardsTab"

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><GuardsTab /></MemoryRouter></QueryClientProvider>)
afterEach(() => { cleanup(); get.mockClear(); patch.mockClear(); vi.restoreAllMocks() })
const lastUrl = () => get.mock.calls[get.mock.calls.length - 1][0]

describe("GuardsTab", () => {
  it("asks the server for the first page and shows rows, flags and counts for the whole set", async () => {
    show()
    expect(await screen.findByText("Ayanda Zulu")).toBeTruthy()
    expect(lastUrl()).toContain("/api/v1/security/guards/directory?sort=name&dir=asc&page=0&size=25")
    expect(screen.getByText("PSiRA expired")).toBeTruthy()
    expect(screen.getByText("Screening flagged")).toBeTruthy()
    expect(screen.getByText("Showing 1 to 25 of 60")).toBeTruthy()
    expect(screen.getByText(/1 expired and 2 expiring/)).toBeTruthy()
  })
  it("sorting, filtering and paging go to the server", async () => {
    show(); await screen.findByText("Ayanda Zulu")
    fireEvent.click(screen.getByText("Grade"))
    await waitFor(() => expect(lastUrl()).toContain("sort=grade&dir=asc"))
    fireEvent.change(screen.getByLabelText("Screening"), { target: { value: "FLAGGED" } })
    await waitFor(() => expect(lastUrl()).toContain("screening=FLAGGED"))
    fireEvent.click(screen.getByLabelText("Next page"))
    await waitFor(() => expect(lastUrl()).toContain("page=1"))
  })
  it("Show them filters to expired or expiring PSiRA", async () => {
    show(); await screen.findByText("Ayanda Zulu")
    fireEvent.click(screen.getByText("Show them"))
    await waitFor(() => expect(lastUrl()).toContain("psira=ATTENTION"))
  })
  it("applies a status to every selected guard and names any the server refused", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true)
    patch.mockImplementationOnce(() => Promise.resolve({ data: {} })).mockImplementationOnce(() => Promise.reject({ response: { data: { message: "Terminated guards cannot change status" } } }))
    show(); await screen.findByText("Ayanda Zulu")
    fireEvent.click(screen.getByLabelText("Select all on this page"))
    expect(screen.getByText("2 selected")).toBeTruthy()
    fireEvent.click(screen.getByText("Set status"))
    expect(await screen.findByText(/Updated 1 of 2\. Not updated: Bafana Khumalo \(Terminated guards cannot change status\)/)).toBeTruthy()
    expect(patch).toHaveBeenCalledTimes(2)
    expect((patch.mock.calls[0] as any)[0]).toBe("/api/v1/security/guards/g1/status")
    expect((patch.mock.calls[0] as any)[1]).toMatchObject({ status: "ON_LEAVE" })
  })
})
