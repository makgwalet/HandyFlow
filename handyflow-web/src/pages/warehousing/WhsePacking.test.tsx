import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import PackingPanel, { parseWeight, type PackingSummary, type PackageRow } from "./WhsePackingPanel"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
const del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))

const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const pkg = (over: Partial<PackageRow> = {}): PackageRow => ({ id: "P1", packageNo: 1, type: "BOX", lengthMm: null, widthMm: null, heightMm: null, tareKg: 0, grossKg: null, status: "OPEN",
  labelPrintedAt: null, weightNote: null, items: [], ...over })
const summary = (over: Partial<PackingSummary> = {}): PackingSummary => ({
  packages: [], lines: [{ lineId: "L1", sku: "WID-1", description: "Widget", toShip: 10, packed: 0, remaining: 10 }], totalGrossKg: 0, unpackedUnits: 10, blockers: [], ...over })

beforeEach(() => { for (const m of [get, post, put, del]) m.mockReset(); post.mockResolvedValue({ data: {} }); put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("parseWeight", () => {
  it("accepts a decimal comma and rejects blank, zero and junk", () => {
    expect(parseWeight("7,5")).toBe(7.5)
    expect(parseWeight("")).toBeNull()
    expect(parseWeight("0")).toBeNull()
    expect(parseWeight("abc")).toBeNull()
  })
})

describe("packing panel", () => {
  it("shows nothing before picking starts", () => {
    const { container } = wrap(<PackingPanel orderId="O1" status="PENDING" />)
    expect(container.textContent).toBe("")
  })

  it("starts a package of the chosen type and can pack everything left", async () => {
    get.mockResolvedValue({ data: summary() })
    wrap(<PackingPanel orderId="O1" status="PICKING" />)
    await waitFor(() => expect(screen.getByText("New package")).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Package type"), { target: { value: "PALLET" } })
    fireEvent.click(screen.getByText("New package"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/packing/orders/O1/packages", { type: "PALLET" }))
    fireEvent.click(screen.getByText("Pack everything left"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/packing/orders/O1/pack-remaining", { type: "PALLET" }))
  })

  it("puts a quantity of a line in an open package", async () => {
    get.mockResolvedValue({ data: summary({ packages: [pkg()] }) })
    wrap(<PackingPanel orderId="O1" status="PICKING" />)
    await waitFor(() => expect(screen.getByLabelText("Quantity for package 1")).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Quantity for package 1"), { target: { value: "4" } })
    fireEvent.click(screen.getByText("Put in package"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/warehousing/packing/packages/P1/items", { lineId: "L1", qty: 4 }))
  })

  it("will not close an empty package or one without a weight", async () => {
    get.mockResolvedValue({ data: summary({ packages: [pkg()] }) })
    wrap(<PackingPanel orderId="O1" status="PICKING" />)
    await waitFor(() => expect(screen.getByText("Close package")).toBeTruthy())
    expect((screen.getByText("Close package") as HTMLButtonElement).disabled).toBe(true)
  })

  it("closes a package with the weighed gross weight", async () => {
    get.mockResolvedValue({ data: summary({ packages: [pkg({ items: [{ lineId: "L1", sku: "WID-1", description: "Widget", qty: 10 }] })], unpackedUnits: 0,
      lines: [{ lineId: "L1", sku: "WID-1", description: "Widget", toShip: 10, packed: 10, remaining: 0 }] }) })
    wrap(<PackingPanel orderId="O1" status="PICKING" />)
    await waitFor(() => expect(screen.getByLabelText("Gross weight for package 1")).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Gross weight for package 1"), { target: { value: "12,5" } })
    fireEvent.click(screen.getByText("Close package"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/packing/packages/P1/close", { grossKg: 12.5 }))
  })

  it("shows the blockers and the weight warning", async () => {
    get.mockResolvedValue({ data: summary({ packages: [pkg({ status: "CLOSED", grossKg: 3, weightNote: "Lighter than expected: contents weigh 3 kg" })], blockers: ["WID-1: 10 not packed yet."] }) })
    wrap(<PackingPanel orderId="O1" status="PICKING" />)
    await waitFor(() => expect(screen.getByText("WID-1: 10 not packed yet.")).toBeTruthy())
    expect(screen.getByText(/Lighter than expected/)).toBeTruthy()
    expect(screen.getByText("Print label")).toBeTruthy()
  })

  it("a packed order is read only but still offers the label", async () => {
    get.mockResolvedValue({ data: summary({ packages: [pkg({ status: "CLOSED", grossKg: 3 })] }) })
    wrap(<PackingPanel orderId="O1" status="PACKED" />)
    await waitFor(() => expect(screen.getByText("Print label")).toBeTruthy())
    expect(screen.queryByText("New package")).toBeNull()
    expect(screen.queryByText("Reopen")).toBeNull()
  })

  it("a packed order with no packages shows no panel", async () => {
    get.mockResolvedValue({ data: summary() })
    const { container } = wrap(<PackingPanel orderId="O1" status="PACKED" />)
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
})
