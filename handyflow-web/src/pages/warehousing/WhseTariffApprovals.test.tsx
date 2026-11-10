import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseApprovalsTab from "./WhseApprovalsTab"
import WhseRatesTab from "./WhseRatesTab"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let perms: string[] = []
let required = false
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.includes(p) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const request = { id: "r1", kind: "TARIFF", clientName: "Acme", sku: null, monthlyRate: 12.5, minimumStorageCharge: null,
  effectiveFrom: "2026-12-01", note: "2027 renewal", requestedByName: "Thabo M", requestedAt: "2026-11-09T08:00:00Z" }
const termsRequest = { ...request, id: "r2", kind: "TERMS", monthlyRate: null, minimumStorageCharge: 900, note: null }

beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); perms = []; required = false
  post.mockResolvedValue({ data: null }); put.mockResolvedValue({ data: null })
  get.mockImplementation(async (url: string) => {
    if (url.includes("tariff-approval-rule")) return { data: { required } }
    if (url.includes("tariff-requests/pending")) return { data: [request, termsRequest] }
    if (url.includes("adjustment-approval-threshold")) return { data: { thresholdQty: null } }
    if (url.includes("adjustment-requests/pending")) return { data: [] }
    return { data: [] }
  })
})
afterEach(cleanup)

describe("tariff and minimum approval", () => {
  it("lists waiting rate and minimum changes with who asked and when they start", async () => {
    wrap(<WhseApprovalsTab />)
    await waitFor(() => expect(screen.getByText(/12,50 per unit per month|12.50 per unit per month/)).toBeTruthy())
    expect(screen.getByText(/Minimum .*900.* per 30 days/)).toBeTruthy()
    expect(screen.getByText(/Starts 2026-12-01 — 2027 renewal/)).toBeTruthy()
    expect(screen.getAllByText(/Asked by Thabo M/).length).toBe(2)
  })

  it("only a holder of the approve permission sees Approve and Reject", async () => {
    wrap(<WhseApprovalsTab />)
    await waitFor(() => screen.getByText(/2027 renewal/))
    expect(screen.queryByText("Approve")).toBeNull()
    cleanup()
    perms = ["WAREHOUSING_TARIFF_APPROVE"]
    wrap(<WhseApprovalsTab />)
    await waitFor(() => expect(screen.getAllByText("Approve").length).toBe(2))
  })

  it("approving posts to the request's approve endpoint", async () => {
    perms = ["WAREHOUSING_TARIFF_APPROVE"]
    wrap(<WhseApprovalsTab />)
    fireEvent.click((await screen.findAllByText("Approve"))[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/tariff-requests/r1/approve", {}))
  })

  it("rejecting needs a reason of at least five characters", async () => {
    perms = ["WAREHOUSING_TARIFF_APPROVE"]
    wrap(<WhseApprovalsTab />)
    fireEvent.click((await screen.findAllByText("Reject"))[0])
    const confirm = screen.getByText("Confirm rejection") as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText(/Reason for rejecting/), { target: { value: "not agreed" } })
    fireEvent.click(confirm)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/tariff-requests/r1/reject", { note: "not agreed" }))
  })

  it("an administrator switches the rule on with a reason; the button states the action", async () => {
    perms = ["WAREHOUSING_ADMIN"]
    wrap(<WhseApprovalsTab />)
    const btn = (await screen.findByText("Require approval")) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Reason for the change/, { selector: "#whse-tariff-rule-reason" }), { target: { value: "client contracts" } })
    fireEvent.click(btn)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/warehousing/tariff-approval-rule", { required: true, reason: "client contracts" }))
  })

  it("with the rule on, the rates tab offers Request rate and says the change was sent", async () => {
    required = true
    perms = ["WAREHOUSING_ADMIN"]
    post.mockResolvedValue({ data: { requestId: "r9" } })
    wrap(<WhseRatesTab clientId="c1" />)
    const btn = await screen.findByText("Request rate")
    fireEvent.change(screen.getByLabelText(/New monthly rate/), { target: { value: "7" } })
    fireEvent.click(btn)
    await waitFor(() => expect(screen.getByRole("status").textContent).toMatch(/Sent for approval/))
    expect(screen.getByText("Request minimum")).toBeTruthy()
  })
})
