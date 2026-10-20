import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import GeofencesTab from "./GeofencesTab"
import { areaText, circleAreaM2, clearShape, clickMap, emptyGeofence, focusPoints, geofenceBody, geofenceProblem, geofenceSummary, movePoint, polygonAreaM2, removePoint, selfIntersects, switchShape, toGeofenceForm, undoPoint, type Geofence, type GeofenceForm, type LatLng } from "./geofences.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a), put: (...a: Args) => put(...a), delete: (...a: Args) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
// Leaflet needs a real browser; the map is a stub with buttons that act as clicks and drags.
vi.mock("./GeofenceMap", () => ({ default: ({ draft, onClick, onMove, onRemove }: { draft: GeofenceForm | null; onClick: (p: LatLng) => void; onMove: (i: number, p: LatLng) => void; onRemove: (i: number) => void }) => (
  <div data-testid="map">
    <span data-testid="draft">{draft ? `${draft.shape}:${draft.polygon.length}:${draft.centre ? draft.centre.join(",") : "none"}` : "none"}</span>
    <button type="button" onClick={() => onClick([-26.0, 28.0])}>click A</button><button type="button" onClick={() => onClick([-26.0, 28.01])}>click B</button>
    <button type="button" onClick={() => onClick([-26.01, 28.01])}>click C</button><button type="button" onClick={() => onClick([-26.01, 28.0])}>click D</button>
    <button type="button" onClick={() => onMove(1, [-26.015, 28.02])}>drag corner 2</button><button type="button" onClick={() => onRemove(0)}>remove corner 1</button>
  </div>) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const square: LatLng[] = [[-26, 28], [-26, 28.01], [-26.01, 28.01], [-26.01, 28]]
const bowtie: LatLng[] = [[-26, 28], [-26.01, 28.01], [-26.01, 28], [-26, 28.01]]
const fences: Geofence[] = [
  { id: "g1", name: "Depot", shape: "CIRCLE", centerLatitude: -26.1, centerLongitude: 28.1, radiusMeters: 500, polygon: null, alertOnEnter: true, alertOnExit: false, speedLimitKph: 20, active: true },
  { id: "g2", name: "Quarry", shape: "POLYGON", centerLatitude: null, centerLongitude: null, radiusMeters: null, polygon: square.map(([latitude, longitude]) => ({ latitude, longitude })), alertOnEnter: true, alertOnExit: true, speedLimitKph: null, active: false },
]
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><GeofencesTab /></QueryClientProvider>)
const named = (f: GeofenceForm, name = "Site") => ({ ...f, name })

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); for (const m of [get, post, put, del]) m.mockReset()
  get.mockImplementation(() => ok(fences)); post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } }); del.mockResolvedValue({ data: { data: null } })
})
afterEach(cleanup)

describe("drawing rules", () => {
  it("a click sets a circle's centre or adds the next polygon corner, capped at 200", () => {
    expect(clickMap(emptyGeofence("CIRCLE"), [1, 2]).centre).toEqual([1, 2])
    const p = clickMap(clickMap(emptyGeofence("POLYGON"), [1, 2]), [3, 4]); expect(p.polygon).toEqual([[1, 2], [3, 4]]); expect(p.centre).toBeNull()
    expect(clickMap({ ...emptyGeofence("POLYGON"), polygon: Array.from({ length: 200 }, (_, i) => [i, i] as LatLng) }, [9, 9]).polygon).toHaveLength(200)
  })
  it("moves, removes, undoes and clears corners", () => {
    const f = { ...emptyGeofence("POLYGON"), polygon: square }
    expect(movePoint(f, 1, [0, 0]).polygon[1]).toEqual([0, 0]); expect(removePoint(f, 0).polygon).toHaveLength(3); expect(undoPoint(f).polygon).toEqual(square.slice(0, 3))
    expect(clearShape(f).polygon).toEqual([]); expect(movePoint({ ...emptyGeofence("CIRCLE"), centre: [1, 1] }, 0, [2, 2]).centre).toEqual([2, 2]); expect(undoPoint({ ...emptyGeofence("CIRCLE"), centre: [1, 1] }).centre).toBeNull()
  })
  it("switching shape starts the outline again but keeps the name and alerts", () => {
    const f = { ...emptyGeofence("CIRCLE"), name: "Depot", centre: [1, 1] as LatLng, alertOnExit: false }
    expect(switchShape(f, "POLYGON")).toMatchObject({ shape: "POLYGON", name: "Depot", centre: null, polygon: [], alertOnExit: false }); expect(switchShape(f, "CIRCLE")).toBe(f)
  })
})

