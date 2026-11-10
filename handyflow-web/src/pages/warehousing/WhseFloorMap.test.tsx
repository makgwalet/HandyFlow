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

  describe("editing the layout", () => {
    const two = () => serve([
      loc({ id: "1", code: "A-01-01", level: 1, posX: 0, posY: 0, onHand: 0 }),
      loc({ id: "2", code: "A-02-01", level: 1, bay: 2, posX: 1, posY: 0, onHand: 0 }),
    ])

    it("shows empty squares only while editing", async () => {
      two()
      wrap(<WhseFloorMap />)
      await waitFor(() => expect(screen.getAllByRole("gridcell")).toHaveLength(2))
      fireEvent.click(screen.getByText("Edit layout"))
      expect(screen.getAllByLabelText(/Empty square/).length).toBeGreaterThan(0)
      fireEvent.click(screen.getByText("Done editing"))
      expect(screen.queryAllByLabelText(/Empty square/)).toHaveLength(0)
    })

    it("moves a bay when it is dropped on an empty square", async () => {
      two(); post.mockResolvedValue({ data: {} })
      wrap(<WhseFloorMap />)
      await waitFor(() => expect(screen.getAllByRole("gridcell")).toHaveLength(2))
      fireEvent.click(screen.getByText("Edit layout"))
      fireEvent.drop(screen.getByLabelText("Empty square column 0, row 1"), { dataTransfer: { getData: () => "1:0" } })
      await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/floor-map/cells/move", { fromX: 1, fromY: 0, toX: 0, toY: 1 }))
    })

    it("moves the selected bay when an empty square is clicked, and shows the server's refusal", async () => {
      two(); post.mockRejectedValue({ response: { data: { message: "Something is already at column 2, row 0." } } })
      wrap(<WhseFloorMap />)
      await waitFor(() => expect(screen.getAllByRole("gridcell")).toHaveLength(2))
      fireEvent.click(screen.getByText("Edit layout"))
      fireEvent.click(screen.getByTitle(/A-01-01/))
      fireEvent.click(screen.getByLabelText("Empty square column 2, row 0"))
      await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/floor-map/cells/move", { fromX: 0, fromY: 0, toX: 2, toY: 0 }))
      await waitFor(() => expect(screen.getAllByText(/already at column 2/).length).toBeGreaterThan(0))
    })

    it("moves by typed column and row, saves type and capacity, adds levels and bays", async () => {
      two(); post.mockResolvedValue({ data: {} })
      wrap(<WhseFloorMap />)
      await waitFor(() => expect(screen.getAllByRole("gridcell")).toHaveLength(2))
      fireEvent.click(screen.getByText("Edit layout"))
      fireEvent.click(screen.getByTitle(/A-01-01/))

      const move = screen.getByText("Move bay") as HTMLButtonElement
      expect(move.disabled).toBe(true)
      fireEvent.change(screen.getByLabelText("Move to column"), { target: { value: "5" } })
      fireEvent.change(screen.getByLabelText("Move to row"), { target: { value: "3" } })
      fireEvent.click(move)
      await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/floor-map/cells/move", { fromX: 0, fromY: 0, toX: 5, toY: 3 }))
    })

    it("edits type, capacity, in-use, levels and aisle length of the selected bay", async () => {
      two(); post.mockResolvedValue({ data: {} })
      wrap(<WhseFloorMap />)
      await waitFor(() => expect(screen.getAllByRole("gridcell")).toHaveLength(2))
      fireEvent.click(screen.getByText("Edit layout"))
      fireEvent.click(screen.getByTitle(/A-01-01/))
      const url = (p: string) => `/api/v1/warehousing/floor-map${p}`

      fireEvent.change(screen.getByLabelText("Type (all levels)"), { target: { value: "SHELF" } })
      fireEvent.click(screen.getByText("Save type"))
      await waitFor(() => expect(post).toHaveBeenCalledWith(url("/cells/edit"), { x: 0, y: 0, locationType: "SHELF" }))

      fireEvent.change(screen.getByLabelText("Capacity per level (units)"), { target: { value: "25" } })
      fireEvent.click(screen.getByText("Save capacity"))
      await waitFor(() => expect(post).toHaveBeenCalledWith(url("/cells/edit"), { x: 0, y: 0, capacityUnits: 25 }))
      fireEvent.click(screen.getByText("Clear"))
      await waitFor(() => expect(post).toHaveBeenCalledWith(url("/cells/edit"), { x: 0, y: 0, clearCapacity: true }))

      fireEvent.change(screen.getByLabelText("Add levels on top"), { target: { value: "2" } })
      fireEvent.click(screen.getByText("Add levels"))
      await waitFor(() => expect(post).toHaveBeenCalledWith(url("/cells/add-levels"), { x: 0, y: 0, count: 2 }))

      fireEvent.change(screen.getByLabelText("Add bays to the end of aisle A"), { target: { value: "3" } })
      fireEvent.click(screen.getByText("Add bays"))
      await waitFor(() => expect(post).toHaveBeenCalledWith(url("/aisles/extend"), { aisle: "A", bays: 3 }))

      fireEvent.click(screen.getByText("Switch bay off"))
      await waitFor(() => expect(post).toHaveBeenCalledWith(url("/cells/edit"), { x: 0, y: 0, active: false }))
    })

    it("shows why a bay with stock cannot be switched off", async () => {
      two(); post.mockRejectedValue({ response: { data: { message: "A-01-01 still holds stock, so this bay cannot be switched off." } } })
      wrap(<WhseFloorMap />)
      await waitFor(() => expect(screen.getAllByRole("gridcell")).toHaveLength(2))
      fireEvent.click(screen.getByText("Edit layout"))
      fireEvent.click(screen.getByTitle(/A-01-01/))
      fireEvent.click(screen.getByText("Switch bay off"))
      await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/still holds stock/))
    })
  })
})
