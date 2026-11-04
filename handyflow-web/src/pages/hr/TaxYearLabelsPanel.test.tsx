import { describe, it, expect, vi, beforeEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TaxYearLabelsPanel from "./TaxYearLabelsPanel"

const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
const row = (id: string, verdict: string) => ({ id, payRunNumber: "PR-" + id, payDate: "2026-05-25", status: "FINALISED", finalisedOrPaid: true, storedLabel: "2025/26", proposedLabel: "2026/27", verdict, reason: "Pay date falls in 2026/27.", payslips: 2, payslipsOnOtherYear: 0 })
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><TaxYearLabelsPanel /></QueryClientProvider>)

describe("TaxYearLabelsPanel", () => {
  beforeEach(() => { cleanup(); get.mockReset(); post.mockReset() })
  it("corrects only the chosen runs and needs a reason", async () => {
    get.mockResolvedValue({ data: { total: 2, correct: 0, wrongLabel: 1, needsReview: 1, rows: [row("a", "WRONG_LABEL"), row("b", "NEEDS_REVIEW")] } })
    post.mockResolvedValue({ data: { migrationRef: "TYL-1", corrected: 1, skipped: 0, skippedReasons: [] } })
    wrap()
    const box = await screen.findByLabelText("Correct PR-a")
    expect(screen.queryByLabelText("Correct PR-b")).toBeNull()
    const btn = screen.getByText("Correct labels") as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.click(box)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Pay date verified" } })
    fireEvent.click(btn)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/pay-runs/tax-year-labels/apply", { payRunIds: ["a"], reason: "Pay date verified" }))
    expect(await screen.findByText(/1 pay run corrected \(ref TYL-1\)/)).toBeTruthy()
  })
})
