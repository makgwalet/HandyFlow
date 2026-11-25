// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TimePage from "./TimePage"
import ClientTimeTab from "./ClientTimeTab"
import ProfitabilityPage from "./ProfitabilityPage"

const get = vi.fn()
const post = vi.fn()
let admin = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => admin, usePermission: () => admin }))

const entry = (over: object = {}) => ({ id: "t1", clientId: "c1", clientName: "Acme", userId: "u1", workDate: "2026-11-24", minutes: 90, serviceCode: "CS-REVIEW", kind: "PLANNED", billable: true, note: null, status: "ACTIVE", voidReason: null, createdAt: "2026-11-24T08:00:00Z", ...over })
const summary = { from: "2026-11-19", to: "2026-11-25", minutes: 150, billableMinutes: 120, reworkMinutes: 30, unplannedMinutes: 0, byClient: [], byDay: [] }
const report = {
  from: "2026-11-01", to: "2026-11-25", minutes: 300, unratedMinutes: 60, labourCost: 800, invoiced: 2000, collected: 1000,
  clients: [{ clientId: "c1", clientName: "Acme", minutes: 300, billableMinutes: 240, reworkMinutes: 60, unplannedMinutes: 0, unratedMinutes: 60, costComplete: false, labourCost: 800, invoiced: 2000, collected: 1000, margin: 1200, marginPct: 60 }],
  services: [{ serviceCode: "CS-REVIEW", minutes: 300, unratedMinutes: 60, labourCost: 800 }],
  people: [{ userId: "u1", minutes: 300, billableMinutes: 240, unratedMinutes: 60, utilisationPct: 80, labourCost: 800 }],
  basis: "Labour cost only.",
}
const staff = [{ id: "u1", firstName: "Thandi", lastName: "Mokoena" }]

function wrap(ui: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

beforeEach(() => {
  admin = true
  get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => {
    if (url.endsWith("/time-entries/mine")) return Promise.resolve({ data: { data: [entry(), entry({ id: "t2", status: "VOID", voidReason: "Wrong client" })] } })
    if (url.endsWith("/time-entries/summary")) return Promise.resolve({ data: { data: summary } })
    if (url.includes("/clients/c1/time-entries")) return Promise.resolve({ data: { data: [entry()] } })
    if (url.includes("/clients?size")) return Promise.resolve({ data: { data: [{ id: "c1", name: "Acme" }] } })
    if (url.endsWith("/identity/users")) return Promise.resolve({ data: { data: staff } })
    if (url.endsWith("/profitability/report")) return Promise.resolve({ data: { data: report } })
    if (url.endsWith("/profitability/cost-rates")) return Promise.resolve({ data: { data: [{ id: "r1", userId: "u1", hourlyCost: 350, effectiveFrom: "2026-01-01", setBy: "u1", setAt: "2026-01-01T00:00:00Z" }] } })
    return Promise.resolve({ data: { data: [] } })
  })
  post.mockResolvedValue({ data: { data: entry() } })
})
afterEach(cleanup)

describe("My time", () => {
  it("shows the week and the last two weeks, with a voided entry marked", async () => {
    wrap(<TimePage />)
    expect(await screen.findByText(/Last 7 days/)).toBeTruthy()
    expect((await screen.findAllByText("Acme")).length).toBeGreaterThan(0)
    expect(document.body.textContent).toContain("Voided: Wrong client")
  })
  it("will not record without a client or a readable duration", async () => {
    wrap(<TimePage />)
    await screen.findByText(/Last 7 days/)
    fireEvent.click(screen.getByRole("button", { name: "Record time" }))
    expect(await screen.findByText("Choose the client")).toBeTruthy()
    fireEvent.change(await screen.findByLabelText("Client"), { target: { value: "c1" } })
    fireEvent.change(screen.getByLabelText("How long"), { target: { value: "lots" } })
    fireEvent.click(screen.getByRole("button", { name: "Record time" }))
    expect(await screen.findByText(/Say how long it took/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })
  it("sends minutes read from what was typed", async () => {
    wrap(<TimePage />)
    await screen.findByText(/Last 7 days/)
    await waitFor(() => expect(screen.getByRole("option", { name: "Acme" })).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Client"), { target: { value: "c1" } })
    fireEvent.change(screen.getByLabelText("How long"), { target: { value: "1h 30m" } })
    fireEvent.click(screen.getByRole("button", { name: "Record time" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0] as [string, { minutes: number; clientId: string; billable: boolean }]
    expect(url.endsWith("/time-entries")).toBe(true)
    expect(body.minutes).toBe(90)
    expect(body.clientId).toBe("c1")
    expect(body.billable).toBe(true)
  })
  it("needs a real reason to void", async () => {
    wrap(<TimePage />)
    fireEvent.click(await screen.findByRole("button", { name: "Void" }))
    fireEvent.change(screen.getByLabelText("Why is this entry wrong?"), { target: { value: "oops" } })
    fireEvent.click(screen.getByRole("button", { name: "Confirm void" }))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Why is this entry wrong?"), { target: { value: "Entered against the wrong client" } })
    fireEvent.click(screen.getByRole("button", { name: "Confirm void" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/compliance-services/time-entries/t1/void", { reason: "Entered against the wrong client" }))
  })
})

describe("Client time tab", () => {
  it("lists hours with the person's name", async () => {
    wrap(<ClientTimeTab clientId="c1" />)
    expect(await screen.findByText(/Thandi Mokoena/)).toBeTruthy()
    expect(document.body.textContent).toContain("Total: 1h 30m")
  })
})

describe("Profitability", () => {
  it("shows the report and warns when hours have no cost rate", async () => {
    wrap(<ProfitabilityPage />)
    expect(await screen.findByText("Labour cost only.")).toBeTruthy()
    expect(document.body.textContent).toContain("1h of these hours have no cost rate")
    expect(document.body.textContent).toContain("Some hours have no cost rate")
    expect(document.body.textContent).toContain("Thandi Mokoena")
  })
  it("is closed to people who are not administrators and asks the server for nothing", async () => {
    admin = false
    wrap(<ProfitabilityPage />)
    expect(await screen.findByText(/only administrators can see it/)).toBeTruthy()
    expect(get).not.toHaveBeenCalled()
  })
  it("checks a cost rate before sending it", async () => {
    wrap(<ProfitabilityPage />)
    await screen.findByText("Labour cost only.")
    fireEvent.click(screen.getByRole("button", { name: "Add rate" }))
    expect(await screen.findByText("Choose the person")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Person"), { target: { value: "u1" } })
    fireEvent.change(screen.getByLabelText("Cost of one hour (R)"), { target: { value: "350" } })
    fireEvent.click(screen.getByRole("button", { name: "Add rate" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/compliance-services/profitability/cost-rates", expect.objectContaining({ userId: "u1", hourlyCost: 350 })))
  })
})
