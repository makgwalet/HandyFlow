import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseReplenTab, { levelsProblem, type RuleRow } from "./WhseReplenTab"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
const del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))

const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const rule = (over: Partial<RuleRow> = {}): RuleRow => ({ id: "R1", clientId: "C1", clientName: "Acme", itemId: "I1", sku: "WID-1", itemDescription: "Widget", locationId: "L1", locationCode: "A-01-01",
  minQty: 10, maxQty: 40, active: true, faceAvailable: 3, inbound: 0, reserveAvailable: 100, status: "BELOW_MIN", ...over })

function serve(rules: RuleRow[]) {
  get.mockImplementation(async (url: string) => {
    if (url.endsWith("/replenishment/rules")) return { data: rules }
    if (url.endsWith("/warehousing/clients")) return { data: [{ id: "C1", tradingName: "Acme" }] }
    if (url.endsWith("/clients/C1/items")) return { data: [{ id: "I1", sku: "WID-1", description: "Widget" }] }
    if (url.endsWith("/warehousing/locations")) return { data: [{ id: "L1", code: "A-01-01", active: true, locationType: "BIN" }, { id: "L2", code: "DOCK-1", active: true, locationType: "DOCK" }] }
    return { data: [] }
  })
}

beforeEach(() => { for (const m of [get, post, put, del]) m.mockReset(); post.mockResolvedValue({ data: { created: 2 } }); put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("levelsProblem", () => {
  it("wants both levels with the maximum above the minimum", () => {
    expect(levelsProblem("", "")).toMatch(/Enter a minimum/)
    expect(levelsProblem("5", "")).toMatch(/Enter a minimum/)
    expect(levelsProblem("-1", "5")).toMatch(/negative/)
    expect(levelsProblem("10", "10")).toMatch(/more than/)
    expect(levelsProblem("0", "5")).toBeNull()
    expect(levelsProblem("2,5", "10")).toBeNull()
  })
})

describe("replenishment tab", () => {
  it("lists rules with their status", async () => {
    serve([rule(), rule({ id: "R2", sku: "GAD-2", status: "NO_RESERVE", reserveAvailable: 0 }), rule({ id: "R3", sku: "OFF-3", active: false, status: "OFF" })])
    wrap(<WhseReplenTab />)
    await waitFor(() => expect(screen.getByText("WID-1")).toBeTruthy())
    expect(screen.getByText("Below minimum")).toBeTruthy()
    expect(screen.getByText("No reserve stock")).toBeTruthy()
    expect(screen.getByText(/3 rules · 2 below minimum/)).toBeTruthy()
  })

  it("saves levels for an item at a pick face, and docks are not offered", async () => {
    serve([])
    wrap(<WhseReplenTab />)
    await waitFor(() => expect(screen.getByText("Acme")).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Client"), { target: { value: "C1" } })
    await waitFor(() => expect(screen.getByText(/WID-1 · Widget/)).toBeTruthy())
    expect(screen.queryByText("DOCK-1")).toBeNull()
    fireEvent.change(screen.getByLabelText("Item"), { target: { value: "I1" } })
    fireEvent.change(screen.getByLabelText("Pick face"), { target: { value: "L1" } })
    fireEvent.change(screen.getByLabelText("Minimum"), { target: { value: "10" } })
    fireEvent.change(screen.getByLabelText("Maximum"), { target: { value: "40" } })
    fireEvent.click(screen.getByText("Save levels"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/warehousing/replenishment/rules", { clientId: "C1", itemId: "I1", locationId: "L1", minQty: 10, maxQty: 40, active: true }))
  })

  it("will not save a maximum at or below the minimum", async () => {
    serve([])
    wrap(<WhseReplenTab />)
    await waitFor(() => expect(screen.getByText("Acme")).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Client"), { target: { value: "C1" } })
    await waitFor(() => expect(screen.getByText(/WID-1 · Widget/)).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Item"), { target: { value: "I1" } })
    fireEvent.change(screen.getByLabelText("Pick face"), { target: { value: "L1" } })
    fireEvent.change(screen.getByLabelText("Minimum"), { target: { value: "10" } })
    fireEvent.change(screen.getByLabelText("Maximum"), { target: { value: "10" } })
    expect((screen.getByText("Save levels") as HTMLButtonElement).disabled).toBe(true)
  })

  it("creates the tasks now and says how many", async () => {
    serve([rule()])
    wrap(<WhseReplenTab />)
    await waitFor(() => expect(screen.getByText("Create replenishment tasks now")).toBeTruthy())
    fireEvent.click(screen.getByText("Create replenishment tasks now"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/replenishment/sync"))
    await waitFor(() => expect(screen.getByText(/2 replenishment tasks created/)).toBeTruthy())
  })

  it("switches a rule off, keeping its levels", async () => {
    serve([rule()])
    wrap(<WhseReplenTab />)
    await waitFor(() => expect(screen.getByText("Switch off")).toBeTruthy())
    fireEvent.click(screen.getByText("Switch off"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/warehousing/replenishment/rules", { clientId: "C1", itemId: "I1", locationId: "L1", minQty: 10, maxQty: 40, active: false }))
  })

  it("deletes a rule", async () => {
    serve([rule()])
    wrap(<WhseReplenTab />)
    await waitFor(() => expect(screen.getByLabelText("Delete rule for WID-1 at A-01-01")).toBeTruthy())
    fireEvent.click(screen.getByLabelText("Delete rule for WID-1 at A-01-01"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/warehousing/replenishment/rules/R1"))
  })
})
