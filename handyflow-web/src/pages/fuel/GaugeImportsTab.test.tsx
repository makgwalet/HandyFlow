import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import GaugeImportsTab from "./GaugeImportsTab"
import { canAccept, onlyAliases, onlyGauges, tankAliasProblem } from "./gaugeOps"

const get = vi.fn(); const post = vi.fn(); const del = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const g = (o: any = {}) => ({ id: "g1", source: "acme-gauge", externalId: "G1", status: "PENDING", receivedAt: "2026-11-17T08:01:00Z", problems: [], tankLabel: "Diesel 1", tankId: "t1", tankName: "Diesel 1", litres: 8200, readAt: "2026-11-17T08:00:00Z", senderName: "Probe 7", decidedBy: null, decidedAt: null, decisionNote: null, dipReadingId: null, ...o })
function routes(items: unknown[], labels: unknown[] = [], tanks: unknown[] = [{ id: "t1", name: "Diesel 1" }], keys: unknown[] = []) {
  get.mockImplementation((url: string) => url.includes("/aliases") ? ok(labels) : url.includes("/keys") ? ok(keys) : url.includes("/api/v1/fuel/tanks") ? ok(tanks) : ok(items))
}
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><GaugeImportsTab /></QueryClientProvider>)
}

describe("GaugeImportsTab", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); del.mockReset(); perms = ["FUEL_MANAGE"]; post.mockResolvedValue({ data: { data: {} } }); del.mockResolvedValue({ data: {} }) })
  afterEach(cleanup)

  it("lists waiting readings and accepts one", async () => {
    routes([g()])
    mount()
    expect(await screen.findByText("Probe 7")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Accept" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/gauge-imports/g1/accept", {}))
  })

  it("will not accept a reading whose tank is not set up, and shows why", async () => {
    routes([g({ tankId: null, tankName: null, tankLabel: "T-9", problems: ["Tank \"T-9\" is not set up in Fuel."] })])
    mount()
    expect(await screen.findByText(/is not set up in Fuel/)).toBeTruthy()
    expect((screen.getByRole("button", { name: "Accept" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("rejects with a reason", async () => {
    routes([g()])
    mount()
    fireEvent.click(await screen.findByRole("button", { name: "Reject" }))
    const box = (await screen.findByRole("dialog")).querySelector("textarea, input") as HTMLElement
    fireEvent.change(box, { target: { value: "Probe was being serviced" } })
    fireEvent.click(screen.getAllByRole("button", { name: "Reject" }).pop()!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/gauge-imports/g1/reject", { reason: "Probe was being serviced" }))
  })

  it("maps a tank label and refuses one already mapped", async () => {
    routes([g()], [{ id: "a1", source: "acme-gauge", alias: "t-2", tankId: "t1", tankName: "Diesel 1", createdAt: "x" }])
    mount()
    await screen.findByText(/is Diesel 1/)
    fireEvent.change(screen.getByLabelText("Source"), { target: { value: "acme-gauge" } })
    fireEvent.change(screen.getByLabelText("Label the sender uses"), { target: { value: "T-2" } })
    await waitFor(() => expect(screen.getByRole("option", { name: "Diesel 1" })).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Fuel tank"), { target: { value: "t1" } })
    expect(screen.getByRole("alert").textContent).toMatch(/already mapped/)
    fireEvent.change(screen.getByLabelText("Label the sender uses"), { target: { value: "T-3" } })
    fireEvent.click(screen.getByRole("button", { name: "Add label" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/gauge-imports/aliases", { source: "acme-gauge", alias: "T-3", tankId: "t1" }))
  })

  it("creates a key and shows it once", async () => {
    routes([g()])
    post.mockResolvedValue({ data: { data: { key: { id: "k1", name: "Depot gauges", keyPrefix: "hf_fuel_abcd", createdAt: "x", lastUsedAt: null, revokedAt: null }, apiKey: "hf_fuel_secretvalue" } } })
    mount()
    fireEvent.change(await screen.findByLabelText("Key name"), { target: { value: "Depot gauges" } })
    fireEvent.click(screen.getByRole("button", { name: "Create key" }))
    expect((await screen.findByRole("status")).textContent).toMatch(/hf_fuel_secretvalue/)
    expect(post).toHaveBeenCalledWith("/api/v1/fuel/gauge-imports/keys", { name: "Depot gauges" })
  })

  it("hides review, labels and keys without permission", async () => {
    perms = []
    routes([g()])
    mount()
    await screen.findByText("Probe 7")
    expect(screen.queryByRole("button", { name: "Accept" })).toBeNull()
    expect(screen.queryByLabelText("Tank labels")).toBeNull()
    expect(screen.queryByLabelText("Keys for gauge systems")).toBeNull()
  })

  it("keeps only well-formed rows from whatever the server sent", () => {
    expect(onlyGauges([g(), { id: "x" }, null])).toHaveLength(1)
    expect(onlyGauges({})).toEqual([])
    expect(onlyAliases([{ id: "a", alias: "x", tankName: "T" }, { id: "b" }])).toHaveLength(1)
    expect(canAccept(g())).toBe(true); expect(canAccept(g({ status: "ACCEPTED" }))).toBe(false)
    expect(tankAliasProblem("", "a", "t", [])).toMatch(/source/)
  })
})
