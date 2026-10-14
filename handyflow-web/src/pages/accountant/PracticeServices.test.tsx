import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ClientLettersPanel from "./ClientLettersPanel"
import ServicesTab from "./ServicesTab"
import WorkloadTab from "./WorkloadTab"

const perms = vi.hoisted(() => ({ held: new Set<string>() }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.held.has(p) }))
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
afterEach(cleanup)
beforeEach(() => { Object.values(api).forEach(f => f.mockReset()); perms.held = new Set(["ACCOUNTANT_READ", "ACCOUNTANT_WRITE"]) })

const letter = (over: any = {}) => ({ id: "l1", clientId: "c1", version: 1, title: "Accounting", scope: "Books", exclusions: null, feeBasis: "RETAINER", feeAmount: 1500,
  feeNotes: null, startDate: "2026-11-01", endDate: null, status: "DRAFT", acceptedByName: null, acceptedOn: null, declineReason: null, ...over })

describe("ClientLettersPanel", () => {
  it("shows a sent letter and records the acceptance with who, how and when", async () => {
    api.get.mockResolvedValue({ data: { data: [letter({ status: "SENT" })] } })
    api.post.mockResolvedValue({ data: { data: letter({ status: "ACCEPTED" }) } })
    wrap(<ClientLettersPanel clientId="c1" />)
    fireEvent.click(await screen.findByText("Record acceptance"))
    fireEvent.change(screen.getByLabelText("Accepted by"), { target: { value: "Jo Client" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(api.post).toHaveBeenCalled())
    expect(api.post.mock.calls[0][0]).toBe("/api/v1/accountant/letters/l1/accept")
    expect(api.post.mock.calls[0][1]).toMatchObject({ acceptedByName: "Jo Client", method: "SIGNED_COPY" })
  })

  it("will not save a new letter without services and a retainer amount", async () => {
    api.get.mockResolvedValue({ data: { data: [] } })
    api.post.mockResolvedValue({ data: { data: letter() } })
    wrap(<ClientLettersPanel clientId="c1" />)
    fireEvent.click(await screen.findByText("New letter"))
    const save = screen.getByText("Save draft") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Services covered"), { target: { value: "Bookkeeping" } })
    fireEvent.change(screen.getByLabelText("Fee amount"), { target: { value: "1500" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/accountant/clients/c1/letters", expect.objectContaining({ scope: "Bookkeeping", feeAmount: 1500, feeBasis: "RETAINER" })))
  })

  it("offers no edit or send for a read-only user", async () => {
    perms.held = new Set(["ACCOUNTANT_READ"])
    api.get.mockResolvedValue({ data: { data: [letter()] } })
    wrap(<ClientLettersPanel clientId="c1" />)
    await screen.findByText(/Version 1/)
    expect(screen.queryByText("Mark as sent")).toBeNull()
    expect(screen.queryByText("New letter")).toBeNull()
    expect(screen.getByText("Show letter")).toBeTruthy()
  })
})

describe("ServicesTab", () => {
  const run = { id: "r1", scheduleId: "s1", clientId: "c1", clientName: "Acme", serviceType: "BOOKKEEPING", description: null, runDate: "2026-11-01", status: "DUE", feeAmount: 1500 }
  it("bills a due run after confirmation", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true)
    api.get.mockImplementation(async (url: string) => ({ data: { data: url.includes("service-runs") ? [run] : [] } }))
    api.post.mockResolvedValue({ data: { data: {} } })
    wrap(<ServicesTab />)
    fireEvent.click(await screen.findByText("Make fee note"))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/accountant/service-runs/r1/bill", {}))
  })
  it("skips a run only with a reason", async () => {
    const prompt = vi.spyOn(window, "prompt").mockReturnValueOnce("  ").mockReturnValueOnce("Client on hold")
    api.get.mockImplementation(async (url: string) => ({ data: { data: url.includes("service-runs") ? [run] : [] } }))
    api.post.mockResolvedValue({ data: { data: {} } })
    wrap(<ServicesTab />)
    fireEvent.click(await screen.findByText("Skip"))
    expect(api.post).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Skip"))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/accountant/service-runs/r1/skip", { note: "Client on hold" }))
    prompt.mockRestore()
  })
})

describe("WorkloadTab", () => {
  it("shows staff, WIP ageing and billed-against-time", async () => {
    api.get.mockImplementation(async (url: string) => {
      if (url.endsWith("/workload")) return { data: { data: [{ userId: "u1", name: "Ann Smith", clientsPrepared: 3, clientsReviewed: 1, clientsPartner: 0, deadlinesNext30Days: 2, deadlinesOverdue: 1, hoursLast30Days: 40, unbilledHours: 6, unbilledValue: 3000, serviceRunsDue: 2 }] } }
      if (url.includes("/wip")) return { data: { data: { totalValue: 3000, totalOver60: 500, rows: [{ clientId: "c1", clientName: "Acme", unbilledHours: 6, unbilledValue: 3000, current: 2500, days31to60: 0, days61to90: 500, over90: 0 }] } } }
      return { data: { data: { rows: [{ clientId: "c1", clientName: "Acme", hours: 10, standardValue: 5000, billed: 4000, realisationPercent: 80, budgetHours: 8, budgetUsedPercent: 125 }] } } }
    })
    wrap(<WorkloadTab />)
    expect(await screen.findByText("Ann Smith")).toBeTruthy()
    expect(await screen.findByText(/older than 60 days/)).toBeTruthy()
    expect(await screen.findByText("80% of standard value")).toBeTruthy()
    expect(screen.getByText("125% of 8 h")).toBeTruthy()
  })
})
