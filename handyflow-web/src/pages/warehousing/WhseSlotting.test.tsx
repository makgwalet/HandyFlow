import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseSlottingTab, { type Overview, type Suggestion } from "./WhseSlottingTab"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))

const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const sug = (over: Partial<Suggestion> = {}): Suggestion => ({ clientId: "C1", clientName: "Acme", itemId: "I1", sku: "FAST-1", description: "Fast thing", abc: "A", picked: 900,
  fromLocationId: "L9", fromCode: "Z-99", toLocationId: "L1", toCode: "A-01", qty: 30, improvement: 89, ...over })
const overview = (s: Suggestion[]): Overview => ({ days: 90, itemsWithStock: 10, classA: 2, classB: 3, classC: 5, suggestions: s })

beforeEach(() => { get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: { id: "T1" } }) })
afterEach(cleanup)

describe("slotting tab", () => {
  it("shows the class counts and the suggested moves", async () => {
    get.mockResolvedValue({ data: overview([sug()]) })
    wrap(<WhseSlottingTab />)
    await waitFor(() => expect(screen.getByText("FAST-1")).toBeTruthy())
    expect(screen.getByText("Class A")).toBeTruthy()
    expect(screen.getByText("Z-99")).toBeTruthy()
    expect(screen.getByText("A-01")).toBeTruthy()
    expect(screen.getByText(/89 places nearer/)).toBeTruthy()
    expect(screen.getByText("Class A (fastest)")).toBeTruthy()
  })

  it("creates the move task for a suggestion", async () => {
    get.mockResolvedValue({ data: overview([sug()]) })
    wrap(<WhseSlottingTab />)
    await waitFor(() => expect(screen.getByText("Create move task")).toBeTruthy())
    fireEvent.click(screen.getByText("Create move task"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/slotting/moves", { clientId: "C1", itemId: "I1", fromLocationId: "L9", toLocationId: "L1" }))
    await waitFor(() => expect(screen.getByText("Task created")).toBeTruthy())
  })

  it("asks again when the period changes", async () => {
    get.mockResolvedValue({ data: overview([]) })
    wrap(<WhseSlottingTab />)
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/warehousing/slotting/suggestions?days=90"))
    fireEvent.change(screen.getByLabelText("Look at picks over"), { target: { value: "365" } })
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/warehousing/slotting/suggestions?days=365"))
  })

  it("says so when nothing needs moving", async () => {
    get.mockResolvedValue({ data: overview([]) })
    wrap(<WhseSlottingTab />)
    await waitFor(() => expect(screen.getByText(/No moves suggested/)).toBeTruthy())
  })
})
