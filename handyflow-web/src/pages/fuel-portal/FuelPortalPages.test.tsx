import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { MemoryRouter, Routes, Route } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { FuelPortalLoginPage } from "./FuelPortalLoginPage"
import { FuelPortalAcceptInvitePage } from "./FuelPortalAcceptInvitePage"
import { FuelPortalHomePage } from "./FuelPortalHomePage"
import { usePortalAuthStore } from "../../store/portalAuth.store"

const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/fuelPortal.client", () => ({ fuelPortalApiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

function mount(path: string) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/fuel/portal/login" element={<FuelPortalLoginPage />} />
          <Route path="/fuel/portal/auth/accept-invite" element={<FuelPortalAcceptInvitePage />} />
          <Route path="/fuel/portal" element={<FuelPortalHomePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>)
}
const type = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } })
const auth = { data: { token: "jwt-1", userId: "u1", email: "buyer@gold.co.za", fullName: "Buyer One" } }

describe("Fuel portal login and invite", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); usePortalAuthStore.getState().logout() })
  afterEach(() => cleanup())

  it("signs in, stores the portal token and opens the portal", async () => {
    post.mockResolvedValueOnce(auth)
    get.mockResolvedValue({ data: [] })
    mount("/fuel/portal/login")
    type("Email", "buyer@gold.co.za"); type("Password", "secret123")
    fireEvent.click(screen.getByText("Sign in"))
    await waitFor(() => expect(usePortalAuthStore.getState().token).toBe("jwt-1"))
    expect(post).toHaveBeenCalledWith("/api/v1/fuel/portal/auth/login", { email: "buyer@gold.co.za", password: "secret123" })
    expect(usePortalAuthStore.getState().user?.portalUserId).toBe("u1")
    await waitFor(() => screen.getByText("Fuel customer portal"))
  })

  it("shows the server message when sign in fails", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "Invalid credentials" } } })
    mount("/fuel/portal/login")
    type("Email", "a@b.co"); type("Password", "x")
    fireEvent.click(screen.getByText("Sign in"))
    await waitFor(() => screen.getByText("Invalid credentials"))
    expect(usePortalAuthStore.getState().token).toBeNull()
  })

  it("registers with the invite token from the link", async () => {
    post.mockResolvedValueOnce(auth)
    get.mockResolvedValue({ data: [] })
    mount("/fuel/portal/auth/accept-invite?token=tok-9")
    type("Your full name", "Buyer One"); type("Choose a password", "password1"); type("Confirm password", "password1")
    fireEvent.click(screen.getByText("Create account & sign in"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post).toHaveBeenCalledWith("/api/v1/fuel/portal/auth/register", { inviteToken: "tok-9", password: "password1", fullName: "Buyer One" })
    await waitFor(() => expect(usePortalAuthStore.getState().token).toBe("jwt-1"))
  })

  it("blocks registration without a token or with mismatched passwords", async () => {
    mount("/fuel/portal/auth/accept-invite")
    expect((screen.getByText("Create account & sign in") as HTMLButtonElement).disabled).toBe(true)
    cleanup()
    mount("/fuel/portal/auth/accept-invite?token=t")
    type("Your full name", "A"); type("Choose a password", "password1"); type("Confirm password", "different1")
    fireEvent.click(screen.getByText("Create account & sign in"))
    await waitFor(() => screen.getByText("Passwords don't match."))
    expect(post).not.toHaveBeenCalled()
  })
})

describe("Fuel portal home", () => {
  const order = { id: "o1", orderNumber: "FO-1", siteName: "Pit 1", fuelType: "DIESEL", litresOrdered: 10000, pricePerLitre: 20, value: 200000, status: "QUOTE", requestedDate: null, customerReference: "PO-77", litresDelivered: 0, litresRemaining: 10000, createdAt: "2026-11-10T08:00:00Z" }
  beforeEach(() => {
    get.mockReset(); post.mockReset()
    usePortalAuthStore.getState().setAuth("jwt-1", { portalUserId: "u1", email: "buyer@gold.co.za", fullName: "Buyer One" })
    get.mockImplementation(async (u: string) => {
      if (u.endsWith("/accounts")) return { data: [{ accountId: "a1", customerName: "Gold Mine Ltd", paymentTermsDays: 30 }] }
      if (u.endsWith("/orders")) return { data: [order] }
      if (u.endsWith("/deliveries")) return { data: [{ id: "d1", deliveredAt: "2026-11-09T10:00:00Z", orderNumber: "FO-0", siteName: "Pit 1", fuelType: "DIESEL", litres: 5000, pricePerLitre: 20, total: 100000, receiptNumber: "RCT-1" }] }
      if (u.endsWith("/sites")) return { data: [{ id: "s1", name: "Pit 1" }] }
      return { data: [] }
    })
  })
  afterEach(() => { cleanup(); usePortalAuthStore.getState().logout() })

  it("shows the account, orders as requests, and deliveries", async () => {
    mount("/fuel/portal")
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    await waitFor(() => screen.getByText("FO-1"))
    expect(screen.getByText("Requested")).toBeTruthy()
    expect(screen.getByText("RCT-1")).toBeTruthy()
  })

  it("sends a fuel request for the account and confirms it", async () => {
    post.mockResolvedValueOnce({ data: { ...order, orderNumber: "FO-2" } })
    mount("/fuel/portal")
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    fireEvent.click(screen.getByText("Request fuel"))
    type("Litres *", "8000"); type("Your reference", "PO-88")
    fireEvent.click(screen.getByText("Send request"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post).toHaveBeenCalledWith("/api/v1/fuel/portal/accounts/a1/order-requests",
      { siteId: null, fuelType: "DIESEL", litres: 8000, requestedDate: null, customerReference: "PO-88", notes: null })
    await waitFor(() => screen.getByText(/Request sent \(FO-2\)/))
  })

  it("rejects an oversize request without calling the server, and shows server errors", async () => {
    mount("/fuel/portal")
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    fireEvent.click(screen.getByText("Request fuel"))
    type("Litres *", "600000")
    fireEvent.click(screen.getByText("Send request"))
    await waitFor(() => screen.getByText(/500,000 litres/))
    expect(post).not.toHaveBeenCalled()
    type("Litres *", "1000")
    post.mockRejectedValueOnce({ response: { data: { message: "We could not price this request automatically. Please call us to place it." } } })
    fireEvent.click(screen.getByText("Send request"))
    await waitFor(() => screen.getByText(/Please call us/))
  })

  it("says so when the user has no active account", async () => {
    get.mockImplementation(async () => ({ data: [] }))
    mount("/fuel/portal")
    await waitFor(() => screen.getByText(/no longer active/))
  })

  it("signs out", async () => {
    mount("/fuel/portal")
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    fireEvent.click(screen.getByText("Sign out"))
    expect(usePortalAuthStore.getState().token).toBeNull()
  })
})
