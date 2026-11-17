import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EligibilityTab from "./EligibilityTab"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
let can = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => can }))

const rule = (o: any = {}) => ({ siteId: "s1", hasRule: false, maxCapacityLitres: null, maxGvmKg: null, maxLengthM: null, maxWidthM: null, maxHeightM: null, approvedTypes: [], accessNotes: null, approvedTankers: [], requireApprovedDrivers: false, requiredTraining: null, ...o })
let overrideRows: any[] = []
let ruleNow: any, approvals: any[], ratings: any[], changeovers: any[], reqRating: boolean
function mount() { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><EligibilityTab /></QueryClientProvider>) }

describe("EligibilityTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); can = true
    ruleNow = rule(); approvals = []; changeovers = []; reqRating = false
    ratings = [{ compartmentId: "k1", number: 1, capacityLitres: 10000, fuelTypes: ["DIESEL"], lastCargo: "DIESEL", lastCargoAt: "2026-11-10T08:00:00Z", lastCleanedAt: null }]
    get.mockImplementation(async (u: string) => ({ data: { data:
      u.endsWith("/customer-accounts") ? [{ id: "a1", customerName: "Gold Mine Ltd" }] : u.endsWith("/customer-accounts/a1") ? { id: "a1", sites: [{ id: "s1", name: "Mine Gate 2" }] }
      : u.endsWith("/access-rule") ? ruleNow : u.endsWith("/driver-approvals") ? approvals : u.endsWith("/tankers") ? [{ id: "t1", registration: "GP 12 AB", active: true, compartments: [] }]
      : u.endsWith("/drivers") ? [{ id: "e1", name: "Moses Sithole" }] : u.endsWith("/specs") ? { tankerType: null, gvmKg: null, lengthM: null, widthM: null, heightM: null }
      : u.endsWith("/compartment-ratings") ? ratings : u.endsWith("/cleanings") ? [] : u.endsWith("/changeover-rules") ? changeovers : u.endsWith("/eligibility/settings") ? { requireCompartmentRating: reqRating } : u.endsWith("/eligibility/overrides") ? overrideRows : null } }))
    put.mockResolvedValue({ data: { data: {} } }); post.mockResolvedValue({ data: { data: {} } }); del.mockResolvedValue({ data: { data: null } })
  })
  afterEach(cleanup)

  async function pickSite() {
    mount()
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    fireEvent.change(screen.getByLabelText("Customer"), { target: { value: "a1" } })
    await waitFor(() => screen.getByText("Mine Gate 2"))
    fireEvent.change(screen.getByLabelText("Site"), { target: { value: "s1" } })
    await screen.findByText(/Nothing is restricted at this site|This site allows/)
  }

  it("saves a site rule with the limits, types, approved tanker and driver requirement", async () => {
    await pickSite()
    fireEvent.change(screen.getByLabelText("Maximum capacity"), { target: { value: "18000" } })
    fireEvent.change(screen.getByLabelText("Tanker types"), { target: { value: "rigid" } })
    fireEvent.click(await screen.findByLabelText("Approve GP 12 AB"))
    fireEvent.click(screen.getByLabelText("Require approved drivers"))
    fireEvent.change(screen.getByLabelText("Required training"), { target: { value: "Mine induction" } })
    fireEvent.click(screen.getByText("Save site rule"))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toBe("/api/v1/fuel/customer-sites/s1/access-rule")
    expect(put.mock.calls[0][1]).toMatchObject({ maxCapacityLitres: 18000, approvedTypes: ["RIGID"], approvedTankerIds: ["t1"], requireApprovedDrivers: true, requiredTraining: "Mine induction" })
  })
  it("does not save an invalid limit", async () => {
    await pickSite()
    fireEvent.change(screen.getByLabelText("Maximum capacity"), { target: { value: "-5" } })
    fireEvent.click(screen.getByText("Save site rule"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/above zero/); expect(put).not.toHaveBeenCalled()
  })
  it("shows each driver's standing in words and adds a driver with an induction", async () => {
    approvals = [{ employeeId: "e1", driverName: "Moses Sithole", status: "APPROVED", inductionDate: "2025-01-01", inductionExpiry: "2025-12-31", trainingRef: null, trainingExpiry: null, conditions: null, state: "EXPIRED" }]
    await pickSite()
    expect(await screen.findByText("Induction expired")).toBeTruthy()
    fireEvent.click(screen.getByText("Add a driver"))
    fireEvent.change(screen.getByLabelText("Approved driver"), { target: { value: "e1" } })
    fireEvent.click(screen.getByText("Save driver"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/induction date/)
    fireEvent.change(screen.getByLabelText("Induction date"), { target: { value: "2026-11-01" } })
    fireEvent.change(screen.getByLabelText("Induction expires"), { target: { value: "2027-11-01" } })
    fireEvent.click(screen.getByText("Save driver"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/customer-sites/s1/driver-approvals/e1", expect.objectContaining({ status: "APPROVED", inductionDate: "2026-11-01", inductionExpiry: "2027-11-01" })))
  })
  it("people who can only read cannot save or add", async () => {
    can = false; await pickSite()
    expect(screen.queryByText("Save site rule")).toBeNull(); expect(screen.queryByText("Add a driver")).toBeNull()
  })

  async function pickTanker() {
    mount()
    fireEvent.click(screen.getByText("Tankers and compartments"))
    await waitFor(() => screen.getByText("GP 12 AB"))
    fireEvent.change(screen.getByLabelText("Tanker to set up"), { target: { value: "t1" } })
    await screen.findByText("Compartment 1")
  }
  it("saves a compartment's approved products and shows what it last carried", async () => {
    await pickTanker()
    expect(screen.getByText(/Last carried DIESEL on 2026-11-10/)).toBeTruthy(); expect(screen.getByText(/No cleaning recorded/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Products for compartment 1"), { target: { value: "diesel, petrol" } })
    fireEvent.click(screen.getByText("Save rating"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/tankers/t1/compartments/k1/rating", { fuelTypes: ["DIESEL", "PETROL"] }))
  })
  it("a cleaning needs evidence and goes to the compartment", async () => {
    await pickTanker()
    fireEvent.click(screen.getByText("Cleanings"))
    fireEvent.change(await screen.findByLabelText("Cleaned at"), { target: { value: "2026-11-14T09:00" } })
    fireEvent.click(screen.getByText("Record cleaning"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/certificate/); expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Certificate reference"), { target: { value: "CERT-77" } })
    fireEvent.click(screen.getByText("Record cleaning"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/fuel/tankers/t1/compartments/k1/cleanings"); expect(post.mock.calls[0][1]).toMatchObject({ certificateRef: "CERT-77", evidenceNote: null })
  })
  it("adds and removes changeover rules, and switches on the rating requirement", async () => {
    changeovers = [{ id: "r1", fromFuel: "DIESEL", toFuel: "PARAFFIN", note: null }]
    mount(); fireEvent.click(screen.getByText("Changeover rules"))
    expect(await screen.findByText(/DIESEL then PARAFFIN needs a cleaning/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("From product"), { target: { value: "petrol" } }); fireEvent.change(screen.getByLabelText("To product"), { target: { value: "petrol" } })
    fireEvent.click(screen.getByText("Add rule")); expect((await screen.findByRole("alert")).textContent).toMatch(/different/); expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("To product"), { target: { value: "jet" } }); fireEvent.click(screen.getByText("Add rule"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/changeover-rules", { fromFuel: "petrol", toFuel: "jet", note: null }))
    fireEvent.click(screen.getByText("Remove")); await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/fuel/changeover-rules/r1"))
    fireEvent.click(await screen.findByLabelText("Require ratings"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/eligibility/settings", { requireCompartmentRating: true }))
  })

  it("shows the review decisions to a person who may see them, with who decided and why", async () => {
    overrideRows = [{ id: "o1", deliveryId: "d1", tankerId: "t1", tankerRegistration: "GP 12 AB", driverEmployeeId: "e1", findings: "Induction not recorded", reason: "Inducted on paper", approvedBy: "u1", approvedByName: "Thabo M", createdAt: "2026-11-17T10:30:00Z" }]
    mount()
    fireEvent.click(await screen.findByRole("tab", { name: "Review decisions" }))
    expect(await screen.findByText(/GP 12 AB · Thabo M/)).toBeTruthy()
    expect(screen.getByText(/Inducted on paper/)).toBeTruthy()
  })

  it("does not offer the review decisions to someone without permission", async () => {
    can = false
    mount()
    await screen.findByRole("tab", { name: "Customer sites" })
    expect(screen.queryByRole("tab", { name: "Review decisions" })).toBeNull()
  })
})
