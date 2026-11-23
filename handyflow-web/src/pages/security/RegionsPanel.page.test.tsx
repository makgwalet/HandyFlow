import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
import RegionsPanel from "./RegionsPanel"

const wrap = (data: unknown) => ({ data: { data } })
const base = [{ id: "r1", name: "KZN", description: null, branchIds: ["b1"], users: 2 }, { id: "r2", name: "Gauteng", description: "Inland", branchIds: [], users: 0 }]
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><RegionsPanel /></QueryClientProvider>)

describe("RegionsPanel", () => {
  beforeEach(() => {
    for (const f of Object.values(api)) f.mockReset()
    api.get.mockResolvedValue(wrap(base))
  })
  afterEach(cleanup)

  it("lists regions with branches and users", async () => {
    show()
    expect(await screen.findByText("KZN")).toBeTruthy()
    expect(screen.getByText("1 branch · 2 users limited to it")).toBeTruthy()
    expect(screen.getByText("0 branches · no users limited to it")).toBeTruthy()
  })

  it("adds a region and shows the fresh list the server returns", async () => {
    const after = [...base, { id: "r3", name: "Free State", branchIds: [], users: 0 }]
    api.post.mockImplementation(() => { api.get.mockResolvedValue(wrap(after)); return Promise.resolve(wrap(after)) })
    show()
    fireEvent.change(await screen.findByLabelText("New region name"), { target: { value: "  Free   State " } })
    fireEvent.click(screen.getByText("Add region"))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/security/regions", { name: "  Free   State " }))
    expect(await screen.findByText("Free State")).toBeTruthy()
  })

  it("refuses a duplicate name before asking the server", async () => {
    show()
    fireEvent.change(await screen.findByLabelText("New region name"), { target: { value: "gauteng" } })
    expect(screen.getByRole("alert").textContent).toBe("A region with that name already exists.")
    expect((screen.getByText("Add region") as HTMLButtonElement).disabled).toBe(true)
    expect(api.post).not.toHaveBeenCalled()
  })

  it("renames, keeping the description", async () => {
    api.put.mockResolvedValue(wrap([{ ...base[0], name: "KwaZulu-Natal" }, base[1]]))
    show()
    fireEvent.click((await screen.findAllByText("Rename"))[1])
    fireEvent.change(screen.getByLabelText("New name for Gauteng"), { target: { value: "Gauteng North" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/api/v1/security/regions/r2", { name: "Gauteng North", description: "Inland" }))
  })

  it("lets a region keep its own name while renaming", async () => {
    show()
    fireEvent.click((await screen.findAllByText("Rename"))[0])
    fireEvent.change(screen.getByLabelText("New name for KZN"), { target: { value: "kzn" } })
    expect((screen.getByText("Save") as HTMLButtonElement).disabled).toBe(false)
  })

  it("cannot delete a region users are limited to, and says why", async () => {
    show()
    const del = await screen.findByLabelText("Delete KZN") as HTMLButtonElement
    expect(del.disabled).toBe(true)
    expect(del.title).toBe("2 users are limited to this region. Take it off them first.")
  })

  it("deletes only after a second click", async () => {
    api.delete.mockImplementation(() => { api.get.mockResolvedValue(wrap([base[0]])); return Promise.resolve(wrap([base[0]])) })
    show()
    fireEvent.click(await screen.findByLabelText("Delete Gauteng"))
    expect(api.delete).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Confirm delete Gauteng"))
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith("/api/v1/security/regions/r2"))
    await waitFor(() => expect(screen.queryByText("Gauteng")).toBeNull())
  })

  it("backs out of a delete", async () => {
    show()
    fireEvent.click(await screen.findByLabelText("Delete Gauteng"))
    fireEvent.click(screen.getByText("Keep"))
    expect(api.delete).not.toHaveBeenCalled()
    expect(screen.getByLabelText("Delete Gauteng")).toBeTruthy()
  })

  it("shows the server's reason when a change is refused", async () => {
    api.post.mockRejectedValue({ response: { status: 403, data: { message: "This needs access to the whole company. Your access is limited to some sites.", errorCode: "SITE_SCOPE_LIMITED" } } })
    show()
    fireEvent.change(await screen.findByLabelText("New region name"), { target: { value: "Free State" } })
    fireEvent.click(screen.getByText("Add region"))
    expect(await screen.findByText("This needs access to the whole company. Your access is limited to some sites.")).toBeTruthy()
  })

  it("shows an empty state", async () => {
    api.get.mockResolvedValue(wrap([]))
    show()
    expect(await screen.findByText("No regions yet.")).toBeTruthy()
  })
})
