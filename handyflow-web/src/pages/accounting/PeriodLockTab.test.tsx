import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance patch F5: the books' lock date.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
import PeriodLockTab, { lastMonthEnd } from "./PeriodLockTab"

const state = (lockedThrough: string | null, history: any[] = []) => ({ data: { data: { lockedThrough, updatedAt: null, history } } })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><PeriodLockTab /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["ACCOUNTING_READ", "ACCOUNTING_ADMIN", "ACCOUNTING_PERIOD_REOPEN"])
  get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("lastMonthEnd", () => {
  it("is the last day of the previous month", () => {
    expect(lastMonthEnd(new Date(2026, 9, 14))).toBe("2026-09-30")
    expect(lastMonthEnd(new Date(2026, 2, 3))).toBe("2026-02-28")
    expect(lastMonthEnd(new Date(2026, 0, 1))).toBe("2025-12-31")
  })
})

describe("PeriodLockTab", () => {
  it("shows nothing closed and closes to the chosen date", async () => {
    get.mockResolvedValue(state(null))
    show()
    await waitFor(() => expect(screen.getByTestId("locked-through").textContent).toBe("Nothing is closed"))
    expect(screen.queryByText("Reopen", { selector: "h3" })).toBeNull()
    fireEvent.change(screen.getByLabelText("Close up to and including"), { target: { value: "2026-09-30" } })
    fireEvent.change(screen.getByLabelText("Note (optional)"), { target: { value: "September VAT submitted" } })
    fireEvent.click(screen.getByRole("button", { name: "Close" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/period-lock",
      { lockedThrough: "2026-09-30", note: "September VAT submitted" }))
  })

  it("shows the server's reason when closing is refused", async () => {
    get.mockResolvedValue(state(null))
    post.mockRejectedValueOnce({ response: { data: { message: "2 draft journals are dated on or before 2026-09-30. Post or discard them first" } } })
    show()
    await waitFor(() => expect(screen.getByTestId("locked-through").textContent).toBe("Nothing is closed"))
    fireEvent.click(screen.getByRole("button", { name: "Close" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/2 draft journals/)
  })

  it("reopens only with a reason, and clears the date to open everything", async () => {
    get.mockResolvedValue(state("2026-09-30", [{ action: "LOCK", fromDate: null, toDate: "2026-09-30", note: "Sept", byUser: null, at: "2026-10-05T08:00:00Z" }]))
    show()
    await waitFor(() => expect(screen.getByTestId("locked-through").textContent).toMatch(/30 September 2026/))
    expect(screen.getByText("Closed")).toBeTruthy()
    const button = screen.getByRole("button", { name: "Reopen" }) as HTMLButtonElement
    expect(button.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Late supplier invoice for September" } })
    fireEvent.click(button)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/period-lock/reopen",
      { lockedThrough: null, reason: "Late supplier invoice for September" }))
  })

  it("read-only without the permissions", async () => {
    perms = new Set(["ACCOUNTING_READ"])
    get.mockResolvedValue(state("2026-09-30"))
    show()
    await waitFor(() => expect(screen.getByTestId("locked-through").textContent).toMatch(/2026/))
    expect(screen.queryByRole("button", { name: "Close" })).toBeNull()
    expect(screen.queryByRole("button", { name: "Reopen" })).toBeNull()
  })
})
