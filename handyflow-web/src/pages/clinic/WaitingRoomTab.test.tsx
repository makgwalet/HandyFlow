import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, render, screen, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
import WaitingRoomTab, { buildBoard, timeNote } from "./WaitingRoomTab"

const at = (h: number, m = 0, day = 7) => new Date(2026, 9, day, h, m).toISOString()
const a = (id: string, status: string, when: string, name = id) => ({ id, status, scheduledAt: when, patientName: name, practitionerName: "Mokoena" })
const now = new Date(2026, 9, 7, 10, 30)

describe("buildBoard", () => {
  it("groups today's appointments by status, earliest first, and leaves out other days and finished ones", () => {
    const b = buildBoard([
      a("late", "SCHEDULED", at(11)), a("early", "CONFIRMED", at(9)), a("w", "CHECKED_IN", at(10)),
      a("t", "TRIAGED", at(10, 5)), a("s", "IN_PROGRESS", at(10, 15)),
      a("done", "COMPLETED", at(8)), a("cx", "CANCELLED", at(9)), a("ns", "NO_SHOW", at(9)),
      a("tomorrow", "SCHEDULED", at(9, 0, 8)), a("yesterday", "CHECKED_IN", at(9, 0, 6)),
    ], now)
    expect(b.expected.map(x => x.id)).toEqual(["early", "late"])
    expect(b.waiting.map(x => x.id)).toEqual(["w"])
    expect(b.triaged.map(x => x.id)).toEqual(["t"])
    expect(b.with.map(x => x.id)).toEqual(["s"])
  })
  it("ignores appointments with no time", () => {
    expect(buildBoard([{ ...a("x", "SCHEDULED", ""), scheduledAt: "" }], now).expected).toEqual([])
  })
})

describe("timeNote", () => {
  it("says how far past or ahead of the scheduled time it is", () => {
    expect(timeNote(at(10, 18), now)).toMatch(/12 min past/)
    expect(timeNote(at(10, 50), now)).toMatch(/in 20 min/)
    expect(timeNote(at(10, 30), now)).toMatch(/now/)
  })
})

describe("WaitingRoomTab", () => {
  beforeEach(() => { get.mockReset() })
  afterEach(cleanup)
  const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WaitingRoomTab /></QueryClientProvider>)

  it("shows each patient in the column for their status", async () => {
    const today = new Date()
    const t = (h: number) => new Date(today.getFullYear(), today.getMonth(), today.getDate(), h, 0).toISOString()
    get.mockResolvedValue({ data: { data: [a("1", "CHECKED_IN", t(9), "Ann Smith"), a("2", "SCHEDULED", t(23), "Bob Jones")] } })
    show()
    await screen.findByText("Ann Smith")
    const waiting = screen.getByRole("region", { name: "Waiting" })
    expect(within(waiting).getByText("Ann Smith")).toBeTruthy()
    expect(within(screen.getByRole("region", { name: "Expected" })).getByText("Bob Jones")).toBeTruthy()
  })
  it("says so when nobody is here", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    show()
    expect(await screen.findByText(/Nobody is expected/)).toBeTruthy()
  })
  it("says the board could not load rather than showing it empty", async () => {
    get.mockImplementation(() => Promise.reject(new Error("x")))
    show()
    expect(await screen.findByRole("alert")).toBeTruthy()
  })
})
