import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EventProgrammeTab from "./EventProgrammeTab"
import EventChecklistTab from "./EventChecklistTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn(); const del = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const dlg = () => within(screen.getByRole("dialog"))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const noop = () => {}

const sess = (o: object = {}) => ({ id: "s1", title: "Keynote", description: null, sessionType: "KEYNOTE", room: "Hall A", speaker: "Dr Dlamini", startAt: "2026-12-01T09:00:00", endAt: "2026-12-01T10:00:00", status: "SCHEDULED", actualStart: null, actualEnd: null, delayMinutes: null, notes: null, ...o })
let eventStatus = "LIVE"; let sheet: any; let items: any[]; let readiness: any
beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); perms = []; eventStatus = "LIVE"
  post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} })
  sheet = { sessions: [sess(), sess({ id: "s2", title: "Lunch", sessionType: "MEAL", startAt: "2026-12-01T12:00:00", endAt: "2026-12-01T13:00:00", room: null, speaker: null })], current: null, next: sess(), runningLateMinutes: 0 }
  items = [{ id: "i1", phase: "SETUP", title: "Test the PA", description: null, assignedTo: "Sipho", dueAt: null, required: true, done: false, doneByName: null, doneAt: null, sortOrder: 0, overdue: true },
    { id: "i2", phase: "CONTINGENCY", title: "Load shedding", description: "Switch to generator", assignedTo: null, dueAt: null, required: true, done: true, doneByName: "Thabo", doneAt: "2026-11-10T08:00:00Z", sortOrder: 0, overdue: false }]
  readiness = { ready: false, checks: [{ code: "VENUE", label: "Venue confirmed", passed: true, detail: null }, { code: "BUDGET", label: "Budget approved", passed: false, detail: "The budget is still a draft" }], checklistProgress: [{ phase: "SETUP", total: 1, done: 0, requiredOpen: 1 }] }
  get.mockImplementation(async (u: string) => ({ data: { data: u.endsWith("/run-sheet") ? sheet : u.endsWith("/checklist") ? items : u.endsWith("/readiness") ? readiness : { status: eventStatus, startDatetime: "2026-12-01T08:00:00" } } }))
})
afterEach(cleanup)

describe("run sheet", () => {
  it("shows the programme to a reader without any buttons", async () => {
    wrap(<EventProgrammeTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText(/Lunch/)); expect(screen.getAllByText(/Keynote/).length).toBeGreaterThan(0); expect(screen.getByText(/On time/)).toBeTruthy(); expect(screen.getByText(/Next: 09:00 Keynote/)).toBeTruthy()
    for (const t of ["Add session", "Start", "Running late?"]) expect(screen.queryByText(t)).toBeNull()
  })
  it("starts a session only while the event is live", async () => {
    perms = ["EVENTS_OPS_MANAGE"]; wrap(<EventProgrammeTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getAllByText("Start")); fireEvent.click(screen.getAllByText("Start")[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/sessions/s1/start")); cleanup(); eventStatus = "PUBLISHED"
    wrap(<EventProgrammeTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />); await waitFor(() => screen.getByText(/Lunch/)); expect(screen.queryByText("Start")).toBeNull()
  })
  it("'running late' moves the programme and checks the minutes", async () => {
    perms = ["EVENTS_MANAGE"]; wrap(<EventProgrammeTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText(/Lunch/)); fireEvent.click(screen.getByText("Running late?"))
    const go = dlg().getByRole("button", { name: "Move programme" }) as HTMLButtonElement; expect(go.disabled).toBe(true)
    fireEvent.change(dlg().getByLabelText(/Minutes/), { target: { value: "300" } }); expect(go.disabled).toBe(true)
    fireEvent.change(dlg().getByLabelText(/Minutes/), { target: { value: "20" } }); expect(go.disabled).toBe(false); fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/sessions/shift", { minutes: 20, fromSessionId: null }))
  })
  it("shows the server's refusal, such as a room clash", async () => {
    perms = ["EVENTS_MANAGE"]; post.mockRejectedValue({ response: { data: { message: "That would clash with Workshop in Hall A" } } })
    wrap(<EventProgrammeTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />); await waitFor(() => screen.getByText(/Lunch/)); fireEvent.click(screen.getByText("Running late?"))
    fireEvent.change(dlg().getByLabelText(/Minutes/), { target: { value: "20" } }); fireEvent.click(dlg().getByRole("button", { name: "Move programme" }))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/clash/))
  })
  it("adds a session with the entered details", async () => {
    perms = ["EVENTS_OPS_MANAGE"]; wrap(<EventProgrammeTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Add session")); fireEvent.click(screen.getByText("Add session"))
    fireEvent.change(dlg().getByLabelText(/Title/), { target: { value: "Panel" } }); fireEvent.change(dlg().getByLabelText(/Type/), { target: { value: "PANEL" } }); fireEvent.change(dlg().getByLabelText(/Starts/), { target: { value: "2026-12-01T08:00" } }); fireEvent.change(dlg().getByLabelText(/Ends/), { target: { value: "2026-12-01T11:00" } })
    fireEvent.click(dlg().getByRole("button", { name: "Save session" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/sessions", expect.objectContaining({ title: "Panel", sessionType: "PANEL", startAt: "2026-12-01T08:00:00", endAt: "2026-12-01T11:00:00", room: null })))
  })
})

describe("readiness and checklist", () => {
  it("explains what is not ready and groups items by phase", async () => {
    wrap(<EventChecklistTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Not ready yet")); expect(screen.getByText(/The budget is still a draft/)).toBeTruthy()
    expect(screen.getByText("Setup")).toBeTruthy(); expect(screen.getByText("Contingency plans")).toBeTruthy(); expect(screen.getByText("0 of 1 done, 1 required still open")).toBeTruthy()
    expect(screen.getByText(/Reviewed by Thabo/)).toBeTruthy(); expect(screen.getByText(/overdue/)).toBeTruthy(); expect(screen.queryByText("Tick off")).toBeNull()
  })
  it("ticks an item off, undoes a reviewed plan and removes an item", async () => {
    perms = ["EVENTS_OPS_MANAGE"]; wrap(<EventChecklistTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Tick off")); fireEvent.click(screen.getByText("Tick off"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/checklist/i1/complete")); fireEvent.click(screen.getByText("Undo"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/checklist/i2/reopen")); fireEvent.click(screen.getAllByText("Remove")[0])
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/events/e1/checklist/i1"))
  })
  it("adds the starter list and says what happened", async () => {
    perms = ["EVENTS_MANAGE"]; post.mockResolvedValue({ data: { data: { added: 6, skipped: 2 } } }); wrap(<EventChecklistTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Add starter checklist")); fireEvent.click(screen.getByText("Add starter checklist"))
    await waitFor(() => expect(screen.getByRole("status").textContent).toBe("Added 6 items; 2 already existed."))
  })
  it("adds an item, defaulting to required", async () => {
    perms = ["EVENTS_MANAGE"]; wrap(<EventChecklistTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Add item")); fireEvent.click(screen.getByText("Add item"))
    fireEvent.change(dlg().getByLabelText(/Title/), { target: { value: "Print badges" } }); fireEvent.click(dlg().getByRole("button", { name: "Save item" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/checklist", { phase: "PRE_EVENT", title: "Print badges", description: null, assignedTo: null, dueAt: null, required: true, sortOrder: null }))
  })
})
