// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { CsPortalLoginPage } from "./CsPortalLoginPage"
import { CsPortalAcceptInvitePage } from "./CsPortalAcceptInvitePage"
import { CsPortalHomePage } from "./CsPortalHomePage"
import { usePortalAuthStore } from "../../store/portalAuth.store"

const api = vi.hoisted(() => ({ login: vi.fn(), register: vi.fn(), clients: vi.fn(), overview: vi.fn() }))
vi.mock("../../api/csPortal.api", () => ({ csPortalApi: api }))

const auth = { token: "t", userId: "u1", email: "c@x.com", fullName: "Naledi Dube" }
const overview = {
  client: { id: "c1", name: "Acme" },
  quotes: [{ id: "q1", quoteNumber: "CQ-000001", title: "Annual", status: "SENT", validUntil: "2099-01-01", total: 1150, lines: [{ serviceCode: "AFS", serviceName: "Annual financial statements", description: null, professional: 1000, disbursements: 0, vat: 150, total: 1150 }] }],
  agreements: [{ id: "a1", agreementNumber: "CA-000001", title: "Monthly", status: "ACTIVE", startDate: "2027-01-01", endDate: null, billingFrequency: "MONTHLY", billingAmount: 500, autoRenew: false, noticeDays: 30, scope: [], nextBills: [{ date: "2099-02-01", amount: 500 }] }],
  invoices: [{ invoiceNumber: "INV-1", status: "OVERDUE", billingDate: "2027-01-05", description: null, total: 500, paid: 0, credited: 0, outstanding: 500 }],
  outstanding: 500,
}
const at = (path: string, ui: React.ReactNode) => render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/compliance-services/portal/login" element={<div>login screen</div>} /><Route path="/compliance-services/portal/home" element={<div>home screen</div>} /><Route path="*" element={ui} /></Routes></MemoryRouter>)

afterEach(cleanup)
beforeEach(() => { vi.clearAllMocks(); usePortalAuthStore.setState({ token: null, user: null }) })

describe("client portal screens", () => {
  it("signs in and keeps the token", async () => {
    api.login.mockResolvedValue(auth)
    at("/x", <CsPortalLoginPage />)
    fireEvent.change(screen.getByLabelText("Email address"), { target: { value: "c@x.com" } })
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret-pass" } })
    fireEvent.click(screen.getByRole("button", { name: "Sign In" }))
    await screen.findByText("home screen")
    expect(usePortalAuthStore.getState().token).toBe("t")
  })
  it("shows the server's message when sign-in fails", async () => {
    api.login.mockRejectedValue({ response: { data: { message: "Bad login" } } })
    at("/x", <CsPortalLoginPage />)
    fireEvent.change(screen.getByLabelText("Email address"), { target: { value: "c@x.com" } })
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "x" } })
    fireEvent.click(screen.getByRole("button", { name: "Sign In" }))
    expect((await screen.findByRole("alert")).textContent).toContain("Bad login")
  })
  it("refuses an invite link with no token, and mismatched passwords", async () => {
    const first = at("/x", <CsPortalAcceptInvitePage />)
    expect(screen.getByText(/missing its token/)).toBeTruthy()
    first.unmount()
    at("/x?token=abc", <CsPortalAcceptInvitePage />)
    fireEvent.change(screen.getByLabelText("Your full name"), { target: { value: "Naledi" } })
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "long-enough" } })
    fireEvent.change(screen.getByLabelText("Confirm password"), { target: { value: "different1" } })
    fireEvent.click(screen.getByRole("button", { name: /Accept invite/ }))
    expect((await screen.findByRole("alert")).textContent).toContain("don't match")
    expect(api.register).not.toHaveBeenCalled()
  })
  it("accepts the invite with the token from the link", async () => {
    api.register.mockResolvedValue(auth)
    at("/x?token=abc", <CsPortalAcceptInvitePage />)
    fireEvent.change(screen.getByLabelText("Your full name"), { target: { value: " Naledi " } })
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "long-enough" } })
    fireEvent.change(screen.getByLabelText("Confirm password"), { target: { value: "long-enough" } })
    fireEvent.click(screen.getByRole("button", { name: /Accept invite/ }))
    await screen.findByText("home screen")
    expect(api.register).toHaveBeenCalledWith({ inviteToken: "abc", password: "long-enough", fullName: "Naledi" })
  })
  it("shows the overview, then the quotes, agreements and invoices", async () => {
    usePortalAuthStore.setState({ token: "t", user: { portalUserId: "u1", email: "c@x.com", fullName: "Naledi Dube" } })
    api.clients.mockResolvedValue([{ id: "c1", name: "Acme" }])
    api.overview.mockResolvedValue(overview)
    at("/x", <CsPortalHomePage />)
    expect(await screen.findByText("Hello, Naledi")).toBeTruthy()
    expect(api.overview).toHaveBeenCalledWith("c1")
    expect(screen.getByText(/Invoice INV-1 has R 500.00 still owing/)).toBeTruthy()
    expect(screen.getByText(/Quote CQ-000001 is waiting/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Quotes" }))
    expect(screen.getByText("Annual financial statements")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Agreements" }))
    expect(screen.getByText(/every month/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Invoices" }))
    expect(screen.getByText("Overdue")).toBeTruthy()
  })
  it("says so when the login has no client, and signs out", async () => {
    usePortalAuthStore.setState({ token: "t", user: { portalUserId: "u1", email: "c@x.com", fullName: "N" } })
    api.clients.mockResolvedValue([])
    at("/x", <CsPortalHomePage />)
    expect(await screen.findByText(/not linked to a client yet/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Sign out" }))
    await waitFor(() => expect(screen.getByText("login screen")).toBeTruthy())
    expect(usePortalAuthStore.getState().token).toBeNull()
  })
})
