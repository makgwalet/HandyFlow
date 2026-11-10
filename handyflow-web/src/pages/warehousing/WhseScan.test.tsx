import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseScanTab from "./WhseScanTab"

const get = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a) } }))

const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WhseScanTab /></QueryClientProvider>)
const scan = (value: string) => {
  fireEvent.change(screen.getByLabelText("Scan or type a code"), { target: { value } })
  fireEvent.submit(screen.getByLabelText("Scan or type a code").closest("form")!)
}
const stockLine = { locationId: "L1", locationCode: "A-01-01", itemId: "I1", sku: "SKU1", description: "Widget", clientName: "Acme", lotNumber: "LOT7", expiryDate: "2027-01-31", onHand: 12, allocated: 2, status: "AVAILABLE" }

beforeEach(() => { get.mockReset(); put.mockReset(); put.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("scan lookup", () => {
  it("keeps the input focused and asks the server to resolve what was scanned", async () => {
    get.mockResolvedValue({ data: [] })
    wrap()
    expect(document.activeElement).toBe(screen.getByLabelText("Scan or type a code"))
    scan(" A-01-01 ")
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/warehousing/scan/resolve", { params: { code: "A-01-01" } }))
    await waitFor(() => expect(screen.getByRole("status").textContent).toMatch(/Nothing matches “A-01-01”/))
    expect((screen.getByLabelText("Scan or type a code") as HTMLInputElement).value).toBe("")
  })

  it("does nothing for an empty scan", () => {
    wrap()
    scan("   ")
    expect(get).not.toHaveBeenCalled()
  })

  it("a single match opens straight away and shows what is stored there", async () => {
    get.mockImplementation((url: string) => Promise.resolve({
      data: url.endsWith("/resolve") ? [{ type: "LOCATION", id: "L1", label: "A-01-01", detail: "Dry", clientId: null }] : [stockLine],
    }))
    wrap()
    scan("A-01-01")
    await waitFor(() => expect(screen.getByText("LOCATION")).toBeTruthy())
    expect(get).toHaveBeenCalledWith("/api/v1/warehousing/scan/locations/L1/stock")
    await waitFor(() => expect(screen.getByText(/12 \(2 allocated\)/)).toBeTruthy())
    expect(screen.getByText(/lot LOT7 · expires 2027-01-31/)).toBeTruthy()
  })

  it("several matches ask which one, then show that item's stock by location", async () => {
    get.mockImplementation((url: string) => Promise.resolve({
      data: url.endsWith("/resolve")
        ? [{ type: "ITEM", id: "I1", label: "SKU1", detail: "Widget · Acme", clientId: "C1" }, { type: "ITEM", id: "I2", label: "SKU1", detail: "Widget · Karoo", clientId: "C2" }]
        : [stockLine],
    }))
    wrap()
    scan("SKU1")
    await waitFor(() => expect(screen.getByText(/could be more than one thing/)).toBeTruthy())
    fireEvent.click(screen.getByText("Widget · Karoo"))
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/warehousing/scan/items/I2/stock"))
    await waitFor(() => expect(screen.getByText("A-01-01")).toBeTruthy())
    fireEvent.click(screen.getByText("Back to the matches"))
    expect(screen.getByText(/could be more than one thing/)).toBeTruthy()
  })

  it("an order or manifest shows its status without a stock query", async () => {
    get.mockResolvedValue({ data: [{ type: "MANIFEST", id: "M1", label: "MAN00003", detail: "LOADING", clientId: null }] })
    wrap()
    scan("MAN00003")
    await waitFor(() => expect(screen.getByText("DISPATCH MANIFEST")).toBeTruthy())
    expect(screen.getByText("LOADING")).toBeTruthy()
    expect(get).toHaveBeenCalledTimes(1)
  })

  it("sets and clears a location barcode, and shows the server's refusal", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: url.endsWith("/resolve") ? [{ type: "LOCATION", id: "L1", label: "A-01-01", detail: null, clientId: null }] : [] }))
    wrap()
    scan("A-01-01")
    await waitFor(() => expect(screen.getByLabelText(/Label barcode for A-01-01/)).toBeTruthy())
    const save = screen.getByText("Save barcode") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Label barcode for A-01-01/), { target: { value: "LOC9" } })
    fireEvent.click(save)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/warehousing/scan/locations/L1/barcode", { barcode: "LOC9" }))
    await waitFor(() => expect(screen.getByText("Barcode saved.")).toBeTruthy())
    fireEvent.click(screen.getByText("Clear"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/warehousing/scan/locations/L1/barcode", { barcode: "" }))
    put.mockRejectedValue({ response: { data: { message: "Another location already uses LOC1 as its barcode or code." } } })
    fireEvent.change(screen.getByLabelText(/Label barcode for A-01-01/), { target: { value: "LOC1" } })
    fireEvent.click(screen.getByText("Save barcode"))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/already uses LOC1/))
  })
})
