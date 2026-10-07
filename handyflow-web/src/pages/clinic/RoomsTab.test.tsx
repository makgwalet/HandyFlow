import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn()
let canAdmin = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => canAdmin }))
import RoomsTab, { roomNameProblem } from "./RoomsTab"

const ROOMS = [{ id: "r1", name: "Room 1", active: true }, { id: "r2", name: "Old room", active: false }]
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><RoomsTab /></QueryClientProvider>)

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); canAdmin = true
  get.mockResolvedValue({ data: { data: ROOMS } }); post.mockResolvedValue({ data: {} }); put.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("roomNameProblem", () => {
  it("needs a name of at most 60 characters", () => {
    expect(roomNameProblem("   ")).toMatch(/needs a name/)
    expect(roomNameProblem("x".repeat(61))).toMatch(/too long/)
    expect(roomNameProblem("x".repeat(60))).toBeNull()
    expect(roomNameProblem(" Room 3 ")).toBeNull()
  })
})

describe("RoomsTab", () => {
  it("lists rooms, including switched-off ones, asking the server for them", async () => {
    show()
    expect(await screen.findByText("Room 1")).toBeTruthy()
    expect(screen.getByText("Switched off")).toBeTruthy()
    expect(get.mock.calls[0][1]).toEqual({ params: { includeInactive: true } })
  })

  it("adds a room", async () => {
    show(); await screen.findByText("Room 1")
    fireEvent.change(screen.getByLabelText("Room name"), { target: { value: " Room 3 " } })
    fireEvent.click(screen.getByText("Add room"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/rooms", { name: "Room 3" }))
  })

  it("does not send an empty name", async () => {
    show(); await screen.findByText("Room 1")
    fireEvent.click(screen.getByText("Add room"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/needs a name/)
    expect(post).not.toHaveBeenCalled()
  })

  it("renames a room", async () => {
    show(); await screen.findByText("Room 1")
    fireEvent.click(screen.getAllByText("Rename")[0])
    fireEvent.change(screen.getByLabelText("Rename Room 1"), { target: { value: "Procedure room" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/clinic/rooms/r1", { name: "Procedure room" }))
  })

  it("switches a room off and another back on", async () => {
    show(); await screen.findByText("Room 1")
    fireEvent.click(screen.getByText("Switch off"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/clinic/rooms/r1", { active: false }))
    fireEvent.click(screen.getByText("Switch on"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/clinic/rooms/r2", { active: true }))
  })

  it("shows the server's reason, such as a duplicate name", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: 'There is already a room called "Room 1"' } } })
    show(); await screen.findByText("Room 1")
    fireEvent.change(screen.getByLabelText("Room name"), { target: { value: "room 1" } })
    fireEvent.click(screen.getByText("Add room"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/already a room/)
  })

  it("is read-only without admin permission", async () => {
    canAdmin = false
    show(); await screen.findByText("Room 1")
    expect(screen.queryByText("Add room")).toBeNull()
    expect(screen.queryByText("Rename")).toBeNull()
    expect(screen.queryByText("Switch off")).toBeNull()
  })
})
