import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ShiftControls from "./ShiftControls"
import AutoAcceptLog from "./AutoAcceptLog"

const get = vi.fn(), put = vi.fn(), del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>{ui}</QueryClientProvider>)

describe("ShiftControls", () => {
  beforeEach(() => {
    get.mockReset(); put.mockReset(); del.mockReset(); put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} })
    get.mockImplementation((u: string) => u.includes("/depots") ? ok([{ id: "d1", name: "Main depot" }])
      : u.includes("/shift-controls") ? ok({ overrides: [{ scope: "PRODUCT", key: "DIESEL", label: "DIESEL", amount: 200 }], policy: { repeatCount: null, repeatDays: null } }) : ok(null))
  })
  afterEach(cleanup)

  it("shows what is set, in plain words", async () => {
    wrap(<ShiftControls />)
    expect(await screen.findByText(/DIESEL: gauge difference up to 200 L/)).toBeTruthy()
    expect(screen.getByText(/Off: repeated variances are not flagged/)).toBeTruthy()
  })

  it("sets a tolerance for a site", async () => {
    wrap(<ShiftControls />)
    await screen.findByText(/DIESEL: gauge difference/)
    await waitFor(() => expect(screen.getByRole("option", { name: "Main depot" })).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Site"), { target: { value: "d1" } })
    fireEvent.change(screen.getByLabelText("Cash variance tolerance for the site"), { target: { value: "50" } })
    fireEvent.click(screen.getByText("Set for site"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/shift-controls/overrides", { scope: "DEPOT", key: "d1", amount: 50 }))
  })

  it("removes an override", async () => {
    wrap(<ShiftControls />)
    fireEvent.click(await screen.findByText("Remove"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/fuel/shift-controls/overrides", { params: { scope: "PRODUCT", key: "DIESEL" } }))
  })

  it("will not save a repeat rule of fewer than 2 shifts, and saves a valid one", async () => {
    wrap(<ShiftControls />)
    await screen.findByText(/DIESEL: gauge difference/)
    fireEvent.change(screen.getByLabelText("Number of shifts"), { target: { value: "1" } })
    fireEvent.change(screen.getByLabelText("Number of days"), { target: { value: "7" } })
    expect((screen.getByText("Save rule") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Number of shifts"), { target: { value: "3" } })
    fireEvent.click(screen.getByText("Save rule"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/shift-controls/repeat-policy", { repeatCount: 3, repeatDays: 7 }))
  })

  it("shows the server's reason when a save is refused", async () => {
    put.mockRejectedValue({ response: { data: { message: "Choose a depot or a fuel type." } } })
    wrap(<ShiftControls />)
    await screen.findByText(/DIESEL: gauge difference/)
    fireEvent.change(screen.getByLabelText("Fuel type"), { target: { value: "petrol" } })
    fireEvent.change(screen.getByLabelText("Gauge difference for the fuel type"), { target: { value: "100" } })
    fireEvent.click(screen.getByText("Set for fuel type"))
    expect(await screen.findByText("Choose a depot or a fuel type.")).toBeTruthy()
  })
})

describe("AutoAcceptLog", () => {
  afterEach(cleanup)
  it("lists shifts the system accepted, with variance and tolerance", async () => {
    get.mockReset(); get.mockImplementation(() => ok([{ id: "l1", shiftId: "s1", shiftNumber: "SH-1", attendantName: "Thabo", varianceAmount: 0, toleranceApplied: 5, actor: "SYSTEM", acceptedAt: "2026-11-17T10:30:00Z" }]))
    wrap(<AutoAcceptLog />)
    expect(await screen.findByText(/SH-1 · Thabo · variance R 0.00 against a tolerance of R 5.00/)).toBeTruthy()
  })
  it("shows nothing when there are none", async () => {
    get.mockReset(); get.mockImplementation(() => ok([]))
    const { container } = wrap(<AutoAcceptLog />)
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
})
