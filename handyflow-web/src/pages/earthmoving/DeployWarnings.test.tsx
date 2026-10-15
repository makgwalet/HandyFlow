import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import DeploymentsTab from "./DeploymentsTab"

afterEach(cleanup)

beforeEach(() => {
  vi.clearAllMocks()
  perms = ["EARTHMOVING_READ", "EARTHMOVING_DEPLOY"]
  get.mockImplementation((url: string) => Promise.resolve(
    url.includes("/deploy-warnings") ? { data: { data: { warnings: ["Service overdue: 250 hour service.", "This machine has never been inspected."] } } }
      : url.includes("/assets") ? { data: { data: { content: [{ id: "a1", name: "Excavator", fleetNumber: "E-01", assetType: "EXCAVATOR", status: "AVAILABLE", currentHours: 10 }], last: true } } }
      : { data: { data: { content: [], last: true } } }))
})

describe("Deployment warnings", () => {
  it("shows warnings for the chosen machine and still lets the deployment go ahead", async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={qc}><DeploymentsTab /></QueryClientProvider>)
    fireEvent.click(await screen.findByText(/Deploy Equipment|Deploy/, { selector: "button" }))
    await screen.findByText(/E-01/)
    fireEvent.change(screen.getAllByRole("combobox")[0], { target: { value: "a1" } })
    expect(await screen.findByText("Service overdue: 250 hour service.")).toBeTruthy()
    expect(screen.getByText("This machine has never been inspected.")).toBeTruthy()
    expect(screen.getByText(/You can still deploy/)).toBeTruthy()
  })
})
