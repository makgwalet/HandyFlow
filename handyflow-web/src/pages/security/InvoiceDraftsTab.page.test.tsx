// @vitest-environment jsdom
import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
vi.mock("./companyZone", async orig => ({ ...(await orig<typeof import("./companyZone")>()), useCompanyZone: () => ({ zone: "Africa/Johannesburg", ready: true }) }))
import InvoiceDraftsTab from "./InvoiceDraftsTab"
import { useAuthStore } from "../../store/auth.store"

const wrap = (data: unknown) => ({ data: { data } })
const item = (o = {}) => ({ id: "d1", siteId: "s1", siteName: "North Gate", month: "2026-10", basis: "SCHEDULED", currency: "ZAR", status: "DRAFT", totalCents: 1850000, inProgress: false,
  unpricedHours: 0, mixedVat: false, note: null, createdAt: "2026-11-01T08:00:00Z", approvedAt: null, voidedAt: null, voidReason: null, ...o })
let body: unknown
const show = () => render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><InvoiceDraftsTab /></QueryClientProvider></MemoryRouter>)
const listCalls = () => api.get.mock.calls.filter(c => c[0] === "/api/v1/security/invoice-drafts")

describe("InvoiceDraftsTab", () => {
  beforeEach(() => {
    api.get.mockReset(); body = { items: [item(), item({ id: "d2", siteId: "s2", siteName: "Mall", status: "APPROVED", totalCents: 500000 }), item({ id: "d3", status: "VOID", voidReason: "Wrong rate", totalCents: 99 })], truncated: false }
    api.get.mockImplementation((url: string) => url === "/api/v1/security/invoice-drafts" ? (body instanceof Error ? Promise.reject(body) : Promise.resolve(wrap(body)))
      : Promise.resolve(wrap({ draft: item(), snapshot: { lines: [], totalCents: 1850000, billedHours: 0, unpricedHours: 0, warnings: [] } })))
    useAuthStore.setState({ user: { permissions: ["SECURITY_ADMIN"] } } as never)
  })
  afterEach(() => { cleanup(); useAuthStore.setState({ user: null } as never) })

  it("asks for nothing and says why when the user is not an administrator", () => {
    useAuthStore.setState({ user: { permissions: ["SECURITY_MANAGE"] } } as never)
    show()
    expect(screen.getByText(/for administrators/)).toBeTruthy()
    expect(api.get).not.toHaveBeenCalled()
  })

  it("lists the drafts of every site with their state, and counts what is waiting", async () => {
    show()
    expect((await screen.findAllByText("North Gate")).length).toBe(2)
    expect(screen.getByText("Mall")).toBeTruthy()
    expect(screen.getByTestId("count-waiting").textContent).toBe("1")
    expect(screen.getByTestId("count-approved").textContent).toBe("1")
    expect(screen.getByTestId("live-total").textContent).toBe("R23 500.00")
    expect(screen.getByText("Voided: Wrong rate")).toBeTruthy()
    expect(listCalls()[0][1]).toEqual({ params: {} })
  })

  it("links each draft to its site", async () => {
    show()
    const link = await screen.findByText("Mall")
    expect(link.getAttribute("href")).toBe("/security/sites/s2")
  })

  it("asks the server for the month and status chosen, and nothing for a month that is not one", async () => {
    show()
    await screen.findAllByText("North Gate")
    fireEvent.change(screen.getByLabelText("Status"), { target: { value: "DRAFT" } })
    await waitFor(() => expect(listCalls().some(c => c[1].params.status === "DRAFT")).toBe(true))
    fireEvent.change(screen.getByLabelText("Month"), { target: { value: "2026-10" } })
    await waitFor(() => expect(listCalls().some(c => c[1].params.month === "2026-10" && c[1].params.status === "DRAFT")).toBe(true))
    fireEvent.click(screen.getByText("Clear filters"))
    await waitFor(() => expect(listCalls().at(-1)![1]).toEqual({ params: {} }))
  })

  it("flags drafts worth a second look", async () => {
    body = { items: [item({ inProgress: true, unpricedHours: 3, mixedVat: true })], truncated: false }
    show()
    expect(await screen.findByText("Month was still in progress")).toBeTruthy()
    expect(screen.getByText("3 hours with no rate")).toBeTruthy()
    expect(screen.getByText("Mixed VAT")).toBeTruthy()
  })

  it("gives no total for a list that was cut off, and says so", async () => {
    body = { items: [item()], truncated: true }
    show()
    expect((await screen.findByRole("status")).textContent).toMatch(/Only the newest 1 drafts are shown, so no total/)
    expect(screen.queryByTestId("live-total")).toBeNull()
  })

  it("says so when nothing matches, and when nothing exists yet", async () => {
    body = { items: [], truncated: false }
    show()
    expect(await screen.findByText(/No invoice drafts yet/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Status"), { target: { value: "VOID" } })
    expect(await screen.findByText(/No invoice drafts match these filters/)).toBeTruthy()
  })

  it("shows the server's words on failure, not an empty list", async () => {
    body = Object.assign(new Error("x"), { response: { status: 403, data: { message: "Not allowed here." } } })
    show()
    expect(await screen.findByText("Not allowed here.")).toBeTruthy()
    expect(screen.queryByText(/No invoice drafts/)).toBeNull()
  })

  it("opens a draft using that draft's own site", async () => {
    show()
    fireEvent.click(await screen.findByLabelText(/Show details of Mall/))
    await waitFor(() => expect(api.get.mock.calls.some(c => c[0] === "/api/v1/security/sites/s2/invoice-drafts/d2")).toBe(true))
  })
})
