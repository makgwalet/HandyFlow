import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ReverseModal from "./ReverseModal"

const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: any[]) => post(...a) } }))

function setup(onClose = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const spy = vi.spyOn(qc, "invalidateQueries")
  render(
    <QueryClientProvider client={qc}>
      <ReverseModal title="Reverse delivery" summary="Puts litres back" endpoint="/api/v1/fuel/deliveries/d1/reverse"
        invalidate={[["deliveries"], ["tanks"]]} onClose={onClose} />
    </QueryClientProvider>,
  )
  return { onClose, spy }
}

describe("ReverseModal", () => {
  beforeEach(() => post.mockReset())
  afterEach(() => cleanup())

  it("keeps the button disabled until the reason has 5 characters", () => {
    setup()
    const btn = screen.getByRole("button", { name: "Reverse entry" })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/reason/i), { target: { value: "abc" } })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/reason/i), { target: { value: "Wrong tank" } })
    expect((btn as HTMLButtonElement).disabled).toBe(false)
  })

  it("posts the trimmed reason, refreshes lists and closes", async () => {
    post.mockResolvedValue({ data: {} })
    const { onClose, spy } = setup()
    fireEvent.change(screen.getByLabelText(/reason/i), { target: { value: "  Wrong tank  " } })
    fireEvent.click(screen.getByRole("button", { name: "Reverse entry" }))
    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(post).toHaveBeenCalledWith("/api/v1/fuel/deliveries/d1/reverse", { reason: "Wrong tank" })
    expect(spy).toHaveBeenCalledWith({ queryKey: ["deliveries"] })
    expect(spy).toHaveBeenCalledWith({ queryKey: ["tanks"] })
  })

  it("shows the server's message when the reversal is refused and stays open", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "Cannot reverse this receipt: some of that fuel has already been used." } } })
    const { onClose } = setup()
    fireEvent.change(screen.getByLabelText(/reason/i), { target: { value: "Entered twice" } })
    fireEvent.click(screen.getByRole("button", { name: "Reverse entry" }))
    expect((await screen.findByRole("alert")).textContent).toContain("some of that fuel has already been used")
    expect(onClose).not.toHaveBeenCalled()
  })

  it("Keep it closes without calling the server", () => {
    const { onClose } = setup()
    fireEvent.click(screen.getByRole("button", { name: "Keep it" }))
    expect(onClose).toHaveBeenCalled()
    expect(post).not.toHaveBeenCalled()
  })
})
