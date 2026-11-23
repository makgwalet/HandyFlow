import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

let manage = true
let scope: "whole" | "limited" = "whole"
let liveItems: object[] = []
let liveFails = false
const targets = {
  companyDefaults: [{ siteId: null, siteName: null, severity: "HIGH", acknowledgeMinutes: 10, resolveMinutes: 120 }],
  sites: [{ siteId: "s1", siteName: "Centurion Mall", severity: "CRITICAL", acknowledgeMinutes: 5, resolveMinutes: 60 }],
}
const report = {
  from: "2026-10-25", to: "2026-11-23",
  rows: [{ siteId: "s1", siteName: "Centurion Mall", severity: "HIGH", total: 4, tracked: 4, met: 3, breached: 1, atRisk: 0, pending: 0, compliancePct: 75, averageAcknowledgeMinutes: 6.5, averageResolveMinutes: 95 }],
  total: { siteId: null, siteName: null, severity: null, total: 4, tracked: 4, met: 3, breached: 1, atRisk: 0, pending: 0, compliancePct: 75, averageAcknowledgeMinutes: 6.5, averageResolveMinutes: 95 },
}
const get = vi.fn((url: string) => {
  if (url.includes("/my-site-scope")) return Promise.resolve({ data: { data: { restricted: scope === "limited" } } })
  if (url.includes("/sla/live")) return liveFails ? Promise.reject({ response: { data: { message: "Too many open incidents to list." } } }) : Promise.resolve({ data: { data: liveItems } })
  if (url.includes("/sla/report")) return Promise.resolve({ data: { data: report } })
  if (url.includes("/sla/targets")) return Promise.resolve({ data: { data: targets } })
  if (url.includes("/security/sites")) return Promise.resolve({ data: { data: { content: [{ id: "s1", name: "Centurion Mall" }, { id: "s2", name: "Menlyn Park" }] } } })
  return Promise.resolve({ data: { data: [] } })
})
const put = vi.fn((...args: [string, object]) => Promise.resolve({ data: { data: targets }, args }))
const del = vi.fn((...args: [string]) => Promise.resolve({ data: { data: targets }, args }))
vi.mock("../../api/client", () => ({ apiClient: { get: (u: string) => get(u), put: (u: string, b: object) => put(u, b), delete: (u: string) => del(u) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => manage }))
import SlaTab from "./SlaTab"

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SlaTab /></QueryClientProvider>)
afterEach(() => { cleanup(); get.mockClear(); put.mockClear(); del.mockClear(); manage = true; scope = "whole"; liveItems = []; liveFails = false })

const openTargets = async () => { show(); fireEvent.click(screen.getByRole("tab", { name: "Targets" })); await screen.findByTestId("target-company-HIGH") }

describe("Service levels: needs attention", () => {
  it("says so when nothing is breached", async () => {
    show()
    expect(await screen.findByTestId("live-empty")).toBeTruthy()
  })

  it("lists breached and at-risk incidents with what is overdue or left", async () => {
    liveItems = [
      { incidentId: "i1", siteId: "s1", siteName: "Centurion Mall", severity: "HIGH", title: "Break-in", status: "OPEN", recordedAt: "2026-11-23T06:00:00Z", state: "BREACHED", stage: "ACKNOWLEDGE", overdueMinutes: 20, remainingMinutes: null },
      { incidentId: "i2", siteId: "s2", siteName: "Menlyn Park", severity: "LOW", title: null, status: "ACKNOWLEDGED", recordedAt: "2026-11-23T06:00:00Z", state: "AT_RISK", stage: "RESOLVE", overdueMinutes: null, remainingMinutes: 10 },
    ]
    show()
    const a = await screen.findByTestId("live-i1")
    expect(a.textContent).toContain("Breached"); expect(a.textContent).toContain("Break-in"); expect(a.textContent).toContain("20 min overdue to acknowledge")
    const b = screen.getByTestId("live-i2")
    expect(b.textContent).toContain("At risk"); expect(b.textContent).toContain("Untitled incident"); expect(b.textContent).toContain("10 min left to resolve")
  })

  it("shows the server's words when it cannot load", async () => {
    liveFails = true
    show()
    expect((await screen.findByRole("alert")).textContent).toContain("Too many open incidents")
  })
})

