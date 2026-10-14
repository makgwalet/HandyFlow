import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance patch F14: tax document gaps shown on an issued invoice.
const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
import TaxCheckNotice, { cleanIssues } from "./TaxCheckNotice"

afterEach(() => { cleanup(); get.mockReset() })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><TaxCheckNotice invoiceId="i1" /></QueryClientProvider>)

describe("cleanIssues", () => {
  it("keeps well-formed issues, worst first, and ignores anything else", () => {
    expect(cleanIssues({ content: [] })).toEqual([])
    expect(cleanIssues(null)).toEqual([])
    const out = cleanIssues([{ severity: "INFO", message: "c" }, { severity: "ERROR", message: "a" }, { severity: "WARNING", message: "b" }, { nope: 1 }])
    expect(out.map(i => i.message)).toEqual(["a", "b", "c"])
  })
})

describe("TaxCheckNotice", () => {
  it("shows each gap", async () => {
    get.mockResolvedValue({ data: { data: [{ severity: "ERROR", message: "The business address is not set." }, { severity: "INFO", message: "If the customer is a VAT vendor, add their number." }] } })
    show()
    expect(await screen.findByText("The business address is not set.")).toBeTruthy()
    expect(screen.getByText(/VAT vendor/)).toBeTruthy()
  })
  it("shows nothing when there is nothing to fix", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    show()
    await new Promise(r => setTimeout(r, 30))
    expect(screen.queryByTestId("tax-check")).toBeNull()
  })
})
