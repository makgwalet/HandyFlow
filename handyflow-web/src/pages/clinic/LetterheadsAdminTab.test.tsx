import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn(); const del = vi.fn()
let can = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a), delete: (...a: any[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => can }))
import LetterheadsAdminTab from "./LetterheadsAdminTab"

const MAIN = { id: "l1", name: "Main", branchName: null, practiceName: "Sample Practice", lines: ["1 Road", "Tel 000"], isDefault: true, active: true, hasLogo: false }
const NORTH = { id: "l2", name: "North", branchName: "Northside", practiceName: "Sample Practice", lines: [], isDefault: false, active: true, hasLogo: true }
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><LetterheadsAdminTab /></QueryClientProvider>)

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); can = true
  get.mockResolvedValue({ data: { data: [MAIN, NORTH] } }); post.mockResolvedValue({ data: {} }); put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("LetterheadsAdminTab", () => {
  it("lists letterheads with what each is for and whether it has a logo", async () => {
    show()
    expect(await screen.findByText("Main")).toBeTruthy()
    expect(screen.getByText("Practice-wide, used when none is chosen")).toBeTruthy()
    expect(screen.getByText("Branch: Northside")).toBeTruthy()
    expect(screen.getByText("Logo set")).toBeTruthy()
    expect(screen.getByText("1 Road")).toBeTruthy()
  })
  it("adds a letterhead, sending the lines as a list", async () => {
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Add a letterhead" }))
    fireEvent.change(screen.getByLabelText(/Name \(what people choose from\)/), { target: { value: "Winter" } })
    fireEvent.change(screen.getByLabelText(/Practice name printed/), { target: { value: "Sample Practice" } })
    fireEvent.change(screen.getByLabelText(/Address, phone/), { target: { value: "A\n\nB" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/letterheads",
      { name: "Winter", branchName: null, practiceName: "Sample Practice", lines: ["A", "B"], makeDefault: false }))
  })
  it("will not save until a name and practice name are given, and says why", async () => {
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Add a letterhead" }))
    expect((screen.getByRole("button", { name: "Save" }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText("Give the letterhead a name")).toBeTruthy()
  })
  it("never makes a branch letterhead the default", async () => {
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Add a letterhead" }))
    fireEvent.change(screen.getByLabelText(/Name \(what people choose from\)/), { target: { value: "East" } })
    fireEvent.change(screen.getByLabelText(/Branch/), { target: { value: "Eastside" } })
    fireEvent.change(screen.getByLabelText(/Practice name printed/), { target: { value: "Sample Practice" } })
    expect(screen.queryByLabelText(/Use as the practice-wide default/)).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(post.mock.calls[0][1].makeDefault).toBe(false))
    expect(post.mock.calls[0][1].branchName).toBe("Eastside")
  })
  it("changes an existing letterhead", async () => {
    show()
    fireEvent.click((await screen.findAllByRole("button", { name: "Edit" }))[0])
    fireEvent.change(screen.getByLabelText(/Practice name printed/), { target: { value: "New Name" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/clinic/letterheads/l1", expect.objectContaining({ practiceName: "New Name", makeDefault: true })))
  })
  it("removes a logo, and a letterhead only after asking", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false)
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Remove logo" }))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/clinic/letterheads/l2/logo"))
    fireEvent.click(screen.getAllByRole("button", { name: "Remove" })[0])
    expect(del).toHaveBeenCalledTimes(1)
    confirm.mockReturnValue(true)
    fireEvent.click(screen.getAllByRole("button", { name: "Remove" })[0])
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/clinic/letterheads/l1"))
    confirm.mockRestore()
  })
  it("refuses a logo of the wrong type before sending it", async () => {
    show()
    fireEvent.click((await screen.findAllByRole("button", { name: /logo/i }))[0])
    const input = screen.getByLabelText("Logo file") as HTMLInputElement
    fireEvent.change(input, { target: { files: [new File(["x"], "a.gif", { type: "image/gif" })] } })
    expect((await screen.findByRole("alert")).textContent).toMatch(/PNG or JPEG/)
    expect(put).not.toHaveBeenCalled()
  })
  it("shows a read-only list to someone who cannot manage letterheads", async () => {
    can = false; show()
    expect(await screen.findByText("Main")).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Add a letterhead" })).toBeNull()
    expect(screen.queryByRole("button", { name: "Edit" })).toBeNull()
  })
  it("says so when there are none", async () => {
    get.mockResolvedValue({ data: { data: [] } }); show()
    expect(await screen.findByText(/No letterhead yet/)).toBeTruthy()
  })
})