describe("Service levels: report", () => {
  it("asks for the last 30 days and shows rows, totals and compliance", async () => {
    show()
    fireEvent.click(screen.getByRole("tab", { name: "Report" }))
    const row = await screen.findByTestId("report-0")
    expect(row.textContent).toContain("Centurion Mall"); expect(row.textContent).toContain("75%"); expect(row.textContent).toContain("1 h 35 min")
    expect(screen.getByTestId("report-total").textContent).toContain("All sites")
    const url = get.mock.calls.map((c) => c[0]).find((u) => u.includes("/sla/report"))!
    expect(url).toMatch(/from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}$/)
  })

  it("adds the chosen site to the request", async () => {
    show()
    fireEvent.click(screen.getByRole("tab", { name: "Report" }))
    await screen.findByTestId("report-0")
    await screen.findByRole("option", { name: "Menlyn Park" })
    fireEvent.change(screen.getByLabelText("Report site"), { target: { value: "s2" } })
    await waitFor(() => expect(get.mock.calls.some((c) => c[0].includes("/sla/report") && c[0].endsWith("&siteId=s2"))).toBe(true))
  })

  it("does not ask when the range is wrong, and says why", async () => {
    show()
    fireEvent.click(screen.getByRole("tab", { name: "Report" }))
    await screen.findByTestId("report-0")
    get.mockClear()
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2020-01-01" } })
    expect((await screen.findByRole("alert")).textContent).toContain("before")
    expect(get.mock.calls.some((c) => c[0].includes("/sla/report"))).toBe(false)
  })
})

describe("Service levels: targets", () => {
  it("shows the company default and site targets", async () => {
    await openTargets()
    expect(screen.getByTestId("target-company-HIGH").textContent).toContain("10 min")
    expect(screen.getByTestId("target-s1-CRITICAL").textContent).toContain("1 h")
  })

  it("will not save wrong minutes", async () => {
    await openTargets()
    fireEvent.change(screen.getByLabelText("Acknowledge minutes"), { target: { value: "60" } })
    fireEvent.change(screen.getByLabelText("Resolve minutes"), { target: { value: "30" } })
    fireEvent.click(screen.getByText("Save target"))
    expect((await screen.findByRole("alert")).textContent).toContain("cannot be shorter")
    expect(put).not.toHaveBeenCalled()
  })

  it("saves a site target with the minutes as numbers, and a company default with no site", async () => {
    await openTargets()
    await screen.findByRole("option", { name: "Menlyn Park" })
    fireEvent.change(screen.getByLabelText("Applies to"), { target: { value: "s2" } })
    fireEvent.change(screen.getByLabelText("Severity"), { target: { value: "LOW" } })
    fireEvent.change(screen.getByLabelText("Acknowledge minutes"), { target: { value: "30" } })
    fireEvent.change(screen.getByLabelText("Resolve minutes"), { target: { value: "300" } })
    fireEvent.click(screen.getByText("Save target"))
    await waitFor(() => expect(put).toHaveBeenCalledTimes(1))
    expect(put.mock.calls[0][0]).toBe("/api/v1/security/sla/targets")
    expect(put.mock.calls[0][1]).toEqual({ siteId: "s2", severity: "LOW", acknowledgeMinutes: 30, resolveMinutes: 300 })
    fireEvent.change(screen.getByLabelText("Applies to"), { target: { value: "" } })
    fireEvent.change(screen.getByLabelText("Acknowledge minutes"), { target: { value: "15" } })
    fireEvent.change(screen.getByLabelText("Resolve minutes"), { target: { value: "90" } })
    fireEvent.click(screen.getByText("Save target"))
    await waitFor(() => expect(put).toHaveBeenCalledTimes(2))
    expect(put.mock.calls[1][1]).toEqual({ siteId: null, severity: "LOW", acknowledgeMinutes: 15, resolveMinutes: 90 })
  })

  it("shows the server's words when saving is refused", async () => {
    put.mockImplementationOnce(() => Promise.reject({ response: { data: { message: "Only the whole company can do this." } } }))
    await openTargets()
    fireEvent.change(screen.getByLabelText("Acknowledge minutes"), { target: { value: "10" } })
    fireEvent.change(screen.getByLabelText("Resolve minutes"), { target: { value: "20" } })
    fireEvent.click(screen.getByText("Save target"))
    expect((await screen.findByRole("alert")).textContent).toContain("whole company")
  })

  it("removes a site target and a company default", async () => {
    await openTargets()
    fireEvent.click(screen.getByTestId("target-s1-CRITICAL").querySelector("button")!)
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/security/sla/targets?severity=CRITICAL&siteId=s1"))
    fireEvent.click(screen.getByTestId("target-company-HIGH").querySelector("button")!)
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/security/sla/targets?severity=HIGH"))
  })

  it("is read only without the manage permission", async () => {
    manage = false
    await openTargets()
    expect(screen.queryByText("Save target")).toBeNull()
    expect(screen.queryByText("Remove")).toBeNull()
  })

  it("is read only for a user limited to some sites, with a note", async () => {
    scope = "limited"
    show()
    fireEvent.click(screen.getByRole("tab", { name: "Targets" }))
    await screen.findByTestId("target-company-HIGH")
    await waitFor(() => expect(screen.queryByText("Save target")).toBeNull())
    expect(screen.queryByText("Remove")).toBeNull()
    expect((await screen.findByTestId("limited-note")).textContent).toContain("whole company")
  })
})
