import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhsePackagePanel from "./WhsePackagePanel"
import { enforcementText, limitText, percentUsed, priceText, type PackageUsage } from "./packageUsage"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
beforeEach(() => get.mockReset())
afterEach(cleanup)

const usage = (over: Partial<PackageUsage> = {}): PackageUsage => ({
  assigned: true, enforcement: "MONITOR", pkg: { code: "STARTER", name: "Starter", monthlyPrice: 699, proposed: false },
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
