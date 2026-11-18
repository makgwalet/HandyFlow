import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { PolicyRefinements, ContractSavings } from "./PolicyRefinements"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset() }); afterEach(cleanup)

const OVERVIEW = {
  baseCurrency: "ZAR", companyTolerancePct: 5, companyVatRecoverablePct: 100,
  categoryTolerances: [{ categoryId: "c1", categoryName: "Fasteners", tolerancePct: 10 }],
  categories: [{ id: "c1", name: "Fasteners" }, { id: "c2", name: "Fuel" }],
  supplierVat: [{ supplierId: "s1", supplierName: "Alpha", vatRecoverablePct: 0, note: "Not VAT registered" }],
  suppliers: [{ id: "s1", name: "Alpha" }, { id: "s2", name: "Beta" }],
  exchangeRates: [{ id: "r1", currency: "USD", rate: 18.55, effectiveFrom: "2026-11-01", createdByName: "Admin" }],
  currenciesWithoutRate: ["EUR"],
}

describe("PolicyRefinements", () => {
  it("shows the overrides and warns about a currency with no rate", async () => {
    get.mockImplementation(() => ok(OVERVIEW))
    wrap(<PolicyRefinements isAdmin />)
    expect(await screen.findByText("Fasteners", { selector: "td" })).toBeTruthy()
    expect(screen.getByText("Not VAT registered")).toBeTruthy()
    expect(screen.getByText(/No rate yet for: EUR/)).toBeTruthy()
  })

  it("sets a category tolerance and says how many commitments were restated for a supplier VAT change", async () => {
    get.mockImplementation(() => ok(OVERVIEW))
    put.mockResolvedValue({ data: { data: { commitmentsRestated: 3, commitmentsSkipped: 0 } } })
    wrap(<PolicyRefinements isAdmin />)
    await screen.findByLabelText("Tolerance category")
    fireEvent.change(screen.getByLabelText("Tolerance category"), { target: { value: "c2" } })
    fireEvent.change(screen.getByLabelText("Category tolerance"), { target: { value: "2" } })
    fireEvent.click(screen.getByText("Set tolerance"))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(String(put.mock.calls[0][0])).toContain("category-tolerance/c2")
    expect(put.mock.calls[0][1]).toEqual({ tolerancePct: 2 })

    fireEvent.change(screen.getByLabelText("VAT supplier"), { target: { value: "s2" } })
    fireEvent.change(screen.getByLabelText("Supplier recoverable VAT"), { target: { value: "0" } })
    fireEvent.click(screen.getByText("Set VAT"))
    await waitFor(() => expect(put).toHaveBeenCalledTimes(2))
    expect(await screen.findByText("3 budget commitments restated.")).toBeTruthy()
  })

  it("refuses a bad rate before calling the server, and sends a good one", async () => {
    get.mockImplementation(() => ok(OVERVIEW))
    post.mockResolvedValue({ data: { data: {} } })
    wrap(<PolicyRefinements isAdmin />)
    await screen.findByLabelText("Rate currency")
    fireEvent.change(screen.getByLabelText("Rate currency"), { target: { value: "ZAR" } })
    fireEvent.change(screen.getByLabelText("Exchange rate"), { target: { value: "1" } })
    fireEvent.click(screen.getByText("Save rate"))
    expect(await screen.findByText(/base currency/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Rate currency"), { target: { value: "eur" } })
    fireEvent.change(screen.getByLabelText("Exchange rate"), { target: { value: "20.1" } })
    fireEvent.click(screen.getByText("Save rate"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ currency: "EUR", rate: 20.1 })
  })

  it("is read-only for a non-administrator", async () => {
    get.mockImplementation(() => ok(OVERVIEW))
    wrap(<PolicyRefinements isAdmin={false} />)
    await screen.findByText("Fasteners", { selector: "td" })
    expect(screen.queryByText("Save rate")).toBeNull()
    expect(screen.queryByText("Remove")).toBeNull()
  })
})

describe("ContractSavings", () => {
  it("runs the report on demand and shows savings, leakage and the lines left out", async () => {
    get.mockImplementation(() => ok({
      baseCurrency: "ZAR", from: "2025-11-19", to: "2026-11-18", listValue: 1000, paidValue: 900, saving: 100, leakage: 50,
      lines: 4, linesWithoutListPrice: 2, linesWithoutRate: 1,
      byItem: [{ catalogueItemId: "i1", itemName: "Bolts M8", qty: 10, listValue: 1000, paidValue: 900, saving: 100, leakage: 50, lines: 4, contractLines: 3 }],
    }))
    wrap(<ContractSavings />)
    expect(get).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Run report"))
    expect(await screen.findByText("Bolts M8")).toBeTruthy()
    expect(screen.getByText(/10 % of list value/)).toBeTruthy()
    expect(screen.getByText(/2 line\(s\) left out: the item has no list price/)).toBeTruthy()
    expect(screen.getByText("3 of 4")).toBeTruthy()
  })
})
