import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import AccidentsTab from "./AccidentsTab"
import { blocksVehicle, emptyReport, reportBody, reportProblem } from "./accidents.logic"

const get = vi.fn(), post = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const acc = (o: object) => ({ id: "x", referenceNumber: "ACC-1", vehicleId: "v1", registration: "ABC123MP", driverName: "John", occurredAt: "2026-10-01T08:00:00Z", location: "N4", description: "Rear-ended",
  injuriesCount: 0, fatalitiesCount: 0, estimatedDamage: null, vehicleDrivable: true, status: "REPORTED", claimStatus: null, claimNumber: null, evidenceCount: 2, ...o })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><AccidentsTab /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => url.includes("/vehicles") ? ok({ content: [{ id: "v1", registration: "ABC123MP" }] })
    : ok({ content: [acc({ id: "a1", referenceNumber: "ACC-2026-00021", vehicleDrivable: false, status: "IN_REPAIR" }), acc({ id: "a2", referenceNumber: "ACC-2026-00022", registration: "XYZ999GP", status: "CLOSED", vehicleDrivable: false })] }))
  post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("accident rules", () => {
  it("only an open case on a vehicle that cannot be driven blocks it", () => {
    expect(blocksVehicle({ status: "IN_REPAIR", vehicleDrivable: false })).toBe(true)
    expect(blocksVehicle({ status: "WRITTEN_OFF", vehicleDrivable: false })).toBe(true)
    expect(blocksVehicle({ status: "CLOSED", vehicleDrivable: false })).toBe(false)
    expect(blocksVehicle({ status: "REPORTED", vehicleDrivable: true })).toBe(false)
  })
  it("a report needs a vehicle, a time and a description", () => {
    const f = emptyReport()
    expect(reportProblem(f)).toMatch(/vehicle/)
    expect(reportProblem({ ...f, vehicleId: "v1" })).toMatch(/when/)
    expect(reportProblem({ ...f, vehicleId: "v1", occurredAt: "2026-10-01T08:00" })).toMatch(/Describe/)
    expect(reportProblem({ ...f, vehicleId: "v1", occurredAt: "2026-10-01T08:00", description: "x", injuries: "-1" })).toMatch(/Injuries/)
  })
  it("builds the request with blanks as null", () => {
    const b = reportBody({ ...emptyReport(), vehicleId: "v1", occurredAt: "2026-10-01T08:00", description: " Hit a pole ", vehicleDrivable: false })
    expect(b.details).toMatchObject({ description: "Hit a pole", location: null, estimatedDamage: null, injuriesCount: 0, vehicleDrivable: false })
  })
})

