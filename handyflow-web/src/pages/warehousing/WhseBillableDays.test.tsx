import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseBillableDaysPanel from "./WhseBillableDaysPanel"
import { billableDaysInStay, ruleProblem, scopeLabel } from "./billableDays"

const get = vi.fn(), post = vi.fn(), del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a) } }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
beforeEach(() => { get.mockReset(); post.mockReset(); del.mockReset(); post.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

const view = (over = {}) => ({ rules: [], holidays: [], options: [{ value: "ALL_DAYS", label: "Every calendar day (standard)" }, { value: "WEEKDAYS_ONLY", label: "Monday to Friday only" }, { value: "WEEKDAYS_NO_HOLIDAYS", label: "Monday to Friday, except public holidays" }], ...over })
const serve = (v: object) => get.mockImplementation(async (url: string) => (url.includes("/clients") ? { data: { content: [{ id: "c1", tradingName: "Acme" }] } } : { data: v }))

describe("billable day helpers", () => {
  it("Friday to Monday is 4 days under the standard, 2 weekdays only, 1 when Monday is a holiday", () => {
    expect(billableDaysInStay("2026-11-13", "2026-11-16", "ALL_DAYS", [])).toBe(4)
    expect(billableDaysInStay("2026-11-13", "2026-11-16", "WEEKDAYS_ONLY", [])).toBe(2)
    expect(billableDaysInStay("2026-11-13", "2026-11-16", "WEEKDAYS_NO_HOLIDAYS", ["2026-11-16"])).toBe(1)
  })
  it("same-day in and out is one day", () => { expect(billableDaysInStay("2026-11-13", "2026-11-13", "ALL_DAYS", [])).toBe(1) })
  it("a rule cannot start in the past", () => {
    expect(ruleProblem("WEEKDAYS_ONLY", "2026-11-10", "2026-11-11")).toMatch(/past/)
    expect(ruleProblem("", "2026-11-12", "2026-11-11")).toMatch(/Choose a rule/)
    expect(ruleProblem("WEEKDAYS_ONLY", "2026-11-11", "2026-11-11")).toBeNull()
  })
  it("scope label", () => {
    expect(scopeLabel({ clientId: null, clientName: null })).toMatch(/Whole warehouse/)
    expect(scopeLabel({ clientId: "c1", clientName: "Acme" })).toBe("Acme")
  })
})

describe("WhseBillableDaysPanel", () => {
  it("explains the standard and shows the worked example", async () => {
    serve(view())
    wrap(<WhseBillableDaysPanel />)
    await screen.findByText("Billable days")
    expect(screen.getByText(/every calendar day bills/)).toBeTruthy()
    expect(screen.getByText(/No contract overrides/)).toBeTruthy()
    expect(screen.getByText(/4 billable days under the standard, 2 under the chosen rule/)).toBeTruthy()
  })
  it("lists existing rules and posts a new one", async () => {
    serve(view({ rules: [{ id: "r1", clientId: "c1", clientName: "Acme", dayRule: "WEEKDAYS_ONLY", label: "Monday to Friday only", effectiveFrom: "2099-01-01", note: "C-14" }] }))
    wrap(<WhseBillableDaysPanel />)
    await screen.findByText(/Monday to Friday only, from 2099-01-01/)
    fireEvent.change(screen.getByLabelText("Starts"), { target: { value: "2099-02-01" } })
    fireEvent.click(screen.getByText("Add rule"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ clientId: null, dayRule: "WEEKDAYS_ONLY", effectiveFrom: "2099-02-01" })
  })
  it("refuses a start date in the past", async () => {
    serve(view())
    wrap(<WhseBillableDaysPanel />)
    await screen.findByText("Billable days")
    fireEvent.change(screen.getByLabelText("Starts"), { target: { value: "2020-01-01" } })
    expect(screen.getByText(/cannot start in the past/)).toBeTruthy()
    expect((screen.getByText("Add rule") as HTMLButtonElement).disabled).toBe(true)
  })
  it("renders nothing for an unexpected response", async () => {
    get.mockResolvedValue({ data: {} })
    const { container } = wrap(<WhseBillableDaysPanel />)
    await new Promise(r => setTimeout(r, 20))
    expect(container.textContent).toBe("")
  })
})
