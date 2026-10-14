import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance F25: fixed-asset register, depreciation run, disposal.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn(), put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: {
  get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a) } }))
import FixedAssetsTab from "./FixedAssetsTab"
import DepreciationTab from "./DepreciationTab"

const accounts = [
  { id: "c1", accountCode: "1520", accountName: "Vehicles", accountType: "ASSET", accountSubtype: "FIXED_ASSET" },
  { id: "a1", accountCode: "1590", accountName: "Accumulated depreciation", accountType: "ASSET", accountSubtype: "DEPRECIATION" },
  { id: "e1", accountCode: "5220", accountName: "Depreciation", accountType: "EXPENSE", accountSubtype: "DEPRECIATION" },
  { id: "g1", accountCode: "4900", accountName: "Gain on disposal", accountType: "INCOME", accountSubtype: null },
]
const asset = { id: "x1", assetNumber: "FA-0001", name: "Bakkie", description: null, serialNumber: null, location: null,
  costAccountId: "c1", accumulatedAccountId: "a1", expenseAccountId: "e1", costCentreId: null, acquiredOn: "2026-01-10",
  cost: 300000, residualValue: 60000, method: "STRAIGHT_LINE", lifeMonths: 60, ratePercent: null, status: "ACTIVE",
  disposedOn: null, disposalProceeds: null, accumulated: 36000, bookValue: 264000 }
const preview = { period: "2026-10-01", lastRunPeriod: "2026-09-01", nextPeriod: "2026-10-01", total: 4000, problem: null,
  lines: [{ assetId: "x1", assetNumber: "FA-0001", name: "Bakkie", charge: 4000, accumulatedAfter: 40000, bookValueAfter: 260000 }] }

const show = (ui: React.ReactElement) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let prev: any
beforeEach(() => {
  perms = new Set(["ACCOUNTING_READ", "ACCOUNTING_MANAGE"])
  prev = preview
  get.mockReset(); post.mockReset(); put.mockReset()
  get.mockImplementation((url: string) => Promise.resolve({ data: { data:
    url.includes("depreciation/preview") ? prev
    : url.includes("depreciation/runs") ? [{ id: "r1", period: "2026-09-01", journalNumber: "JE-9", journalStatus: "DRAFT", total: 4000, assets: 1 }]
    : url.includes("reconciliation") ? [{ accountId: "c1", code: "1520", name: "Vehicles", role: "COST", ledger: 300000, register: 300000, difference: 0 },
        { accountId: "a1", code: "1590", name: "Accumulated depreciation", role: "ACCUMULATED", ledger: 32000, register: 36000, difference: -4000 }]
    : url.includes("fixed-assets") ? [asset]
    : url.includes("cost-centres") ? []
    : url.includes("/accounts") ? accounts : [] } }))
  post.mockResolvedValue({ data: { data: { journalNumber: "JE-10", period: "2026-10-01" } } })
  put.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("FixedAssetsTab", () => {
  it("lists assets with book value and totals", async () => {
    show(<FixedAssetsTab />)
    await screen.findByText("FA-0001")
    expect(screen.getAllByText(/264[\s ,]?000[.,]00/).length).toBeGreaterThan(0)
    expect(screen.getByText("Active assets")).toBeTruthy()
  })

  it("shows where the register and the ledger disagree", async () => {
    show(<FixedAssetsTab />)
    await screen.findByText("Register against the ledger")
    expect(screen.getByText("Agrees")).toBeTruthy()
    expect(screen.getAllByText(/4[\s ,]?000[.,]00/).length).toBeGreaterThan(0)
  })

  it("fills the three accounts from the chart and adds a straight-line asset", async () => {
    show(<FixedAssetsTab />)
    await screen.findByText("FA-0001")
    await waitFor(() => expect(screen.getByText("Add an asset")).toBeTruthy())
    fireEvent.click(screen.getByText("Add an asset"))
    expect((screen.getByLabelText("Cost account") as HTMLSelectElement).value).toBe("c1")
    expect((screen.getByLabelText("Accumulated depreciation account") as HTMLSelectElement).value).toBe("a1")
    expect((screen.getByLabelText("Depreciation expense account") as HTMLSelectElement).value).toBe("e1")
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Forklift" } })
    fireEvent.change(screen.getByLabelText("Acquired on"), { target: { value: "2026-05-01" } })
    fireEvent.change(screen.getByLabelText("Cost"), { target: { value: "120000" } })
    fireEvent.click(screen.getByText("Add asset"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/fixed-assets",
      expect.objectContaining({ name: "Forklift", cost: 120000, method: "STRAIGHT_LINE", lifeMonths: 60, ratePercent: null, costAccountId: "c1" })))
  })

  it("will not submit while something is missing and says what", async () => {
    show(<FixedAssetsTab />)
    await screen.findByText("FA-0001")
    fireEvent.click(screen.getByText("Add an asset"))
    expect(screen.getByText("Give the asset a name.")).toBeTruthy()
    expect((screen.getByText("Add asset") as HTMLButtonElement).disabled).toBe(true)
  })

  it("drafts a disposal and says another person must post it", async () => {
    show(<FixedAssetsTab />)
    await screen.findByText("FA-0001")
    fireEvent.click(screen.getByText("Dispose"))
    const btn = screen.getByText("Draft the disposal journal") as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Gain or loss account"), { target: { value: "g1" } })
    fireEvent.change(screen.getByLabelText("Proceeds"), { target: { value: "0" } })
    fireEvent.click(btn)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/fixed-assets/x1/dispose",
      expect.objectContaining({ proceeds: 0, gainLossAccountId: "g1", proceedsAccountId: null })))
    expect(await screen.findByText(/A different person must post it/)).toBeTruthy()
  })

  it("hides the buttons without ACCOUNTING_MANAGE", async () => {
    perms = new Set(["ACCOUNTING_READ"])
    show(<FixedAssetsTab />)
    await screen.findByText("FA-0001")
    expect(screen.queryByText("Add an asset")).toBeNull()
    expect(screen.queryByText("Dispose")).toBeNull()
  })
})

describe("DepreciationTab", () => {
  it("previews the next month and drafts the journal", async () => {
    show(<DepreciationTab />)
    await screen.findByText(/Next run:/)
    expect(screen.getByText("FA-0001 Bakkie")).toBeTruthy()
    fireEvent.click(screen.getByText("Draft the depreciation journal"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/fixed-assets/depreciation/run", { period: "2026-10-01" }))
    expect(await screen.findByText(/drafted as journal JE-10/)).toBeTruthy()
  })

  it("lists earlier runs with their posting state", async () => {
    show(<DepreciationTab />)
    await screen.findByText("JE-9")
    expect(screen.getByText("Waiting to be posted")).toBeTruthy()
  })

  it("explains why nothing can be run", async () => {
    prev = { ...preview, lines: [], total: 0, problem: "Nothing to depreciate for that month." }
    show(<DepreciationTab />)
    await screen.findByText("Nothing to depreciate for that month.")
    expect((screen.getByText("Draft the depreciation journal") as HTMLButtonElement).disabled).toBe(true)
  })

  it("says when depreciation is up to date", async () => {
    prev = { period: null, lastRunPeriod: "2026-10-01", nextPeriod: null, lines: [], total: 0, problem: "Depreciation is up to date." }
    show(<DepreciationTab />)
    await screen.findByText("Depreciation is up to date.")
    expect(screen.queryByText("Draft the depreciation journal")).toBeNull()
  })
})