describe("AccidentsTab", () => {
  it("names the vehicles that cannot be driven and flags only open cases", async () => {
    show()
    await screen.findByText("ACC-2026-00021")
    expect(screen.getByText(/1 vehicle cannot be driven:/)).toBeTruthy()
    expect(screen.getAllByText("Cannot be driven")).toHaveLength(1)
  })
  it("moves a case to the next stage", async () => {
    show()
    await screen.findByText("ACC-2026-00021")
    fireEvent.change(screen.getByLabelText("Move ACC-2026-00021 to"), { target: { value: "CLOSED" } })
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/accidents/a1/status", { status: "CLOSED" }))
  })
  it("shows the server's reason when a move is refused", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "Cannot move straight there" } } })
    show()
    await screen.findByText("ACC-2026-00021")
    fireEvent.change(screen.getByLabelText("Move ACC-2026-00021 to"), { target: { value: "CLOSED" } })
    expect(await screen.findByText("Cannot move straight there")).toBeTruthy()
  })
  it("reports an accident and warns when the vehicle cannot be driven", async () => {
    show()
    await screen.findByText("ACC-2026-00021")
    fireEvent.click(screen.getByText("Report an accident"))
    await screen.findByText("ABC123MP", { selector: "option" })
    fireEvent.change(screen.getByLabelText("Vehicle"), { target: { value: "v1" } })
    fireEvent.change(screen.getByLabelText("When it happened"), { target: { value: "2026-10-01T08:00" } })
    fireEvent.change(screen.getByLabelText("What happened"), { target: { value: "Hit a pole" } })
    fireEvent.click(screen.getByLabelText("The vehicle can still be driven"))
    expect(screen.getAllByText(/cannot start trips until the case is closed/).length).toBeGreaterThan(0)
    fireEvent.click(screen.getAllByText("Report accident").find(e => e.tagName === "BUTTON")!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/accidents", expect.objectContaining({ vehicleId: "v1", details: expect.objectContaining({ vehicleDrivable: false, description: "Hit a pole" }) })))
  })
  it("sends the people, other party and police details with the report", async () => {
    show()
    await screen.findByText("ACC-2026-00021")
    fireEvent.click(screen.getByText("Report an accident"))
    await screen.findByText("ABC123MP", { selector: "option" })
    const type = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } })
    type("Vehicle", "v1"); type("When it happened", "2026-10-01T08:00"); type("What happened", "Rear-ended")
    type("Weather", "HEAVY_RAIN"); type("Road condition", "WET"); type("Driver's name", "Sam Driver"); type("Witnesses", "J Smith 082 000 0000")
    type("Injuries", "2"); type("Third-party damage", "12500"); type("Other vehicle registration", "CA 123 456"); type("Other driver's name", "P Other")
    type("Other driver's contact", "083 111 2222"); type("Other insurer", "Santam"); type("Police case number", "CAS 12/10/2026"); type("SAPS station", "Midrand")
    fireEvent.click(screen.getAllByText("Report accident").find(e => e.tagName === "BUTTON")!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/accidents", expect.objectContaining({ details: expect.objectContaining({
      weather: "HEAVY_RAIN", roadCondition: "WET", driverName: "Sam Driver", witnesses: "J Smith 082 000 0000", injuriesCount: 2, thirdPartyDamage: 12500,
      otherVehicleRegistration: "CA 123 456", otherDriverName: "P Other", otherDriverContact: "083 111 2222", otherInsurer: "Santam", policeCaseNumber: "CAS 12/10/2026", sapsStation: "Midrand" }) })))
  })
  it("asks for the police case number when someone died", async () => {
    show()
    await screen.findByText("ACC-2026-00021")
    fireEvent.click(screen.getByText("Report an accident"))
    await screen.findByText("ABC123MP", { selector: "option" })
    fireEvent.change(screen.getByLabelText("Vehicle"), { target: { value: "v1" } })
    fireEvent.change(screen.getByLabelText("When it happened"), { target: { value: "2026-10-01T08:00" } })
    fireEvent.change(screen.getByLabelText("What happened"), { target: { value: "Head-on" } })
    fireEvent.change(screen.getByLabelText("Fatalities"), { target: { value: "1" } })
    expect(screen.getByText("A fatal accident needs the police case number.")).toBeTruthy()
  })
  it("read-only users see no report or move controls", async () => {
    perms = new Set(["FLEET_READ"])
    show()
    await screen.findByText("ACC-2026-00021")
    expect(screen.queryByText("Report an accident")).toBeNull()
    expect(screen.queryByLabelText("Move ACC-2026-00021 to")).toBeNull()
  })
})

describe("accident list at scale", () => {
  it("reads every page, searches, and shows 25 at a time", async () => {
    const all = Array.from({ length: 30 }, (_, i) => acc({ id: `a${i}`, referenceNumber: `ACC-${String(i).padStart(3, "0")}`, registration: `REG${i}`, status: "CLOSED", vehicleDrivable: true }))
    get.mockImplementation((url: string) => {
      if (url.includes("/vehicles")) return ok({ content: [{ id: "v1", registration: "ABC123MP" }] })
      const page = Number(/page=(\d+)/.exec(url)?.[1] ?? 0)
      return ok({ content: all.slice(page * 20, page * 20 + 20), totalPages: 2 })
    })
    show()
    await waitFor(() => expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–25 of 30"))
    fireEvent.change(screen.getByLabelText("Search accidents"), { target: { value: "acc-029" } })
    expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–1 of 1")
  })
})

