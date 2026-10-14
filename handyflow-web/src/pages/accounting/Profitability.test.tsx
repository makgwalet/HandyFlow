import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance F28: profitability by cost centre and the automatic tagging rules.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), delete: vi.fn() } }))
import ProfitabilityTab from "./ProfitabilityTab"
import TagRulesTab from "./TagRulesTab"

const row = (id: string | null, code: string, name: string, income: number, expenses: Record<string, number>) => {
  const expenseTotal = Object.values(expenses).reduce((a, b) => a + b, 0)
  return { centreId: id, code, name, kind: "SITE", income, expenses, expenseTotal, profit: income - expenseTotal, marginPercent: null }
}
const view = {
  from: "2026-03-01", to: "2026-10-14", rollUp: false, kind: "SITE", subtypes: ["COS", "PAYROLL", "RENT"],
  rows: [row("s1", "MALL", "Sandton Mall", 10000, { PAYROLL: 6000, RENT: 1000 }), row("s2", "DEPOT", "Depot", 1000, { PAYROLL: 3000 })],
  unallocated: row(null, "-", "Unallocated", 0, { RENT: 500 }),
  total: row(null, "-", "Total", 11000, { PAYROLL: 9000, RENT: 1500 }),
}
const trend = { centreId: "s1", code: "MALL", name: "Sandton Mall", months: [
  { month: "2026-03-01", income: 1000, expenses: { PAYROLL: 400 }, expenseTotal: 400, profit: 600 }] }
const rules = [{ id: "r1", name: "Fuel CA 123", priority: 10, accountCode: "5140", textContains: "CA 123", entryType: null, costCentreId: "v1", costCentreName: "CA 123", active: true }]
const centres = [{ id: "v1", code: "CA123", name: "CA 123", kind: "VEHICLE", parentId: null, active: true, lineCount: 0 }]
const show = (ui: React.ReactElement) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["ACCOUNTING_READ", "ACCOUNTING_MANAGE", "ACCOUNTING_ADMIN"])
  get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => Promise.resolve({ data: { data:
    url.includes("/trend") ? trend : url.includes("profitability") ? view : url.includes("tag-rules") ? rules : url.includes("cost-centres") ? centres : [] } }))
  post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("ProfitabilityTab", () => {
  it("shows rows, unallocated and total, with usual direct costs ticked", async () => {
    show(<ProfitabilityTab />)
    await screen.findByText("Sandton Mall")
    expect(screen.getByText("Unallocated")).toBeTruthy()
    expect(screen.getByText("Total")).toBeTruthy()
    expect((screen.getByLabelText("Wages and salaries") as HTMLInputElement).checked).toBe(true)
    expect((screen.getByLabelText("Rent") as HTMLInputElement).checked).toBe(false)
    expect(get.mock.calls[0][0]).toContain("kind=SITE")
  })

  it("moves a cost between direct and overheads when ticked", async () => {
    show(<ProfitabilityTab />)
    await screen.findByText("Sandton Mall")
    // Mall: direct 6,000 -> contribution 4,000
    expect(screen.getAllByText(/R\s?4[\s, ]?000[.,]00/).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByLabelText("Rent"))
    // Mall: direct 7,000 -> contribution 3,000
    await waitFor(() => expect(screen.getAllByText(/R\s?3[\s, ]?000[.,]00/).length).toBeGreaterThan(0))
  })

  it("flags loss-makers", async () => {
    show(<ProfitabilityTab />)
    const note = await screen.findByText(/losing money/)
    expect(note.textContent).toContain("DEPOT")
    expect(note.textContent).not.toContain("MALL")
  })

  it("warns when a large share is unallocated", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("profitability")
      ? { ...view, unallocated: row(null, "-", "Unallocated", 5000, { RENT: 500 }) } : [] } }))
    show(<ProfitabilityTab />)
    expect(await screen.findByText(/carry no cost centre/)).toBeTruthy()
  })

  it("loads the monthly trend when a name is clicked", async () => {
    show(<ProfitabilityTab />)
    await screen.findByText("Sandton Mall")
    fireEvent.click(screen.getAllByTitle("Month by month")[0])
    await screen.findByText(/month by month/)
    expect(get.mock.calls.some(c => String(c[0]).includes("/profitability/s1/trend?"))).toBe(true)
  })
})

describe("TagRulesTab", () => {
  it("lists rules in words and can switch one off", async () => {
    show(<TagRulesTab />)
    await screen.findByText("Fuel CA 123")
    expect(screen.getByText(/account 5140 and text contains/)).toBeTruthy()
    fireEvent.click(screen.getByText("Switch off"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/tag-rules/r1/active", { active: false }))
  })

  it("creates a rule only once it has a condition and a cost centre", async () => {
    show(<TagRulesTab />)
    await screen.findByText("Fuel CA 123")
    const add = screen.getByText("Add rule") as HTMLButtonElement
    fireEvent.change(screen.getByLabelText("Rule name"), { target: { value: "Mall rent" } })
    expect(add.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Words in the text"), { target: { value: "Sandton Mall" } })
    fireEvent.change(screen.getByLabelText("Tag with"), { target: { value: "v1" } })
    expect(add.disabled).toBe(false)
    fireEvent.click(add)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/tag-rules",
      { name: "Mall rent", priority: 100, accountCode: null, textContains: "Sandton Mall", entryType: null, costCentreId: "v1" }))
  })

  it("previews, then applies to past journals", async () => {
    post.mockResolvedValueOnce({ data: { data: { applied: false, matched: 3, limitReached: false, byRule: { "Fuel CA 123": 3 }, samples: [] } } })
    show(<TagRulesTab />)
    await screen.findByText("Fuel CA 123")
    fireEvent.click(screen.getByText("Preview"))
    await screen.findByText(/3 untagged lines match/)
    expect(post.mock.calls[0][1]).toMatchObject({ apply: false })
    post.mockResolvedValueOnce({ data: { data: { applied: true, matched: 3, limitReached: false, byRule: {}, samples: [] } } })
    fireEvent.click(screen.getByText("Apply to 3 lines"))
    await screen.findByText("Tagged 3 lines.")
    expect(post.mock.calls[1][1]).toMatchObject({ apply: true })
  })

  it("hides controls without the right permissions", async () => {
    perms = new Set(["ACCOUNTING_READ"])
    show(<TagRulesTab />)
    await screen.findByText("Fuel CA 123")
    expect(screen.queryByText("Add rule")).toBeNull()
    expect(screen.queryByText("Switch off")).toBeNull()
    expect(screen.queryByText("Preview")).toBeNull()
  })

  it("applying needs ACCOUNTING_ADMIN", async () => {
    perms = new Set(["ACCOUNTING_READ", "ACCOUNTING_MANAGE"])
    show(<TagRulesTab />)
    await screen.findByText("Fuel CA 123")
    expect(screen.getByText("Add rule")).toBeTruthy()
    expect(screen.queryByText("Preview")).toBeNull()
  })
})
