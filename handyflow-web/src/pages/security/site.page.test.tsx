import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

vi.mock("react-leaflet", () => ({
  MapContainer: ({ children }: any) => <div data-testid="map">{children}</div>,
  TileLayer: () => null, Marker: ({ children }: any) => <div data-testid="pin">{children}</div>, Popup: ({ children }: any) => <span>{children}</span>,
}))
vi.mock("leaflet", () => ({ default: { divIcon: () => ({}) } }))
vi.mock("leaflet/dist/leaflet.css", () => ({}))

const overview = (over: any = {}) => ({
  site: { id: "s1", name: "Centurion Mall", address: { street: "1 Main Rd", city: "Pretoria" }, latitude: -25.85, longitude: 28.19, contactName: "Ann", contactPhone: "082", active: true, contractStatus: "ACTIVE", contractStart: "2026-01-01", contractEnd: "2027-01-01", terminationReason: null, requireSignedQr: true },
  counts: { guardsOnSite: 1, upcomingShifts7d: 2, openIncidents: 1, activePatrolRoutes: 1, checkpoints: 2 },
  onSite: [{ guardId: "g1", guardName: "Nomsa Dlamini", grade: "C", shiftId: "sh1", shiftStart: "2026-10-07T06:00:00Z", shiftEnd: "2026-10-07T16:00:00Z", overrunning: false, siteId: "s1", siteName: "Centurion Mall", latitude: -25.85, longitude: 28.19, recordedAt: "2026-10-07T08:00:00Z", gpsState: "LIVE", lastScanAt: null, lastScanCheckpoint: null }],
  recentIncidents: [{ id: "i1", title: "Tailgating", severity: "CRITICAL", status: "OPEN", reportedAt: "2026-06-29T06:00:00Z" }],
  checkpoints: [{ id: "c1", name: "North Gate", scans30d: 0, lastScanAt: null }, { id: "c2", name: "Server Room", scans30d: 4, lastScanAt: "2026-10-05T10:00:00Z" }],
  upcoming: [{ shiftId: "u1", guardId: "g2", guardName: "Priya Govender", startAt: "2026-10-08T06:00:00Z", endAt: "2026-10-08T16:00:00Z" }],
  ...over,
})
let payload: any = overview()
vi.mock("../../api/client", () => ({ apiClient: { get: vi.fn(() => Promise.resolve({ data: { data: payload } })) } }))
import SiteDetailPage from "./SiteDetailPage"

const show = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={["/security/sites/s1"]}><Routes><Route path="/security/sites/:id" element={<SiteDetailPage />} /></Routes></MemoryRouter>
  </QueryClientProvider>)

afterEach(cleanup)
describe("SiteDetailPage", () => {
  it("shows the site, who is on it, incidents and checkpoint health", async () => {
    payload = overview(); show()
    expect(await screen.findByText("1 Main Rd, Pretoria")).toBeTruthy()
    expect(screen.getByText("Nomsa Dlamini")).toBeTruthy()
    expect(screen.getByText("Priya Govender")).toBeTruthy()
    expect(screen.getByText("Tailgating")).toBeTruthy()
    expect(screen.getByText("Never scanned")).toBeTruthy()
    expect(screen.getByText("4 scans in 30 days")).toBeTruthy()
    expect(screen.getByText(/1 with no scans in 30 days/)).toBeTruthy()
    expect(screen.getByText("Signed QR required")).toBeTruthy()
  })
  it("shows no map for a site without coordinates", async () => {
    payload = overview({ site: { ...overview().site, latitude: null, longitude: null } }); show()
    expect(await screen.findByText(/no coordinates/)).toBeTruthy()
    expect(screen.queryByTestId("map")).toBeNull()
  })
  it("says so when nothing is on record", async () => {
    payload = overview({ onSite: [], recentIncidents: [], checkpoints: [], upcoming: [], counts: { guardsOnSite: 0, upcomingShifts7d: 0, openIncidents: 0, activePatrolRoutes: 0, checkpoints: 0 } }); show()
    expect(await screen.findByText("No guard is on an active shift here.")).toBeTruthy()
    expect(screen.getByText("No incidents recorded at this site.")).toBeTruthy()
    expect(screen.getByText("No checkpoints set up for this site.")).toBeTruthy()
    expect(screen.getByText("No shifts scheduled in the next 7 days.")).toBeTruthy()
  })
})
