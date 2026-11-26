// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvSitePermissionsPanel from "./TrainProvSitePermissionsPanel"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const site = (id: string, name: string) => ({ id, name, siteType: "MAIN", address: null, province: null, contactPerson: null, phone: null, status: "ACTIVE", retiredReason: null })
const perm = (over: object = {}) => ({
  id: "x1", practitionerId: "p1", practitionerNo: "PRC-00001", practitionerName: "Thandi Mokoena", siteId: "s1", siteName: "Main campus", role: "FACILITATOR",
  programmeId: null, programmeTitle: null, validFrom: "2020-03-01", validTo: null, basis: "Placed for the new intake", status: "ACTIVE", withdrawnReason: null, inForceToday: true, createdAt: "", ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => {
  get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => Promise.resolve({
    data: { data: /\/sites$/.test(url) ? [site("s1", "Main campus"), site("s2", "North branch")] : /practitioners$/.test(url) ? [{ id: "p1", practitionerNo: "PRC-00001", firstNames: "Thandi", surname: "Mokoena" }]
      : /programmes/.test(url) ? [] : [perm()] },
  }))
  post.mockResolvedValue({ data: { data: perm() } })
})
afterEach(cleanup)

describe("site permissions", () => {
  it("says it is the provider's own record and not an approval", async () => {
    wrap(<TrainProvSitePermissionsPanel />)
    expect(await screen.findByText(/does not replace them, and it is not a regulatory approval/)).toBeTruthy()
    expect(await screen.findByText("Thandi Mokoena")).toBeTruthy()
    expect(screen.getByText("In force")).toBeTruthy()
  })

  it("will not save without a person", async () => {
    wrap(<TrainProvSitePermissionsPanel />)
    fireEvent.click(await screen.findByText("Record a permission"))
    fireEvent.click(screen.getByText("Save permission"))
    expect((await screen.findByRole("alert")).textContent).toBe("Choose the person")
    expect(post).not.toHaveBeenCalled()
  })

  it("records a permission", async () => {
    wrap(<TrainProvSitePermissionsPanel />)
    fireEvent.click(await screen.findByText("Record a permission"))
    await waitFor(() => expect(screen.getAllByRole("option", { name: "North branch" }).length).toBeGreaterThan(1))
    await waitFor(() => expect(screen.getByRole("option", { name: /Thandi Mokoena/ })).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Person"), { target: { value: "p1" } })
    fireEvent.change(screen.getByLabelText("Site"), { target: { value: "s2" } })
    fireEvent.change(screen.getByLabelText("Why is the person placed here?"), { target: { value: "Covers the northern intake" } })
    fireEvent.click(screen.getByText("Save permission"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/site-permissions$/), expect.objectContaining({ practitionerId: "p1", siteId: "s2", role: "FACILITATOR", programmeId: null })))
  })

  it("needs a reason to withdraw", async () => {
    wrap(<TrainProvSitePermissionsPanel />)
    fireEvent.click(await screen.findByText("Withdraw"))
    fireEvent.click(screen.getByText("Withdraw permission"))
    expect((await screen.findByRole("alert")).textContent).toContain("at least 10")
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Why is it withdrawn?"), { target: { value: "Moved to the northern branch" } })
    fireEvent.click(screen.getByText("Withdraw permission"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/x1\/withdraw$/), { reason: "Moved to the northern branch" }))
  })
})
