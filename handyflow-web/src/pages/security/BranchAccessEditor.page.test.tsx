import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
import BranchAccessEditor from "./BranchAccessEditor"
import type { ScopeView } from "./siteScopeLogic"

const wrap = (data: unknown) => ({ data: { data } })
const whole: ScopeView = { restricted: false, branches: [], clients: [], sites: [], visibleSites: -1 }
const viaJhb: ScopeView = { restricted: true, branches: [{ id: "b1", name: "Johannesburg", role: "VIEWER" }], clients: [], sites: [], visibleSites: 4 }
let branches: unknown[] = []
let branchCall: () => Promise<unknown>

const show = (view: ScopeView, block: string | null = null) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><BranchAccessEditor userId="u2" view={view} block={block} /></QueryClientProvider>)
const select = (name: string) => screen.findByLabelText(`Access to ${name}`) as Promise<HTMLSelectElement>
const saveBtn = () => screen.getByText("Save branch access") as HTMLButtonElement

describe("BranchAccessEditor", () => {
  beforeEach(() => {
    api.get.mockReset(); api.put.mockReset()
    branches = [{ id: "b1", name: "Johannesburg", region: "Gauteng", active: true }, { id: "b2", name: "Cape Town", region: null, active: true }]
    branchCall = () => Promise.resolve(wrap(branches))
    api.get.mockImplementation((url: string) => url === "/api/v1/security/branches" ? branchCall() : Promise.reject(new Error("unexpected " + url)))
    api.put.mockResolvedValue(wrap(viaJhb))
  })
  afterEach(cleanup)

  it("shows nothing editable until the branch list has arrived, so a held branch never looks switched off", async () => {
    let release: (v: unknown) => void = () => {}
    branchCall = () => new Promise(r => { release = r })
    show(viaJhb)
    expect(screen.queryByLabelText("Access to Johannesburg")).toBeNull()
    expect(screen.queryByText(/switched off/)).toBeNull()
    release(wrap(branches))
    expect(((await select("Johannesburg")).value)).toBe("VIEWER")
  })

  it("lists each branch with the user's access and nothing to save yet", async () => {
    show(viaJhb)
    expect((await select("Johannesburg")).value).toBe("VIEWER")
    expect((await select("Cape Town")).value).toBe("NONE")
    expect(screen.getByText("Johannesburg · Gauteng")).toBeTruthy()
    expect(screen.getByText("Access through 1 branch.")).toBeTruthy()
    expect(saveBtn().disabled).toBe(true)
  })

  it("limits a whole-company user to the chosen branches, warns first, and sends branch ids and roles", async () => {
    show(whole)
    fireEvent.change(await select("Cape Town"), { target: { value: "MANAGER" } })
    expect(screen.getByText(/can see every site now/)).toBeTruthy()
    fireEvent.click(saveBtn())
    await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1))
    expect(api.put.mock.calls[0][0]).toBe("/api/v1/security/users/u2/branch-access")
    expect(api.put.mock.calls[0][1]).toEqual({ branches: [{ branchId: "b2", role: "MANAGER" }] })
    expect(await screen.findByText("Branch access saved.")).toBeTruthy()
  })

  it("changes a role and removes a branch in one save", async () => {
    show({ ...viaJhb, branches: [{ id: "b1", name: "Johannesburg", role: "VIEWER" }, { id: "b2", name: "Cape Town", role: "MANAGER" }] })
    fireEvent.change(await select("Johannesburg"), { target: { value: "MANAGER" } })
    fireEvent.change(await select("Cape Town"), { target: { value: "NONE" } })
    fireEvent.click(saveBtn())
    await waitFor(() => expect(api.put).toHaveBeenCalled())
    expect(api.put.mock.calls[0][1]).toEqual({ branches: [{ branchId: "b1", role: "MANAGER" }] })
  })

  it("warns that removing the last branch lifts the limit", async () => {
    show(viaJhb)
    fireEvent.change(await select("Johannesburg"), { target: { value: "NONE" } })
    expect(screen.getByText(/removes the last limit/)).toBeTruthy()
    fireEvent.click(saveBtn())
    await waitFor(() => expect(api.put.mock.calls[0][1]).toEqual({ branches: [] }))
  })

  it("is read only, and says why, for yourself or for a limited administrator", async () => {
    show(viaJhb, "You cannot change your own site access. Ask another administrator.")
    expect((await select("Johannesburg")).disabled).toBe(true)
    expect(saveBtn().disabled).toBe(true)
  })

  it("shows a branch that has been switched off, never sends it, and says saving removes it", async () => {
    show({ ...viaJhb, branches: [{ id: "b9", name: "Old depot", role: "VIEWER" }] })
    const off = await select("Old depot")
    expect(off.disabled).toBe(true)
    expect(screen.getByText(/Old depot \(branch switched off\)/)).toBeTruthy()
    expect(saveBtn().disabled).toBe(false)
    expect(screen.getByText(/Saving removes access through a branch that has been switched off/)).toBeTruthy()
    fireEvent.click(saveBtn())
    await waitFor(() => expect(api.put.mock.calls[0][1]).toEqual({ branches: [] }))
  })

  it("cannot save at all when blocked, even though a switched-off branch would otherwise make saving possible", async () => {
    show({ ...viaJhb, branches: [{ id: "b9", name: "Old depot", role: "VIEWER" }] }, "Only someone with access to the whole company can change site access.")
    await select("Old depot")
    expect(saveBtn().disabled).toBe(true)
    fireEvent.click(saveBtn())
    expect(api.put).not.toHaveBeenCalled()
  })

  it("keeps the choices and shows the server's words when saving is refused", async () => {
    api.put.mockRejectedValue({ response: { status: 409, data: { errorCode: "SCOPE_SELF", message: "You cannot change your own site access. Ask another administrator." } } })
    show(whole)
    fireEvent.change(await select("Johannesburg"), { target: { value: "VIEWER" } })
    fireEvent.click(saveBtn())
    expect(await screen.findByText("You cannot change your own site access. Ask another administrator.")).toBeTruthy()
    expect((await select("Johannesburg")).value).toBe("VIEWER")
    expect(screen.queryByText("Branch access saved.")).toBeNull()
  })

  it("shows what the user holds, read only, when the branch list cannot be loaded", async () => {
    branchCall = () => Promise.reject({ response: { status: 403, data: { message: "Not allowed." } } })
    show(viaJhb)
    expect(await screen.findByText("Not allowed.")).toBeTruthy()
    expect(screen.getByText("Johannesburg · Can view")).toBeTruthy()
    expect(screen.queryByLabelText("Access to Johannesburg")).toBeNull()
    expect(saveBtn().disabled).toBe(true)
  })

  it("says so when the company has no branches yet", async () => {
    branches = []
    show(whole)
    expect(await screen.findByText(/No branches yet/)).toBeTruthy()
  })
})
