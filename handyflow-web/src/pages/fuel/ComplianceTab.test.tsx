import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ComplianceTab from "./ComplianceTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn(); const del = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const item = (o: any = {}) => ({ id: "c1", itemType: "CERTIFICATE", title: "Roadworthy", referenceNo: "RW-1", subjectType: "TANKER", subjectId: "t1", subjectLabel: "GP 12 AB", issuedOn: null, expiresOn: "2026-11-01", notes: null, status: "EXPIRED", daysToExpiry: -10, ...o })
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><ComplianceTab /></QueryClientProvider>)
}

describe("ComplianceTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); perms = ["FUEL_MANAGE"]
    post.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.endsWith("/compliance")) return { data: { data: [item({ id: "c2", title: "Calibration", status: "VALID", daysToExpiry: 200, expiresOn: "2027-05-30", subjectType: "COMPANY", subjectLabel: "Company" }), item()] } }
      if (u.endsWith("/tankers")) return { data: { data: [{ id: "t1", registration: "GP 12 AB" }] } }
      return { data: { data: [] } }
    })
  })
  afterEach(cleanup)

  it("lists the most urgent first and warns about expired items", async () => {
    mount()
    expect((await screen.findByRole("status")).textContent).toMatch(/1 expired/)
    const titles = screen.getAllByText(/Roadworthy|Calibration/).map(e => e.textContent)
    expect(titles[0]).toMatch(/Roadworthy/)
    expect(screen.getByText("expired 10 days ago")).toBeTruthy()
  })

  it("renewal must go later than the current expiry", async () => {
    mount()
    await screen.findByText("Roadworthy")
    fireEvent.click(screen.getAllByText("Renew")[0])
    fireEvent.change(screen.getByLabelText("New expiry"), { target: { value: "2026-10-01" } })
    fireEvent.click(screen.getAllByText("Renew").pop()!)
    expect((await screen.findByRole("alert")).textContent).toMatch(/later than/)
    expect(post).not.toHaveBeenCalled()
  })

  it("renews with a later date", async () => {
    mount()
    await screen.findByText("Roadworthy")
    fireEvent.click(screen.getAllByText("Renew")[0])
    fireEvent.change(screen.getByLabelText("New expiry"), { target: { value: "2027-11-01" } })
    fireEvent.click(screen.getAllByText("Renew").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toMatch(/c1\/renew/)
    expect(post.mock.calls[0][1]).toMatchObject({ newExpiresOn: "2027-11-01" })
  })

  it("a tanker item needs a tanker chosen", async () => {
    mount()
    await screen.findByText("Roadworthy")
    fireEvent.click(screen.getByText("Add item"))
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Insurance" } })
    fireEvent.change(screen.getByLabelText("Expires"), { target: { value: "2027-01-01" } })
    fireEvent.click(screen.getByText("Save"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/Choose which tanker/)
  })

  it("read-only users cannot add or renew", async () => {
    perms = ["FUEL_READ"]
    mount()
    await screen.findByText("Roadworthy")
    expect(screen.queryByText("Add item")).toBeNull()
    expect(screen.queryByText("Renew")).toBeNull()
  })
})
