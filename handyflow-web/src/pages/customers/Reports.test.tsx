import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ReportsPanel from "./ReportsPanel"
import { EMPTY_REPORT, lastSentText, recipientText, reportBody, reportProblem, sentToText, toReportForm, toggledId, type ReportSchedule } from "./reports.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
beforeEach(() => { perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"]); for (const m of [get, post, put, del]) m.mockReset(); for (const m of [post, put, del]) m.mockResolvedValue({ data: { data: {} } }) })
afterEach(cleanup)

const sch = (over: Partial<ReportSchedule> = {}): ReportSchedule => ({ id: "s1", name: "Monday pipeline", reportType: "PIPELINE", frequency: "WEEKLY", recipientIds: [], active: true, lastSentAt: null, description: "Pipeline summary, every Monday", ...over })
const users = { content: [{ id: "u1", firstName: "Olive", lastName: "Other" }, { id: "u2", firstName: "Sam", lastName: "Seller" }] }
const serve = (s: ReportSchedule[]) => get.mockImplementation((url: string) => ok(url.includes("preview") ? { title: "Pipeline summary: 3 open deals", body: "Open deals: 3 worth R 5 000." } : url.includes("report-schedules") ? s : users))

describe("report logic", () => {
  const f = { ...EMPTY_REPORT, name: "Weekly", reportType: "PIPELINE" as const }
  it("checks the form", () => {
    expect(reportProblem(EMPTY_REPORT)).toBe("Name the report."); expect(reportProblem({ ...EMPTY_REPORT, name: "x" })).toBe("Choose which report to send.")
    expect(reportProblem(f)).toBeNull(); expect(reportProblem({ ...f, frequency: "" })).toBe("Choose how often to send it.")
    expect(reportProblem({ ...f, name: "x".repeat(121) })).toBe("The name can be at most 120 characters.")
    expect(reportProblem({ ...f, recipientIds: Array.from({ length: 21 }, (_, i) => String(i)) })).toBe("A report can go to at most 20 people.")
  })
  it("builds the body and round-trips", () => {
    expect(reportBody({ ...f, name: " Weekly ", recipientIds: ["u1"] })).toEqual({ name: "Weekly", reportType: "PIPELINE", frequency: "WEEKLY", recipientIds: ["u1"], active: true })
    expect(toReportForm(sch({ recipientIds: ["u2"] }))).toEqual({ name: "Monday pipeline", reportType: "PIPELINE", frequency: "WEEKLY", recipientIds: ["u2"], active: true })
  })
  it("words things for people", () => {
    expect(toggledId(["a"], "b")).toEqual(["a", "b"]); expect(toggledId(["a", "b"], "a")).toEqual(["b"])
    const names = new Map([["u1", "Olive Other"]])
    expect(recipientText([], names)).toBe("the account's admins"); expect(recipientText(["u1", "zz"], names)).toBe("Olive Other, Someone")
    expect(lastSentText(null)).toBe("Not sent yet"); expect(lastSentText("nonsense")).toBe("Not sent yet"); expect(lastSentText("2026-10-19T04:00:00Z")).toMatch(/^Last sent /)
    expect(sentToText(1)).toBe("Sent to 1 person."); expect(sentToText(3)).toBe("Sent to 3 people.")
  })
})

describe("ReportsPanel", () => {
  it("lists schedules with who gets them and when they last went out", async () => {
    serve([sch(), sch({ id: "s2", name: "Source check", reportType: "SOURCES", recipientIds: ["u2"], active: false, lastSentAt: "2026-10-19T04:00:00Z", description: "Lead sources, every Monday" })])
    wrap(<ReportsPanel />)
    const a = await screen.findByLabelText("Monday pipeline")
    expect(within(a).getByText(/To the account's admins\. Not sent yet\./)).toBeTruthy()
    const b = screen.getByLabelText("Source check")
    expect(await within(b).findByText(/To Sam Seller\. Last sent/)).toBeTruthy(); expect(within(b).getByText("(off)")).toBeTruthy()
  })
  it("says so when nothing is scheduled", async () => { serve([]); wrap(<ReportsPanel />); expect(await screen.findByText("No reports are scheduled.")).toBeTruthy() })
  it("schedules a report for chosen people", async () => {
    serve([])
    wrap(<ReportsPanel />)
    fireEvent.click(await screen.findByText("Schedule a report"))
    const save = screen.getByText("Save report") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Report name"), { target: { value: "Sources monthly" } })
    fireEvent.change(screen.getByLabelText("Report"), { target: { value: "SOURCES" } })
    fireEvent.change(screen.getByLabelText("How often"), { target: { value: "MONTHLY" } })
    fireEvent.click(await screen.findByLabelText("Sam Seller"))
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/report-schedules", { name: "Sources monthly", reportType: "SOURCES", frequency: "MONTHLY", recipientIds: ["u2"], active: true }))
  })
  it("previews a report with today's numbers", async () => {
    serve([])
    wrap(<ReportsPanel />)
    fireEvent.click(await screen.findByText("Schedule a report"))
    const btn = screen.getByText("Preview with today's numbers") as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Report"), { target: { value: "PIPELINE" } })
    fireEvent.click(btn)
    expect((await screen.findByLabelText("Report preview")).textContent).toContain("Open deals: 3 worth R 5 000.")
    expect(get).toHaveBeenCalledWith("/api/v1/crm/report-schedules/preview", { params: { type: "PIPELINE" } })
  })
  it("sends now, turns off, edits and deletes", async () => {
    serve([sch()])
    post.mockResolvedValue({ data: { data: { recipients: 2 } } })
    wrap(<ReportsPanel />)
    fireEvent.click(await screen.findByLabelText("Send Monday pipeline now"))
    expect(await screen.findByText("Sent to 2 people.")).toBeTruthy(); expect(post).toHaveBeenCalledWith("/api/v1/crm/report-schedules/s1/send")
    fireEvent.click(screen.getByLabelText("Turn off Monday pipeline"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/report-schedules/s1", expect.objectContaining({ active: false, reportType: "PIPELINE" })))
    fireEvent.click(screen.getByLabelText("Edit Monday pipeline"))
    fireEvent.change(screen.getByLabelText("How often"), { target: { value: "MONTHLY" } })
    fireEvent.click(screen.getByText("Save report"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/report-schedules/s1", expect.objectContaining({ frequency: "MONTHLY" })))
    fireEvent.click(screen.getByLabelText("Delete Monday pipeline"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/crm/report-schedules/s1"))
  })
  it("hides editing without permission and shows a server refusal", async () => {
    perms = new Set(["CUSTOMER_READ"]); serve([sch()])
    wrap(<ReportsPanel />)
    await screen.findByLabelText("Monday pipeline")
    expect(screen.queryByText("Schedule a report")).toBeNull(); expect(screen.queryByLabelText("Send Monday pipeline now")).toBeNull()
    cleanup(); perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"]); serve([sch()])
    post.mockRejectedValue({ response: { data: { message: "Nobody could be found to send the report to" } } })
    wrap(<ReportsPanel />)
    fireEvent.click(await screen.findByLabelText("Send Monday pipeline now"))
    expect((await screen.findByRole("alert")).textContent).toContain("Nobody could be found")
  })
})
