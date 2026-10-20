import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { StageSelector } from "./StageSelector"

const get = vi.fn(), patch = vi.fn()
type Args = unknown[]
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), patch: (...a: Args) => patch(...a) } }))
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><StageSelector customerId="c1" customerType="LEAD" /></QueryClientProvider>)
beforeEach(() => { get.mockReset(); patch.mockReset(); get.mockResolvedValue({ data: { customerId: "c1", customerType: "LEAD", stage: "NEW" } }); patch.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("stage selector in the customer window", () => {
  it("asks why before marking lost, then sends the reason", async () => {
    show(); fireEvent.click(await screen.findByText("Lost")); expect(patch).not.toHaveBeenCalled()
    expect((screen.getByText("Mark lost") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Lost reason"), { target: { value: "PRICE" } }); fireEvent.click(screen.getByText("Mark lost"))
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/crm/customers/c1/stage", { stage: "LOST", lostReason: "PRICE", lostNote: null }))
  })
  it("moves straight to an open stage and shows a server refusal", async () => {
    patch.mockRejectedValue({ response: { data: { message: "Cannot set a pipeline stage on a non-lead customer" } } })
    show(); fireEvent.click(await screen.findByText("Contacted")); expect((await screen.findByRole("alert")).textContent).toMatch(/non-lead/)
    expect(patch).toHaveBeenCalledWith("/api/v1/crm/customers/c1/stage", { stage: "CONTACTED" })
  })
  it("blocks won from lost until the lead is reopened", async () => {
    get.mockResolvedValue({ data: { customerId: "c1", customerType: "LEAD", stage: "LOST" } }); show()
    await waitFor(() => expect((screen.getByText("Won") as HTMLButtonElement).disabled).toBe(true)); expect((screen.getByText("New") as HTMLButtonElement).disabled).toBe(false)
  })
})
