import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseFloorMap from "./WhseFloorMap"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const base = { zone: "Dry", aisle: "A", bay: 1, locationType: "PALLET", pickSequence: 1, capacityUnits: 10, active: true, allocated: 0, held: 0, skuCount: 0, earliestExpiry: null, utilisationPct: null }
const loc = (o: object) => ({ ...base, ...o })

function serve(locations: object[]) {
  get.mockResolvedValue({ data: { locations, placed: locations.filter((l: any) => l.posX != null).length, unplaced: locations.filter((l: any) => l.posX == null).length } })
}

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); put.mockResolvedValue({ data: null }) })
afterEach(cleanup)

describe("floor map", () => {
  it("draws one cell per bay and lists the levels of the selected bay", async () => {
    serve([
      loc({ id: "1", code: "A-01-01", level: 1, posX: 0, posY: 0, onHand: 8, utilisationPct: 80 }),
      loc({ id: "2", code: "A-01-02", level: 2, posX: 0, posY: 0, onHand: 0 }),
      loc({ id: "3", code: "A-02-01", level: 1, bay: 2, posX: 1, posY: 0, onHand: 3, held: 3, earliestExpiry: "2020-01-01" }),
    ])
    wrap(<WhseFloorMap />)
    await waitFor(() => expect(screen.getAllByRole("gridcell")).toHaveLength(2))
    fireEvent.click(screen.getAllByRole("gridcell")[0])
    expect(screen.getByText("A-01-02")).toBeTruthy()
    expect(screen.getByText(/8 on hand/)).toBeTruthy()
    expect(screen.getAllByText("Empty").length).toBeGreaterThan(1) // the legend and the empty level
  })

  it("filters by zone", async () => {
    serve([loc({ id: "1", code: "A-01-01", level: 1, posX: 0, posY: 0 }), loc({ id: "2", code: "B-01-01", zone: "Cold", aisle: "B", level: 1, posX: 0, posY: 2 })])
    wrap(<WhseFloorMap />)
    await waitFor(() => expect(screen.getAllByRole("gridcell")).toHaveLength(2))
    fireEvent.change(screen.getByLabelText("Zone"), { target: { value: "Cold" } })
    expect(screen.getAllByRole("gridcell")).toHaveLength(1)
  })

  it("lets an unplaced location be given a cell", async () => {
    serve([loc({ id: "9", code: "OLD-1", level: null, bay: null, aisle: null, posX: null, posY: null })])
    wrap(<WhseFloorMap />)
    await waitFor(() => expect(screen.getByText(/Not on the map \(1\)/)).toBeTruthy())
    const place = screen.getByText("Place") as HTMLButtonElement
    expect(place.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Column for OLD-1"), { target: { value: "4" } })
    fireEvent.change(screen.getByLabelText("Row for OLD-1"), { target: { value: "2" } })
    fireEvent.click(place)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/warehousing/floor-map/locations/9/layout", expect.objectContaining({ posX: 4, posY: 2, locationType: "PALLET" })))
  })

  it("previews generated racking and only enables Create when nothing clashes", async () => {
    serve([])
    wrap(<WhseFloorMap />)
    fireEvent.click(await screen.findByText("Generate racking"))
    post.mockResolvedValueOnce({ data: [{ slot: { code: "A-01-01", x: 0, y: 0, level: 1 }, conflict: "Code A-01-01 already exists." }] })
    fireEvent.click(screen.getByText("Preview"))
    await waitFor(() => expect(screen.getByText(/clash with existing/)).toBeTruthy())
    expect((screen.getByText("Create") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByDisplayValue("A-C"), { target: { value: "D" } })
    post.mockResolvedValueOnce({ data: [{ slot: { code: "D-01-01", x: 0, y: 0, level: 1 }, conflict: null }] })
    fireEvent.click(screen.getByText("Preview"))
    await waitFor(() => expect(screen.getByText(/will be created/)).toBeTruthy())
    expect((screen.getByText("Create") as HTMLButtonElement).disabled).toBe(false)
  })
})
