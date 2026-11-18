import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { ArrivalCheckDialog, PodExceptionDialog } from "./ArrivalPodDialogs"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>{ui}</QueryClientProvider>)

describe("ArrivalCheckDialog", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: {} }); perms = ["FUEL_ARRIVAL_CHECK_OVERRIDE"] })
  afterEach(cleanup)
  const open = (status: string) => { get.mockResolvedValue({ data: { data: { allowed: status !== "BLOCKED", status, checkedAt: "2026-11-17T06:00:00Z", expiresAt: "2026-11-17T18:00:00Z", freshHours: 12, findings: status === "BLOCKED" ? [{ message: "Driver's licence has expired" }] : [] } } }); wrap(<ArrivalCheckDialog deliveryId="D1" label="Diesel delivery" onClose={() => {}} />) }

  it("releases a stale check with a reason and an explanation", async () => {
    open("STALE")
    expect(await screen.findByText("Clear, but too old")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Release reason"), { target: { value: "SITE_QUEUE" } })
    fireEvent.change(screen.getByLabelText("Release explanation"), { target: { value: "Queue at the gate for two hours" } })
    fireEvent.click(screen.getByRole("button", { name: "Release check" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/deliveries/D1/arrival-check/release", { reasonCode: "SITE_QUEUE", explanation: "Queue at the gate for two hours" }))
  })

  it("will not release without an explanation", async () => {
    open("STALE")
    await screen.findByText("Clear, but too old")
    fireEvent.change(screen.getByLabelText("Release reason"), { target: { value: "OTHER" } })
    fireEvent.click(screen.getByRole("button", { name: "Release check" }))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("offers no release without the override permission", async () => {
    perms = ["FUEL_READ"]
    open("STALE")
    expect(await screen.findByText(/which you do not have/)).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Release check" })).toBeNull()
  })

  it("never offers to release a blocked check, even to someone with the permission", async () => {
    open("BLOCKED")
    expect(await screen.findByText("Blocked")).toBeTruthy()
    expect(screen.getByText("Driver's licence has expired")).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Release check" })).toBeNull()
  })

  it("shows the server's refusal of a release", async () => {
    post.mockImplementation(() => Promise.reject({ response: { data: { message: "The licence has expired since the check, so it cannot be released." } } }))
    open("STALE")
    await screen.findByText("Clear, but too old")
    fireEvent.change(screen.getByLabelText("Release reason"), { target: { value: "DRIVER_DELAYED" } })
    fireEvent.change(screen.getByLabelText("Release explanation"), { target: { value: "Driver was delayed on the N1" } })
    fireEvent.click(screen.getByRole("button", { name: "Release check" }))
    expect(await screen.findByText(/cannot be released/)).toBeTruthy()
  })
})

describe("PodExceptionDialog", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: {} }); perms = ["FUEL_POD_EXCEPTION_APPROVE"]; get.mockImplementation(() => ok([{ id: "x1", type: "AFTER_HOURS_APPROVED", reason: "Customer closed", status: "USED", evidenceCount: 2 }])) })
  afterEach(cleanup)

  it("lists exceptions already approved or used", async () => {
    wrap(<PodExceptionDialog deliveryId="D1" label="Diesel delivery" onClose={() => {}} />)
    expect(await screen.findByText(/2 pieces of evidence/)).toBeTruthy()
    expect(screen.getByText(/used/)).toBeTruthy()
  })

  it("approves an after-hours exception with a reason", async () => {
    wrap(<PodExceptionDialog deliveryId="D1" label="Diesel delivery" onClose={() => {}} />)
    await screen.findByText("Approved after-hours delivery")
    fireEvent.change(screen.getByLabelText("Exception kind"), { target: { value: "CONTACTLESS_AUTHORISED" } })
    fireEvent.change(screen.getByLabelText("Exception reason"), { target: { value: "Site closed, CCTV confirms the tank was filled" } })
    fireEvent.click(screen.getByRole("button", { name: "Approve" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/deliveries/D1/pod-exception/approve", { type: "CONTACTLESS_AUTHORISED", reason: "Site closed, CCTV confirms the tank was filled" }))
  })

  it("does not offer unattended or automated sites here: they are set on the site", async () => {
    wrap(<PodExceptionDialog deliveryId="D1" label="x" onClose={() => {}} />)
    await screen.findByText("Approved after-hours delivery")
    const opts = Array.from((screen.getByLabelText("Exception kind") as HTMLSelectElement).options).map(o => o.value)
    expect(opts).toEqual(["", "AFTER_HOURS_APPROVED", "CONTACTLESS_AUTHORISED"])
  })

  it("offers no approval without the approval permission", async () => {
    perms = ["FUEL_READ"]
    wrap(<PodExceptionDialog deliveryId="D1" label="x" onClose={() => {}} />)
    expect(await screen.findByText(/which you do not have/)).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Approve" })).toBeNull()
  })
})
