import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseSetupTab, { settingsProblem, trackingText, type WhseSettings } from "./WhseSetupTab"

const get = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a) } }))

const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const settings = (over: Partial<WhseSettings> = {}): WhseSettings => ({
  profileExists: true, operatingModel: "THREE_PL", industryProfile: "GENERAL", defaultLotTracked: false, defaultExpiryTracked: false, activeClients: 1,
  operatingModels: [{ value: "INTERNAL", label: "Internal: your own stock only" }, { value: "THREE_PL", label: "3PL: stock of several customers" }, { value: "HYBRID", label: "Hybrid: your own stock and customers'" }],
  industryProfiles: [{ value: "GENERAL", label: "General" }, { value: "PHARMA", label: "Pharmaceuticals and medical supplies" }, { value: "FOOD", label: "Food and beverages" }, { value: "AGRI", label: "Agriculture inputs" }], ...over })

beforeEach(() => { get.mockReset(); put.mockReset(); put.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("setup helpers", () => {
  it("internal is refused while several accounts are open, and a profile must exist", () => {
    expect(settingsProblem({ profileExists: false, activeClients: 0 }, "HYBRID")).toMatch(/profile first/)
    expect(settingsProblem({ profileExists: true, activeClients: 3 }, "INTERNAL")).toMatch(/3 client accounts/)
    expect(settingsProblem({ profileExists: true, activeClients: 1 }, "INTERNAL")).toBeNull()
    expect(settingsProblem({ profileExists: true, activeClients: 9 }, "THREE_PL")).toBeNull()
  })
  it("says what new items start with", () => {
    expect(trackingText(false, false)).toMatch(/no lot or expiry/)
    expect(trackingText(true, true)).toMatch(/lot and expiry/)
    expect(trackingText(true, false)).toMatch(/lot tracking/)
  })
})

describe("WhseSetupTab", () => {
  it("shows the current choices and saves a change", async () => {
    get.mockResolvedValue({ data: settings() })
    wrap(<WhseSetupTab />)
    await screen.findByText("Operating model")
    expect((screen.getByText("Save") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByLabelText(/Hybrid/))
    fireEvent.change(screen.getByLabelText("Industry profile"), { target: { value: "PHARMA" } })
    expect(screen.getByText(/lot and expiry tracking switched on/)).toBeTruthy()
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/warehousing/settings", { operatingModel: "HYBRID", industryProfile: "PHARMA" }))
  })

  it("will not save internal while several client accounts are open", async () => {
    get.mockResolvedValue({ data: settings({ activeClients: 4 }) })
    wrap(<WhseSetupTab />)
    await screen.findByText("Operating model")
    fireEvent.click(screen.getByLabelText(/Internal/))
    expect(screen.getByRole("alert").textContent).toMatch(/4 client accounts/)
    expect((screen.getByText("Save") as HTMLButtonElement).disabled).toBe(true)
  })

  it("asks for the warehouse profile first when there is none", async () => {
    get.mockResolvedValue({ data: settings({ profileExists: false, activeClients: 0 }) })
    wrap(<WhseSetupTab />)
    await screen.findByText("Operating model")
    fireEvent.click(screen.getByLabelText(/Hybrid/))
    expect(screen.getByRole("alert").textContent).toMatch(/profile first/)
  })
})
