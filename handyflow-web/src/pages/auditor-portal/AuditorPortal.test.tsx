import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"

vi.mock("../../api/auditorPortal.api", () => ({ auditorPortalApi: { login: vi.fn(), register: vi.fn(), getMyTenants: vi.fn(), getControlExceptions: vi.fn(), getEvidence: vi.fn() } }))
vi.mock("../../components/evidenceRequests/EvidenceRequests", () => ({ EvidenceRequests: () => <div>requests</div> }))
vi.mock("../../components/auditQueries/AuditQueries", () => ({ AuditQueries: () => <div>queries</div> }))
vi.mock("../../api/evidenceRequests.api", () => ({ auditorEvidenceRequestApi: () => ({}) }))
vi.mock("../../api/auditQueries.api", () => ({ auditorAuditQueryApi: () => ({}) }))
import { auditorPortalApi } from "../../api/auditorPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { AuditorPortalLoginPage } from "./AuditorPortalLoginPage"
import { AuditorPortalHomePage } from "./AuditorPortalHomePage"
import { AuditorPortalTenantDetailPage } from "./AuditorPortalTenantDetailPage"

const m = auditorPortalApi as any
afterEach(cleanup)
beforeEach(() => { vi.clearAllMocks(); usePortalAuthStore.setState({ token: null, user: null }) })

const at = (ui: React.ReactElement, path = "/") => render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/" element={ui} /><Route path="/auditor/portal" element={<div>portal home</div>} /><Route path="/auditor/portal/tenants/:tenantId" element={ui} /></Routes></MemoryRouter>)

describe("Auditor portal", () => {
  it("login fields are labelled, and a good sign-in stores the portal user", async () => {
    m.login.mockResolvedValue({ token: "t", portalUserId: "p1", email: "a@x.co", fullName: "Alma Auditor" })
    at(<AuditorPortalLoginPage />)
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "a@x.co" } })
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret123" } })
    fireEvent.click(screen.getByText("Sign In"))
    await waitFor(() => expect(m.login).toHaveBeenCalledWith({ email: "a@x.co", password: "secret123" }))
    expect(await screen.findByText("portal home")).toBeTruthy()
    expect(usePortalAuthStore.getState().user).toEqual({ portalUserId: "p1", email: "a@x.co", fullName: "Alma Auditor" })
  })

  it("a failed sign-in is announced", async () => {
    m.login.mockRejectedValue({ response: { data: { message: "Wrong email or password" } } })
    at(<AuditorPortalLoginPage />)
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "a@x.co" } })
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "bad" } })
    fireEvent.click(screen.getByText("Sign In"))
    expect((await screen.findByRole("alert")).textContent).toBe("Wrong email or password")
  })

  it("a load failure on the home page is shown, not mistaken for having no access", async () => {
    m.getMyTenants.mockRejectedValue({ response: { data: { message: "Service unavailable" } } })
    at(<AuditorPortalHomePage />)
    expect((await screen.findByRole("alert")).textContent).toBe("Service unavailable")
    expect(screen.queryByText("No businesses have granted you access yet.")).toBeNull()
  })

  it("no access is shown as no access", async () => {
    m.getMyTenants.mockResolvedValue([])
    at(<AuditorPortalHomePage />)
    expect(await screen.findByText("No businesses have granted you access yet.")).toBeTruthy()
  })

  it("a load failure on a business is shown, not mistaken for empty records", async () => {
    m.getControlExceptions.mockRejectedValue({ response: { data: { message: "Access revoked" } } })
    m.getEvidence.mockResolvedValue([])
    at(<AuditorPortalTenantDetailPage />, "/auditor/portal/tenants/abcdef123456")
    expect((await screen.findByRole("alert")).textContent).toBe("Access revoked")
    expect(screen.queryByText("No control exceptions on record.")).toBeNull()
  })

  it("the sections are real tabs and switch", async () => {
    m.getControlExceptions.mockResolvedValue([])
    m.getEvidence.mockResolvedValue([])
    at(<AuditorPortalTenantDetailPage />, "/auditor/portal/tenants/abcdef123456")
    expect(await screen.findByText("No control exceptions on record.")).toBeTruthy()
    expect(screen.getByRole("tab", { name: "Control Exceptions" }).getAttribute("aria-selected")).toBe("true")
    fireEvent.click(screen.getByRole("tab", { name: "Evidence" }))
    expect(await screen.findByText("No evidence on file.")).toBeTruthy()
    expect(screen.getByRole("tab", { name: "Evidence" }).getAttribute("aria-selected")).toBe("true")
  })
})
