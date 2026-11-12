import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import AccessLimitsTab from "./AccessLimitsTab"
import { describeScope, toggle } from "./scopeOps"

const get = vi.fn(); const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
function routes(scopes: unknown[], usersFail = false) {
  get.mockImplementation((url: string) => {
    if (url.includes("/fuel/scopes")) return ok(scopes)
    if (url.includes("/fuel/depots")) return ok([{ id: "d1", name: "Main depot" }, { id: "d2", name: "North depot" }])
    if (url.includes("identity/users")) return usersFail ? Promise.reject(new Error("403")) : ok([{ id: "u1", firstName: "Thabo", lastName: "M", email: "t@x.co" }])
    return ok([])
  })
}
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><AccessLimitsTab /></QueryClientProvider>)
}

describe("AccessLimitsTab", () => {
  beforeEach(() => { get.mockReset(); put.mockReset(); perms = ["FUEL_CONFIGURATION_MANAGE"]; put.mockResolvedValue({ data: {} }) })
  afterEach(cleanup)

  it("says nobody is limited yet", async () => {
    routes([])
    mount()
    expect(await screen.findByText("Nobody is limited yet.")).toBeTruthy()
  })

  it("lists limited people with their divisions and depot names", async () => {
    routes([{ userId: "u1", divisions: ["DISTRIBUTION"], depotIds: ["d1"] }])
    mount()
    expect(await screen.findByText(/Distribution · Main depot/)).toBeTruthy()
  })

  it("saves a whole new set for the chosen person", async () => {
    routes([])
    mount()
    await screen.findByText("Nobody is limited yet.")
    await waitFor(() => expect(screen.getByText(/Thabo M/)).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Person"), { target: { value: "u1" } })
    fireEvent.click(await screen.findByLabelText("Distribution"))
    fireEvent.click(screen.getByLabelText("Main depot"))
    fireEvent.click(screen.getByText("Save limits"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/scopes/users/u1", { divisions: ["DISTRIBUTION"], depotIds: ["d1"] }))
  })

  it("clearing every tick saves an empty set, which removes the limits", async () => {
    routes([{ userId: "u1", divisions: ["DISTRIBUTION"], depotIds: [] }])
    mount()
    fireEvent.click(await screen.findByLabelText(/Edit limits for/))
    fireEvent.click(await screen.findByLabelText("Distribution"))
    fireEvent.click(screen.getByText("Save limits"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/scopes/users/u1", { divisions: [], depotIds: [] }))
  })

  it("still works when the list of people cannot be read, using the ids that already have limits", async () => {
    routes([{ userId: "abcdef12-0000", divisions: ["TRUCK_STOP"], depotIds: [] }], true)
    mount()
    expect((await screen.findAllByText("User abcdef12")).length).toBeGreaterThan(0)
    await waitFor(() => expect(screen.getByText(/could not be loaded/)).toBeTruthy())
  })

  it("shows a read-only list without the manage permission", async () => {
    perms = []
    routes([{ userId: "u1", divisions: ["DISTRIBUTION"], depotIds: [] }])
    mount()
    await screen.findByText(/Distribution/)
    expect(screen.queryByText("Save limits")).toBeNull()
    expect(screen.queryByLabelText("Person")).toBeNull()
  })

  it("shows the server's message when saving is refused", async () => {
    routes([])
    put.mockRejectedValue({ response: { data: { message: "A depot in the list was not found." } } })
    mount()
    await waitFor(() => expect(screen.getByText(/Thabo M/)).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Person"), { target: { value: "u1" } })
    fireEvent.click(await screen.findByText("Save limits"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/not found/)
  })
})

describe("scopeOps", () => {
  it("toggle adds and removes", () => { expect(toggle(["a"], "b")).toEqual(["a", "b"]); expect(toggle(["a", "b"], "a")).toEqual(["b"]) })
  it("describeScope covers none, divisions, depots and unknown depots", () => {
    expect(describeScope(undefined, {})).toBe("No limits")
    expect(describeScope({ divisions: [], depotIds: [] }, {})).toBe("No limits")
    expect(describeScope({ divisions: ["RETAIL_FORECOURT"], depotIds: ["x"] }, {})).toBe("Retail & Forecourt · Unknown depot")
  })
})
