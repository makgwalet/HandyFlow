import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
import RegionAccessEditor from "./RegionAccessEditor"
import type { ScopeView } from "./siteScopeLogic"

const wrap = (data: unknown) => ({ data: { data } })
const regions = [{ id: "r1", name: "KwaZulu-Natal", branchIds: ["b1", "b2"], users: 1 }, { id: "r2", name: "Gauteng", branchIds: [], users: 0 }]
const whole: ScopeView = { restricted: false, branches: [], clients: [], sites: [], visibleSites: -1, regions: [] }
const inKzn: ScopeView = { restricted: true, branches: [], clients: [], sites: [], visibleSites: 5, regions: [{ id: "r1", name: "KwaZulu-Natal" }] }
let list: () => Promise<unknown> = () => Promise.resolve(wrap(regions))

const show = (view: ScopeView, block: string | null = null) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><RegionAccessEditor userId="u2" view={view} block={block} /></QueryClientProvider>)

describe("RegionAccessEditor", () => {
  beforeEach(() => { api.get.mockReset(); api.put.mockReset(); list = () => Promise.resolve(wrap(regions)); api.get.mockImplementation((u: string) => u === "/api/v1/security/regions" ? list() : Promise.reject(new Error("unexpected " + u))); api.put.mockResolvedValue(wrap(inKzn)) })
  afterEach(cleanup)

  it("lists the regions with their branch counts, nothing ticked, nothing to save", async () => {
    show(whole)
    expect((await screen.findByLabelText(/KwaZulu-Natal/) as HTMLInputElement).checked).toBe(false)
    expect(screen.getByText("2 branches")).toBeTruthy(); expect(screen.getByText("0 branches")).toBeTruthy()
    expect((screen.getByText("Save region access") as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText("No region access.")).toBeTruthy()
  })

  it("warns before limiting a whole-company user and sends only the ids", async () => {
    show(whole)
    fireEvent.click(await screen.findByLabelText(/KwaZulu-Natal/))
    expect(screen.getByText(/can see every site now/)).toBeTruthy()
    fireEvent.click(screen.getByText("Save region access"))
    await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1))
    expect(api.put.mock.calls[0][0]).toBe("/api/v1/security/users/u2/region-access")
    expect(api.put.mock.calls[0][1]).toEqual({ regionIds: ["r1"] })
    expect(await screen.findByText("Region access saved.")).toBeTruthy()
  })

  it("shows the region the user holds ticked and warns about the last limit", async () => {
    show(inKzn)
    const box = await screen.findByLabelText(/KwaZulu-Natal/) as HTMLInputElement
    expect(box.checked).toBe(true)
    fireEvent.click(box)
    expect(screen.getByText("Saving removes the last limit: this user will see the whole company.")).toBeTruthy()
    fireEvent.click(screen.getByText("Save region access"))
    await waitFor(() => expect(api.put.mock.calls[0][1]).toEqual({ regionIds: [] }))
  })

  it("says plainly when the only region chosen has no branches", async () => {
    show(whole)
    fireEvent.click(await screen.findByLabelText(/Gauteng/))
    expect(screen.getByText(/This region holds no branches yet, so this user would see no sites/)).toBeTruthy()
  })

  it("is read-only when blocked, and still shows what is held", async () => {
    show(inKzn, "You cannot change your own site access.")
    const box = await screen.findByLabelText(/KwaZulu-Natal/) as HTMLInputElement
    expect(box.disabled).toBe(true)
    expect((screen.getByText("Save region access") as HTMLButtonElement).disabled).toBe(true)
  })

  it("shows nothing at all when the company has no regions and the user holds none", async () => {
    list = () => Promise.resolve(wrap([]))
    const { container } = show(whole)
    await waitFor(() => expect(api.get).toHaveBeenCalled())
    await waitFor(() => expect(container.querySelector("[aria-label='Region access']")).toBeNull())
  })

  it("still shows a region the user holds when the list is empty", async () => {
    list = () => Promise.resolve(wrap([]))
    show(inKzn)
    expect(await screen.findByLabelText(/KwaZulu-Natal/)).toBeTruthy()
  })

  it("shows the server's reason when saving is refused", async () => {
    api.put.mockRejectedValue({ response: { status: 409, data: { message: "You cannot change your own site access. Ask another administrator.", errorCode: "SCOPE_SELF" } } })
    show(whole)
    fireEvent.click(await screen.findByLabelText(/KwaZulu-Natal/))
    fireEvent.click(screen.getByText("Save region access"))
    expect(await screen.findByText("You cannot change your own site access. Ask another administrator.")).toBeTruthy()
    expect(screen.queryByText("Region access saved.")).toBeNull()
  })

  it("shows an error when the regions cannot be loaded, and not an empty list", async () => {
    list = () => Promise.reject(new Error("boom"))
    show(whole)
    expect(await screen.findByRole("alert")).toBeTruthy()
    expect(screen.queryByText("Save region access")).not.toBeNull()
  })
})
