import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
const patch = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a),
    patch: (...a: unknown[]) => patch(...a), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import WorkOrdersTab from "./WorkOrdersTab"
import PlansTab from "./PlansTab"

afterEach(cleanup)

const asset = { id: "a1", name: "CAT D9", fleetNumber: "D9-001", status: "BREAKDOWN" }
const wo = (over: Record<string, unknown> = {}) => ({
  id: "w1", assetId: "a1", number: "WO-0001", title: "Fix hydraulic hose", description: null, type: "REPAIR", priority: "HIGH",
  status: "OPEN", assignedToUserId: null, assignedToName: null, dueDate: null, overdue: false, source: "MANUAL", planId: null,
  incidentId: null, blocksRelease: true, startedAt: null, completedAt: null, meterHours: null, downtimeHours: null, labourHours: null,
  workDone: null, testResult: null, maintenanceRecordId: null, cancelReason: null, totalCost: 0, lines: [], createdAt: "2026-10-14T08:00:00Z", ...over,
})
const plan = (over: Record<string, unknown> = {}) => ({
  id: "p1", assetId: "a1", name: "250 hour service", taskType: "SERVICE", triggerType: "HOURS", intervalValue: 250, leadValue: 25,
  lastDoneHours: 1000, lastDoneDate: null, priority: "NORMAL", blocksRelease: true, active: true, state: "DUE", used: 260,
  remaining: -10, unit: "hours", ...over,
})

let orders: unknown[] = []
let plans: unknown[] = []
const page = (content: unknown[]) => ({ data: { data: { content, last: true } } })
const mount = (ui: React.ReactElement) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = []
  orders = [wo()]
  plans = [plan()]
  get.mockImplementation((url: string) => Promise.resolve(
    url.includes("/work-orders") ? page(orders)
      : url.includes("/maintenance-plans") ? { data: { data: plans } }
      : url.includes("/assets") ? page([asset])
      : url.includes("assignable-users") ? { data: [{ id: "u9", name: "Sipho Mechanic" }] }
      : page([])))
  patch.mockResolvedValue({ data: { data: wo() } })
  post.mockResolvedValue({ data: { data: wo() } })
  put.mockResolvedValue({ data: { data: plan() } })
})