describe("geometry", () => {
  it("measures areas on the ground", () => {
    expect(polygonAreaM2(square)).toBeGreaterThan(1_090_000); expect(polygonAreaM2(square)).toBeLessThan(1_130_000); expect(polygonAreaM2(square.slice(0, 2))).toBe(0)
    expect(Math.round(circleAreaM2(100))).toBe(31416); expect([0, 500, 31416, 2_500_000].map(areaText)).toEqual(["—", "500 m²", "3.1 ha", "2.50 km²"])
  })
  it("spots an outline whose edges cross", () => { expect(selfIntersects(square)).toBe(false); expect(selfIntersects(bowtie)).toBe(true); expect(selfIntersects(square.slice(0, 3))).toBe(false) })
})

describe("saving rules", () => {
  it("checks a circle", () => {
    const c = named(emptyGeofence("CIRCLE"))
    expect(geofenceProblem({ ...c, name: " " })).toMatch(/Name/); expect(geofenceProblem(c)).toMatch(/centre/)
    const placed = { ...c, centre: [1, 2] as LatLng }
    expect(geofenceProblem(placed)).toBeNull(); expect(geofenceProblem({ ...placed, radiusMeters: "5" })).toMatch(/radius/); expect(geofenceProblem({ ...placed, radiusMeters: "100001" })).toMatch(/radius/)
  })
  it("checks a polygon: three corners, no crossing", () => {
    const p = named(emptyGeofence("POLYGON"))
    expect(geofenceProblem({ ...p, polygon: square.slice(0, 2) })).toMatch(/2 of at least 3/); expect(geofenceProblem({ ...p, polygon: bowtie })).toMatch(/crosses itself/); expect(geofenceProblem({ ...p, polygon: square })).toBeNull()
  })
  it("needs a speed limit in range and something for the zone to do", () => {
    const c = { ...named(emptyGeofence("CIRCLE")), centre: [1, 2] as LatLng }
    expect(geofenceProblem({ ...c, speedLimitKph: "3" })).toMatch(/5 to 200/); expect(geofenceProblem({ ...c, speedLimitKph: "50" })).toBeNull()
    expect(geofenceProblem({ ...c, alertOnEnter: false, alertOnExit: false })).toMatch(/does nothing/); expect(geofenceProblem({ ...c, alertOnEnter: false, alertOnExit: false, speedLimitKph: "30" })).toBeNull()
  })
  it("builds the request for each shape, with the other shape's fields null", () => {
    expect(geofenceBody({ ...named(emptyGeofence("CIRCLE"), " Depot "), centre: [-26.123456789, 28.1], speedLimitKph: "20" })).toEqual({ name: "Depot", shape: "CIRCLE", centerLatitude: -26.123457, centerLongitude: 28.1, radiusMeters: 500, polygon: null, alertOnEnter: true, alertOnExit: true, speedLimitKph: 20, active: true })
    expect(geofenceBody({ ...named(emptyGeofence("POLYGON")), polygon: square })).toMatchObject({ shape: "POLYGON", centerLatitude: null, radiusMeters: null, speedLimitKph: null, polygon: [{ latitude: -26, longitude: 28 }, { latitude: -26, longitude: 28.01 }, { latitude: -26.01, longitude: 28.01 }, { latitude: -26.01, longitude: 28 }] })
  })
  it("round-trips a saved fence and describes it", () => {
    expect(toGeofenceForm(fences[0])).toMatchObject({ id: "g1", shape: "CIRCLE", centre: [-26.1, 28.1], radiusMeters: "500", speedLimitKph: "20" }); expect(toGeofenceForm(fences[1]).polygon).toEqual(square)
    expect(geofenceSummary(fences[0])).toBe("500 m radius · alerts on enter · 20 km/h limit"); expect(geofenceSummary(fences[1])).toMatch(/4 corners · .* · alerts on enter and exit$/)
  })
  it("centres the map on the draft, else every fence", () => {
    expect(focusPoints({ ...emptyGeofence("POLYGON"), polygon: square }, fences)).toEqual(square); expect(focusPoints({ ...emptyGeofence("CIRCLE"), centre: [1, 2] }, fences)).toEqual([[1, 2]]); expect(focusPoints(null, fences)).toHaveLength(5)
  })
})

