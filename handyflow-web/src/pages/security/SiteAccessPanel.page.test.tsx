import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
import SiteAccessPanel from "./SiteAccessPanel"
import MySiteScopeBanner from "./MySiteScopeBanner"

const wrap = (data: unknown) => ({ data: { data } })
const whole = { restricted: false, branches: [], clients: [], sites: [], visibleSites: -1 }
const ltd = { restricted: true, branches: [], clients: [{ id: "c1", name: "Acme Retail" }], sites: [{ id: "s1", name: "Mall" }], visibleSites: 4 }
const sites = [{ id: "s1", name: "Mall" }, { id: "s2", name: "Depot" }]

let target: unknown = whole, mine: unknown = whole
function route() {
  api.get.mockImplementation((url: string) => {
    if (url.includes("/users/u2/site-scope")) return Promise.resolve(wrap(target))
    if (url.endsWith("/my-site-scope")) return Promise.resolve(wrap(mine))
    if (url === "/api/v1/security/branches") return Promise.resolve(wrap([{ id: "b1", name: "Johannesburg", region: "Gauteng", active: true }, { id: "b2", name: "Cape Town", region: null, active: true }]))
    if (url === "/api/v1/security/regions") return Promise.resolve(wrap([]))
    if (url.startsWith("/api/v1/security/sites")) return Promise.resolve(wrap({ content: sites }))
    if (url.startsWith("/api/v1/crm/customers")) return Promise.resolve(wrap({ content: [{ id: "c9", name: "Globex" }, { id: "c1", name: "Acme Retail" }] }))
    return Promise.reject(new Error("unexpected " + url))
  })
  api.put.mockResolvedValue(wrap(ltd))
}
const show = (actorId = "u1") => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SiteAccessPanel userId="u2" userName="Thandi Nkosi" actorId={actorId} onClose={() => {}} /></QueryClientProvider>)

