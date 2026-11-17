import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import AlertRecipients from "./AlertRecipients"

const get = vi.fn(); const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const users = [{ id: "u1", name: "Thabo M · t@x.co" }, { id: "u2", name: "Lerato K · l@x.co" }]
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><AlertRecipients users={users} /></QueryClientProvider>)
}

describe("AlertRecipients", () => {
  beforeEach(() => { get.mockReset(); put.mockReset(); put.mockResolvedValue({ data: {} }) })
  afterEach(cleanup)

  it("shows administrators only by default", async () => {
    get.mockImplementation(() => ok([{ topic: "VARIANCE", label: "Delivery variances", userIds: [] }]))
    mount()
    expect(await screen.findByText("Administrators only")).toBeTruthy()
  })

  it("saves the chosen people for the topic", async () => {
    get.mockImplementation(() => ok([{ topic: "VARIANCE", label: "Delivery variances", userIds: [] }]))
    mount()
    fireEvent.click(await screen.findByLabelText("Change who is told about Delivery variances"))
    fireEvent.click(await screen.findByLabelText(/Lerato K/))
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/alert-recipients/VARIANCE", { userIds: ["u2"] }))
  })

  it("shows the server's reason when a person is refused", async () => {
    get.mockImplementation(() => ok([{ topic: "VARIANCE", label: "Delivery variances", userIds: [] }]))
    put.mockRejectedValue({ response: { data: { message: "Every person chosen must be an active user of this company with an email address." } } })
    mount()
    fireEvent.click(await screen.findByLabelText("Change who is told about Delivery variances"))
    fireEvent.click(await screen.findByLabelText(/Thabo M/))
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    expect(await screen.findByRole("alert")).toBeTruthy()
  })

  it("shows nothing when the answer is not a list of topics", async () => {
    get.mockImplementation(() => ok([{ id: "x" }]))
    mount()
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(screen.queryByText("Who is told about alerts")).toBeNull()
  })
})
