import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const card = (key: string, title: string, scope: string, lastRun: any = null) => ({ key, title, description: `${title} description`, scope, lastRun })
let catalogue: any = {
  cards: [
    card("monthly-summary", "Monthly summary", "NONE", { reportKey: "monthly-summary", period: "2026-09", subject: null, format: "PDF", generatedBy: "Thabo", generatedAt: new Date(Date.now() - 2 * 3600_000).toISOString() }),
    card("site-coverage", "Site coverage", "SITE"), card("guard-attendance", "Guard attendance", "GUARD"), card("site-access", "Site access", "SITE"),
    card("payroll-pack", "Payroll pack", "NONE"),
  ],
  recent: [{ reportKey: "monthly-summary", period: "2026-09", subject: null, format: "PDF", generatedBy: "Thabo", generatedAt: new Date(Date.now() - 2 * 3600_000).toISOString() }],
}
const summary = { month: "2026-09", totalShifts: 40, completedShifts: 38, missedShifts: 2, totalGuardHours: 320, overallCompletionRatePct: 95, totalIncidents: 3, incidentsBySeverity: {}, activeGuards: 12, siteSummaries: [] }
const access = { siteName: "Centurion Mall", month: "2026-09", totalEntries: 14, currentlyOnSite: 2, departed: 12, overstayed: 1, entriesByType: { VISITOR: 9, CONTRACTOR: 5 }, entries: [{ personName: "Ann Botha" }] }
const get = vi.fn((url: string) => {
  if (url.includes("/reports/catalogue")) return Promise.resolve({ data: { data: catalogue } })
  if (url.includes("/reports/monthly-summary")) return Promise.resolve({ data: { data: summary } })
  if (url.includes("/reports/site-access")) return Promise.resolve({ data: { data: access } })
  if (url.includes("/sites")) return Promise.resolve({ data: { data: { content: [{ id: "s1", name: "Centurion Mall" }] } } })
  return Promise.resolve({ data: { data: { content: [{ id: "g1", fullName: "Ann Botha" }] } } })
})
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => (get as any)(...a) } }))
import ReportsTab from "./ReportsTab"

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ReportsTab /></QueryClientProvider>)
afterEach(() => { cleanup(); get.mockClear() })

describe("ReportsTab landing", () => {
  it("shows a card per known report with when it was last generated, and ignores unknown ones", async () => {
    show()
    expect(await screen.findByTestId("report-card-monthly-summary")).toBeTruthy()
    expect(screen.getByText("Generated 2 h ago by Thabo")).toBeTruthy()
    expect(screen.getAllByText("Not generated yet")).toHaveLength(3)
    expect(screen.queryByText("Payroll pack")).toBeNull()
  })
  it("lists recent runs", async () => {
    show()
    const recent = await screen.findByTestId("recent-runs")
    expect(recent.textContent).toContain("September 2026 · PDF")
  })
  it("choosing a card selects that report, and Generate waits for the site", async () => {
    show()
    fireEvent.click(await screen.findByTestId("report-card-site-access"))
    expect(screen.getByTestId("report-card-site-access").getAttribute("aria-pressed")).toBe("true")
    expect((screen.getByText("Generate Report") as HTMLButtonElement).disabled).toBe(true)
    await screen.findByText("Select site…")
  })
  it("generates the summary and asks for the catalogue again so the card updates", async () => {
    show()
    await screen.findByTestId("report-card-monthly-summary")
    const before = get.mock.calls.filter(c => String(c[0]).includes("/catalogue")).length
    fireEvent.click(screen.getByText("Generate Report"))
    await screen.findByText("Total Shifts")
    await waitFor(() => expect(get.mock.calls.filter(c => String(c[0]).includes("/catalogue")).length).toBeGreaterThan(before))
  })
  it("shows site access as counts only, never the names in the entries", async () => {
    show()
    fireEvent.click(await screen.findByTestId("report-card-site-access"))
    fireEvent.change(await screen.findByDisplayValue("Select site…"), { target: { value: "s1" } })
    fireEvent.click(screen.getByText("Generate Report"))
    await screen.findByText("Total Entries")
    expect(screen.getByText("9 visitor")).toBeTruthy()
    expect(screen.queryByText("Ann Botha")).toBeNull()
  })
})
