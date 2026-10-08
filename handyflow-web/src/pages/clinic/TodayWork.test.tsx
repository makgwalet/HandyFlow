import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
const perms: Record<string, boolean> = {}
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
import { ResultsToReviewPanel, TasksPanel } from "./TodayWork"

const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
afterEach(cleanup)
beforeEach(() => {
  get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: {} })
  for (const k of Object.keys(perms)) delete perms[k]
})

describe("ResultsToReviewPanel", () => {
  beforeEach(() => { get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("critical")
    ? [{ id: "c1", patientName: "Ann One", receivedAt: "2026-10-08T06:00:00Z", criticalMarkers: "K 6.9" }]
    : [{ id: "c1", receivedAt: "2026-10-08T06:00:00Z" }, { id: "u1", patientId: "p2", patientNameRaw: "Bob Two", receivedAt: "2026-10-08T05:00:00Z", hasAbnormal: true }] } })) })

  it("renders nothing without read permission", () => {
    const { container } = wrap(<ResultsToReviewPanel onNavigate={vi.fn()} />)
    expect(container.innerHTML).toBe("")
  })
  it("shows critical first, counts each result once, and reviews", async () => {
    perms.CLINIC_RESULT_READ = true; perms.CLINIC_RESULT_REVIEW = true
    wrap(<ResultsToReviewPanel onNavigate={vi.fn()} />)
    await screen.findByText(/CRITICAL · Ann One/)
    expect(screen.getByRole("alert").textContent).toMatch(/1 critical result not yet reviewed/)
    expect(screen.getByText(/· 2$/)).toBeTruthy()
    expect(screen.queryByText("Follow-up task")).toBeNull()
    fireEvent.click(screen.getAllByText("Mark reviewed")[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/lab/results/c1/review"))
  })
  it("creates a follow-up task tied to the result", async () => {
    perms.CLINIC_RESULT_READ = true; perms.CLINIC_TASK_CREATE = true
    wrap(<ResultsToReviewPanel onNavigate={vi.fn()} />)
    await screen.findByText(/Bob Two/)
    fireEvent.click(screen.getAllByText("Follow-up task")[1])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/tasks", expect.objectContaining({ kind: "RESULT_FOLLOW_UP", sourceType: "LAB_RESULT", sourceId: "u1" })))
  })
})

describe("TasksPanel", () => {
  beforeEach(() => { get.mockResolvedValue({ data: { data: [
    { id: "t1", kind: "GENERAL", title: "Call Mrs Dube", dueDate: "2026-10-01", overdue: true, status: "OPEN" },
    { id: "t2", kind: "GENERAL", title: "Order stock", dueDate: null, overdue: false, status: "OPEN" }] } }) })

  it("renders nothing without read permission", () => {
    expect(wrap(<TasksPanel />).container.innerHTML).toBe("")
  })
  it("lists overdue first and completes a task", async () => {
    perms.CLINIC_TASK_READ = true; perms.CLINIC_TASK_COMPLETE = true
    wrap(<TasksPanel />)
    await screen.findByText("Call Mrs Dube")
    expect(screen.getByText(/Overdue since 2026-10-01/)).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Done: Call Mrs Dube"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/tasks/t1/complete", {}))
  })
  it("requires a reason to dismiss", async () => {
    perms.CLINIC_TASK_READ = true; perms.CLINIC_TASK_COMPLETE = true
    wrap(<TasksPanel />)
    await screen.findByText("Order stock")
    fireEvent.click(screen.getByLabelText("Dismiss: Order stock"))
    const go = screen.getByRole("button", { name: "Dismiss" }) as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Why dismiss"), { target: { value: "Already ordered" } })
    expect(go.disabled).toBe(false)
    fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/tasks/t2/dismiss", { note: "Already ordered" }))
  })
  it("adds a task only with a valid title", async () => {
    perms.CLINIC_TASK_READ = true; perms.CLINIC_TASK_CREATE = true
    wrap(<TasksPanel />)
    await screen.findByText("Order stock")
    const add = screen.getByRole("button", { name: /Add/ }) as HTMLButtonElement
    expect(add.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("New task"), { target: { value: "Recall diabetics" } })
    fireEvent.click(add)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/tasks", { title: "Recall diabetics", dueDate: undefined }))
  })
})