describe("Work orders", () => {
  it("lists open jobs with the machine and a blocks-release marker", async () => {
    perms = ["EARTHMOVING_READ"]
    mount(<WorkOrdersTab />)
    expect(await screen.findByText("Fix hydraulic hose")).toBeTruthy()
    expect(screen.getByText(/Blocks release/)).toBeTruthy()
    expect(screen.getByText(/D9-001 — CAT D9/)).toBeTruthy()
  })

  it("a reader sees no create button and no actions", async () => {
    perms = ["EARTHMOVING_READ"]
    mount(<WorkOrdersTab />)
    fireEvent.click(await screen.findByText("Fix hydraulic hose"))
    expect(screen.queryByText(/New work order/)).toBeNull()
    expect(screen.queryByText("Start work")).toBeNull()
    expect(screen.queryByText(/Cancel job/)).toBeNull()
  })

  it("someone with write authority can raise one", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    mount(<WorkOrdersTab />)
    fireEvent.click(await screen.findByText(/New work order/))
    await screen.findByText(/D9-001 — CAT D9/, { selector: "option" })
    fireEvent.change(document.getElementById("nwo-asset")!, { target: { value: "a1" } })
    fireEvent.change(screen.getByLabelText(/What needs doing/), { target: { value: "Replace track pad" } })
    expect(screen.queryByLabelText(/Assign to/)).toBeNull()
    fireEvent.click(screen.getByText("Raise work order"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const body = post.mock.calls[0][1]
    expect(post.mock.calls[0][0]).toContain("/work-orders")
    expect(body).toMatchObject({ assetId: "a1", title: "Replace track pad", blocksRelease: true, assignedToUserId: null })
  })

  it("a mechanic starts the job", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    mount(<WorkOrdersTab />)
    fireEvent.click(await screen.findByText("Fix hydraulic hose"))
    fireEvent.click(await screen.findByText("Start work"))
    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect(patch.mock.calls[0][0]).toContain("/work-orders/w1/start")
  })

  it("completing a repair needs a test result, and says it does not release the machine", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    orders = [wo({ status: "IN_PROGRESS" })]
    mount(<WorkOrdersTab />)
    fireEvent.click(await screen.findByText("Fix hydraulic hose"))
    fireEvent.click(await screen.findByText("Complete…"))
    const dlg = await screen.findByRole("dialog")
    expect(within(dlg).getByText(/does not release the machine/)).toBeTruthy()
    const submit = within(dlg).getByText("Complete job") as HTMLButtonElement
    fireEvent.change(within(dlg).getByLabelText(/Work done/), { target: { value: "Replaced the hose" } })
    expect(submit.disabled).toBe(true)
    fireEvent.change(within(dlg).getByLabelText(/Test result/), { target: { value: "Ran 20 min, no leaks" } })
    expect(submit.disabled).toBe(false)
    fireEvent.click(submit)
    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect(patch.mock.calls[0][0]).toContain("/work-orders/w1/complete")
    expect(patch.mock.calls[0][1]).toMatchObject({ workDone: "Replaced the hose", testResult: "Ran 20 min, no leaks" })
  })

  it("a service needs the meter reading but no test result", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    orders = [wo({ type: "SERVICE", status: "IN_PROGRESS" })]
    mount(<WorkOrdersTab />)
    fireEvent.click(await screen.findByText("Fix hydraulic hose"))
    fireEvent.click(await screen.findByText("Complete…"))
    const dlg = await screen.findByRole("dialog")
    const submit = within(dlg).getByText("Complete job") as HTMLButtonElement
    fireEvent.change(within(dlg).getByLabelText(/Work done/), { target: { value: "Oil and filters" } })
    expect(submit.disabled).toBe(true)
    fireEvent.change(within(dlg).getByLabelText(/Meter hours/), { target: { value: "1250" } })
    expect(submit.disabled).toBe(false)
  })

  it("stopping a job blocking release needs a written reason", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    mount(<WorkOrdersTab />)
    fireEvent.click(await screen.findByText("Fix hydraulic hose"))
    fireEvent.click(await screen.findByText(/Stop blocking release/))
    const dlg = await screen.findByRole("dialog")
    const ok = within(dlg).getByText("Confirm") as HTMLButtonElement
    expect(ok.disabled).toBe(true)
    fireEvent.change(within(dlg).getByLabelText(/Reason/), { target: { value: "Cosmetic only, machine is safe" } })
    fireEvent.click(ok)
    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect(patch.mock.calls[0][0]).toContain("/blocks-release")
    expect(patch.mock.calls[0][1]).toMatchObject({ blocksRelease: false, reason: "Cosmetic only, machine is safe" })
  })

  it("a completed job points to the release step and offers no actions", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    orders = [wo({ status: "COMPLETED", workDone: "Replaced hose", testResult: "No leaks", completedAt: "2026-10-15T08:00:00Z" })]
    mount(<WorkOrdersTab />)
    fireEvent.click(await screen.findByText("All"))
    fireEvent.click(await screen.findByText("Fix hydraulic hose"))
    expect(await screen.findByText(/still has to be released from Fleet/)).toBeTruthy()
    expect(screen.queryByText("Start work")).toBeNull()
  })

  it("shows the server's message when an action is refused", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    patch.mockRejectedValue({ response: { data: { message: "Start the work order before completing it." } } })
    mount(<WorkOrdersTab />)
    fireEvent.click(await screen.findByText("Fix hydraulic hose"))
    fireEvent.click(await screen.findByText("Start work"))
    expect(await screen.findByText(/Start the work order before completing it/)).toBeTruthy()
  })
})

describe("Service plans", () => {
  it("shows where each plan stands", async () => {
    perms = ["EARTHMOVING_READ"]
    mount(<PlansTab />)
    expect(await screen.findByText("250 hour service")).toBeTruthy()
    expect(screen.getByText("Due")).toBeTruthy()
    expect(screen.getByText(/10 hours over/)).toBeTruthy()
    expect(screen.queryByText(/Check now/)).toBeNull()
    expect(screen.queryByText(/New plan/)).toBeNull()
  })

  it("Check now reports what was raised", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    post.mockResolvedValue({ data: { data: { checked: 3, workOrdersRaised: 1, raised: ["WO-0005"] } } })
    mount(<PlansTab />)
    fireEvent.click(await screen.findByText(/Check now/))
    expect((await screen.findByRole("status")).textContent).toContain("WO-0005")
    expect(post.mock.calls[0][0]).toContain("/maintenance-plans/scan")
  })

  it("creates a plan and sends the interval as a number", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    post.mockResolvedValue({ data: { data: plan() } })
    mount(<PlansTab />)
    fireEvent.click(await screen.findByText(/New plan/))
    await screen.findByText(/D9-001 — CAT D9/, { selector: "option" })
    fireEvent.change(document.getElementById("pl-asset")!, { target: { value: "a1" } })
    fireEvent.change(screen.getByLabelText(/^Name/), { target: { value: "500 hour service" } })
    fireEvent.change(screen.getByLabelText(/Every/), { target: { value: "500" } })
    fireEvent.click(screen.getByText("Create plan"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ assetId: "a1", name: "500 hour service", intervalValue: 500, triggerType: "HOURS", lastDoneHours: null })
  })
})
