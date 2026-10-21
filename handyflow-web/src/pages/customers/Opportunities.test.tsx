import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import OpportunitiesPanel from "./OpportunitiesPanel"
import GroupsPanel from "./GroupsPanel"
import { CompanyGroupPanel } from "./CompanyGroupPanel"
import { OPP_STAGES, buildTree, filterOpps, NO_OPP_FILTER, oppBody, oppCloseState, oppCloseText, oppColumns, oppMoves, oppProblem, oppStageBody, oppTotals, EMPTY_OPP, type GroupNode, type Opportunity } from "./opportunities.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), patch = vi.fn(), del = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), patch: (...a: unknown[]) => patch(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const TODAY = "2026-10-21"
const opp = (id: string, stage: Opportunity["stage"], amount: number, close: string | null = null, extra: Partial<Opportunity> = {}): Opportunity =>
  ({ id, customerId: "c1", customerName: "Acme", name: id, stage, chance: 0, amount, weightedAmount: 0, expectedCloseDate: close, ownerId: null, lostReason: null, lostNote: null, notes: null, ...extra })
const list = [opp("Rollout", "PROPOSAL", 100000, "2026-10-01", { weightedAmount: 50000 }), opp("Support", "QUALIFIED", 40000, "2026-12-01", { weightedAmount: 10000 }), opp("Old deal", "WON", 30000), opp("Gone", "LOST", 5000, null, { lostReason: "PRICE" })]
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
beforeEach(() => { perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"]); get.mockReset(); post.mockReset(); put.mockReset(); patch.mockReset(); del.mockReset(); for (const m of [post, put, patch, del]) m.mockResolvedValue({ data: { data: {} } }) })
afterEach(cleanup)

describe("opportunity rules", () => {
  it("moves: closed ones are reopened before the other outcome", () => {
    expect(oppMoves("PROSPECTING")).toContain("LOST"); expect(oppMoves("PROSPECTING")).not.toContain("PROSPECTING")
    expect(oppMoves("WON")).not.toContain("LOST"); expect(oppMoves("LOST")).not.toContain("WON"); expect(oppMoves("WON")).toContain("NEGOTIATION")
    expect(OPP_STAGES).toHaveLength(6)
  })
  it("checks the form like the server", () => {
    const ok = { ...EMPTY_OPP, customerId: "c", name: "Rollout", amount: "1 000.50" }
    expect(oppProblem(ok, true)).toBeNull(); expect(oppProblem({ ...ok, customerId: "" }, true)).toBe("Choose the company."); expect(oppProblem({ ...ok, customerId: "" }, false)).toBeNull()
    expect(oppProblem({ ...ok, name: " " }, false)).toBe("Give the opportunity a name."); expect(oppProblem({ ...ok, name: "x".repeat(256) }, false)).toBe("The name can be at most 255 characters.")
    expect(oppProblem({ ...ok, amount: "abc" }, false)).toBe("The amount must be a number."); expect(oppProblem({ ...ok, amount: "-1" }, false)).toBe("The amount cannot be negative.")
    expect(oppProblem({ ...ok, amount: "1e14" }, false)).toBe("That amount is too large."); expect(oppProblem({ ...ok, expectedCloseDate: "2026-02-30" }, false)).toBe("Use a real date.")
    expect(oppProblem({ ...ok, amount: "" }, false)).toBeNull()
  })
  it("builds request bodies", () => {
    expect(oppBody({ customerId: "c", name: " Rollout ", amount: "1 000.505", expectedCloseDate: "", notes: " " }, "u1")).toEqual({ customerId: "c", name: "Rollout", amount: 1000.51, expectedCloseDate: null, ownerId: "u1", notes: null })
    expect(oppBody({ ...EMPTY_OPP, name: "x" }).amount).toBe(0)
    expect(oppStageBody("WON")).toEqual({ stage: "WON" }); expect(oppStageBody("LOST", "PRICE", " too dear ")).toEqual({ stage: "LOST", lostReason: "PRICE", lostNote: "too dear" })
  })
  it("adds up the board", () => {
    const t = oppTotals(list, TODAY)
    expect(t).toMatchObject({ openCount: 2, openValue: 140000, weighted: 60000, wonCount: 1, wonValue: 30000, lostCount: 1, overdue: 1 })
    expect(oppColumns(list).map(c => c.count)).toEqual([0, 1, 1, 0, 1, 1]); expect(oppColumns([])[0].value).toBe(0)
  })
  it("flags overdue only for open opportunities", () => {
    expect(oppCloseState(list[0], TODAY)).toBe("overdue"); expect(oppCloseState(opp("w", "WON", 1, "2020-01-01"), TODAY)).toBe("none"); expect(oppCloseState(opp("s", "QUALIFIED", 1, "2026-11-01"), TODAY)).toBe("soon")
    expect(oppCloseText(list[0], TODAY)).toBe("Overdue by 20 days"); expect(oppCloseText(opp("n", "QUALIFIED", 1), TODAY)).toBe("No close date"); expect(oppCloseText(opp("t", "QUALIFIED", 1, TODAY), TODAY)).toBe("Closes today")
  })
  it("filters by name, company and overdue", () => {
    expect(filterOpps(list, { search: "roll", overdueOnly: false }, TODAY).map(o => o.id)).toEqual(["Rollout"])
    expect(filterOpps(list, { search: "acme", overdueOnly: false }, TODAY)).toHaveLength(4); expect(filterOpps(list, { search: "", overdueOnly: true }, TODAY).map(o => o.id)).toEqual(["Rollout"])
    expect(filterOpps(list, NO_OPP_FILTER, TODAY)).toHaveLength(4)
  })
})

describe("company group tree", () => {
  const n = (id: string, name: string, parentId: string | null): GroupNode => ({ id, name, parentId, customerType: "CUSTOMER", status: "ACTIVE" })
  const mine = (id: string, c: string, stage: Opportunity["stage"], amount: number) => opp(id, stage, amount, null, { customerId: c })
  it("indents branches under their head office and rolls the totals up", () => {
    const rows = buildTree([n("b", "Acme Cape Town", "a"), n("a", "Acme HQ", null), n("c", "Acme Durban", "a"), n("z", "Zeta", null), n("d", "Durban Depot", "c")],
      [mine("1", "a", "QUALIFIED", 1000), mine("2", "b", "PROPOSAL", 2000), mine("3", "d", "WON", 500)], TODAY)
    expect(rows.map(r => [r.node.name, r.depth])).toEqual([["Acme HQ", 0], ["Acme Cape Town", 1], ["Acme Durban", 1], ["Durban Depot", 2], ["Zeta", 0]])
    expect(rows[0].group).toMatchObject({ openCount: 2, openValue: 3000, wonValue: 500 }); expect(rows[0].own.openCount).toBe(1); expect(rows[0].branches).toBe(3); expect(rows[2].branches).toBe(1); expect(rows[4].branches).toBe(0)
  })
  it("shows a company whose parent is missing at the top, and survives a loop", () => {
    expect(buildTree([n("x", "Orphan", "gone")], [], TODAY).map(r => r.depth)).toEqual([0])
    const rows = buildTree([n("a", "A", "b"), n("b", "B", "a")], [], TODAY)
    expect(rows.map(r => r.node.id).sort()).toEqual(["a", "b"])
  })
})

describe("OpportunitiesPanel", () => {
  const serve = (items: Opportunity[]) => get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("/crm/customers?") ? { content: [{ id: "c9", name: "Zulu Mining" }] } : { content: items, totalPages: 1 } } }))
  it("shows the board with totals and each card's weighted value", async () => {
    serve(list); wrap(<OpportunitiesPanel />)
    expect(await screen.findByLabelText("Proposal column")).toBeTruthy()
    const col = screen.getByLabelText("Proposal column"); expect(within(col).getByLabelText("Rollout").textContent).toContain("50% = R 50"); expect(within(col).getByLabelText("Rollout").textContent).toContain("Overdue by 20 days")
    expect(screen.getByLabelText("Opportunity totals").textContent).toContain("2 open"); expect(within(screen.getByLabelText("Lost column")).getByLabelText("Gone").textContent).toContain("Lost: Price")
  })
  it("narrows to one company when given a customerId", async () => {
    serve(list); wrap(<OpportunitiesPanel customerId="c1" />)
    await screen.findByLabelText("Proposal column"); expect(get.mock.calls[0][0]).toContain("customerId=c1"); expect(screen.queryByText("Acme")).toBeNull()
  })
  it("moves a card to another stage", async () => {
    serve(list); wrap(<OpportunitiesPanel />)
    fireEvent.change(await screen.findByLabelText("Move Rollout"), { target: { value: "NEGOTIATION" } })
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/crm/opportunities/Rollout/stage", { stage: "NEGOTIATION" }))
  })
  it("will not mark lost without a reason, then sends it", async () => {
    serve(list); wrap(<OpportunitiesPanel />)
    fireEvent.change(await screen.findByLabelText("Move Support"), { target: { value: "LOST" } })
    const form = await screen.findByLabelText("Mark Support lost"); const save = within(form).getByRole("button", { name: "Mark lost" }) as HTMLButtonElement; expect(save.disabled).toBe(true)
    fireEvent.change(within(form).getByLabelText("Lost reason"), { target: { value: "OTHER" } }); expect(within(form).getByText("Add a note saying why.")).toBeTruthy(); expect(save.disabled).toBe(true)
    fireEvent.change(within(form).getByLabelText("Lost note"), { target: { value: "Went in-house" } }); fireEvent.click(save)
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/crm/opportunities/Support/stage", { stage: "LOST", lostReason: "OTHER", lostNote: "Went in-house" }))
  })
  it("shows the server's message when a move is refused", async () => {
    serve(list); patch.mockRejectedValue({ response: { data: { message: "Reopen the opportunity before closing it as lost" } } }); wrap(<OpportunitiesPanel />)
    fireEvent.change(await screen.findByLabelText("Move Rollout"), { target: { value: "WON" } })
    expect((await screen.findByRole("alert")).textContent).toContain("Reopen the opportunity")
  })
  it("creates an opportunity for a chosen company", async () => {
    serve(list); wrap(<OpportunitiesPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "New opportunity" }))
    const form = screen.getByLabelText("New opportunity", { selector: "form" }); const submit = within(form).getByRole("button", { name: "Save opportunity" }) as HTMLButtonElement; expect(submit.disabled).toBe(true)
    fireEvent.change(within(form).getByLabelText("Search Company"), { target: { value: "zu" } })
    await within(form).findByRole("option", { name: "Zulu Mining" })
    fireEvent.change(within(form).getByRole("combobox", { name: "Company" }), { target: { value: "c9" } })
    fireEvent.change(within(form).getByLabelText("Opportunity name"), { target: { value: "Fleet tracking" } }); fireEvent.change(within(form).getByLabelText("Amount"), { target: { value: "25000" } })
    expect(submit.disabled).toBe(false); fireEvent.click(submit)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/opportunities", { customerId: "c9", name: "Fleet tracking", amount: 25000, expectedCloseDate: null, ownerId: null, notes: null }))
  })
  it("edits in place and keeps the owner", async () => {
    serve([opp("Rollout", "PROPOSAL", 100000, null, { ownerId: "u1" })]); wrap(<OpportunitiesPanel />)
    fireEvent.click(await screen.findByLabelText("Edit Rollout")); fireEvent.change(screen.getByLabelText("Amount"), { target: { value: "120000" } }); fireEvent.click(screen.getByRole("button", { name: "Save opportunity" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/opportunities/Rollout", expect.objectContaining({ amount: 120000, ownerId: "u1" })))
  })
  it("asks before deleting", async () => {
    serve(list); wrap(<OpportunitiesPanel />)
    fireEvent.click(await screen.findByLabelText("Delete Gone")); expect(del).not.toHaveBeenCalled(); fireEvent.click(screen.getByLabelText("Confirm delete Gone"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/crm/opportunities/Gone"))
  })
  it("is read-only without edit permission, and says when there are none", async () => {
    perms = new Set(["CUSTOMER_READ"]); serve(list); wrap(<OpportunitiesPanel />)
    await screen.findByLabelText("Proposal column"); expect(screen.queryByRole("button", { name: "New opportunity" })).toBeNull(); expect(screen.queryByLabelText("Move Rollout")).toBeNull()
    cleanup(); serve([]); wrap(<OpportunitiesPanel />); expect(await screen.findByText(/No opportunities yet/)).toBeTruthy()
  })
  it("switches to the list and filters", async () => {
    serve(list); wrap(<OpportunitiesPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "List" })); const t = screen.getByLabelText("Opportunities"); expect(within(t).getAllByRole("row")).toHaveLength(5)
    fireEvent.change(screen.getByLabelText("Search opportunities"), { target: { value: "support" } }); expect(within(screen.getByLabelText("Opportunities")).getAllByRole("row")).toHaveLength(2)
    fireEvent.change(screen.getByLabelText("Search opportunities"), { target: { value: "nothing" } }); expect(await screen.findByText("No opportunities match these filters.")).toBeTruthy()
  })
})

describe("CompanyGroupPanel", () => {
  const node = (id: string, name: string, parentId: string | null) => ({ id, name, parentId, customerType: "CUSTOMER", status: "ACTIVE" })
  const h = (over = {}) => ({ self: node("me", "Acme Durban", "hq"), ancestors: [node("top", "Acme Group", null), node("hq", "Acme HQ", "top")], children: [node("k", "Durban Depot", "me")], branchCount: 2,
    rollup: { companies: 3, openCount: 2, openValue: 3000, weightedValue: 1500, wonCount: 1, wonValue: 500 }, ...over })
  const serve = (data: unknown) => get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("/crm/customers?") ? { content: [{ id: "p2", name: "Other HQ" }, { id: "k", name: "Durban Depot" }] } : data } }))
  it("shows the path, branches and group roll-up", async () => {
    serve(h()); wrap(<CompanyGroupPanel customerId="me" />)
    expect((await screen.findByLabelText("Group path")).textContent).toBe("Acme Group › Acme HQ › Acme Durban"); expect(within(screen.getByLabelText("Parent company")).getByText("Acme HQ")).toBeTruthy()
    expect(screen.getByLabelText("Branches").textContent).toContain("Durban Depot"); expect(screen.getByLabelText("Branches").textContent).toContain("2 in total")
    expect(screen.getByLabelText("Group opportunities").textContent).toContain("3 companies"); expect(screen.getByLabelText("Group opportunities").textContent).toContain("2 open")
  })
  it("changes the parent and does not offer the company's own branches", async () => {
    serve(h()); wrap(<CompanyGroupPanel customerId="me" />)
    fireEvent.click(await screen.findByRole("button", { name: "Change parent" })); const form = screen.getByLabelText("Choose parent company")
    fireEvent.change(within(form).getByLabelText("Search Parent company"), { target: { value: "ot" } })
    await within(form).findByRole("option", { name: "Other HQ" }); const pick = within(form).getByRole("combobox", { name: "Parent company" }); expect(within(pick).queryByText("Durban Depot")).toBeNull()
    fireEvent.change(pick, { target: { value: "p2" } }); fireEvent.click(within(form).getByRole("button", { name: "Save parent" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/hierarchy/me/parent", { parentId: "p2" }))
  })
  it("makes a branch top-level", async () => {
    serve(h()); wrap(<CompanyGroupPanel customerId="me" />)
    fireEvent.click(await screen.findByRole("button", { name: "Make top-level" })); await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/hierarchy/me/parent", { parentId: null }))
  })
  it("shows the server's reason when a parent is refused", async () => {
    serve(h()); put.mockRejectedValue({ response: { data: { message: "A hierarchy can be at most 5 levels deep" } } }); wrap(<CompanyGroupPanel customerId="me" />)
    fireEvent.click(await screen.findByRole("button", { name: "Make top-level" })); expect((await screen.findByRole("alert")).textContent).toContain("5 levels")
  })
  it("a top-level company with no branches says so, and read-only users get no buttons", async () => {
    perms = new Set(["CUSTOMER_READ"]); serve(h({ self: node("me", "Solo", null), ancestors: [], children: [], branchCount: 0, rollup: { companies: 1, openCount: 0, openValue: 0, weightedValue: 0, wonCount: 0, wonValue: 0 } })); wrap(<CompanyGroupPanel customerId="me" />)
    expect(await screen.findByText(/This is a top-level company/)).toBeTruthy(); expect(screen.getByText(/No branches/)).toBeTruthy(); expect(screen.queryByRole("button")).toBeNull()
  })
})

describe("GroupsPanel", () => {
  it("lists head offices with branches and group totals", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("hierarchy") ? [{ id: "a", name: "Acme HQ", parentId: null, customerType: "CUSTOMER", status: "ACTIVE" }, { id: "b", name: "Acme Cape Town", parentId: "a", customerType: "CUSTOMER", status: "ACTIVE" }] : { content: [opp("x", "QUALIFIED", 1000, null, { customerId: "b" })], totalPages: 1 } } }))
    wrap(<GroupsPanel />)
    const hq = await screen.findByLabelText("Acme HQ"); expect(hq.textContent).toContain("1 branch"); expect(hq.textContent).toMatch(/R\s1\D?000/); expect(screen.getByLabelText("Acme Cape Town")).toBeTruthy()
  })
  it("explains when there are no groups", async () => {
    get.mockResolvedValue({ data: { data: [] } }); wrap(<GroupsPanel />); expect(await screen.findByText(/No company groups yet/)).toBeTruthy()
  })
})