describe("GeofencesTab", () => {
  it("lists saved zones with what each one does", async () => {
    show(); expect((await screen.findByLabelText("Depot")).textContent).toContain("500 m radius"); expect(screen.getByLabelText("Quarry").textContent).toContain("switched off"); expect(screen.getByText("Geofences (2)")).toBeTruthy()
  })
  it("draws a circle and saves it", async () => {
    show(); fireEvent.click(await screen.findByText("New circle")); fireEvent.change(screen.getByLabelText("Geofence name"), { target: { value: "Gate" } })
    expect((screen.getByText("Save geofence") as HTMLButtonElement).disabled).toBe(true); expect(screen.getAllByText(/place the centre/).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByText("click A")); fireEvent.change(screen.getByLabelText("Radius in metres"), { target: { value: "250" } }); fireEvent.click(screen.getByText("Save geofence"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/telematics/geofences", expect.objectContaining({ name: "Gate", shape: "CIRCLE", centerLatitude: -26, centerLongitude: 28, radiusMeters: 250, polygon: null })))
  })
  it("draws a polygon corner by corner, adjusts a corner, and shows the area", async () => {
    show(); fireEvent.click(await screen.findByText("New polygon")); fireEvent.change(screen.getByLabelText("Geofence name"), { target: { value: "Yard" } })
    for (const c of ["click A", "click B", "click C"]) fireEvent.click(screen.getByText(c))
    expect(screen.getByLabelText("Area").textContent).toMatch(/ha|m²/); fireEvent.click(screen.getByText("click D")); fireEvent.click(screen.getByText("drag corner 2"))
    expect(screen.getByTestId("draft").textContent).toBe("POLYGON:4:none")
    fireEvent.click(screen.getByText("Save geofence"))
    await waitFor(() => expect(post).toHaveBeenCalled()); const body = post.mock.calls[0][1] as { polygon: { latitude: number; longitude: number }[] }
    expect(body.polygon).toHaveLength(4); expect(body.polygon[1]).toEqual({ latitude: -26.015, longitude: 28.02 })
  })
  it("blocks a crossing outline and undoes the last corner", async () => {
    show(); fireEvent.click(await screen.findByText("New polygon")); fireEvent.change(screen.getByLabelText("Geofence name"), { target: { value: "Yard" } })
    for (const c of ["click A", "click C", "click B", "click D"]) fireEvent.click(screen.getByText(c))
    expect(screen.getByText(/crosses itself/)).toBeTruthy(); expect((screen.getByText("Save geofence") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByText("Undo last corner")); expect(screen.getByTestId("draft").textContent).toBe("POLYGON:3:none")
  })
  it("edits a saved zone and sends it as an update", async () => {
    show(); fireEvent.click(await screen.findByLabelText("Edit Depot")); expect((screen.getByLabelText("Geofence name") as HTMLInputElement).value).toBe("Depot")
    fireEvent.change(screen.getByLabelText("Speed limit inside"), { target: { value: "15" } }); fireEvent.click(screen.getByLabelText("Alert on exit")); fireEvent.click(screen.getByText("Save geofence"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/telematics/geofences/g1", expect.objectContaining({ speedLimitKph: 15, alertOnExit: true, centerLatitude: -26.1 })))
  })
  it("switches a zone off, and deletes only after a confirm", async () => {
    show(); fireEvent.click(await screen.findByLabelText("Switch off Depot")); await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/telematics/geofences/g1", expect.objectContaining({ active: false })))
    fireEvent.click(screen.getByLabelText("Delete Quarry")); expect(del).not.toHaveBeenCalled(); fireEvent.click(screen.getByLabelText("Confirm delete Quarry"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/fleet/telematics/geofences/g2"))
  })
  it("shows a server refusal and keeps the drawing", async () => {
    post.mockRejectedValue({ response: { data: { message: "A polygon needs at least three points" } } })
    show(); fireEvent.click(await screen.findByText("New circle")); fireEvent.change(screen.getByLabelText("Geofence name"), { target: { value: "Gate" } }); fireEvent.click(screen.getByText("click A")); fireEvent.click(screen.getByText("Save geofence"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/three points/); expect(screen.getByTestId("draft").textContent).toContain("CIRCLE")
  })
  it("read-only users see the zones but cannot draw, edit or delete", async () => {
    perms = new Set(["FLEET_READ"]); show(); await screen.findByLabelText("Depot")
    expect(screen.queryByText("New circle")).toBeNull(); expect(screen.queryByLabelText("Edit Depot")).toBeNull(); expect(screen.queryByLabelText("Delete Depot")).toBeNull()
  })
})
