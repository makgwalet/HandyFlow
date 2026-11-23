import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const card = (key: string, title: string, scope: string) => ({ key, title, description: `${title} description`, scope, lastRun: null })
const catalogue = { cards: [card("monthly-summary", "Monthly summary", "NONE"), card("site-coverage", "Site coverage", "SITE"), card("guard-attendance", "Guard attendance", "GUARD"), card("site-access", "Site access", "SITE")], recent: [] }
const summary = { month: "2026-09", totalShifts: 40, completedShifts: 38, missedShifts: 2, totalGuardHours: 320, overallCompletionRatePct: 95, totalIncidents: 3, incidentsBySeverity: {}, activeGuards: 12, siteSummaries: [] }
let myScope: "limited" | "whole" | "fails" = "whole"
let pdfFails = false
const get = vi.fn((url: string) => {
  if (url.includes("/my-site-scope")) return myScope === "fails" ? Promise.reject(new Error("boom")) : Promise.resolve({ data: { data: { restricted: myScope === "limited" } } })
  if (url.includes("/reports/catalogue")) return Promise.resolve({ data: { data: catalogue } })
  if (url.includes("/monthly-summary/pdf")) return pdfFails ? Promise.reject(new Error("403")) : Promise.resolve({ data: new Blob(["x"]) })
  if (url.includes("/reports/monthly-summary")) return Promise.resolve({ data: { data: summary } })
  if (url.includes("/sites")) return Promise.resolve({ data: { data: { content: [{ id: "s1", name: "Centurion Mall" }] } } })
  return Promise.resolve({ data: { data: { content: [{ id: "g1", fullName: "Ann Botha" }] } } })
})
vi.mock("../../api/client", () => ({ apiClient: { get: (url: string) => get(url) } }))
import ReportsTab from "./ReportsTab"

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ReportsTab /></QueryClientProvider>)
afterEach(() => { cleanup(); get.mockClear(); myScope = "whole"; pdfFails = false })

async function generateSummary() {
  await screen.findByTestId("report-card-monthly-summary")
  fireEvent.click(screen.getByText("Generate Report"))
  await screen.findByText("Total Shifts")
}

describe("Reports for a user limited to some sites", () => {
  it("says the monthly summary is for their sites and explains the two limits", async () => {
    myScope = "limited"; show()
    expect(await screen.findByText("Monthly Summary (your sites)")).toBeTruthy()
    expect((await screen.findByTestId("limited-note")).textContent).toContain("Guard attendance")
  })

  it("cannot choose guard attendance: the card and the option are off", async () => {
    myScope = "limited"; show()
    await screen.findByText("Monthly Summary (your sites)")
    const c = (await screen.findByTestId("report-card-guard-attendance")) as HTMLButtonElement
    expect(c.disabled).toBe(true)
    fireEvent.click(c)
    expect(screen.getByTestId("report-card-monthly-summary").getAttribute("aria-pressed")).toBe("true")
    expect(c.getAttribute("aria-pressed")).toBe("false")
    expect((screen.getByText("Guard Attendance") as HTMLOptionElement).disabled).toBe(true)
  })

  it("can still choose the site reports", async () => {
    myScope = "limited"; show()
    await screen.findByText("Monthly Summary (your sites)")
    fireEvent.click(await screen.findByTestId("report-card-site-coverage"))
    expect(screen.getByTestId("report-card-site-coverage").getAttribute("aria-pressed")).toBe("true")
  })

  it("gets the monthly summary figures but no PDF button for it", async () => {
    myScope = "limited"; show()
    await screen.findByText("Monthly Summary (your sites)")
    await generateSummary()
    expect(screen.queryByText("Download PDF")).toBeNull()
  })
})

describe("Reports for the whole company", () => {
  it("is unchanged: all sites, no note, a PDF button, guard attendance open", async () => {
    show()
    expect(await screen.findByText("Monthly Summary (all sites)")).toBeTruthy()
    expect(screen.queryByTestId("limited-note")).toBeNull()
    expect(((await screen.findByTestId("report-card-guard-attendance")) as HTMLButtonElement).disabled).toBe(false)
    await generateSummary()
    expect(screen.getByText("Download PDF")).toBeTruthy()
  })

  it("treats a failed scope look-up as the whole company, never as limited", async () => {
    myScope = "fails"; show()
    expect(await screen.findByText("Monthly Summary (all sites)")).toBeTruthy()
    expect(((await screen.findByTestId("report-card-guard-attendance")) as HTMLButtonElement).disabled).toBe(false)
    expect(screen.queryByTestId("limited-note")).toBeNull()
  })

  it("says so when the PDF cannot be downloaded", async () => {
    pdfFails = true; show()
    await screen.findByText("Monthly Summary (all sites)")
    await generateSummary()
    fireEvent.click(screen.getByText("Download PDF"))
    expect((await screen.findByRole("alert")).textContent).toContain("The PDF could not be downloaded")
  })

  it("clears that message when another report is chosen", async () => {
    pdfFails = true; show()
    await screen.findByText("Monthly Summary (all sites)")
    await generateSummary()
    fireEvent.click(screen.getByText("Download PDF"))
    await screen.findByRole("alert")
    fireEvent.click(await screen.findByTestId("report-card-site-coverage"))
    await waitFor(() => expect(screen.queryByRole("alert")).toBeNull())
  })
})
