import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn(); const del = vi.fn()
let canWrite = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), delete: (...a: any[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => canWrite }))
import TimeOffTab, { timeOffProblem } from "./TimeOffTab"

const BLOCK = { id: "t1", practitionerId: "p1", startsAt: "2026-10-08T06:00:00Z", endsAt: "2026-10-08T15:00:00Z", reason: "Annual leave" }
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><TimeOffTab /></QueryClientProvider>)
const choose = async () => {
  await screen.findByText("Dr Lee")
  fireEvent.change(screen.getByLabelText("Practitioner"), { target: { value: "p1" } })
}

beforeEach(() => { get.mockReset(); post.mockReset(); del.mockReset(); canWrite = true
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("time-off") ? [BLOCK] : [{ id: "p1", fullName: "Dr Lee" }] } })) })
afterEach(cleanup)

describe("timeOffProblem", () => {
  it("needs both ends, in order, and at most 90 days", () => {
    expect(timeOffProblem("", "2026-10-08T10:00")).toMatch(/Choose/)
    expect(timeOffProblem("2026-10-08T10:00", "2026-10-08T09:00")).toMatch(/after the start/)
    expect(timeOffProblem("2026-10-08T10:00", "2026-10-08T10:00")).toMatch(/after the start/)
    expect(timeOffProblem("2026-10-08T10:00", "2027-01-08T10:00")).toMatch(/90 days/)
    expect(timeOffProblem("2026-10-08T08:00", "2026-10-08T17:00")).toBeNull()
  })
})

describe("TimeOffTab", () => {
  it("lists a practitioner's upcoming time off once chosen", async () => {
    show()
    await choose()
    expect(await screen.findByText("Annual leave")).toBeTruthy()
    expect(get.mock.calls.some(c => String(c[0]).endsWith("/practitioners/p1/time-off"))).toBe(true)
  })

  it("adds time off and sends instants", async () => {
    post.mockResolvedValue({ data: { data: BLOCK } })
    show(); await choose()
    await screen.findByText("Annual leave")
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2099-01-05T08:00" } })
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2099-01-05T17:00" } })
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: " Training " } })
    fireEvent.click(screen.getByText("Add time off"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/clinic/practitioners/p1/time-off")
    expect(post.mock.calls[0][1]).toEqual({ startsAt: new Date("2099-01-05T08:00").toISOString(), endsAt: new Date("2099-01-05T17:00").toISOString(), reason: "Training" })
  })

  it("does not call the server for an end before the start", async () => {
    show(); await choose(); await screen.findByText("Annual leave")
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2099-01-05T17:00" } })
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2099-01-05T08:00" } })
    fireEvent.click(screen.getByText("Add time off"))
    expect(screen.getByText(/after the start/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("cancels a block", async () => {
    del.mockResolvedValue({ data: {} })
    show(); await choose()
    await screen.findByText("Annual leave")
    fireEvent.click(screen.getByText("Cancel"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/clinic/time-off/t1"))
  })

  it("is read-only without CLINIC_WRITE", async () => {
    canWrite = false
    show(); await choose()
    await screen.findByText("Annual leave")
    expect(screen.queryByText("Add time off")).toBeNull()
    expect(screen.queryByText("Cancel")).toBeNull()
  })

  it("shows a load failure instead of an empty list", async () => {
    get.mockImplementation((url: string) => url.includes("time-off") ? Promise.reject(new Error("x")) : Promise.resolve({ data: { data: [{ id: "p1", fullName: "Dr Lee" }] } }))
    show(); await choose()
    expect(await screen.findByText(/Could not load/)).toBeTruthy()
  })
})