describe("SiteAccessPanel", () => {
  beforeEach(() => { api.get.mockReset(); api.put.mockReset(); target = whole; mine = whole; route() })
  afterEach(cleanup)

  it("shows a whole-company user, with nothing ticked and nothing to save yet", async () => {
    show()
    expect(await screen.findByText("Whole company: every site, current and future.")).toBeTruthy()
    expect((await screen.findByLabelText("Mall") as HTMLInputElement).checked).toBe(false)
    expect((screen.getByText("Save site access") as HTMLButtonElement).disabled).toBe(true)
  })

  it("limits a user to the ticked sites, warns first, and sends only the ids", async () => {
    show()
    fireEvent.click(await screen.findByLabelText("Depot"))
    expect(screen.getByText(/can see every site now/)).toBeTruthy()
    fireEvent.click(screen.getByText("Save site access"))
    await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1))
    expect(api.put.mock.calls[0][0]).toBe("/api/v1/security/users/u2/site-scope")
    expect(api.put.mock.calls[0][1]).toEqual({ clientIds: [], siteIds: ["s2"] })
    expect(await screen.findByText(/^Saved\./)).toBeTruthy()
  })

  it("refreshes the list of limited users after site access is saved", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(client, "invalidateQueries")
    render(<QueryClientProvider client={client}><SiteAccessPanel userId="u2" userName="Thandi Nkosi" actorId="u1" onClose={() => {}} /></QueryClientProvider>)
    fireEvent.click(await screen.findByLabelText("Depot"))
    fireEvent.click(screen.getByText("Save site access"))
    await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(spy.mock.calls.some(c => JSON.stringify((c[0] as { queryKey?: unknown })?.queryKey) === JSON.stringify(["site-access-overview"]))).toBe(true))
  })

  it("shows an existing limit, and removing the last site and client warns that it lifts the limit", async () => {
    target = ltd
    show()
    expect(await screen.findByText("Limited to 4 sites (from 1 client, 1 named site).")).toBeTruthy()
    expect((await screen.findByLabelText("Mall") as HTMLInputElement).checked).toBe(true)
    fireEvent.click(screen.getByLabelText("Mall"))
    fireEvent.click(screen.getByLabelText("Remove Acme Retail"))
    expect(screen.getByText(/will see the whole company/)).toBeTruthy()
    fireEvent.click(screen.getByText("Save site access"))
    await waitFor(() => expect(api.put.mock.calls[0][1]).toEqual({ clientIds: [], siteIds: [] }))
  })

  it("finds a client by name and adds it, leaving out clients already chosen", async () => {
    show()
    await screen.findByLabelText("Mall")
    fireEvent.change(screen.getByLabelText("Search clients"), { target: { value: "gl" } })
    fireEvent.click(await screen.findByText("Add Globex"))
    expect(screen.getByText("Globex")).toBeTruthy()
    fireEvent.click(screen.getByText("Save site access"))
    await waitFor(() => expect(api.put.mock.calls[0][1]).toEqual({ clientIds: ["c9"], siteIds: [] }))
  })

  it("does not offer a client that is already chosen", async () => {
    target = ltd
    show()
    await screen.findByLabelText("Mall")
    fireEvent.change(screen.getByLabelText("Search clients"), { target: { value: "ac" } })
    expect(await screen.findByText("Add Globex")).toBeTruthy()
    expect(screen.queryByText("Add Acme Retail")).toBeNull()
  })

  it("does not search clients for a single character", async () => {
    show()
    await screen.findByLabelText("Mall")
    fireEvent.change(screen.getByLabelText("Search clients"), { target: { value: "g" } })
    await new Promise(r => setTimeout(r, 400))
    expect(api.get.mock.calls.some(c => String(c[0]).startsWith("/api/v1/crm/customers"))).toBe(false)
  })

  it("will not let you change your own access", async () => {
    show("u2")
    expect(await screen.findByText(/cannot change your own site access/)).toBeTruthy()
    expect((screen.getByLabelText("Mall") as HTMLInputElement).disabled).toBe(true)
    expect((screen.getByLabelText("Search clients") as HTMLInputElement).disabled).toBe(true)
    expect((screen.getByText("Save site access") as HTMLButtonElement).disabled).toBe(true)
  })

  it("will not let a limited administrator change anyone's access", async () => {
    mine = ltd
    show()
    expect(await screen.findByText(/Only someone with access to the whole company/)).toBeTruthy()
    expect((screen.getByLabelText("Depot") as HTMLInputElement).disabled).toBe(true)
  })

  it("shows the branches the user has, as choices that can be changed, saved apart from the sites", async () => {
    target = { ...ltd, branches: [{ id: "b1", name: "Johannesburg", role: "MANAGER" }] }
    show()
    expect(((await screen.findByLabelText("Access to Johannesburg")) as HTMLSelectElement).value).toBe("MANAGER")
    expect((screen.getByLabelText("Access to Cape Town") as HTMLSelectElement).value).toBe("NONE")
    expect((screen.getByText("Save site access") as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByText("Save branch access") as HTMLButtonElement).disabled).toBe(true)
  })

  it("keeps the person's choices and shows the server's message when saving is refused", async () => {
    api.put.mockRejectedValue({ response: { status: 403, data: { errorCode: "SITE_SCOPE_LIMITED", message: "This needs access to the whole company. Your access is limited to some sites." } } })
    show()
    fireEvent.click(await screen.findByLabelText("Depot"))
    fireEvent.click(screen.getByText("Save site access"))
    expect(await screen.findByText(/This needs access to the whole company/)).toBeTruthy()
    expect((screen.getByLabelText("Depot") as HTMLInputElement).checked).toBe(true)
    expect(screen.queryByText(/^Saved\./)).toBeNull()
  })

  it("shows an error, not an empty form, when the access cannot be loaded", async () => {
    api.get.mockImplementation((url: string) => url.includes("site-scope") ? Promise.reject({ response: { status: 403, data: { message: "Your plan does not include this module." } } }) : Promise.resolve(wrap({ content: sites })))
    show()
    expect(await screen.findByText("Your plan does not include this module.")).toBeTruthy()
    expect(screen.queryByText("Save site access")).toBeNull()
  })
})

describe("MySiteScopeBanner", () => {
  beforeEach(() => { api.get.mockReset() })
  afterEach(cleanup)
  const banner = () => render(<QueryClientProvider client={new QueryClient()}><MySiteScopeBanner /></QueryClientProvider>)

  it("tells a limited user", async () => {
    api.get.mockResolvedValue(wrap(ltd)); banner()
    expect(await screen.findByRole("status")).toHaveProperty("textContent", "Your access is limited to 4 sites. Company-wide pages and figures are not shown to you.")
  })
  it("shows nothing to an unrestricted user", async () => {
    api.get.mockResolvedValue(wrap(whole)); const { container } = banner()
    await waitFor(() => expect(api.get).toHaveBeenCalled())
    await new Promise(r => setTimeout(r, 20))
    expect(container.innerHTML).toBe("")
  })
  it("shows nothing when the lookup fails (for example a user without SECURITY_READ)", async () => {
    api.get.mockRejectedValue({ response: { status: 403 } }); const { container } = banner()
    await waitFor(() => expect(api.get).toHaveBeenCalled())
    await new Promise(r => setTimeout(r, 20))
    expect(container.innerHTML).toBe("")
  })
})
