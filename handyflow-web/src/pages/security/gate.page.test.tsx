import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const row = (over: any = {}) => ({ id: "e1", siteId: "s1", siteName: "Centurion Mall", accessPointName: "Main gate", entryType: "VISITOR", personName: "Ann Botha", company: "Acme", hostName: "Pieter",
  vehicleRegistration: "CA 123-456", loggedInAt: new Date(Date.now() - 3 * 3600_000).toISOString(), status: "ON_SITE", ...over })
let data: any = {
  counts: { onSiteNow: 2, overstayed: 1, enteredToday: 5, departedToday: 3, onSiteByType: { VISITOR: 1, CONTRACTOR: 1 } },
  onSite: [row(), row({ id: "e2", personName: "Sipho Dube", company: "Fixit", entryType: "CONTRACTOR", status: "OVERSTAYED", vehicleRegistration: null })],
  onSiteTruncated: false, bySite: [{ siteId: "s1", siteName: "Centurion Mall", onSite: 2, enteredToday: 5 }], todayStartedAt: "2026-10-06T22:00:00Z" }
const get = vi.fn((url: string) => Promise.resolve({ data: { data: url.includes("/gate/dashboard") ? data : { content: [{ id: "s1", name: "Centurion Mall" }] } } }))
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => (get as any)(...a) } }))
import GateDashboardTab from "./GateDashboardTab"

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><GateDashboardTab /></MemoryRouter></QueryClientProvider>)
afterEach(() => { cleanup(); get.mockClear() })

describe("GateDashboardTab", () => {
  it("shows the counts, who is on site and the overstay flag, without ID or phone numbers", async () => {
    show()
    expect(await screen.findByText("Ann Botha")).toBeTruthy()
    expect(screen.getByText("Sipho Dube")).toBeTruthy()
    expect(screen.getByText("Overstayed", { selector: "span" })).toBeTruthy()
    expect(screen.getAllByText(/3 h 0\d min on site|3 h 00 min on site/).length).toBeGreaterThan(0)
    expect(screen.getByText("5")).toBeTruthy() // entered today
  })
  it("filters to overstayed only", async () => {
    show(); await screen.findByText("Ann Botha")
    fireEvent.click(screen.getByLabelText("Overstayed only"))
    expect(screen.queryByText("Ann Botha")).toBeNull()
    expect(screen.getByText("Sipho Dube")).toBeTruthy()
  })
  it("narrows to a site when its card is chosen, asking the server for that site", async () => {
    show(); await screen.findByText("Ann Botha")
    fireEvent.click(screen.getByLabelText("Show Centurion Mall only"))
    await screen.findByText("Ann Botha")
    expect(get.mock.calls.some(c => String(c[0]).includes("siteId=s1"))).toBe(true)
  })
  it("says so when nobody is on site and notes a truncated list", async () => {
    data = { ...data, counts: { onSiteNow: 0, overstayed: 0, enteredToday: 0, departedToday: 0, onSiteByType: {} }, onSite: [], bySite: [], onSiteTruncated: true }
    show()
    expect(await screen.findByText("Nobody is signed in on site right now.")).toBeTruthy()
    expect(screen.getByText(/Showing the 200 longest-staying entries/)).toBeTruthy()
  })
})
