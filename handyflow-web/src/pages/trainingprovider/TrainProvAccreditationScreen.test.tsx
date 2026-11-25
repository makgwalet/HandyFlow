// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvAccreditationTab from "./TrainProvAccreditationTab"
import TrainProvCatalogueTab from "./TrainProvCatalogueTab"

const get = vi.fn()
const post = vi.fn()
let admin = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn(), delete: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (p: string[]) => admin || !p.includes("TRAININGPROVIDER_ADMIN"), usePermission: () => admin }))

const acc = (over: object = {}) => ({
  id: "a1", authorityId: "au1", authorityName: "Authority One", title: "Main approval", accreditationNumber: "ACC-1", status: "APPROVED", effectiveStatus: "APPROVED",
  submittedOn: null, approvalDate: "2026-01-10", expiryDate: "2029-01-10", daysToExpiry: 800, conditions: null, statusReason: null, verificationSource: "Letter",
  lastVerifiedOn: "2026-01-11", nextStatuses: ["SUSPENDED"], detailsEditable: false, liveScopes: 1, ...over,
})
const prog = (over: object = {}) => ({
  id: "p1", code: "SP-01", title: "Safety skills", programmeType: "SKILLS_PROGRAMME", trainingActivity: "ACCREDITED", deliveryMode: "CLASSROOM", qualificationId: "q1",
  qualificationTitle: "Qual", courseId: null, description: null, status: "ACTIVE", retiredReason: null, approvedVersionNo: 1, liveOfferings: 1, advertisableAsAccredited: false, ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => {
  admin = true; get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => {
    const data = url.endsWith("/accreditations") ? [acc(), acc({ id: "a2", title: "Old", status: "APPROVED", effectiveStatus: "EXPIRED", daysToExpiry: -9 })]
      : url.endsWith("/programmes") ? [prog(), prog({ id: "p2", title: "Short course", trainingActivity: "NON_ACCREDITED", programmeType: "NON_ACCREDITED_COURSE" })]
      : []
    return Promise.resolve({ data: { data } })
  })
})
afterEach(cleanup)

describe("accreditation screen", () => {
  it("shows an expired approval as expired, not approved", async () => {
    wrap(<TrainProvAccreditationTab />)
    await waitFor(() => expect(screen.getByText("Old")).toBeTruthy())
    expect(screen.getAllByText("Expired").length).toBeGreaterThan(0)
    expect(screen.getByText("Expired 9 days ago")).toBeTruthy()
  })
  it("refuses a new record without an authority, and sends nothing", async () => {
    wrap(<TrainProvAccreditationTab />)
    fireEvent.click(await screen.findByText("New accreditation record"))
    fireEvent.click(screen.getByText("Create"))
    expect((await screen.findByRole("alert")).textContent).toBe("Choose the authority")
    expect(post).not.toHaveBeenCalled()
  })
  it("tells the user a typed number does not make them accredited", async () => {
    wrap(<TrainProvAccreditationTab />)
    fireEvent.click(await screen.findByText("New accreditation record"))
    expect(screen.getByText(/does not make the provider accredited/)).toBeTruthy()
  })
})

describe("catalogue screen", () => {
  it("does not call an accredited programme covered unless the server says so", async () => {
    wrap(<TrainProvCatalogueTab />)
    await waitFor(() => expect(screen.getByText("Safety skills")).toBeTruthy())
    expect(screen.getByText("Accredited programme, but not covered anywhere it is offered today")).toBeTruthy()
    expect(screen.getAllByText("Non-accredited").length).toBeGreaterThan(1)
  })
  it("an accredited programme without a qualification is refused before sending", async () => {
    wrap(<TrainProvCatalogueTab />)
    fireEvent.click(await screen.findByText("New programme"))
    fireEvent.change(screen.getByLabelText("Programme code"), { target: { value: "SP-9" } })
    fireEvent.change(screen.getByLabelText("Programme title"), { target: { value: "New skills" } })
    fireEvent.click(screen.getByText("Create"))
    expect(await screen.findByText(/needs the qualification/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })
})
