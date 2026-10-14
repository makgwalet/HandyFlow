import { describe, it, expect, vi, beforeEach } from "vitest"
import { render, screen, waitFor, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ClientSignoffPanel from "./ClientSignoffPanel"

const perms = vi.hoisted(() => ({ held: new Set<string>() }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.held.has(p) }))
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), delete: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))

const year = (status: string) => ({ financialYear: 2026, yearStart: "2025-03-01", yearEnd: "2026-02-28", status, unpostedJournals: 0, warnings: [] })

function mount(status: string) {
  api.get.mockImplementation(async (url: string) => {
    if (url.endsWith("/engagements")) return { data: { data: [year(status)] } }
    if (url.endsWith("/assignments")) return { data: { data: [{ id: "a1", userId: "u1", userName: "Thandi Mokoena", role: "PREPARER" }] } }
    return { data: { content: [] } }
  })
  api.post.mockResolvedValue({ data: {} })
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ClientSignoffPanel clientId="c1" /></QueryClientProvider>)
}

describe("ClientSignoffPanel", () => {
  beforeEach(() => { vi.clearAllMocks(); perms.held = new Set() })

  it("shows the team and the year status", async () => {
    mount("PREPARED")
    expect(await screen.findByText(/Thandi Mokoena/)).toBeTruthy()
    expect(screen.getByText("Prepared")).toBeTruthy()
  })

  it("marks a year prepared with write permission", async () => {
    perms.held = new Set(["ACCOUNTANT_WRITE"])
    mount("OPEN")
    fireEvent.click(await screen.findByText("Mark prepared"))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/accountant/clients/c1/engagements/2026/prepare", { note: null }))
  })

  it("hides sign-off without the sign-off permission", async () => {
    perms.held = new Set(["ACCOUNTANT_WRITE"])
    mount("REVIEWED")
    await screen.findByText("Reviewed")
    expect(screen.queryByText("Sign off")).toBeNull()
    expect(screen.getByText("Send back")).toBeTruthy()
  })

  it("will not send back until a reason is given", async () => {
    perms.held = new Set(["ACCOUNTANT_WRITE"])
    mount("PREPARED")
    fireEvent.click(await screen.findByText("Send back"))
    const confirm = screen.getByText(/Confirm: Send back/) as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "bank rec not finished" } })
    expect(confirm.disabled).toBe(false)
    fireEvent.click(confirm)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/accountant/clients/c1/engagements/2026/reopen", { note: "bank rec not finished" }))
  })

  it("shows the refusal when the person is not assigned", async () => {
    perms.held = new Set(["ACCOUNTANT_WRITE"])
    mount("OPEN")
    api.post.mockRejectedValue({ response: { status: 403, data: {} } })
    fireEvent.click(await screen.findByText("Mark prepared"))
    expect(await screen.findByRole("alert")).toBeTruthy()
  })
})
