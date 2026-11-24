// @vitest-environment jsdom
import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
vi.mock("./companyZone", async orig => ({ ...(await orig<typeof import("./companyZone")>()), useCompanyZone: () => ({ zone: "Africa/Johannesburg", ready: true }) }))
import SiteBillingCard from "./SiteBillingCard"
import { useAuthStore } from "../../store/auth.store"

const wrap = (data: unknown) => ({ data: { data } })
const rate = (o = {}) => ({ id: "r1", siteId: "s1", type: "HOURLY", amountCents: 18500, currency: "ZAR", vatInclusive: false, effectiveFrom: "2026-01-01", effectiveTo: null, note: null, createdAt: "2026-01-01T00:00:00Z", ...o })
const preview = (o = {}) => ({
  siteId: "s1", siteName: "Mall", month: "2026-10", from: "2026-10-01", to: "2026-10-31", inProgress: false, basis: "SCHEDULED", currency: "ZAR", excludesVat: true,
  lines: [{ rateId: "r1", type: "HOURLY", rateCents: 18500, vatInclusive: false, hours: 100, daysInForce: 31, amountCents: 1850000 }],
  billedHours: 100, unpricedHours: 0, mixedVat: false, totalCents: 1850000, warnings: ["VAT is not calculated. Amounts are as set on each rate."], ...o })
const draft = (o = {}) => ({ id: "d1", siteId: "s1", month: "2026-09", basis: "SCHEDULED", currency: "ZAR", status: "DRAFT", totalCents: 1000000, inProgress: false, unpricedHours: 0, mixedVat: false,
  note: null, createdAt: "2026-10-02T00:00:00Z", approvedAt: null, voidedAt: null, voidReason: null, ...o })

let rates: unknown[] = [], drafts: unknown[] = [], pv: unknown = preview()
function route() {
  api.get.mockImplementation((url: string) => {
    if (url.endsWith("/rates")) return Promise.resolve(wrap(rates))
    if (url.endsWith("/billing-preview")) return Promise.resolve(wrap(pv))
    if (url.endsWith("/invoice-drafts")) return Promise.resolve(wrap(drafts))
    return Promise.reject(new Error("unexpected " + url))
  })
  api.post.mockResolvedValue(wrap({}))
}
function show() {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SiteBillingCard siteId="s1" /></QueryClientProvider>)
}
const asAdmin = () => useAuthStore.setState({ user: { permissions: ["SECURITY_ADMIN"] } } as never)

