import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"

const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
import SiteAccessOverviewTab from "./SiteAccessOverviewTab"
import { useAuthStore } from "../../store/auth.store"

const wrap = (data: unknown) => ({ data: { data } })
const lu = (o = {}) => ({ userId: "u2", name: "Thandi Nkosi", email: "thandi@acme.co.za", status: "ACTIVE", branches: 1, clients: 0, sites: 2, visibleSites: 5, ...o })
let list: unknown
const scopeOf = { restricted: true, branches: [{ id: "b1", name: "Johannesburg", role: "VIEWER" }], clients: [], sites: [{ id: "s1", name: "Mall" }], visibleSites: 5 }
const whole = { restricted: false, branches: [], clients: [], sites: [], visibleSites: -1 }

const show = () => render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SiteAccessOverviewTab /></QueryClientProvider></MemoryRouter>)

describe("SiteAccessOverviewTab", () => {
  beforeEach(() => {
    api.get.mockReset(); api.put.mockReset()
    list = [lu(), lu({ userId: "u3", name: "Pieter van Wyk", email: "pieter@acme.co.za", branches: 0, sites: 0, clients: 1, visibleSites: 0 }), lu({ userId: "u4", name: "Sipho Dlamini", email: "sipho@acme.co.za", status: "SUSPENDED", visibleSites: 1 })]
    api.get.mockImplementation((url: string) => {
      if (url === "/api/v1/security/site-access") return list instanceof Error ? Promise.reject(list) : Promise.resolve(wrap(list))
      if (url.includes("/users/u2/site-scope")) return Promise.resolve(wrap(scopeOf))
      if (url.endsWith("/my-site-scope")) return Promise.resolve(wrap(whole))
      if (url === "/api/v1/security/branches") return Promise.resolve(wrap([{ id: "b1", name: "Johannesburg", active: true }]))
      if (url.startsWith("/api/v1/security/sites")) return Promise.resolve(wrap({ content: [{ id: "s1", name: "Mall" }] }))
      return Promise.reject(new Error("unexpected " + url))
    })
    useAuthStore.setState({ user: { permissions: ["SECURITY_ADMIN"], userId: "u1" } } as never)
  })
  afterEach(() => { cleanup(); useAuthStore.setState({ user: null } as never) })

  it("asks for nothing, and says why, when the user is not an administrator", () => {
    useAuthStore.setState({ user: { permissions: ["SECURITY_MANAGE"] } } as never)
    show()
    expect(screen.getByText(/for administrators/)).toBeTruthy()
    expect(api.get).not.toHaveBeenCalled()
  })

  it("lists the limited users with how they are limited and how many sites that leaves them", async () => {
    show()
    expect(await screen.findByText("Thandi Nkosi")).toBeTruthy()
    expect(screen.getByTestId("limited-count").textContent).toBe("3 limited users")
    expect(screen.getAllByText("1 branch, 2 named sites")).toHaveLength(2)
    expect(screen.getByText("1 client")).toBeTruthy()
    expect(screen.getByText("5 sites")).toBeTruthy()
  })

  it("points out anyone who can see no sites, and anyone not active", async () => {
    show()
    expect(await screen.findByText("Sees no sites")).toBeTruthy()
    expect(screen.getByText("1 can see no sites")).toBeTruthy()
    expect(screen.getByText("Suspended")).toBeTruthy()
  })

  it("searches by name or email", async () => {
    show()
    await screen.findByText("Thandi Nkosi")
    fireEvent.change(screen.getByLabelText("Search people"), { target: { value: "wyk" } })
    expect(screen.queryByText("Thandi Nkosi")).toBeNull()
    expect(screen.getByText("Pieter van Wyk")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Search people"), { target: { value: "zzz" } })
    expect(screen.getByText("Nobody matches that search.")).toBeTruthy()
  })

  it("says so when nobody is limited", async () => {
    list = []
    show()
    expect(await screen.findByText(/Nobody is limited/)).toBeTruthy()
    expect(screen.getByTestId("limited-count").textContent).toBe("0 limited users")
  })

  it("shows the server's words, not an empty list, when a limited administrator is refused", async () => {
    list = Object.assign(new Error("x"), { response: { status: 403, data: { errorCode: "SITE_SCOPE_LIMITED", message: "This needs access to the whole company. Your access is limited to some sites." } } })
    show()
    expect(await screen.findByText(/This needs access to the whole company/)).toBeTruthy()
    expect(screen.queryByText(/Nobody is limited/)).toBeNull()
  })

  it("opens the access panel for the person chosen, with the signed-in user as the actor, and closes again", async () => {
    show()
    fireEvent.click(await screen.findByLabelText("Edit access for Thandi Nkosi"))
    const dialog = await screen.findByRole("dialog")
    expect(within(dialog).getByText("Site access: Thandi Nkosi")).toBeTruthy()
    expect(await within(dialog).findByLabelText("Access to Johannesburg")).toBeTruthy()
    expect(api.get.mock.calls.some(c => String(c[0]).includes("/users/u2/site-scope"))).toBe(true)
    fireEvent.click(within(dialog).getByLabelText("Close"))
    expect(screen.queryByRole("dialog")).toBeNull()
  })

  it("opens the person that was clicked, not a fixed one", async () => {
    show()
    fireEvent.click(await screen.findByLabelText("Edit access for Pieter van Wyk"))
    await screen.findByRole("dialog")
    await waitFor(() => expect(api.get.mock.calls.some(c => String(c[0]).includes("/users/u3/site-scope"))).toBe(true))
    expect(api.get.mock.calls.some(c => String(c[0]).includes("/users/u2/site-scope"))).toBe(false)
  })

  it("tells the signed-in user they cannot change their own access, because the panel is given who they are", async () => {
    list = [lu({ userId: "u1", name: "Me Myself" })]
    api.get.mockImplementation((url: string) => {
      if (url === "/api/v1/security/site-access") return Promise.resolve(wrap(list))
      if (url.includes("/users/u1/site-scope")) return Promise.resolve(wrap(scopeOf))
      if (url.endsWith("/my-site-scope")) return Promise.resolve(wrap(whole))
      if (url === "/api/v1/security/branches") return Promise.resolve(wrap([{ id: "b1", name: "Johannesburg", active: true }]))
      if (url.startsWith("/api/v1/security/sites")) return Promise.resolve(wrap({ content: [] }))
      return Promise.reject(new Error("unexpected " + url))
    })
    show()
    fireEvent.click(await screen.findByLabelText("Edit access for Me Myself"))
    expect(await screen.findByText(/You cannot change your own site access/)).toBeTruthy()
    expect(((await screen.findByLabelText("Access to Johannesburg")) as HTMLSelectElement).disabled).toBe(true)
  })

  it("refreshes the list after branch access is saved", async () => {
    api.put.mockResolvedValue(wrap(scopeOf))
    show()
    fireEvent.click(await screen.findByLabelText("Edit access for Thandi Nkosi"))
    const dialog = await screen.findByRole("dialog")
    fireEvent.change(await within(dialog).findByLabelText("Access to Johannesburg"), { target: { value: "MANAGER" } })
    const before = api.get.mock.calls.filter(c => c[0] === "/api/v1/security/site-access").length
    fireEvent.click(within(dialog).getByText("Save branch access"))
    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/api/v1/security/users/u2/branch-access", { branches: [{ branchId: "b1", role: "MANAGER" }] }))
    await waitFor(() => expect(api.get.mock.calls.filter(c => c[0] === "/api/v1/security/site-access").length).toBeGreaterThan(before))
  })
})
