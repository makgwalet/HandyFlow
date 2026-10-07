import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const round = (over: any = {}) => ({ id: "r1", shiftId: "s1", siteId: "x", siteName: "Centurion Mall", routeName: "Perimeter", guardName: "Nomsa Dlamini", roundNumber: 2, status: "PARTIAL",
  expectedStartAt: "2026-10-07T06:00:00Z", expectedEndAt: "2026-10-07T07:40:00Z", startedAt: "2026-10-07T06:10:00Z", completedAt: null, checkpointsExpected: 3, checkpointsScanned: 1,
  offSchedule: false, offScheduleReason: null, acknowledged: false, ...over })
let list: any[] = [round(), round({ id: "r2", status: "COMPLETE", roundNumber: 1, checkpointsScanned: 3, guardName: "Priya Govender" }), round({ id: "r3", status: "MISSED", roundNumber: 3, checkpointsScanned: 0, offSchedule: true, offScheduleReason: "Round 3 started 10 min after round 2" })]
let detail: any = { round: round(), acknowledgementNote: null, checkpoints: [
  { id: "c1", name: "North Gate", sequence: 1, scannedAt: "2026-10-07T06:10:00Z", method: "QR", scannedBy: "Nomsa Dlamini" },
  { id: "c2", name: "Loading Bay", sequence: 2, scannedAt: null, method: null, scannedBy: null },
  { id: "c3", name: "Server Room", sequence: 3, scannedAt: null, method: null, scannedBy: null }] }
const post = vi.fn(() => Promise.resolve({ data: {} }))
vi.mock("../../api/client", () => ({ apiClient: {
  get: vi.fn((url: string) => Promise.resolve({ data: { data: url.includes("/patrols/") ? detail : url.includes("/patrols?") ? list : { content: [{ id: "x", name: "Centurion Mall" }] } } })),
  post: (...a: any[]) => (post as any)(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
import PatrolsTab from "./PatrolsTab"
import PatrolDetailPage from "./PatrolDetailPage"

const wrap = (ui: any, path = "/") => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter></QueryClientProvider>)
afterEach(() => { cleanup(); post.mockClear() })

describe("PatrolsTab", () => {
  it("lists rounds with the summary and flags", async () => {
    wrap(<PatrolsTab />)
    expect(await screen.findByText("Priya Govender")).toBeTruthy()
    expect(screen.getAllByText("Needs attention").length).toBeGreaterThanOrEqual(2) // tile label + chips
    expect(screen.getByText("Off schedule", { selector: "span" })).toBeTruthy()
    expect(screen.getByText("33%")).toBeTruthy() // 1 complete of 3 finished
    expect(screen.getAllByText("Round 2")[0].getAttribute("href")).toBe("/security/patrols/r1")
  })
  it("can show only the rounds that need attention", async () => {
    wrap(<PatrolsTab />)
    await screen.findByText("Priya Govender")
    fireEvent.click(screen.getByLabelText("Needs attention only"))
    expect(screen.queryByText("Priya Govender")).toBeNull()
  })
  it("says why the list is empty", async () => {
    list = []; wrap(<PatrolsTab />)
    expect(await screen.findByText(/No patrol rounds were due in this period/)).toBeTruthy()
  })
})

describe("PatrolDetailPage", () => {
  const open = () => wrap(<Routes><Route path="/security/patrols/:id" element={<PatrolDetailPage />} /></Routes>, "/security/patrols/r1")
  it("shows scanned and unscanned checkpoints", async () => {
    open()
    expect(await screen.findByText("North Gate")).toBeTruthy()
    expect(screen.getByText(/Nomsa Dlamini · QR/)).toBeTruthy()
    expect(screen.getAllByText("Not scanned").length).toBe(2)
    expect(screen.getByText(/2 not scanned/)).toBeTruthy()
  })
  it("requires a note and sends the acknowledgement", async () => {
    open()
    await screen.findByText("North Gate")
    fireEvent.click(screen.getByRole("button", { name: "Acknowledge" }))
    expect((await screen.findByRole("alert")).textContent).toContain("Write a note")
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText(/What happened/), { target: { value: "Gate was locked" } })
    fireEvent.click(screen.getByRole("button", { name: "Acknowledge" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/security/patrols/r1/acknowledge", { note: "Gate was locked" }))
  })
  it("offers no acknowledgement for a complete round", async () => {
    detail = { ...detail, round: round({ status: "COMPLETE", checkpointsScanned: 3 }) }
    open()
    await screen.findByText("North Gate")
    expect(screen.queryByRole("button", { name: "Acknowledge" })).toBeNull()
  })
})
