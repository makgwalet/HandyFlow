import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TerminationApprovals, { askedByText } from "./TerminationApprovals"

const get = vi.fn(), post = vi.fn()
let allowed = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => allowed }))

const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><TerminationApprovals /></QueryClientProvider>)
const pending = { id: "t1", employeeId: "e1", employeeName: "Jane Dlamini", endDate: "2026-11-03", reason: "Resigned", endType: null,
  noticeGivenOn: null, requestedBy: "u1", requestedByName: "Thandi Mokoena", requestedAt: "2026-11-05T10:00:00" }

beforeEach(() => { allowed = true; get.mockReset(); post.mockReset(); get.mockResolvedValue({ data: { data: [pending] } }) })
afterEach(cleanup)

describe("terminations waiting for approval", () => {
  it("names who asked, and falls back when the name is unknown", () => {
    expect(askedByText({ requestedByName: " Thandi Mokoena " })).toBe("Thandi Mokoena")
    expect(askedByText({ requestedByName: null })).toBe("A colleague")
  })

  it("shows nothing without the HR manager permission, and does not ask the server", () => {
    allowed = false
    const { container } = wrap()
    expect(container.textContent).toBe("")
    expect(get).not.toHaveBeenCalled()
  })

  it("shows nothing when nothing is waiting", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    const { container } = wrap()
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })

  it("lists the request and approves it", async () => {
    post.mockResolvedValue({ data: { data: "APPROVED" } })
    wrap()
    await screen.findByText(/Jane Dlamini/)
    expect(screen.getByText(/Thandi Mokoena asked: Resigned/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Approve" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/terminations/t1/approve", {}))
    await screen.findByText(/Termination of Jane Dlamini approved/)
  })

  it("shows the server's refusal, such as the same person approving", async () => {
    post.mockRejectedValue({ response: { data: { message: "The person who asked for a termination cannot approve it. Ask another HR manager." } } })
    wrap()
    await screen.findByText(/Jane Dlamini/)
    fireEvent.click(screen.getByRole("button", { name: "Approve" }))
    await screen.findByText(/cannot approve it/)
  })

  it("will not decline without a reason, then declines with one", async () => {
    post.mockResolvedValue({ data: { data: "REJECTED" } })
    wrap()
    await screen.findByText(/Jane Dlamini/)
    fireEvent.click(screen.getByRole("button", { name: "Decline" }))
    const send = screen.getByRole("button", { name: "Decline termination" }) as HTMLButtonElement
    expect(send.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason for declining"), { target: { value: "Still in notice" } })
    fireEvent.click(send)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/terminations/t1/reject", { note: "Still in notice" }))
    await screen.findByText(/They stay employed/)
  })
})