describe("SiteBillingCard", () => {
  beforeEach(() => { api.get.mockReset(); api.post.mockReset(); rates = [rate()]; drafts = []; pv = preview(); route(); asAdmin() })
  afterEach(() => { cleanup(); useAuthStore.setState({ user: null } as never) })

  it("shows nothing, and asks for nothing, to anyone without SECURITY_ADMIN", () => {
    useAuthStore.setState({ user: { permissions: ["SECURITY_MANAGE"] } } as never)
    const { container } = show()
    expect(container.innerHTML).toBe("")
    expect(api.get).not.toHaveBeenCalled()
  })

  it("lists rates, the month's total and the warnings", async () => {
    show()
    expect(await screen.findByText("R185.00 per hour", { selector: "strong" })).toBeTruthy()
    expect(screen.getByText("In force")).toBeTruthy()
    expect(screen.getByText("Excludes VAT")).toBeTruthy()
    expect(await screen.findByTestId("preview-total")).toHaveProperty("textContent", "R18 500.00")
    expect(screen.getByText(/^VAT is not calculated\. Amounts are as set/)).toBeTruthy()
    const call = api.get.mock.calls.find(c => String(c[0]).endsWith("/billing-preview"))!
    expect(call[1].params).toEqual({ month: expect.stringMatching(/^\d{4}-\d{2}$/), basis: "SCHEDULED" })
  })

  it("asks for the other basis and the earlier month", async () => {
    show()
    await screen.findByTestId("preview-total")
    fireEvent.change(screen.getByLabelText("Hours to bill"), { target: { value: "ACTUAL" } })
    await waitFor(() => expect(api.get.mock.calls.some(c => String(c[0]).endsWith("/billing-preview") && c[1].params.basis === "ACTUAL")).toBe(true))
    const before = api.get.mock.calls.filter(c => String(c[0]).endsWith("/billing-preview")).length
    fireEvent.click(screen.getByLabelText("Previous billing month"))
    await waitFor(() => expect(api.get.mock.calls.filter(c => String(c[0]).endsWith("/billing-preview")).length).toBeGreaterThan(before))
  })

  it("says so when no rate covers the month, and shows the server's refusal instead of a total", async () => {
    pv = preview({ lines: [], totalCents: 0, billedHours: 0 }); rates = []
    show()
    expect(await screen.findByText(/No rate covers this month/)).toBeTruthy()
    expect(screen.getByText(/No rates yet/)).toBeTruthy()
    cleanup(); api.get.mockImplementation((url: string) => String(url).endsWith("/billing-preview") ? Promise.reject({ response: { status: 400, data: { message: "That month has not started." } } }) : Promise.resolve(wrap([])))
    show()
    expect(await screen.findByText("That month has not started.")).toBeTruthy()
    expect(screen.queryByTestId("preview-total")).toBeNull()
  })

  it("checks a new rate before sending, then sends cents", async () => {
    show()
    fireEvent.click(await screen.findByText("Add a rate"))
    fireEvent.click(screen.getByText("Save rate"))
    expect(screen.getByRole("alert").textContent).toMatch(/amount in rand/)
    expect(api.post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Amount in rand"), { target: { value: "190,50" } })
    fireEvent.change(screen.getByLabelText("First day"), { target: { value: "2026-11-01" } })
    fireEvent.click(screen.getByText("Save rate"))
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1))
    expect(api.post.mock.calls[0][0]).toBe("/api/v1/security/sites/s1/rates")
    expect(api.post.mock.calls[0][1]).toEqual({ rateType: "HOURLY", amountCents: 19050, vatInclusive: false, effectiveFrom: "2026-11-01", effectiveTo: null, note: null })
  })

  it("shows the server's message when a rate is refused", async () => {
    api.post.mockRejectedValue({ response: { status: 409, data: { message: "This overlaps an existing rate." } } })
    show()
    fireEvent.click(await screen.findByText("Add a rate"))
    fireEvent.change(screen.getByLabelText("Amount in rand"), { target: { value: "190" } })
    fireEvent.change(screen.getByLabelText("First day"), { target: { value: "2026-11-01" } })
    fireEvent.click(screen.getByText("Save rate"))
    expect(await screen.findByText("This overlaps an existing rate.")).toBeTruthy()
  })

  it("ends an open-ended rate with a last day", async () => {
    show()
    fireEvent.click(await screen.findByText("End this rate"))
    const confirm = screen.getByText("Confirm") as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Last day of this rate"), { target: { value: "2026-12-31" } })
    fireEvent.click(confirm)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/security/sites/s1/rates/r1/end", { lastDay: "2026-12-31" }))
  })

  it("offers no end button for a rate that already has an end date", async () => {
    rates = [rate({ effectiveTo: "2026-12-31" })]
    show()
    await screen.findByText("R185.00 per hour", { selector: "strong" })
    expect(screen.queryByText("End this rate")).toBeNull()
  })

  it("saves the shown month and basis as a draft", async () => {
    show()
    await screen.findByTestId("preview-total")
    fireEvent.change(screen.getByLabelText("Draft note"), { target: { value: "  PO 4471 " } })
    fireEvent.click(await screen.findByText(/as a draft$/))
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1))
    expect(api.post.mock.calls[0][0]).toBe("/api/v1/security/sites/s1/invoice-drafts")
    expect(api.post.mock.calls[0][1]).toEqual({ month: expect.stringMatching(/^\d{4}-\d{2}$/), basis: "SCHEDULED", note: "PO 4471" })
  })

  it("saves a draft on the basis being previewed", async () => {
    show()
    await screen.findByTestId("preview-total")
    fireEvent.change(screen.getByLabelText("Hours to bill"), { target: { value: "ACTUAL" } })
    fireEvent.click(await screen.findByText(/hours actually worked\) as a draft$/))
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1))
    expect(api.post.mock.calls[0][1].basis).toBe("ACTUAL")
  })

  it("will not offer a second draft for a month that has a live one", async () => {
    const m = (() => { const d = new Date(); d.setMonth(d.getMonth() - 1); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}` })()
    drafts = [draft({ month: m })]
    show()
    expect(await screen.findByText(/There is already an invoice draft for this month/)).toBeTruthy()
    expect((screen.getByText(/as a draft$/) as HTMLButtonElement).disabled).toBe(true)
  })

  it("approves a clean draft", async () => {
    drafts = [draft()]
    show()
    fireEvent.click(await screen.findByText("Approve"))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/security/sites/s1/invoice-drafts/d1/approve", { acknowledgeWarnings: false }))
  })

  it("needs a tick before approving a draft with unpriced hours, and sends it", async () => {
    drafts = [draft({ unpricedHours: 4 })]
    show()
    const approve = await screen.findByText("Approve") as HTMLButtonElement
    expect(approve.disabled).toBe(true)
    fireEvent.click(screen.getByLabelText(/4 hours with no contract rate/))
    expect(approve.disabled).toBe(false)
    fireEvent.click(approve)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/security/sites/s1/invoice-drafts/d1/approve", { acknowledgeWarnings: true }))
  })

  it("will not approve a draft made for a month still in progress or one that bills nothing", async () => {
    drafts = [draft({ id: "a", inProgress: true }), draft({ id: "b", month: "2026-08", totalCents: 0 })]
    show()
    const buttons = await screen.findAllByText("Approve")
    expect(buttons.every(b => (b as HTMLButtonElement).disabled)).toBe(true)
    expect(screen.getByText(/still in progress/)).toBeTruthy()
    expect(screen.getByText(/bills nothing/)).toBeTruthy()
  })

  it("shows the server's message when approval is refused (second person rule)", async () => {
    drafts = [draft()]
    api.post.mockRejectedValue({ response: { status: 403, data: { message: "You saved this invoice draft, so someone else must approve it" } } })
    show()
    fireEvent.click(await screen.findByText("Approve"))
    expect(await screen.findByText(/someone else must approve it/)).toBeTruthy()
  })

  it("voids with a reason of at least five characters", async () => {
    drafts = [draft({ status: "APPROVED" })]
    show()
    fireEvent.click(await screen.findByText("Void"))
    const go = screen.getByText("Void it") as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason for voiding"), { target: { value: "abc" } })
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason for voiding"), { target: { value: " Wrong site " } })
    fireEvent.click(go)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/security/sites/s1/invoice-drafts/d1/void", { reason: "Wrong site" }))
  })

  it("shows a void draft with its reason and no actions", async () => {
    drafts = [draft({ status: "VOID", voidReason: "Wrong rate" })]
    show()
    expect(await screen.findByText("Voided: Wrong rate")).toBeTruthy()
    const row = screen.getByText("Voided: Wrong rate").closest("div")!.parentElement!.parentElement!
    expect(within(row).queryByRole("button", { name: "Approve" })).toBeNull()
    expect(within(row).queryByRole("button", { name: "Void" })).toBeNull()
    expect(within(row).getByText("Void")).toBeTruthy() // the status chip
  })

  it("shows an error, not an empty list, when drafts or rates fail to load", async () => {
    api.get.mockImplementation((url: string) => String(url).endsWith("/billing-preview") ? Promise.resolve(wrap(preview())) : Promise.reject(new Error("x")))
    show()
    await waitFor(() => expect(screen.getAllByRole("alert").length).toBe(2))
    expect(screen.queryByText("No drafts yet.")).toBeNull()
    expect(screen.queryByText(/No rates yet/)).toBeNull()
  })
})
