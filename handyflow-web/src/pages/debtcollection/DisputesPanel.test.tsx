import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import DisputesPanel from "./DisputesPanel"

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))

const dispute = (over: any = {}) => ({
  id: "d1", reasonType: "AMOUNT", description: "Says the price was agreed lower", amountDisputed: 200, status: "OPEN",
  raisedOn: "2026-10-01", responseDue: "2026-10-15", overdue: false, ownerUserName: "Thandi", ...over,
})

function mount(list: any[], canManage = true) {
  api.get.mockResolvedValue({ data: { data: list } })
  api.post.mockResolvedValue({ data: {} })
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <DisputesPanel caseId="c1" invoiceIds={["11111111-aaaa"]} canManage={canManage} />
  </QueryClientProvider>)
}

describe("DisputesPanel", () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(() => cleanup())

  it("says collection is paused while a dispute is open", async () => {
    mount([dispute()])
    expect(await screen.findByText(/1 open, collection paused/)).toBeTruthy()
    expect(screen.getByText("Says the price was agreed lower")).toBeTruthy()
  })

  it("hides actions without manage permission", async () => {
    mount([dispute()], false)
    await screen.findByText(/Says the price/)
    expect(screen.queryByText("Resolve")).toBeNull()
    expect(screen.queryByText("+ Record dispute")).toBeNull()
  })

  it("records a dispute once the form is complete", async () => {
    mount([])
    fireEvent.click(await screen.findByText("+ Record dispute"))
    const send = screen.getByText("Record dispute and pause case") as HTMLButtonElement
    expect(send.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "AMOUNT" } })
    fireEvent.change(screen.getByLabelText("Description"), { target: { value: "Price was agreed lower" } })
    expect(send.disabled).toBe(false)
    fireEvent.click(send)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/debtcollection/cases/c1/disputes",
      { reasonType: "AMOUNT", invoiceId: null, description: "Price was agreed lower", amountDisputed: null }))
  })

  it("will not resolve an upheld dispute without an amount", async () => {
    mount([dispute()])
    fireEvent.click(await screen.findByText("Resolve"))
    fireEvent.change(screen.getByLabelText("Outcome"), { target: { value: "UPHELD" } })
    fireEvent.change(screen.getByLabelText("Outcome note"), { target: { value: "Credit agreed with debtor" } })
    const confirm = screen.getByText("Confirm resolution") as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Amount conceded"), { target: { value: "200" } })
    expect(confirm.disabled).toBe(false)
    fireEvent.click(confirm)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/debtcollection/disputes/d1/resolve",
      { resolution: "UPHELD", note: "Credit agreed with debtor", amountConceded: 200, creditNoteRef: null }))
  })

  it("shows the server's message when it refuses", async () => {
    mount([dispute()])
    api.post.mockRejectedValue({ response: { data: { message: "This dispute is already resolved." } } })
    fireEvent.click(await screen.findByText("Take into review"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/already resolved/)
  })
})
