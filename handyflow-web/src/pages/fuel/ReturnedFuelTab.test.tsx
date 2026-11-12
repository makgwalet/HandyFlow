import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ReturnedFuelTab from "./ReturnedFuelTab"

const get = vi.fn(), post = vi.fn()
let can = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => can }))

const row = (o: any = {}) => ({ deliveryId: "d1", customerName: "Acme Mining", vehicleReg: "GP 12 AB", driverName: "Sipho", loadedLitres: 1000, declaredReturned: 600, confirmedBack: 250, outstanding: 350, lastAttemptAt: "2026-11-10T08:00:00Z", ...o })
function mount() { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ReturnedFuelTab /></QueryClientProvider>) }

describe("ReturnedFuelTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); can = true
    get.mockImplementation(async (u: string) => ({ data: { data: u.includes("outstanding") ? [row()] : [{ id: "t1", name: "Depot tank 1" }] } }))
    post.mockResolvedValue({ data: { data: {} } })
  })
  afterEach(cleanup)

  it("lists loads with fuel still to confirm and says it changes no stock", async () => {
    mount()
    expect(await screen.findByText(/Acme Mining/)).toBeTruthy()
    expect(screen.getByText(/does not change any tank's stock/)).toBeTruthy()
  })
  it("confirms into a chosen tank with the litres and a request id", async () => {
    mount()
    fireEvent.click(await screen.findByText("Confirm into a tank"))
    await screen.findByText("Depot tank 1")
    fireEvent.change(screen.getByLabelText(/Tank/), { target: { value: "t1" } })
    fireEvent.change(screen.getByLabelText(/Litres put into the tank/), { target: { value: "300" } })
    fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/fuel/deliveries/d1/returns")
    expect(post.mock.calls[0][1]).toMatchObject({ tankId: "t1", litres: 300 }); expect(post.mock.calls[0][1].clientRequestId).toBeTruthy()
  })
  it("will not send more than is still to confirm", async () => {
    mount()
    fireEvent.click(await screen.findByText("Confirm into a tank"))
    await screen.findByText("Depot tank 1")
    fireEvent.change(screen.getByLabelText(/Tank/), { target: { value: "t1" } })
    fireEvent.change(screen.getByLabelText(/Litres put into the tank/), { target: { value: "351" } })
    fireEvent.click(screen.getByText("Confirm"))
    expect(await screen.findByText(/Only 350 L/)).toBeTruthy(); expect(post).not.toHaveBeenCalled()
  })
  it("people who can only read see the list without the button, and an empty list says so", async () => {
    can = false; mount()
    await screen.findByText(/Acme Mining/); expect(screen.queryByText("Confirm into a tank")).toBeNull()
    cleanup(); can = true
    get.mockResolvedValue({ data: { data: [] } }); mount()
    expect(await screen.findByText(/Nothing waiting/)).toBeTruthy()
  })
})
