import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ScoringRoutingPanel from "./ScoringRoutingPanel"
import PipelineBoard from "./PipelineBoard"
import { ConditionEditor } from "./ConditionEditor"
import { NO_CONDITION, conditionBody, conditionProblem, movedOrder, opsFor, pointsProblem, routeResultText, routingBody, routingProblem, scoreMap, scoringBody, scoringProblem, withField, EMPTY_ROUTING, EMPTY_SCORING, type ScoringRule, type RoutingRule } from "./rules.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), patch = vi.fn(), del = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), patch: (...a: unknown[]) => patch(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: { user: { userId: string } }) => unknown) => sel({ user: { userId: "me" } }) }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
beforeEach(() => { perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"]); for (const m of [get, post, put, patch, del]) m.mockReset(); for (const m of [post, put, del]) m.mockResolvedValue({ data: { data: {} } }) })
afterEach(cleanup)

const sRule: ScoringRule = { id: "s1", name: "Came from website", field: "SOURCE", op: "EQ", value: "WEBSITE", points: 20, active: true, description: "Source is Website" }
const rRule = (id: string, name: string, over: Partial<RoutingRule> = {}): RoutingRule => ({ id, name, position: 1, field: "SOURCE", op: "EQ", value: "WEBSITE", description: "Source is Website", assigneeIds: ["u1", "u2"], active: true, ...over })
const users = { content: [{ id: "u1", firstName: "Olive", lastName: "Other" }, { id: "u2", firstName: "Sam", lastName: "Seller" }] }
const serve = (s: ScoringRule[], r: RoutingRule[]) => get.mockImplementation((url: string) => ok(url.includes("scoring") ? s : url.includes("routing") ? r : users))

describe("rule logic", () => {
  it("checks a condition", () => {
    expect(conditionProblem(NO_CONDITION)).toBe("Choose what to check."); expect(conditionProblem(NO_CONDITION, true)).toBeNull()
    expect(conditionProblem(withField("SOURCE"))).toBe("Give the value to compare with."); expect(conditionProblem({ field: "SOURCE", op: "EQ", value: "WEBSITE" })).toBeNull()
    expect(conditionProblem({ field: "DEAL_VALUE", op: "GTE", value: "abc" })).toBe("The deal value must be a number."); expect(conditionProblem({ field: "DEAL_VALUE", op: "GTE", value: "-1" })).toBe("The deal value cannot be negative.")
    expect(conditionProblem({ field: "TAG", op: "EQ", value: "x".repeat(101) })).toBe("The value can be at most 100 characters.")
    expect(opsFor("DEAL_VALUE").map(o => o.id)).toEqual(["GTE", "LTE"]); expect(opsFor("TAG").map(o => o.id)).toEqual(["EQ"]); expect(withField("DEAL_VALUE").op).toBe("GTE")
  })
  it("builds condition bodies", () => {
    expect(conditionBody(NO_CONDITION)).toEqual({ field: null, op: null, value: null }); expect(conditionBody({ field: "DEAL_VALUE", op: "GTE", value: " 50 000 " })).toEqual({ field: "DEAL_VALUE", op: "GTE", value: "50000" })
    expect(conditionBody({ field: "TAG", op: "", value: " vip " })).toEqual({ field: "TAG", op: "EQ", value: "vip" })
  })
  it("checks points and scoring rules", () => {
    expect(pointsProblem("")).toBe("Enter the points."); expect(pointsProblem("1.5")).toBe("Points must be a whole number."); expect(pointsProblem("0")).toBe("Points must be between -100 and 100, and not zero."); expect(pointsProblem("101")).toBe("Points must be between -100 and 100, and not zero."); expect(pointsProblem("-100")).toBeNull()
    const f = { ...EMPTY_SCORING, name: "Big", condition: { field: "DEAL_VALUE" as const, op: "GTE" as const, value: "50000" } }
    expect(scoringProblem(f)).toBeNull(); expect(scoringProblem({ ...f, name: " " })).toBe("Name the scoring rule."); expect(scoringProblem({ ...f, condition: NO_CONDITION })).toBe("Choose what to check.")
    expect(scoringBody(f)).toEqual({ name: "Big", field: "DEAL_VALUE", op: "GTE", value: "50000", points: 10, active: true })
  })
  it("checks routing rules", () => {
    const f = { ...EMPTY_ROUTING, name: "Web", assigneeIds: ["u1"] }
    expect(routingProblem(f)).toBeNull(); expect(routingProblem({ ...f, assigneeIds: [] })).toBe("Choose at least one person to receive the leads."); expect(routingProblem({ ...f, name: "" })).toBe("Name the routing rule.")
    expect(routingProblem({ ...f, condition: { field: "SOURCE", op: "EQ", value: "" } })).toBe("Give the value to compare with."); expect(routingProblem({ ...f, assigneeIds: Array.from({ length: 21 }, (_, i) => String(i)) })).toBe("A rule can have at most 20 people.")
    expect(routingBody(f)).toEqual({ name: "Web", field: null, op: null, value: null, assigneeIds: ["u1"], active: true })
  })
  it("reorders, summarises runs and maps scores", () => {
    expect(movedOrder(["a", "b", "c"], "b", -1)).toEqual(["b", "a", "c"]); expect(movedOrder(["a", "b", "c"], "a", -1)).toEqual(["a", "b", "c"]); expect(movedOrder(["a", "b", "c"], "c", 1)).toEqual(["a", "b", "c"]); expect(movedOrder(["a"], "x", 1)).toEqual(["a"])
    expect(routeResultText({ routed: 0, left: 0 })).toMatch(/already has an owner/); expect(routeResultText({ routed: 1, left: 0 })).toBe("1 lead was given an owner."); expect(routeResultText({ routed: 3, left: 2 })).toBe("3 leads were given an owner. 2 still have none: no rule fits them.")
    expect(scoreMap([{ customerId: "a", score: 5, band: "COLD", reasons: [] }, { score: 1 } as never]).size).toBe(1)
  })
})

describe("ConditionEditor", () => {
  it("shows choices for source and a number box for deal value", () => {
    let value = NO_CONDITION; const change = vi.fn((c: typeof value) => { value = c })
    const { rerender } = render(<ConditionEditor value={value} onChange={change} />)
    fireEvent.change(screen.getByLabelText("Condition field"), { target: { value: "SOURCE" } }); expect(change).toHaveBeenLastCalledWith({ field: "SOURCE", op: "EQ", value: "" })
    rerender(<ConditionEditor value={value} onChange={change} />); expect(within(screen.getByLabelText("Condition value")).getByText("Website")).toBeTruthy()
    rerender(<ConditionEditor value={{ field: "DEAL_VALUE", op: "GTE", value: "" }} onChange={change} />); expect((screen.getByLabelText("Condition value") as HTMLInputElement).placeholder).toBe("Amount in R"); expect(screen.getByLabelText("Condition comparison").textContent).toContain("is at least")
  })
})

describe("ScoringRoutingPanel", () => {
  it("lists both kinds of rules with people's names", async () => {
    serve([sRule], [rRule("r1", "Web leads")]); wrap(<ScoringRoutingPanel />)
    const s = await screen.findByLabelText("Came from website"); expect(s.textContent).toContain("+20"); expect(s.textContent).toContain("Source is Website")
    const r = await screen.findByLabelText("Web leads"); await waitFor(() => expect(r.textContent).toContain("Olive Other, Sam Seller"))
  })
  it("adds a scoring rule once it is complete", async () => {
    serve([], []); wrap(<ScoringRoutingPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Add scoring rule" })); const form = screen.getByLabelText("Scoring rule"); const save = within(form).getByRole("button", { name: "Save rule" }) as HTMLButtonElement; expect(save.disabled).toBe(true)
    fireEvent.change(within(form).getByLabelText("Rule name"), { target: { value: "Big deal" } }); expect(within(form).getByText("Choose what to check.")).toBeTruthy()
    fireEvent.change(within(form).getByLabelText("Scoring condition field"), { target: { value: "DEAL_VALUE" } }); fireEvent.change(within(form).getByLabelText("Scoring condition value"), { target: { value: "50000" } }); fireEvent.change(within(form).getByLabelText("Points"), { target: { value: "30" } })
    expect(save.disabled).toBe(false); fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/scoring/rules", { name: "Big deal", field: "DEAL_VALUE", op: "GTE", value: "50000", points: 30, active: true }))
  })
  it("turns a rule off and deletes one", async () => {
    serve([sRule], []); wrap(<ScoringRoutingPanel />)
    fireEvent.click(await screen.findByLabelText("Turn off Came from website")); await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/scoring/rules/s1", expect.objectContaining({ active: false, points: 20 })))
    fireEvent.click(screen.getByLabelText("Delete Came from website")); await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/crm/scoring/rules/s1"))
  })
  it("shows the server's reason when a rule is refused", async () => {
    serve([], []); post.mockRejectedValue({ response: { data: { message: "At most 50 scoring rules" } } }); wrap(<ScoringRoutingPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Add scoring rule" })); const form = screen.getByLabelText("Scoring rule")
    fireEvent.change(within(form).getByLabelText("Rule name"), { target: { value: "x" } }); fireEvent.change(within(form).getByLabelText("Scoring condition field"), { target: { value: "TAG" } }); fireEvent.change(within(form).getByLabelText("Scoring condition value"), { target: { value: "vip" } })
    fireEvent.click(within(form).getByRole("button", { name: "Save rule" })); expect((await screen.findByRole("alert")).textContent).toContain("At most 50")
  })
  it("adds a routing rule for a chosen team", async () => {
    serve([], []); wrap(<ScoringRoutingPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Add routing rule" })); const form = screen.getByLabelText("Routing rule")
    fireEvent.change(within(form).getByLabelText("Rule name"), { target: { value: "Everyone" } }); expect(within(form).getByText("Choose at least one person to receive the leads.")).toBeTruthy()
    fireEvent.click(await within(form).findByLabelText("Olive Other")); fireEvent.click(within(form).getByLabelText("Sam Seller")); fireEvent.click(within(form).getByRole("button", { name: "Save rule" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/routing/rules", { name: "Everyone", field: null, op: null, value: null, assigneeIds: ["u1", "u2"], active: true }))
  })
  it("reorders routing rules and disables the end buttons", async () => {
    serve([], [rRule("r1", "First"), rRule("r2", "Second")]); wrap(<ScoringRoutingPanel />)
    expect((await screen.findByLabelText("Move First up") as HTMLButtonElement).disabled).toBe(true); expect((screen.getByLabelText("Move Second down") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByLabelText("Move Second up")); await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/routing/order", { ids: ["r2", "r1"] }))
  })
  it("routes unassigned leads and says what happened", async () => {
    serve([], [rRule("r1", "Web leads")]); post.mockResolvedValue({ data: { data: { routed: 3, left: 1 } } }); wrap(<ScoringRoutingPanel />)
    await screen.findByLabelText("Web leads"); fireEvent.click(screen.getByRole("button", { name: "Route unassigned leads now" })); expect((await screen.findByRole("status")).textContent).toBe("3 leads were given an owner. 1 still has none: no rule fits it.")
    expect(post).toHaveBeenCalledWith("/api/v1/crm/routing/run")
  })
  it("is read-only without edit permission", async () => {
    perms = new Set(["CUSTOMER_READ"]); serve([sRule], [rRule("r1", "Web leads")]); wrap(<ScoringRoutingPanel />)
    await screen.findByLabelText("Came from website"); expect(screen.queryByRole("button")).toBeNull()
  })
})

describe("score badge on the pipeline board", () => {
  it("shows hot, warm or cold with the reasons", async () => {
    const lead = { id: "l1", name: "Acme", email: null, phone: null, ownerId: null, dealValue: null, expectedCloseDate: null, pipelineStage: "NEW" }
    get.mockImplementation((url: string) => ok(url.includes("scoring") ? [{ customerId: "l1", score: 72, band: "HOT", reasons: ["Big deal (+50)", "Came from website (+22)"] }] : [lead]))
    wrap(<PipelineBoard />)
    const badge = await screen.findByLabelText("Score for Acme"); expect(badge.textContent).toBe("Hot 72"); expect(badge.getAttribute("title")).toContain("Big deal (+50)")
  })
  it("still shows the board when scores cannot be loaded", async () => {
    const lead = { id: "l1", name: "Acme", email: null, phone: null, ownerId: null, dealValue: null, expectedCloseDate: null, pipelineStage: "NEW" }
    get.mockImplementation((url: string) => url.includes("scoring") ? Promise.reject(new Error("no")) : ok([lead]))
    wrap(<PipelineBoard />); expect(await screen.findByLabelText("Acme")).toBeTruthy(); expect(screen.queryByLabelText("Score for Acme")).toBeNull(); expect(screen.queryByRole("alert")).toBeNull()
  })
})
