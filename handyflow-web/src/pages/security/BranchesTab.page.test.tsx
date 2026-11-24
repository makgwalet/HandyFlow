// @vitest-environment jsdom
import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
import BranchesTab from "./BranchesTab"

const wrap = (data: unknown) => ({ data: { data } })
const branch = (id: string, name: string, region: string | null) => ({ id, tenantId: "t", name, region, description: null, active: true, createdAt: "", updatedAt: "" })
const branches = [branch("b1", "Durban", "KZN"), branch("b2", "Sandton", null)]
const regions = [{ id: "r1", name: "KZN", branchIds: ["b1"], users: 2 }, { id: "r2", name: "Gauteng", branchIds: [], users: 0 }]
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><BranchesTab /></QueryClientProvider>)

describe("BranchesTab regions", () => {
  beforeEach(() => {
    for (const f of Object.values(api)) f.mockReset()
    api.get.mockImplementation((u: string) => u === "/api/v1/security/branches" ? Promise.resolve(wrap(branches)) : u === "/api/v1/security/regions" ? Promise.resolve(wrap(regions)) : Promise.reject(new Error("unexpected " + u)))
    api.post.mockResolvedValue(wrap(branch("b9", "Cape Town", null)))
    api.put.mockResolvedValue(wrap(regions))
  })
  afterEach(cleanup)

  it("offers the company's regions, not a fixed list", async () => {
    show()
    fireEvent.click(await screen.findByText("New Branch"))
    const sel = screen.getByLabelText("Region") as HTMLSelectElement
    expect(Array.from(sel.options).map(o => o.textContent)).toEqual(["— No region —", "KZN", "Gauteng"])
  })

  it("creates the branch, then puts it in the chosen region", async () => {
    show()
    fireEvent.click(await screen.findByText("New Branch"))
    fireEvent.change(screen.getByPlaceholderText(/e\.g\. Gauteng Region/), { target: { value: "Cape Town" } })
    fireEvent.change(await screen.findByLabelText("Region"), { target: { value: "r2" } })
    fireEvent.click(screen.getByText("Create Branch"))
    await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1))
    expect(api.post.mock.calls[0][1]).toEqual({ name: "Cape Town", region: "Gauteng", description: "" })
    expect(api.put.mock.calls[0]).toEqual(["/api/v1/security/branches/b9/region", { regionId: "r2" }])
  })

  it("does not call the region endpoint for a branch with no region", async () => {
    show()
    fireEvent.click(await screen.findByText("New Branch"))
    fireEvent.change(screen.getByPlaceholderText(/e\.g\. Gauteng Region/), { target: { value: "Cape Town" } })
    fireEvent.click(screen.getByText("Create Branch"))
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1))
    expect(api.put).not.toHaveBeenCalled()
  })

  it("starts an edit on the branch's current region and only moves it when it changed", async () => {
    show()
    fireEvent.click((await screen.findAllByText("Edit"))[0])
    expect((screen.getByLabelText("Region") as HTMLSelectElement).value).toBe("r1")
    fireEvent.click(screen.getByText("Save Changes"))
    await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1))
    expect(api.put.mock.calls[0][0]).toBe("/api/v1/security/branches/b1")
    expect(api.put.mock.calls[0][1]).toEqual({ name: "Durban", region: "KZN", description: "" })
  })

  it("moves a branch to another region, warning who is affected first", async () => {
    show()
    fireEvent.click((await screen.findAllByText("Edit"))[0])
    fireEvent.change(screen.getByLabelText("Region"), { target: { value: "r2" } })
    expect(screen.getByText("2 users limited to KZN will stop seeing them.")).toBeTruthy()
    fireEvent.click(screen.getByText("Save Changes"))
    await waitFor(() => expect(api.put).toHaveBeenCalledTimes(2))
    expect(api.put.mock.calls[1]).toEqual(["/api/v1/security/branches/b1/region", { regionId: "r2" }])
  })

  it("takes a branch out of its region with a null region", async () => {
    show()
    fireEvent.click((await screen.findAllByText("Edit"))[0])
    fireEvent.change(screen.getByLabelText("Region"), { target: { value: "" } })
    fireEvent.click(screen.getByText("Save Changes"))
    await waitFor(() => expect(api.put).toHaveBeenCalledTimes(2))
    expect(api.put.mock.calls[0][1]).toEqual({ name: "Durban", region: "", description: "" })
    expect(api.put.mock.calls[1]).toEqual(["/api/v1/security/branches/b1/region", { regionId: null }])
  })

  it("tells a user with no regions where to add one", async () => {
    api.get.mockImplementation((u: string) => u === "/api/v1/security/branches" ? Promise.resolve(wrap(branches)) : Promise.resolve(wrap([])))
    show()
    fireEvent.click(await screen.findByText("New Branch"))
    expect(await screen.findByText(/No regions yet\. Add one in the Regions box above\./)).toBeTruthy()
  })

  it("shows the server's reason when the branch cannot be saved", async () => {
    api.post.mockRejectedValue({ response: { status: 403, data: { message: "This needs access to the whole company. Your access is limited to some sites.", errorCode: "SITE_SCOPE_LIMITED" } } })
    show()
    fireEvent.click(await screen.findByText("New Branch"))
    fireEvent.change(screen.getByPlaceholderText(/e\.g\. Gauteng Region/), { target: { value: "X" } })
    fireEvent.click(screen.getByText("Create Branch"))
    expect(await screen.findByText(/needs access to the whole company/)).toBeTruthy()
  })
})
