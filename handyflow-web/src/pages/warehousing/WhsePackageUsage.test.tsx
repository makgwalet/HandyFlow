import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhsePackagePanel from "./WhsePackagePanel"
import { enforcementText, limitText, percentUsed, periodText, priceText, upgradeText, windowText, type PackageUsage } from "./packageUsage"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
beforeEach(() => get.mockReset())
afterEach(cleanup)

const usage = (over: Partial<PackageUsage> = {}): PackageUsage => ({
  assigned: true, periodStart: "2026-11-01", periodEnd: "2026-11-30", enforcement: "MONITOR", pkg: { code: "STARTER", name: "Starter", monthlyPrice: 699, proposed: false },
  meters: [
    { key: "WAREHOUSES", label: "warehouses", used: 2, limit: 1, over: true },
    { key: "CLIENT_ACCOUNTS", label: "3PL client accounts", used: 1, limit: 3, over: false },
    { key: "WEBHOOKS", label: "webhooks", used: 4, limit: null, over: false },
  ], ...over })

describe("package helpers", () => {
  it("percent used is capped and zero without a limit", () => {
    expect(percentUsed({ used: 1, limit: 4 })).toBe(25)
    expect(percentUsed({ used: 9, limit: 4 })).toBe(100)
    expect(percentUsed({ used: 9, limit: null })).toBe(0)
    expect(percentUsed({ used: 1, limit: 0 })).toBe(100)
  })
  it("names the billing month", () => {
    expect(periodText({ periodStart: "2026-10-15", periodEnd: "2026-11-14" })).toBe("Billing month 2026-10-15 to 2026-11-14")
    expect(periodText({})).toBeNull()
  })
  it("explains what the dates on a tier mean", () => {
    expect(windowText({ state: "SCHEDULED", effectiveFrom: "2027-01-01" })).toMatch(/starts on 2027-01-01/)
    expect(windowText({ state: "EXPIRED", expiresOn: "2026-10-31" })).toMatch(/ended on 2026-10-31/)
    expect(windowText({ state: "ACTIVE", expiresOn: "2027-06-30" })).toMatch(/2027-06-30/)
    expect(windowText({ state: "ACTIVE" })).toBeNull()
  })
  it("describes an upgrade with its price and limits", () => {
    const t = upgradeText({ code: "PRO", name: "Professional", monthlyPrice: 4999, maxWarehouses: 5, maxClientAccounts: 40, maxMonthlyTransactions: 15000, maxWebhooks: null })
    expect(t).toMatch(/^Professional: R4[\s ,]?999 per month/)
    expect(t).toMatch(/5 warehouses/)
    expect(t).toMatch(/unlimited webhooks/)
    expect(upgradeText({ code: "E", name: "Enterprise", monthlyPrice: null, maxWarehouses: null, maxClientAccounts: null, maxMonthlyTransactions: null, maxWebhooks: null })).toMatch(/Custom quotation/)
  })
  it("words", () => {
    expect(limitText(null)).toBe("no limit")
    expect(priceText(null)).toBe("Custom quotation")
    expect(priceText(699)).toMatch(/^R699 per month, excluding VAT/)
    expect(enforcementText("ENFORCE")).toMatch(/refused/)
    expect(enforcementText("OFF")).toMatch(/not applied/)
  })
})

describe("WhsePackagePanel", () => {
  it("shows the package, price, usage and flags what is over", async () => {
    get.mockResolvedValue({ data: usage() })
    wrap(<WhsePackagePanel />)
    await screen.findByText("Package and usage")
    expect(screen.getByText("Starter")).toBeTruthy()
    expect(screen.getByText(/2 of 1 \(over\)/)).toBeTruthy()
    expect(screen.getByText(/4 no limit/)).toBeTruthy()
    expect(screen.getAllByRole("progressbar").length).toBe(2)
    expect(screen.getByText(/Billing month 2026-11-01 to 2026-11-30/)).toBeTruthy()
  })
  it("offers upgrades and shows an override on a meter", async () => {
    get.mockResolvedValue({ data: usage({
      meters: [{ key: "WAREHOUSES", label: "warehouses", used: 1, limit: 3, over: false, extra: 2 }],
      upgradeOptions: [{ code: "SME", name: "SME", monthlyPrice: 1999, maxWarehouses: 2, maxClientAccounts: 10, maxMonthlyTransactions: 3000, maxWebhooks: 1 }] }) })
    wrap(<WhsePackagePanel />)
    await screen.findByText("Need more room?")
    expect(screen.getByText(/SME: R1[\s ,]?999 per month/)).toBeTruthy()
    expect(screen.getByText(/includes 2 extra allowed for now/)).toBeTruthy()
  })
  it("says no limits apply when no package is assigned", async () => {
    get.mockResolvedValue({ data: usage({ assigned: false, pkg: null, enforcement: "OFF", meters: [] }) })
    wrap(<WhsePackagePanel />)
    await screen.findByText(/No package limits apply/)
  })
  it("renders nothing for an unexpected response", async () => {
    get.mockResolvedValue({ data: {} })
    const { container } = wrap(<WhsePackagePanel />)
    await new Promise(r => setTimeout(r, 20))
    expect(container.textContent).toBe("")
  })
})
