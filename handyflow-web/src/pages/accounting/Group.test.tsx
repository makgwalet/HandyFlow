import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance F26: company groups, intercompany rules, consolidated statements.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: {
  get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a), delete: (...a: any[]) => del(...a) } }))
import GroupTab from "./GroupTab"
import ConsolidationTab from "./ConsolidationTab"

const parent = { id: "g1", name: "Acme Group", myRole: "PARENT", parentTenantId: "t1", parentName: "Acme HQ", members: [
  { tenantId: "t1", name: "Acme HQ", role: "PARENT", ownershipPercent: 100, joinedAt: "2026-01-01T00:00:00Z" },
  { tenantId: "t2", name: "Acme Sub", role: "SUBSIDIARY", ownershipPercent: 60, joinedAt: "2026-02-01T00:00:00Z" }] }
const sub = { id: "g1", name: "Acme Group", myRole: "SUBSIDIARY", parentTenantId: "t1", parentName: "Acme HQ", members: [] }
const report = {
  from: "2026-03-01", to: "2026-10-14", entities: [{ tenantId: "t1", name: "Acme HQ", ownershipPercent: 100 }, { tenantId: "t2", name: "Acme Sub", ownershipPercent: 60 }],
  lines: [
    { code: "1000", name: "Bank", type: "ASSET", amounts: [700, 100], elimination: 0, consolidated: 800 },
    { code: "1900", name: "Loan to Sub", type: "ASSET", amounts: [100, 0], elimination: -100, consolidated: 0 },
    { code: "4000", name: "Sales", type: "INCOME", amounts: [200, 0], elimination: 0, consolidated: 200 }],
  checks: [{ name: "HQ loan to Sub", sideA: 100, sideB: -90, difference: 10 }],
  income: 200, expenses: 0, profit: 200, assets: 800, liabilities: 0, equity: 500, balanceDifference: 0,
  warnings: ["Acme Sub is owned for 60%. It is included at 100%; the minority share is not worked out."],
}

