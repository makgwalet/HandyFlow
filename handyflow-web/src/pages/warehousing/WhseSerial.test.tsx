import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import SerialPanel, { SerialEntry } from "./WhseSerialPanel"
import ItemTrackingModal from "./ItemTrackingModal"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))

const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const line = (serials: string[], toShip = 2) => ({ lineId: "LN1", itemId: "I1", sku: "LAP1", description: "Laptop", toShip, serials })

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); post.mockResolvedValue({ data: {} }); put.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("serial entry", () => {
  it("counts the serials against the units", () => {
    const { rerender } = render(<SerialEntry value="A1 A2" onChange={() => {}} units={3} />)
    expect(screen.getByText("2 of 3 units")).toBeTruthy()
    rerender(<SerialEntry value={"A1\nA2\nA3"} onChange={() => {}} units={3} />)
    expect(screen.getByText("3 of 3 units")).toBeTruthy()
  })
})

describe("order serial panel", () => {
  it("shows nothing for an order with no serial-tracked lines", async () => {
    get.mockResolvedValue({ data: [] })
    const { container } = wrap(<SerialPanel orderId="O1" status="PICKING" />)
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })

  it("records serials on a line being picked, and removes one", async () => {
    get.mockResolvedValue({ data: [line(["SN-1"])] })
    wrap(<SerialPanel orderId="O1" status="PICKING" />)
    await waitFor(() => expect(screen.getByText("1 of 2 recorded")).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Serials for LAP1"), { target: { value: "SN-2\nSN-3" } })
    fireEvent.click(screen.getByText("Add"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/serials/pick", { orderId: "O1", lineId: "LN1", serials: ["SN-2", "SN-3"] }))
    fireEvent.click(screen.getByLabelText("Remove SN-1"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/serials/unpick", { orderId: "O1", lineId: "LN1", serials: ["SN-1"] }))
  })

  it("shows the server's reason when a serial is refused", async () => {
    get.mockResolvedValue({ data: [line([])] })
    post.mockRejectedValue({ response: { data: { message: "Serial SN-9 is shipped, not available to pick." } } })
    wrap(<SerialPanel orderId="O1" status="PICKING" />)
    await waitFor(() => expect(screen.getByText("0 of 2 recorded")).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Serials for LAP1"), { target: { value: "SN-9" } })
    fireEvent.click(screen.getByText("Add"))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/not available to pick/))
  })

  it("is read-only once the order is no longer being picked, and a complete line has no entry box", async () => {
    get.mockResolvedValue({ data: [line(["SN-1", "SN-2"])] })
    const { unmount } = wrap(<SerialPanel orderId="O1" status="PICKING" />)
    await waitFor(() => expect(screen.getByText("2 of 2 recorded")).toBeTruthy())
    expect(screen.queryByLabelText("Serials for LAP1")).toBeNull()
    unmount()
    get.mockResolvedValue({ data: [line(["SN-1"])] })
    wrap(<SerialPanel orderId="O1" status="PACKED" />)
    await waitFor(() => expect(screen.getByText("1 of 2 recorded")).toBeTruthy())
    expect(screen.queryByLabelText("Serials for LAP1")).toBeNull()
    expect(screen.queryByLabelText("Remove SN-1")).toBeNull()
  })
})

describe("item tracking modal", () => {
  it("switches serial tracking on with its own call", async () => {
    wrap(<ItemTrackingModal clientId="C1" item={{ id: "I1", sku: "LAP1" }} onClose={() => {}} />)
    fireEvent.click(screen.getByLabelText(/Track by serial number/))
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/warehousing/items/I1/serial-tracking", { serialTracked: true }))
    expect(put).toHaveBeenCalledWith("/api/v1/warehousing/items/I1/tracking", expect.anything())
  })

  it("does not touch serial tracking when it was not changed", async () => {
    wrap(<ItemTrackingModal clientId="C1" item={{ id: "I1", sku: "LAP1", serialTracked: true }} onClose={() => {}} />)
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(put).toHaveBeenCalledTimes(1))
    expect(put).not.toHaveBeenCalledWith(expect.stringContaining("serial-tracking"), expect.anything())
  })
})
