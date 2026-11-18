import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ExecutionModal from "./ExecutionModal"
import { figureNotes } from "./executionOps"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const exec = (o: any = {}) => ({ deliveryId: "d1", status: "DELIVERED", driverName: "Thabo", vehicleReg: "AB 12 CD GP", receiverName: "Sipho", signedOnBehalf: false, litresOrdered: 10000, litresLoaded: 10000, litresDelivered: 9950, meterStart: 100, meterEnd: 10050, failedAttempts: 1,
  events: [
    { at: "2026-11-18T05:00:00Z", type: "SCHEDULED", text: "Load created", severity: "OK" },
    { at: "2026-11-18T09:00:00Z", type: "ARRIVAL_CHECK", text: "Arrival check blocked: Tanker cert expired", severity: "PROBLEM" },
    { at: "2026-11-18T11:00:00Z", type: "DELIVERED", text: "Delivered 9950 L, received by Sipho", severity: "OK" }], ...o })
const mount = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ExecutionModal deliveryId="d1" label="RC-1" onClose={() => {}} /></QueryClientProvider>)

describe("figureNotes", () => {
  it("points out differences between loaded, delivered and the meter, and failed attempts", () => {
    expect(figureNotes(exec())).toEqual(["50 L less delivered than loaded.", "1 failed attempt."])
    const flat = (a: string[]) => a.map(t => t.replace(/\s/g, " "))
    expect(flat(figureNotes(exec({ litresDelivered: 10000, failedAttempts: 0, meterEnd: 9000 })))).toEqual(["The meter shows 8 900 L against 10 000 L recorded."])
  })
  it("says nothing when the figures agree", () => {
    expect(figureNotes(exec({ litresDelivered: 10000, meterEnd: 10100, failedAttempts: 0 }))).toEqual([])
  })
})

describe("ExecutionModal", () => {
  beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: { data: exec() } }) })
  afterEach(cleanup)

  it("lists what happened in order, with the figures to look at", async () => {
    mount()
    const list = await screen.findByRole("list", { name: "Timeline" })
    expect(Array.from(list.querySelectorAll("li")).map(li => li.textContent)).toEqual([
      expect.stringContaining("Load created"), expect.stringContaining("Tanker cert expired"), expect.stringContaining("received by Sipho")])
    expect(screen.getByRole("list", { name: "Points to look at" })).toBeTruthy()
    expect(get.mock.calls[0][0]).toBe("/api/v1/fuel/deliveries/d1/execution")
  })

  it("says so when nothing is recorded or loading fails", async () => {
    get.mockResolvedValue({ data: { data: exec({ events: [], failedAttempts: 0, litresDelivered: null, litresLoaded: null }) } })
    mount()
    expect(await screen.findByText("Nothing has been recorded for this load yet.")).toBeTruthy()
    cleanup(); get.mockRejectedValue(new Error("x")); mount()
    expect(await screen.findByRole("alert")).toBeTruthy()
  })
})
