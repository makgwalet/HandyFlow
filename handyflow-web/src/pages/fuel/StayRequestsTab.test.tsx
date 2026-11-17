import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import StayRequestsTab from "./StayRequestsTab"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const req = (o: any = {}) => ({ id: "r1", accountId: "a1", customerName: "Acme Haulage", requestedByEmail: "d@acme.co.za", vehicleRegistration: "GP 55 TS GP", driverName: "Sipho Nkosi", driverPhone: "0825550123",
  arrivalDate: "2026-11-20", departureDate: "2026-11-22", nights: 2, status: "PENDING", note: "Night stop", decisionNote: null, stayId: null, createdAt: "2026-11-17T10:00:00Z", decidedAt: null, ...o })
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><StayRequestsTab /></QueryClientProvider>)
}

describe("StayRequestsTab", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); perms = ["FUEL_TRUCKSTOP_MANAGE"]; post.mockResolvedValue({ data: { data: {} } }) })
  afterEach(cleanup)

  it("asks for waiting requests and shows who, which truck and when", async () => {
    get.mockImplementation(() => ok([req()]))
    mount()
    expect(await screen.findByText("GP 55 TS GP")).toBeTruthy()
    expect(screen.getByText(/Acme Haulage/)).toBeTruthy()
    expect(screen.getByText("20 Nov to 22 Nov · 2 nights")).toBeTruthy()
    expect(get.mock.calls.some(c => String(c[0]).endsWith("stay-requests?status=PENDING"))).toBe(true)
  })

  it("approves, sending the note only if typed", async () => {
    get.mockImplementation(() => ok([req()]))
    mount()
    fireEvent.change(await screen.findByLabelText("Note to the customer"), { target: { value: "See you then" } })
    fireEvent.click(screen.getByLabelText("Approve GP 55 TS GP"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/stay-requests/r1/approve", { note: "See you then" }))
  })

  it("declines with a reason the customer will see", async () => {
    get.mockImplementation(() => ok([req()]))
    mount()
    fireEvent.click(await screen.findByLabelText("Decline GP 55 TS GP"))
    fireEvent.change(await screen.findByLabelText("Reason *"), { target: { value: "We are closed that week" } })
    fireEvent.click(screen.getAllByText("Decline").slice(-1)[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/stay-requests/r1/decline", { reason: "We are closed that week" }))
  })

  it("shows a refused booking so staff can decline instead", async () => {
    get.mockImplementation(() => ok([req()]))
    post.mockRejectedValue({ response: { data: { message: "No bay is free on the night of 2026-11-20: all 2 are taken." } } })
    mount()
    fireEvent.click(await screen.findByLabelText("Approve GP 55 TS GP"))
    expect(await screen.findByText(/No bay is free/)).toBeTruthy()
  })

  it("without manage permission there are no action buttons", async () => {
    perms = ["FUEL_READ"]
    get.mockImplementation(() => ok([req()]))
    mount()
    expect(await screen.findByText("GP 55 TS GP")).toBeTruthy()
    expect(screen.queryByLabelText("Approve GP 55 TS GP")).toBeNull()
    expect(screen.queryByLabelText("Note to the customer")).toBeNull()
  })

  it("says so when nobody is waiting", async () => {
    get.mockImplementation(() => ok([]))
    mount()
    expect(await screen.findByText("No customer is waiting for an answer.")).toBeTruthy()
  })
})
