import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(), put = vi.fn(), del = vi.fn()
let canAdmin = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), put: (...a: any[]) => put(...a), delete: (...a: any[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => canAdmin }))
import VisitTypeGroupsPanel, { addGroup, move } from "./VisitTypeGroupsPanel"

const e = (c: string, r = false) => ({ groupCode: c, required: r })
const show = () => render(<QueryClientProvider client={new QueryClient()}><VisitTypeGroupsPanel groupCodes={["A", "B", "C"]} /></QueryClientProvider>)
beforeEach(() => { get.mockReset(); put.mockReset(); del.mockReset(); canAdmin = true
  get.mockResolvedValue({ data: { data: { visitType: "CONSULTATION", source: "PLATFORM", groups: [e("A"), e("B", true)] } } })
  put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} }) })
afterEach(() => { cleanup(); vi.restoreAllMocks() })

describe("move / addGroup", () => {
  it("moves within range only and never mutates", () => {
    const l = [e("A"), e("B"), e("C")]
    expect(move(l, 0, 1).map(x => x.groupCode)).toEqual(["B", "A", "C"])
    expect(move(l, 0, -1)).toBe(l); expect(move(l, 2, 1)).toBe(l)
    expect(l.map(x => x.groupCode)).toEqual(["A", "B", "C"])
  })
  it("adds a group once and ignores blanks", () => {
    expect(addGroup([e("A")], "B").map(x => x.groupCode)).toEqual(["A", "B"])
    const l = [e("A")]
    expect(addGroup(l, "A")).toBe(l); expect(addGroup(l, "")).toBe(l)
  })
})

describe("VisitTypeGroupsPanel", () => {
  it("shows the list and says it is the platform default", async () => {
    show()
    expect(await screen.findByText(/Platform default/)).toBeTruthy()
    expect(screen.getByText("A")).toBeTruthy()
  })
  it("saves reordered groups with their required flags, in order", async () => {
    show()
    await screen.findByText(/Platform default/)
    fireEvent.click(screen.getByLabelText("Move B up"))
    fireEvent.change(screen.getByLabelText("Group to add"), { target: { value: "C" } })
    fireEvent.click(screen.getByText("Add"))
    fireEvent.click(screen.getByText("Save list"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/clinic/visit-types/CONSULTATION/groups",
      { groups: [e("B", true), e("A"), e("C")] }))
  })
  it("shows the server's refusal", async () => {
    put.mockRejectedValue({ response: { data: { message: "There is no question group with the code X." } } })
    show()
    await screen.findByText(/Platform default/)
    fireEvent.click(screen.getByLabelText("Remove A"))
    fireEvent.click(screen.getByText("Save list"))
    expect(await screen.findByText(/no question group/)).toBeTruthy()
  })
  it("offers the way back to the default only for a practice's own list, after confirming", async () => {
    get.mockResolvedValue({ data: { data: { visitType: "CONSULTATION", source: "TENANT", groups: [e("A")] } } })
    show()
    fireEvent.click(await screen.findByText("Use platform default"))
    expect(del).not.toHaveBeenCalled()
    fireEvent.click(within(screen.getByRole("dialog", { name: "Use the platform default?" })).getByText("Use default"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/clinic/visit-types/CONSULTATION/groups"))
  })
  it("is read-only without author rights", async () => {
    canAdmin = false
    show()
    await screen.findByText(/Platform default/)
    expect(screen.queryByText("Save list")).toBeNull()
    expect(screen.queryByLabelText("Move A up")).toBeNull()
  })
})