const show = (ui: React.ReactElement) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let group: any
beforeEach(() => {
  perms = new Set(["ACCOUNTING_READ", "ACCOUNTING_ADMIN"])
  group = parent
  get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset()
  get.mockImplementation((url: string) => Promise.resolve({ data: { data:
    url.endsWith("/group/report") || url.includes("/group/report?") ? report
    : url.includes("/group/invites") ? [{ id: "i1", label: "Sub company", ownershipPercent: 60, expiresAt: "2026-10-21T10:00:00Z" }]
    : url.includes("/group/mappings") || url.includes("/group/eliminations") ? []
    : url.includes("/accounts") ? [{ code: "1000", name: "Bank", type: "ASSET" }, { code: "1900", name: "Loan", type: "ASSET" }]
    : url.endsWith("/api/v1/accounting/group") ? group : null } }))
  post.mockResolvedValue({ data: { data: { code: "ABCD-EFGH-JKLM-NPQR" } } })
  put.mockResolvedValue({ data: { data: [] } }); del.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("GroupTab", () => {
  it("offers to start or join a group when the company is in none", async () => {
    group = null
    show(<GroupTab />)
    await screen.findByText("Start a group with this company as the parent")
    fireEvent.change(screen.getByLabelText("Group name"), { target: { value: "My Group" } })
    fireEvent.click(screen.getByText("Create group"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/group", { name: "My Group" }))
  })

  it("joins with a code and explains what that allows", async () => {
    group = null
    show(<GroupTab />)
    await screen.findByText("Join a group")
    expect(screen.getByText(/cannot see individual transactions/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Invitation code"), { target: { value: " abcd-efgh " } })
    fireEvent.click(screen.getByText("Join group"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/group/join", { code: "abcd-efgh" }))
  })

  it("shows a subsidiary only the group and its parent, and lets it leave", async () => {
    group = sub
    vi.spyOn(window, "confirm").mockReturnValue(true)
    show(<GroupTab />)
    await screen.findByText(/owned by/)
    expect(screen.getByText("Acme HQ")).toBeTruthy()
    fireEvent.click(screen.getByText("Leave the group"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/group/leave"))
  })

  it("lists the companies with ownership for the parent", async () => {
    show(<GroupTab />)
    await screen.findByText("Subsidiary")
    expect(screen.getByText("60%")).toBeTruthy()
    expect(screen.getByText(/minority share is not worked out/)).toBeTruthy()
  })

  it("makes an invitation and shows the code once", async () => {
    show(<GroupTab />)
    await screen.findByText("Subsidiary")
    fireEvent.click(screen.getByText("Make an invitation code"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/group/invites", { label: null, ownershipPercent: 100 }))
    expect(await screen.findByText("ABCD-EFGH-JKLM-NPQR")).toBeTruthy()
  })

  it("will not add a rule between a company and itself", async () => {
    show(<GroupTab />)
    await screen.findByText("Intercompany rules")
    fireEvent.change(screen.getByLabelText("Rule name"), { target: { value: "Loan" } })
    fireEvent.change(screen.getByLabelText("First company"), { target: { value: "t1" } })
    fireEvent.change(screen.getByLabelText("Second company"), { target: { value: "t1" } })
    await waitFor(() => expect((screen.getByLabelText("First account") as HTMLSelectElement).options.length).toBeGreaterThan(1))
    fireEvent.change(screen.getByLabelText("First account"), { target: { value: "1900" } })
    fireEvent.change(screen.getByLabelText("Second account"), { target: { value: "1000" } })
    expect((screen.getByText("Add rule") as HTMLButtonElement).disabled).toBe(true)
  })

  it("adds a rule between two companies", async () => {
    show(<GroupTab />)
    await screen.findByText("Intercompany rules")
    fireEvent.change(screen.getByLabelText("Rule name"), { target: { value: "HQ loan to Sub" } })
    fireEvent.change(screen.getByLabelText("First company"), { target: { value: "t1" } })
    fireEvent.change(screen.getByLabelText("Second company"), { target: { value: "t2" } })
    await waitFor(() => expect((screen.getByLabelText("First account") as HTMLSelectElement).options.length).toBeGreaterThan(1))
    fireEvent.change(screen.getByLabelText("First account"), { target: { value: "1900" } })
    await waitFor(() => expect((screen.getByLabelText("Second account") as HTMLSelectElement).options.length).toBeGreaterThan(1))
    fireEvent.change(screen.getByLabelText("Second account"), { target: { value: "1000" } })
    fireEvent.click(screen.getByText("Add rule"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/group/eliminations",
      { name: "HQ loan to Sub", tenantAId: "t1", accountCodeA: "1900", tenantBId: "t2", accountCodeB: "1000" }))
  })

  it("hides the controls without ACCOUNTING_ADMIN", async () => {
    perms = new Set(["ACCOUNTING_READ"])
    show(<GroupTab />)
    await screen.findByText("Subsidiary")
    expect(screen.queryByText("Make an invitation code")).toBeNull()
    expect(screen.queryByText("Remove")).toBeNull()
  })
})

describe("ConsolidationTab", () => {
  it("shows a column per company, eliminations and the consolidated total", async () => {
    show(<ConsolidationTab />)
    await screen.findByText("Eliminations")
    expect(screen.getByText("Acme Sub")).toBeTruthy()
    expect(screen.getByText("Consolidated")).toBeTruthy()
    expect(screen.getAllByText(/Loan to Sub/).length).toBeGreaterThan(0)
    expect(screen.getByText("Profit for the period")).toBeTruthy()
  })

  it("shows the warnings and the intercompany difference", async () => {
    show(<ConsolidationTab />)
    await screen.findByText("Check before relying on this")
    expect(screen.getByText(/minority share is not worked out/)).toBeTruthy()
    expect(screen.getByText(/Differs by/)).toBeTruthy()
  })

  it("explains itself to a company that is not a group parent", async () => {
    group = sub
    show(<ConsolidationTab />)
    await screen.findByText(/available to the company that owns a group/)
    expect(get.mock.calls.some(c => String(c[0]).includes("group/report"))).toBe(false)
  })
})
