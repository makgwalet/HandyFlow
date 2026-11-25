// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvPractitionersTab from "./TrainProvPractitionersTab"

const get = vi.fn()
const post = vi.fn()
let admin = false
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (p: string[]) => (p.includes("TRAININGPROVIDER_ADMIN") && p.length === 1 ? admin : true) }))

const person = (over: object = {}) => ({
  id: "r1", practitionerNo: "PRC-00001", firstNames: "Test", surname: "Person", email: null, phone: null, employmentType: "EMPLOYEE", userId: null, notes: null,
  status: "ACTIVE", inactiveReason: null, liveAppointments: 1, verifiedRoles: [], ...over,
})
const appt = (over: object = {}) => ({
  id: "x1", practitionerId: "r1", role: "ASSESSOR", programmeId: "p1", programmeTitle: "Safety skills", authorityId: null, authorityName: null, credentialRef: "REF-123",
  credentialNote: null, validFrom: null, validTo: null, verificationSource: null, verifiedOn: null, status: "ACTIVE", withdrawnReason: null, standing: "NOT_VERIFIED",
  standingNote: "", history: [], ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => {
  admin = false; get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => {
    let data: unknown = []
    if (url.endsWith("/practitioners")) data = [person()]
    else if (url.endsWith("/practitioners/r1")) data = { practitioner: person(), appointments: [appt()] }
    else if (url.endsWith("/eligible")) data = [{ practitionerId: "r1", practitionerNo: "PRC-00001", name: "Test Person", role: "ASSESSOR", appointmentId: "x1", verifiedOn: "2026-11-01" }]
    else if (url.endsWith("/programmes")) data = [{ id: "p1", code: "SP1", title: "Safety skills", trainingActivity: "ACCREDITED" }]
    return Promise.resolve({ data: { data } })
  })
  post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("practitioners screen", () => {
  it("lists people and says nobody is checked yet", async () => {
    wrap(<TrainProvPractitionersTab />)
    expect(await screen.findByText("Test Person")).toBeTruthy()
    expect(screen.getByText(/none checked/)).toBeTruthy()
  })

  it("shows an unchecked appointment honestly and records a check", async () => {
    wrap(<TrainProvPractitionersTab />)
    fireEvent.click(await screen.findByText("Test Person"))
    expect(await screen.findByText(/Nobody has recorded a check/)).toBeTruthy()
    expect(screen.getByText("Not yet checked")).toBeTruthy()
    fireEvent.click(screen.getByText("Record a check"))
    fireEvent.click(screen.getByText("Record the check"))
    expect((await screen.findByRole("alert")).textContent).toContain("what you checked")
    fireEvent.change(screen.getByLabelText("What was checked"), { target: { value: "Authority register entry" } })
    fireEvent.click(screen.getByText("Record the check"))
    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
    expect(post.mock.calls[0][0]).toContain("/appointments/x1/verify")
    expect(post.mock.calls[0][1]).toMatchObject({ verificationSource: "Authority register entry", overrideReason: null })
  })

  it("asks for a written reason when no second person can check an assessor", async () => {
    wrap(<TrainProvPractitionersTab />)
    fireEvent.click(await screen.findByText("Test Person"))
    fireEvent.click(await screen.findByText("Record a check"))
    fireEvent.change(screen.getByLabelText("What was checked"), { target: { value: "Authority register entry" } })
    fireEvent.click(screen.getByLabelText(/no second person can check it/))
    fireEvent.click(screen.getByText("Record the check"))
    expect((await screen.findByRole("alert")).textContent).toContain("second person")
    expect(post).not.toHaveBeenCalled()
  })

  it("only an administrator sees the option to make a person inactive", async () => {
    wrap(<TrainProvPractitionersTab />)
    fireEvent.click(await screen.findByText("Test Person"))
    await screen.findByText("Appointments")
    expect(screen.queryByText("Make inactive")).toBeNull()
    cleanup(); admin = true
    wrap(<TrainProvPractitionersTab />)
    fireEvent.click(await screen.findByText("Test Person"))
    expect(await screen.findByText("Make inactive")).toBeTruthy()
  })

  it("lists who can be used for a programme", async () => {
    wrap(<TrainProvPractitionersTab />)
    fireEvent.click(await screen.findByText("Who can be used"))
    await screen.findByText("SP1 · Safety skills")
    fireEvent.change(screen.getByLabelText("Programme"), { target: { value: "p1" } })
    expect(await screen.findByText("Test Person")).toBeTruthy()
    expect(screen.getByText(/does not say anyone is competent/)).toBeTruthy()
  })
})
