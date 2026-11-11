import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ComplianceTab from "./ComplianceTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn(); const del = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const item = (o: any = {}) => ({ id: "c1", itemType: "CERTIFICATE", title: "Roadworthy", referenceNo: "RW-1", subjectType: "TANKER", subjectId: "t1", subjectLabel: "GP 12 AB", issuedOn: null, expiresOn: "2026-11-01", notes: null, status: "EXPIRED", daysToExpiry: -10, ...o })
let coverage: any = { entities: [], unlinkedLicences: 0 }; let evidence: any[] = []
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><ComplianceTab /></QueryClientProvider>)
}

describe("ComplianceTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); perms = ["FUEL_MANAGE"]
    coverage = { entities: [], unlinkedLicences: 0 }; evidence = []
    post.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.endsWith("/compliance")) return { data: { data: [item({ id: "c2", title: "Calibration", status: "VALID", daysToExpiry: 200, expiresOn: "2027-05-30", subjectType: "COMPANY", subjectLabel: "Company" }), item()] } }
      if (u.endsWith("/coverage")) return { data: { data: coverage } }
      if (u.endsWith("/legal-entities")) return { data: { data: [{ id: "e1", name: "Acme Fuels (Pty) Ltd" }] } }
      if (u.endsWith("/evidence")) return { data: { data: evidence } }
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

  it("shows licences by legal entity with the unlinked count and a not-advice note", async () => {
    coverage = { entities: [{ legalEntityId: "e1", name: "Acme Fuels (Pty) Ltd", valid: 2, expiring: 1, expired: 0, activities: ["WHOLESALE", "STORAGE"] }], unlinkedLicences: 3 }
    mount()
    expect(await screen.findByText("Licences by legal entity")).toBeTruthy()
    expect(screen.getByText(/2 valid, 1 expiring, 0 expired/)).toBeTruthy()
    expect(screen.getByText(/3 licences are not linked/)).toBeTruthy()
    expect(screen.getByText(/does not know which licences/)).toBeTruthy()
  })

  it("links a licence to a legal entity, activity and authority when adding", async () => {
    mount()
    await screen.findByText("Roadworthy")
    fireEvent.click(screen.getByText("Add item"))
    fireEvent.change(screen.getByLabelText("For"), { target: { value: "COMPANY" } })
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Wholesale licence" } })
    fireEvent.change(screen.getByLabelText("Expires"), { target: { value: "2027-01-01" } })
    await waitFor(() => screen.getByText("Acme Fuels (Pty) Ltd"))
    fireEvent.change(screen.getByLabelText("Legal entity"), { target: { value: "e1" } })
    fireEvent.change(screen.getByLabelText("Licensed activity"), { target: { value: "WHOLESALE" } })
    fireEvent.change(screen.getByLabelText("Issuing authority"), { target: { value: " Regulator X " } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ legalEntityId: "e1", licensedActivity: "WHOLESALE", issuingAuthority: "Regulator X", subjectType: "COMPANY" })
  })

  it("adds evidence and replaces it with a reason; read-only users can only look", async () => {
    evidence = [{ id: "v1", itemId: "c1", fileRef: "scans/rw1.pdf", note: null, capturedAt: "2026-11-01T08:00:00Z", status: "ACTIVE", supersededReason: null }]
    mount()
    await screen.findByText("Roadworthy")
    fireEvent.click(screen.getAllByText(/^Evidence \(/)[0])
    await screen.findByText("scans/rw1.pdf")
    fireEvent.click(screen.getByText("Add evidence"))
    expect((await screen.findAllByRole("alert")).some(a => /file link/.test(a.textContent ?? ""))).toBe(true)
    fireEvent.change(screen.getByLabelText("File link or reference *"), { target: { value: "scans/rw2.pdf" } })
    fireEvent.click(screen.getByText("Add evidence"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/compliance/c1/evidence", { fileRef: "scans/rw2.pdf", note: null }))
    fireEvent.click(screen.getByText("Replace"))
    fireEvent.change(await screen.findByLabelText(/Reason/), { target: { value: "Wrong scan uploaded" } })
    fireEvent.click(screen.getAllByText("Replace").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/compliance/evidence/v1/supersede", { reason: "Wrong scan uploaded" }))
    cleanup(); perms = ["FUEL_READ"]
    mount()
    await screen.findByText("Roadworthy")
    fireEvent.click(screen.getAllByText(/^Evidence \(/)[0])
    await screen.findByText("scans/rw1.pdf")
    expect(screen.queryByText("Add evidence")).toBeNull()
    expect(screen.queryByText("Replace")).toBeNull()
  })
})
