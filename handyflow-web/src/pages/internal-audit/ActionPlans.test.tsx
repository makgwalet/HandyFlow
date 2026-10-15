import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ActionPlansPanel from "./ActionPlansPanel"

const perms = vi.hoisted(() => ({ held: new Set<string>() }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.held.has(p) }))
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "me" } }) }))
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
afterEach(cleanup)
beforeEach(() => { api.get.mockReset(); api.post.mockReset(); perms.held = new Set(["AUDIT_READ", "AUDIT_MANAGE"]); vi.spyOn(window, "prompt") })

const plan = (o: any) => ({ id: "p1", findingId: "f1", description: "Reconcile suspense monthly", owner: "someone", ownerName: "Thandi", dueDate: "2026-11-30", status: "OPEN", overdue: false, completionNote: null, validationNote: null, timesReturned: 0, cancelReason: null, ...o })
const users = [{ id: "u1", firstName: "Thandi", lastName: "M" }]

describe("ActionPlansPanel", () => {
  it("explains that actions gate closing when there are none", async () => {
    api.get.mockResolvedValue({ data: [] })
    wrap(<ActionPlansPanel findingId="f1" findingClosed={false} users={users} />)
    expect(await screen.findByText(/can only be closed once every action has been checked/)).toBeTruthy()
  })

  it("adds an action only with a description and a due date", async () => {
    api.get.mockResolvedValue({ data: [] })
    api.post.mockResolvedValue({ data: plan({}) })
    wrap(<ActionPlansPanel findingId="f1" findingClosed={false} users={users} />)
    fireEvent.click(await screen.findByText("Add action"))
    const save = screen.getByText("Save action") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("What will be done"), { target: { value: "Reconcile suspense monthly" } })
    fireEvent.change(screen.getByLabelText("Action due date"), { target: { value: "2026-11-30" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/internal-audit/findings/f1/action-plans", { description: "Reconcile suspense monthly", owner: null, dueDate: "2026-11-30" }))
  })

  it("will not add actions to a closed finding", async () => {
    api.get.mockResolvedValue({ data: [plan({ status: "VALIDATED" })] })
    wrap(<ActionPlansPanel findingId="f1" findingClosed={true} users={users} />)
    expect(await screen.findByText("Reconcile suspense monthly")).toBeTruthy()
    expect(screen.queryByText("Add action")).toBeNull()
  })

  it("needs a real note before marking an action done", async () => {
    api.get.mockResolvedValue({ data: [plan({})] })
    api.post.mockResolvedValue({ data: plan({ status: "COMPLETED" }) })
    ;(window.prompt as any).mockReturnValueOnce("done").mockReturnValueOnce("Procedure issued and first run attached")
    wrap(<ActionPlansPanel findingId="f1" findingClosed={false} users={users} />)
    fireEvent.click(await screen.findByText("Mark done"))
    expect(await screen.findByRole("alert")).toBeTruthy()
    expect(api.post).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Mark done"))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/internal-audit/action-plans/p1/complete", { note: "Procedure issued and first run attached" }))
  })

  it("lets an auditor validate or return, but not the action's own owner", async () => {
    api.get.mockResolvedValue({ data: [plan({ status: "COMPLETED", completionNote: "Issued", overdue: true })] })
    wrap(<ActionPlansPanel findingId="f1" findingClosed={false} users={users} />)
    expect(await screen.findByText("Validate")).toBeTruthy()
    expect(screen.getByText("Return")).toBeTruthy()
    expect(screen.getByText(/OVERDUE/)).toBeTruthy()
    cleanup()
    api.get.mockResolvedValue({ data: [plan({ status: "COMPLETED", owner: "me" })] })
    wrap(<ActionPlansPanel findingId="f1" findingClosed={false} users={users} />)
    await screen.findByText("Reconcile suspense monthly")
    expect(screen.queryByText("Validate")).toBeNull()
  })

  it("shows the server's reason when closing rules refuse a change", async () => {
    api.get.mockResolvedValue({ data: [plan({ status: "COMPLETED" })] })
    api.post.mockRejectedValue({ response: { data: { message: "The person who completed an action cannot validate it" } } })
    ;(window.prompt as any).mockReturnValue("Re-performed the reconciliation; it agrees")
    wrap(<ActionPlansPanel findingId="f1" findingClosed={false} users={users} />)
    fireEvent.click(await screen.findByText("Validate"))
    expect(await screen.findByText("The person who completed an action cannot validate it")).toBeTruthy()
  })
})
