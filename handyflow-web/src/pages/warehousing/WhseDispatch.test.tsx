import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseDispatchTab from "./WhseDispatchTab"

const get = vi.fn()
const post = vi.fn()
const del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a) } }))

const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WhseDispatchTab /></QueryClientProvider>)

const manifest = (over: Record<string, unknown> = {}) => ({
  id: "m1", manifestNumber: "MAN00001", carrier: "DHL", vehicleRegistration: "CA 123", driverName: "Sipho", notes: null, status: "OPEN",
  createdAt: "2026-11-10T08:00:00Z", loadingStartedAt: null, dispatchedAt: null, cancelReason: null, orderCount: 1, ...over,
})
const order = (over: Record<string, unknown> = {}) => ({
  orderId: "o1", orderReference: "ORD-1", clientName: "Acme", shipToName: "Shop", status: "PACKED", deliveredAt: null, podReceivedBy: null, podNote: null,
  lines: [{ sku: "SKU1", description: "Widget", qty: 7 }], ...over,
})

function serve(m: Record<string, unknown>, orders = [order()], ready: unknown[] = []) {
  get.mockImplementation(async (url: string) => {
    if (url.endsWith("/dispatch/manifests")) return { data: [manifest(m)] }
    if (url.includes("/dispatch/manifests/m1")) return { data: { manifest: manifest(m), orders } }
    if (url.includes("ready-orders")) return { data: ready }
    return { data: [] }
  })
}

async function openFirst() {
  fireEvent.click(await screen.findByText("MAN00001", { exact: false }))
}

beforeEach(() => { get.mockReset(); post.mockReset(); del.mockReset(); post.mockResolvedValue({ data: null }); del.mockResolvedValue({ data: null }); vi.restoreAllMocks() })
afterEach(cleanup)

describe("dispatch manifests", () => {
  it("lists manifests with their status and order count", async () => {
    serve({ status: "LOADING", orderCount: 3 })
    wrap()
    expect(await screen.findByText("LOADING")).toBeTruthy()
    expect(screen.getByText("3 orders")).toBeTruthy()
    expect(screen.getByText(/DHL · CA 123 · Sipho/)).toBeTruthy()
  })

  it("creating a manifest posts the details and opens it", async () => {
    serve({})
    post.mockResolvedValue({ data: { id: "m1" } })
    wrap()
    fireEvent.click(await screen.findByText("New manifest"))
    fireEvent.change(screen.getByLabelText("Carrier"), { target: { value: "DHL" } })
    fireEvent.change(screen.getByLabelText("Vehicle registration"), { target: { value: "CA 123" } })
    fireEvent.click(screen.getByText("Create manifest"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/dispatch/manifests",
      { carrier: "DHL", vehicleRegistration: "CA 123", driverName: null, notes: null }))
    expect(await screen.findByText("All manifests", { exact: false })).toBeTruthy()
  })

  it("an open manifest without vehicle and driver says they are needed before loading", async () => {
    serve({ vehicleRegistration: null, driverName: null })
    wrap()
    await openFirst()
    expect(await screen.findByText(/needed before loading can start/)).toBeTruthy()
  })

  it("a packed order can be added, and loading started", async () => {
    serve({}, [order()], [{ orderId: "o2", orderReference: "ORD-2", clientName: "Beta", shipToName: null }])
    wrap()
    await openFirst()
    const select = await screen.findByLabelText("Add a packed order")
    await waitFor(() => expect(screen.getByText(/ORD-2 · Beta/)).toBeTruthy())
    fireEvent.change(select, { target: { value: "o2" } })
    fireEvent.click(screen.getByText("Add"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/dispatch/manifests/m1/orders", { orderId: "o2" }))
    fireEvent.click(screen.getByText("Start loading"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/dispatch/manifests/m1/start-loading", {}))
  })

  it("an order can be taken off while the manifest is open", async () => {
    serve({})
    wrap()
    await openFirst()
    fireEvent.click(await screen.findByTitle("Take off the manifest"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/warehousing/dispatch/manifests/m1/orders/o1"))
  })

  it("dispatching asks for confirmation first", async () => {
    serve({ status: "LOADING", loadingStartedAt: "2026-11-10T09:00:00Z" })
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true)
    wrap()
    await openFirst()
    const btn = await screen.findByText("Dispatch")
    fireEvent.click(btn)
    expect(post).not.toHaveBeenCalled()
    fireEvent.click(btn)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/dispatch/manifests/m1/dispatch", {}))
    expect(confirm).toHaveBeenCalledTimes(2)
  })

  it("cancelling needs a reason", async () => {
    serve({})
    wrap()
    await openFirst()
    fireEvent.click(await screen.findByText("Cancel manifest"))
    const go = screen.getAllByText("Cancel manifest").pop() as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "truck broke down" } })
    fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/dispatch/manifests/m1/cancel", { reason: "truck broke down" }))
  })

  it("a dispatched manifest records delivery per order, needing a receiver", async () => {
    serve({ status: "DISPATCHED", loadingStartedAt: "2026-11-10T09:00:00Z", dispatchedAt: "2026-11-10T10:00:00Z" },
      [order({ status: "SHIPPED" }), order({ orderId: "o2", orderReference: "ORD-2", status: "SHIPPED", deliveredAt: "2026-11-10T12:00:00Z", podReceivedBy: "T. Mokoena" })])
    wrap()
    await openFirst()
    expect(await screen.findByText(/received by T\. Mokoena/)).toBeTruthy()
    fireEvent.click(screen.getByText("Record delivery"))
    const save = screen.getByText("Save delivery") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Received by *"), { target: { value: "Lerato" } })
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0]
    expect(url).toBe("/api/v1/warehousing/outbound-orders/o1/delivery")
    expect(body.receivedBy).toBe("Lerato")
    expect(body.note).toBeNull()
    expect(new Date(body.deliveredAt).getTime()).toBeLessThanOrEqual(Date.now() + 1000)
  })

  it("downloads the manifest PDF", async () => {
    serve({})
    Object.assign(window.URL, { createObjectURL: vi.fn(() => "blob:x"), revokeObjectURL: vi.fn() })
    wrap()
    await openFirst()
    fireEvent.click(await screen.findByText("Manifest PDF"))
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/warehousing/dispatch/manifests/m1/pdf", { responseType: "blob" }))
  })
})
