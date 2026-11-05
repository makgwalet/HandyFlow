import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import StatusChanges, { StatusChangeDialog, askedBy, reasonProblem, verb, type StatusItem } from "./StatusChanges"

const get = vi.fn(), post = vi.fn()
let allowed = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => allowed }))

const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const item = (o: Partial<StatusItem> = {}): StatusItem => ({ id: "s1", employeeId: "e1", employeeName: "Jane Dlamini", fromStatus: "ACTIVE", toStatus: "SUSPENDED",
  reason: "Safety incident on site", emergency: false, status: "PENDING", requestedBy: "u1", requestedByName: "Thandi Mokoena", requestedAt: "2026-11-05T10:00:00",
  reviewDueAt: null, reviewOverdue: false, ...o })

beforeEach(() => { allowed = true; get.mockReset(); post.mockReset() })
afterEach(cleanup)

describe("status change helpers", () => {
  it("names the request and requires a reason", () => {
    expect(verb("SUSPENDED")).toBe("suspension")
    expect(verb("ACTIVE")).toBe("reinstatement")
    expect(reasonProblem("short")).toMatch(/reason/)
    expect(reasonProblem("A proper written reason")).toBeNull()
    expect(askedBy({ requestedByName: null })).toBe("A colleague")
  })
})

describe("status changes waiting", () => {
  it("shows nothing without the HR manager permission and does not ask the server", () => {
    allowed = false
    const { container } = wrap(<StatusChanges />)
    expect(container.textContent).toBe("")
    expect(get).not.toHaveBeenCalled()
  })

  it("lists a request and approves it", async () => {
    get.mockResolvedValue({ data: { data: { approvals: [item()], reviews: [] } } })
    post.mockResolvedValue({ data: {} })
    wrap(<StatusChanges />)
    expect(await screen.findByText(/Thandi Mokoena asked: Safety incident on site/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Approve" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/status-changes/s1/approve", {}))
    expect(await screen.findByText(/suspension approved/)).toBeTruthy()
  })

  it("shows the server's refusal when you try to approve your own request", async () => {
    get.mockResolvedValue({ data: { data: { approvals: [item()], reviews: [] } } })
    post.mockRejectedValue({ response: { data: { message: "The person who asked for this status change cannot approve it. Ask another HR manager." } } })
    wrap(<StatusChanges />)
    fireEvent.click(await screen.findByRole("button", { name: "Approve" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/Ask another HR manager/)
  })

  it("will not decline without a reason", async () => {
    get.mockResolvedValue({ data: { data: { approvals: [item()], reviews: [] } } })
    post.mockResolvedValue({ data: {} })
    wrap(<StatusChanges />)
    fireEvent.click(await screen.findByRole("button", { name: "Decline" }))
    const go = screen.getAllByRole("button", { name: "Decline" }).pop() as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason for declining"), { target: { value: "Not justified" } })
    expect(go.disabled).toBe(false)
    fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/status-changes/s1/reject", { note: "Not justified" }))
  })

  it("flags an overdue emergency review and reverses the suspension with a written outcome", async () => {
    get.mockResolvedValue({ data: { data: { approvals: [], reviews: [item({ id: "r1", emergency: true, status: "APPLIED", reviewOverdue: true, reviewDueAt: "2026-11-04T21:59:00Z" })] } } })
    post.mockResolvedValue({ data: {} })
    wrap(<StatusChanges />)
    expect(await screen.findByText(/Review overdue/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Reverse" }))
    const go = screen.getByRole("button", { name: "Reverse and reinstate" }) as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Outcome of the review"), { target: { value: "No risk was found on site" } })
    fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/status-changes/r1/review", { confirm: false, note: "No risk was found on site" }))
  })
})

describe("asking for a status change", () => {
  it("needs a reason, and sends an emergency suspension when ticked", async () => {
    post.mockResolvedValue({ data: { message: "Status changed" } })
    wrap(<StatusChangeDialog employeeId="e1" employeeName="Jane Dlamini" currentStatus="ACTIVE" onClose={() => {}} />)
    const dialog = screen.getByRole("dialog", { name: "Suspend Jane Dlamini" })
    expect((within(dialog).getByRole("button", { name: "Ask for suspension" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(within(dialog).getByLabelText(/Reason/), { target: { value: "Armed threat reported at the gate" } })
    fireEvent.click(within(dialog).getByLabelText(/Emergency security or safety suspension/))
    fireEvent.click(within(dialog).getByRole("button", { name: "Suspend now" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/employees/e1/status-change",
      { toStatus: "SUSPENDED", reason: "Armed threat reported at the gate", emergency: true }))
    expect(await within(dialog).findByText("Status changed")).toBeTruthy()
  })

  it("offers reinstatement, never an emergency, for a suspended employee", () => {
    wrap(<StatusChangeDialog employeeId="e1" employeeName="Jane Dlamini" currentStatus="SUSPENDED" onClose={() => {}} />)
    expect(screen.getByRole("dialog", { name: "Reinstate Jane Dlamini" })).toBeTruthy()
    expect(screen.queryByLabelText(/Emergency/)).toBeNull()
  })
})
