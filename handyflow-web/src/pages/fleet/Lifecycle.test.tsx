import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import LifecyclePanel from "./LifecyclePanel"
import { actionsFor, disposalBody, disposalProblem, emptyDisposal, emptyRecovery, emptyTheft, emptyWriteOff, gainLossText, recoveryProblem, theftBody, theftProblem, writeOffProblem } from "./lifecycle.logic"

const get = vi.fn(), post = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const life = (lifecycleStatus: string, events: object[] = []) => ({ vehicleId: "v1", registration: "ABC123MP", lifecycleStatus, operationalStatus: "AVAILABLE", events })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><LifecyclePanel vehicleId="v1" /></QueryClientProvider>)
const set = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } })

beforeEach(() => { perms = new Set(["FLEET_READ", "FLEET_MANAGE", "FLEET_ADMIN"]); get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: { data: {} } }); get.mockImplementation(() => ok(life("ACTIVE"))) })
afterEach(cleanup)

describe("lifecycle rules", () => {
  it("offers only the moves the lifecycle allows", () => {
    expect(actionsFor("ACTIVE").map(a => a.label)).toEqual(["Report stolen or missing", "Mark impounded", "Write off", "Sell", "Transfer", "Mark retired"])
    expect(actionsFor("STOLEN").map(a => a.label)).toEqual(["Record recovery", "Write off"])
    expect(actionsFor("WRITTEN_OFF").map(a => a.label)).toEqual(["Dispose"])
    expect(actionsFor("SOLD")).toEqual([])
  })
  it("checks each form", () => {
    expect(theftProblem(emptyTheft())).toMatch(/stolen/)
    expect(recoveryProblem(emptyRecovery())).toMatch(/recovered/)
    expect(writeOffProblem(emptyWriteOff())).toMatch(/decided/)
    expect(disposalProblem(emptyDisposal("SOLD"))).toMatch(/date/)
    expect(disposalProblem({ ...emptyDisposal("SOLD"), disposalDate: "2026-10-01" })).toBe("Who bought it?")
    expect(disposalProblem({ ...emptyDisposal("SOLD"), disposalDate: "2026-10-01", counterpartyName: "Bob" })).toBe("Enter the sale price.")
    expect(disposalProblem({ ...emptyDisposal("DISPOSED"), disposalDate: "2026-10-01" })).toBeNull()
  })
  it("sends blanks as null", () => {
    expect(disposalBody({ ...emptyDisposal("SOLD"), disposalDate: "2026-10-01", counterpartyName: " Bob ", salePrice: "50000" })).toMatchObject({ target: "SOLD", counterpartyName: "Bob", salePrice: 50000, odometerAtDisposal: null, accountingReference: null })
    expect(theftBody({ ...emptyTheft(), occurredAt: "2026-10-01T08:00", insurer: " " }).insurer).toBeNull()
  })
  it("words gain and loss", () => { expect([50000, -1200, 0, null].map(gainLossText)).toEqual(["Gain R 50 000", "Loss R 1 200", "No gain or loss", "—"].map(s => s.replace(/ (?=\d)/g, " ").replace("R ", "R "))) })
})

describe("LifecyclePanel", () => {
  it("shows the status and history", async () => {
    get.mockImplementation(() => ok(life("SOLD", [{ id: "e1", eventType: "DISPOSAL", fromStatus: "ACTIVE", toStatus: "SOLD", occurredAt: "2026-10-01T08:00:00Z", reason: null, theft: null, disposal: { gainLoss: -1200, salePrice: 90000, counterpartyName: "Bob" } }])))
    show(); expect(await screen.findByText(/Disposal/)).toBeTruthy(); expect(screen.getByText(/Loss R/)).toBeTruthy(); expect(screen.queryByText("Sell")).toBeNull()
  })
  it("reports a theft with the police and insurer details", async () => {
    show(); fireEvent.click(await screen.findByText("Report stolen or missing"))
    expect((screen.getByText("Confirm") as HTMLButtonElement).disabled).toBe(true)
    set("When it was stolen or last seen", "2026-10-01T08:00"); set("Police case number", "CAS 12/10/2026"); set("Insurer", "Santam")
    fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/vehicles/v1/lifecycle/theft", expect.objectContaining({ kind: "STOLEN", policeCaseNumber: "CAS 12/10/2026", insurer: "Santam" })))
  })
  it("sells a vehicle and shows the server's finance refusal", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "This vehicle is financed — confirm the finance has been settled" } } })
    show(); fireEvent.click(await screen.findByText("Sell"))
    set("Date", "2026-10-01"); set("Buyer", "Bob"); set("Sale price", "90000"); fireEvent.click(screen.getByText("Confirm"))
    expect(await screen.findByText(/financed/)).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Finance cleared")); fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenLastCalledWith("/api/v1/fleet/vehicles/v1/lifecycle/disposal", expect.objectContaining({ target: "SOLD", salePrice: 90000, financeCleared: true })))
  })
  it("records a recovery for a stolen vehicle", async () => {
    get.mockImplementation(() => ok(life("STOLEN")))
    show(); fireEvent.click(await screen.findByText("Record recovery")); set("When it was recovered", "2026-10-05T10:00"); fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/vehicles/v1/lifecycle/recovery", expect.objectContaining({ recoveryStatus: "RECOVERED" })))
  })
  it("makes a simple move with a reason", async () => {
    show(); fireEvent.click(await screen.findByText("Mark impounded")); set("Reason", "Towed by metro police"); fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/vehicles/v1/lifecycle/transition", { target: "IMPOUNDED", reason: "Towed by metro police" }))
  })
  it("hides write-off and sale from people without FLEET_ADMIN, and everything from read-only users", async () => {
    perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); show()
    await screen.findByText("Report stolen or missing"); expect(screen.queryByText("Write off")).toBeNull(); expect(screen.queryByText("Sell")).toBeNull()
    cleanup(); perms = new Set(["FLEET_READ"]); show(); await screen.findByText(/Lifecycle:/); expect(screen.queryByText("Report stolen or missing")).toBeNull()
  })
})
