// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvResourcesPanel from "./TrainProvResourcesPanel"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const site = (id: string, name: string) => ({ id, name, siteType: "MAIN", address: null, province: null, contactPerson: null, phone: null, status: "ACTIVE", retiredReason: null })
const res = (over: object = {}) => ({
  id: "r1", resourceNo: "RES-00001", siteId: "s1", siteName: "Main campus", venueId: null, venueName: null, name: "Welding rig", resourceType: "EQUIPMENT", identifier: "WR-1", quantity: 2,
  shareable: false, notes: null, status: "IN_SERVICE", statusReason: null, statusChangedOn: null, nextStatuses: ["OUT_OF_SERVICE", "RETIRED", "UNDER_REPAIR"], ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let list: unknown[] = []
beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); list = [res()]
  get.mockImplementation((url: string) => Promise.resolve({
    data: { data: /\/sites$/.test(url) ? [site("s1", "Main campus"), site("s2", "North branch")] : /venues$/.test(url) ? [] : /site-resources\/r1$/.test(url)
      ? { resource: res(), history: [{ id: "e1", eventType: "STATUS", fromStatus: "IN_SERVICE", toStatus: "UNDER_REPAIR", fromSiteName: null, toSiteName: null, note: "Hydraulic leak on the arm", occurredOn: "2020-03-01", createdAt: "" }] }
      : list },
  }))
  post.mockResolvedValue({ data: { data: res() } }); put.mockResolvedValue({ data: { data: res() } })
})
afterEach(cleanup)

describe("site resource register", () => {
  it("states that it is a register and not a booking or a standard", async () => {
    wrap(<TrainProvResourcesPanel />)
    expect(await screen.findByText(/does not book resources, and listing one does not show that it meets any standard/)).toBeTruthy()
    expect(await screen.findByText("Welding rig")).toBeTruthy()
    expect(screen.getByText(/RES-00001 · Equipment · Main campus · quantity 2 · WR-1/)).toBeTruthy()
  })

  it("will not add a resource without a site and a type", async () => {
    wrap(<TrainProvResourcesPanel />)
    fireEvent.click(await screen.findByText("Add a resource"))
    fireEvent.click(screen.getByText("Add resource"))
    expect((await screen.findByRole("alert")).textContent).toBe("Choose the site")
    expect(post).not.toHaveBeenCalled()
  })

  it("adds a resource", async () => {
    wrap(<TrainProvResourcesPanel />)
    fireEvent.click(await screen.findByText("Add a resource"))
    await waitFor(() => expect(screen.getAllByRole("option", { name: "North branch" }).length).toBeGreaterThan(1))
    fireEvent.change(screen.getByLabelText("Site"), { target: { value: "s2" } })
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Forklift" } })
    fireEvent.change(screen.getByLabelText("Type"), { target: { value: "VEHICLE" } })
    fireEvent.click(screen.getByText("Add resource"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/site-resources$/), expect.objectContaining({ siteId: "s2", name: "Forklift", resourceType: "VEHICLE", quantity: 1 })))
  })

  it("needs a reason to take a resource out of service", async () => {
    wrap(<TrainProvResourcesPanel />)
    fireEvent.click(await screen.findByText("Mark under repair"))
    fireEvent.click(screen.getByText("Save"))
    expect(await screen.findByText(/reason of at least 10/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText(/Change to under repair: reason/), { target: { value: "Hydraulic leak on the arm" } })
    fireEvent.change(screen.getByLabelText("Changed on"), { target: { value: "2020-03-01" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/site-resources\/r1\/status$/), { toStatus: "UNDER_REPAIR", reason: "Hydraulic leak on the arm", changedOn: "2020-03-01" }))
  })

  it("offers no changes on a retired resource but keeps its history", async () => {
    list = [res({ status: "RETIRED", statusReason: "Scrapped after the safety review", statusChangedOn: "2020-03-01", nextStatuses: [] })]
    wrap(<TrainProvResourcesPanel />)
    expect(await screen.findByText(/Retired since 2020-03-01: Scrapped after the safety review/)).toBeTruthy()
    expect(screen.queryByText("Change details")).toBeNull()
    expect(screen.queryByText("Move to another site")).toBeNull()
    fireEvent.click(screen.getByText("History"))
    expect(await screen.findByText(/In service to Under repair: Hydraulic leak on the arm/)).toBeTruthy()
  })

  it("will not move a resource without a site", async () => {
    wrap(<TrainProvResourcesPanel />)
    fireEvent.click(await screen.findByText("Move to another site"))
    fireEvent.click(screen.getByText("Move"))
    expect((await screen.findByRole("alert")).textContent).toBe("Choose the site it is moving to")
    expect(post).not.toHaveBeenCalled()
  })
})
