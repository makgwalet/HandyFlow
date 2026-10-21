import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import PipelineBoard from "./PipelineBoard"
import FunnelReport from "./FunnelReport"
import { canConvert, LEAD_SOURCES, sourceLabel, isConverted, allowedMoves, lostProblem, reasonLabel, stageBody, barShare, biggestDropOff, closeState, closeText, columns, dealBody, dealProblem, daysText, filterLeads, localToday, NO_FILTER, ownerText, percent, pipelineTotals, stageOf, type Funnel, type PipelineLead } from "./pipeline.logic"

const get = vi.fn(), patch = vi.fn(), post = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), patch: (...a: Args) => patch(...a), post: (...a: Args) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: { user: { userId: string } }) => unknown) => sel({ user: { userId: "me" } }) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const TODAY = "2026-10-20"
const lead = (id: string, name: string, o: Partial<PipelineLead> = {}): PipelineLead => ({ id, name, email: `${id}@x.co`, phone: null, ownerId: null, dealValue: null, expectedCloseDate: null, pipelineStage: "NEW", ...o })
const leads: PipelineLead[] = [
  lead("a", "Acme Mining", { pipelineStage: "QUALIFIED", dealValue: 50000, expectedCloseDate: "2026-10-10", ownerId: "me" }),
  lead("b", "Bravo Foods", { pipelineStage: "NEW", dealValue: 12000.5, expectedCloseDate: "2026-10-25", ownerId: "other" }),
  lead("c", "Cape Wines", { pipelineStage: "WON", dealValue: 80000, expectedCloseDate: "2026-09-01" }),
  lead("d", "Delta Haulage", { pipelineStage: "LOST", dealValue: 9000 }),
  lead("e", "Echo Retail", { pipelineStage: null }),
]
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
beforeEach(() => { perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"]); get.mockReset(); patch.mockReset(); post.mockReset(); post.mockResolvedValue({ data: {} }); get.mockImplementation(() => ok(leads)); patch.mockResolvedValue({ data: {} }); vi.useRealTimers() })
afterEach(cleanup)

describe("pipeline rules", () => {
  it("reads a missing stage as New, and judges overdue only for open leads", () => {
    expect(stageOf(leads[4])).toBe("NEW")
    expect(closeState(leads[0], TODAY)).toBe("overdue"); expect(closeState(leads[1], TODAY)).toBe("soon"); expect(closeState(lead("x", "X", { expectedCloseDate: "2027-03-01" }), TODAY)).toBe("later")
    expect(closeState(leads[2], TODAY)).toBe("none"); expect(closeState(leads[4], TODAY)).toBe("none")
    expect(closeText(leads[0], TODAY)).toBe("Overdue by 10 days"); expect(closeText(leads[1], TODAY)).toBe("Closes in 5 days"); expect(closeText(lead("y", "Y", { expectedCloseDate: TODAY }), TODAY)).toBe("Closes today")
    expect(closeText(leads[4], TODAY)).toBe("No close date"); expect(closeText(leads[2], TODAY)).toBe("Close 2026-09-01")
  })
  it("uses the local calendar date, not the UTC one", () => {
    expect(localToday(new Date(2026, 9, 20, 23, 30))).toBe("2026-10-20"); expect(localToday(new Date(2026, 0, 5, 0, 5))).toBe("2026-01-05")
  })
  it("groups by stage, soonest close date first, with counts and values", () => {
    const cols = columns(leads), by = Object.fromEntries(cols.map(c => [c.stage, c]))
    expect(cols.map(c => c.stage)).toEqual(["NEW", "CONTACTED", "QUALIFIED", "WON", "LOST"]); expect(by.NEW.leads.map(l => l.id)).toEqual(["b", "e"]); expect(by.NEW.value).toBe(12000.5)
    expect(by.CONTACTED.count).toBe(0); expect(by.WON.value).toBe(80000)
  })
  it("totals open, won, lost, overdue and open leads without a value", () => {
    expect(pipelineTotals(leads, TODAY)).toEqual({ openCount: 3, openValue: 62000.5, wonValue: 80000, lostValue: 9000, overdue: 1, noValue: 1 })
  })
  it("filters by text, owner and overdue", () => {
    const ids = (f: Partial<typeof NO_FILTER>) => filterLeads(leads, { ...NO_FILTER, ...f }, "me", TODAY).map(l => l.id)
    expect(ids({ search: "  ACME " })).toEqual(["a"]); expect(ids({ search: "b@x" })).toEqual(["b"]); expect(ids({ owner: "mine" })).toEqual(["a"]); expect(ids({ owner: "unassigned" })).toEqual(["c", "d", "e"]); expect(ids({ overdueOnly: true })).toEqual(["a"])
    expect(filterLeads(leads, { ...NO_FILTER, owner: "mine" }, null, TODAY)).toEqual([]); expect(ownerText(null, "me")).toBe("Unassigned"); expect(ownerText("me", "me")).toBe("You"); expect(ownerText("z", "me")).toBe("Colleague")
  })
  it("validates a deal and sends both fields, an empty box clearing it", () => {
    expect(dealProblem("abc", "")).toMatch(/number/); expect(dealProblem("-5", "")).toMatch(/negative/); expect(dealProblem("1e15", "")).toMatch(/too large/); expect(dealProblem("100", "2026-02-30")).toMatch(/real date/); expect(dealProblem("", "")).toBeNull(); expect(dealProblem("1 500.50", "2026-12-01")).toBeNull()
    expect(dealBody("1 500.505", "2026-12-01")).toEqual({ dealValue: 1500.51, expectedCloseDate: "2026-12-01" }); expect(dealBody("", "")).toEqual({ dealValue: null, expectedCloseDate: null })
  })
})

describe("pipeline board", () => {
  it("shows leads in their stage columns with totals, and flags the overdue one", async () => {
    wrap(<PipelineBoard />)
    const q = await screen.findByLabelText("Qualified column"); expect(within(q).getByLabelText("Acme Mining").textContent).toContain("Overdue by")
    expect(within(screen.getByLabelText("New column")).getByLabelText("Echo Retail")).toBeTruthy(); expect(screen.getByLabelText("Pipeline totals").textContent).toMatch(/3\s*open leads/); expect(screen.getByLabelText("Pipeline totals").textContent).toContain("past their close date")
  })
  it("moves a lead with the stage endpoint", async () => {
    wrap(<PipelineBoard />); fireEvent.change(await screen.findByLabelText("Move Bravo Foods"), { target: { value: "CONTACTED" } })
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/crm/customers/b/stage", { stage: "CONTACTED" }))
  })
  it("assigns to me and unassigns, using the signed-in user's id", async () => {
    wrap(<PipelineBoard />); fireEvent.click(await screen.findByLabelText("Assign Bravo Foods to me")); await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/crm/customers/b/owner", { ownerId: "me" }))
    fireEvent.click(screen.getByLabelText("Unassign Acme Mining")); await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/crm/customers/a/owner", { ownerId: null }))
  })
  it("edits a deal, blocks a bad value, and saves value and date together", async () => {
    wrap(<PipelineBoard />); fireEvent.click(await screen.findByLabelText("Edit deal for Bravo Foods"))
    const form = screen.getByRole("form", { name: "Deal for Bravo Foods" }); fireEvent.change(within(form).getByLabelText("Deal value"), { target: { value: "x" } })
    expect(within(form).getByText(/must be a number/)).toBeTruthy(); expect((within(form).getByText("Save deal") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(within(form).getByLabelText("Deal value"), { target: { value: "15000" } }); fireEvent.change(within(form).getByLabelText("Expected close date"), { target: { value: "2026-11-30" } }); fireEvent.click(within(form).getByText("Save deal"))
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/crm/customers/b/deal", { dealValue: 15000, expectedCloseDate: "2026-11-30" }))
  })
  it("shows the server's refusal and keeps the board", async () => {
    patch.mockRejectedValue({ response: { data: { message: "Cannot set a pipeline stage on a non-lead customer" } } })
    wrap(<PipelineBoard />); fireEvent.change(await screen.findByLabelText("Move Bravo Foods"), { target: { value: "WON" } })
    expect((await screen.findByRole("alert")).textContent).toMatch(/non-lead/); expect(screen.getByLabelText("Bravo Foods")).toBeTruthy()
  })
  it("filters the board and says when nothing matches", async () => {
    wrap(<PipelineBoard />); fireEvent.change(await screen.findByLabelText("Search leads"), { target: { value: "zzz" } }); expect(screen.getByText(/No leads match/)).toBeTruthy()
    fireEvent.click(screen.getByText("Clear filters")); fireEvent.change(screen.getByLabelText("Owner filter"), { target: { value: "mine" } }); expect(screen.getByLabelText("Acme Mining")).toBeTruthy(); expect(screen.queryByLabelText("Bravo Foods")).toBeNull()
  })
  it("read-only users see the board without move, edit or assign controls", async () => {
    perms = new Set(["CUSTOMER_READ"]); wrap(<PipelineBoard />); await screen.findByLabelText("Acme Mining")
    expect(screen.queryByLabelText("Move Acme Mining")).toBeNull(); expect(screen.queryByLabelText("Edit deal for Acme Mining")).toBeNull(); expect(screen.queryByLabelText("Unassign Acme Mining")).toBeNull()
  })
  it("explains an empty pipeline and a load failure, and warns when the 500 cap is reached", async () => {
    get.mockImplementation(() => ok([])); wrap(<PipelineBoard />); expect(await screen.findByText(/No leads yet/)).toBeTruthy(); cleanup()
    get.mockRejectedValue(new Error("down")); wrap(<PipelineBoard />); expect((await screen.findByRole("alert")).textContent).toMatch(/Couldn't load/); cleanup()
    get.mockImplementation(() => ok(Array.from({ length: 500 }, (_, i) => lead(`l${i}`, `Lead ${i}`)))); wrap(<PipelineBoard />); expect((await screen.findByRole("status")).textContent).toMatch(/newest 500/)
  })
})

const funnel: Funnel = { totalLeads: 10, totalWon: 2, totalLost: 3, totalStillOpen: 5, overallConversionRate: 0.2, stages: [
  { stage: "NEW", reachedCount: 10, conversionFromPreviousStage: null, avgDaysInStage: 2.34 }, { stage: "CONTACTED", reachedCount: 8, conversionFromPreviousStage: 0.8, avgDaysInStage: 0.4 },
  { stage: "QUALIFIED", reachedCount: 3, conversionFromPreviousStage: 0.375, avgDaysInStage: null }, { stage: "WON", reachedCount: 2, conversionFromPreviousStage: 0.6667, avgDaysInStage: null }, { stage: "LOST", reachedCount: 3, conversionFromPreviousStage: null, avgDaysInStage: null } ] }
describe("funnel report", () => {
  it("formats rates and times, sizes bars against the widest stage and finds the biggest drop", () => {
    expect(percent(0.2)).toBe("20%"); expect(percent(0.6667)).toBe("66.7%"); expect(percent(null)).toBe("—"); expect(daysText(2.34)).toBe("2.3 days"); expect(daysText(0.4)).toBe("under a day"); expect(daysText(null)).toBe("—")
    expect(barShare(funnel.stages, funnel.stages[1])).toBe(80); expect(barShare([], funnel.stages[0])).toBe(0)
    expect(biggestDropOff(funnel.stages)).toEqual({ from: "CONTACTED", to: "QUALIFIED", lost: 5 }); expect(biggestDropOff([{ ...funnel.stages[0], reachedCount: 3 }, { ...funnel.stages[1], reachedCount: 3 }])).toBeNull()
  })
  it("shows the totals, the per-stage table and where leads stop", async () => {
    get.mockImplementation(() => ok(funnel)); wrap(<FunnelReport />)
    expect((await screen.findByLabelText("Win rate")).textContent).toContain("20%"); expect(screen.getByLabelText("Still open").textContent).toContain("5")
    expect(within(screen.getByLabelText("Contacted")).getByText("80%")).toBeTruthy(); expect(screen.getByRole("note").textContent).toMatch(/Contacted.*Qualified.*5 leads/)
  })
  it("says so when there are no leads and when the report fails", async () => {
    get.mockImplementation(() => ok({ ...funnel, totalLeads: 0 })); wrap(<FunnelReport />); expect(await screen.findByText(/nothing to report/)).toBeTruthy(); cleanup()
    get.mockRejectedValue(new Error("x")); wrap(<FunnelReport />); expect((await screen.findByRole("alert")).textContent).toMatch(/Couldn't load/)
  })
})

describe("lost reasons and stage rules", () => {
  it("offers only legal moves: a closed lead is reopened first", () => {
    expect(allowedMoves("NEW")).toEqual(["CONTACTED", "QUALIFIED", "WON", "LOST"]); expect(allowedMoves("WON")).toEqual(["NEW", "CONTACTED", "QUALIFIED"]); expect(allowedMoves("LOST")).toEqual(["NEW", "CONTACTED", "QUALIFIED"])
  })
  it("needs a reason to lose a lead, and a note for Other", () => {
    expect(lostProblem("", "")).toMatch(/Choose why/); expect(lostProblem("OTHER", "  ")).toMatch(/note/); expect(lostProblem("PRICE", "")).toBeNull(); expect(lostProblem("OTHER", "x".repeat(501))).toMatch(/500/)
    expect(stageBody("LOST", "PRICE", " too dear ")).toEqual({ stage: "LOST", lostReason: "PRICE", lostNote: "too dear" }); expect(stageBody("WON", "PRICE", "x")).toEqual({ stage: "WON" }); expect(reasonLabel(null)).toBe("No reason recorded"); expect(reasonLabel("PRICE")).toBe("Price too high")
  })
  it("asks why before marking a lead lost, then sends the reason", async () => {
    wrap(<PipelineBoard />); fireEvent.change(await screen.findByLabelText("Move Bravo Foods"), { target: { value: "LOST" } })
    expect(patch).not.toHaveBeenCalled(); const form = screen.getByRole("form", { name: "Mark Bravo Foods lost" }); expect((within(form).getByText("Mark lost") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(within(form).getByLabelText("Lost reason"), { target: { value: "OTHER" } }); expect(within(form).getByText(/Add a note/)).toBeTruthy()
    fireEvent.change(within(form).getByLabelText("Lost note"), { target: { value: "Went with a cousin" } }); fireEvent.click(within(form).getByText("Mark lost"))
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/crm/customers/b/stage", { stage: "LOST", lostReason: "OTHER", lostNote: "Went with a cousin" }))
  })
  it("cancelling leaves the lead where it was", async () => {
    wrap(<PipelineBoard />); fireEvent.change(await screen.findByLabelText("Move Bravo Foods"), { target: { value: "LOST" } }); fireEvent.click(screen.getByText("Cancel"))
    expect(screen.queryByRole("form", { name: "Mark Bravo Foods lost" })).toBeNull(); expect(patch).not.toHaveBeenCalled(); expect(screen.getByLabelText("Move Bravo Foods")).toBeTruthy()
  })
  it("shows why a lead was lost on its card, and a lost lead cannot be moved straight to won", async () => {
    get.mockImplementation(() => ok([{ ...leads[3], lostReason: "COMPETITOR", lostNote: "Cheaper quote" }])); wrap(<PipelineBoard />)
    const card = await screen.findByLabelText("Delta Haulage"); expect(card.textContent).toContain("Lost: Chose a competitor — Cheaper quote")
    const options = Array.from((within(card).getByLabelText("Move Delta Haulage") as HTMLSelectElement).options).map(o => o.value); expect(options).not.toContain("WON"); expect(options).toContain("NEW")
  })
  it("lists the lost reasons in the funnel report, biggest first, with legacy leads called out", async () => {
    get.mockImplementation((url: string) => ok(url.includes("lost-reasons") ? [{ reason: "PRICE", count: 3, dealValue: 90000 }, { reason: null, count: 1, dealValue: 0 }] : funnel)); wrap(<FunnelReport />)
    const t = await screen.findByLabelText("Lost reasons"); expect(within(t).getByLabelText("Price too high").textContent).toContain("3"); expect(within(t).getByLabelText("No reason recorded").textContent).toContain("1")
  })
  it("shows which sources win, with the win rate only once leads are decided", async () => {
    get.mockImplementation((url: string) => ok(url.includes("/sources") ? [{ source: "WEBSITE", leads: 4, won: 2, lost: 1, open: 1, winRate: 66.7, wonValue: 1000 }, { source: "REFERRAL", leads: 1, won: 0, lost: 0, open: 1, winRate: null, wonValue: 0 }, { source: null, leads: 2, won: 0, lost: 0, open: 2, winRate: null, wonValue: null }] : funnel)); wrap(<FunnelReport />)
    const t = await screen.findByLabelText("Lead sources")
    expect(within(t).getByLabelText("Source Website").textContent).toContain("66.7%"); expect(within(t).getByLabelText("Source Referral").textContent).toContain("—"); expect(within(t).getByLabelText("Source Not recorded").textContent).toContain("2")
  })
  it("names sources in words", () => { expect(sourceLabel("WALK_IN")).toBe("Walk-in"); expect(sourceLabel(null)).toBe("Not recorded"); expect(LEAD_SOURCES.map(x => x.id)).toContain("SOCIAL_MEDIA") })
  it("tells the person when the lost-reason report fails but still shows the funnel", async () => {
    get.mockImplementation((url: string) => (url.includes("lost-reasons") ? Promise.reject(new Error("x")) : ok(funnel))); wrap(<FunnelReport />)
    expect(await screen.findByLabelText("Win rate")).toBeTruthy(); expect((await screen.findByText(/Couldn't load the lost-deal reasons/)).textContent).toBeTruthy()
  })
})

describe("converting a won lead", () => {
  const won = { ...leads[2], customerType: "LEAD" as const }
  it("only a won lead that is still a lead can be converted", () => {
    expect(canConvert(won)).toBe(true); expect(canConvert({ ...won, customerType: "CUSTOMER" })).toBe(false); expect(canConvert(leads[0])).toBe(false); expect(isConverted({ ...won, customerType: "CUSTOMER" })).toBe(true)
  })
  it("asks to confirm, then converts through the server", async () => {
    get.mockImplementation(() => ok([won])); wrap(<PipelineBoard />)
    fireEvent.click(await screen.findByLabelText("Convert Cape Wines to customer")); expect(post).not.toHaveBeenCalled(); expect(screen.getByText(/cannot be undone/)).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Confirm convert Cape Wines")); await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/customers/c/convert"))
  })
  it("cancelling does nothing, and an open lead has no convert button", async () => {
    wrap(<PipelineBoard />); fireEvent.click(await screen.findByLabelText("Convert Cape Wines to customer")); fireEvent.click(screen.getByText("Cancel")); expect(post).not.toHaveBeenCalled()
    expect(screen.queryByLabelText("Convert Acme Mining to customer")).toBeNull()
  })
  it("a converted customer stays in the Won column, marked, with no move or convert controls", async () => {
    get.mockImplementation(() => ok([{ ...won, customerType: "CUSTOMER" }])); wrap(<PipelineBoard />)
    const card = await screen.findByLabelText("Cape Wines"); expect(card.textContent).toContain("Customer"); expect(within(screen.getByLabelText("Won column")).getByLabelText("Cape Wines")).toBeTruthy()
    expect(screen.queryByLabelText("Move Cape Wines")).toBeNull(); expect(screen.queryByLabelText("Convert Cape Wines to customer")).toBeNull()
  })
  it("shows the server's refusal", async () => {
    post.mockRejectedValue({ response: { data: { message: "Only a won lead can be converted to a customer" } } }); get.mockImplementation(() => ok([won])); wrap(<PipelineBoard />)
    fireEvent.click(await screen.findByLabelText("Convert Cape Wines to customer")); fireEvent.click(screen.getByLabelText("Confirm convert Cape Wines")); expect((await screen.findByRole("alert")).textContent).toMatch(/won lead/)
  })
})
