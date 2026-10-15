import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), patch: vi.fn(), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import { HireContractDialog, ContractSettingsDialog } from "./HireContractDialog"
import DeploymentsTab from "./DeploymentsTab"

afterEach(cleanup)

const asset = { id: "a1", name: "Excavator", fleetNumber: "E-01", assetType: "EXCAVATOR", status: "DEPLOYED", currentHours: 10 } as never
const panel = (over: Record<string, unknown> = {}) => ({
  deploymentId: "d1", contract: null, addenda: [], expectedValue: 10000, threshold: 5000, expected: true,
  warning: "This hire is worth about R10000.00, which is at or above the R5000.00 from which a signed contract is expected, and no contract is linked to it.", ...over,
})
const signed = { id: "c1", number: "CON-0001", title: "Equipment hire: E-01", status: "SIGNED", startDate: null, endDate: null }
let current = panel()

const mount = (ui: React.ReactElement) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter>{ui}</MemoryRouter></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = ["EARTHMOVING_READ", "EARTHMOVING_DEPLOY"]
  current = panel()
  get.mockImplementation((url: string) => Promise.resolve(
    url.includes("/deployments/d1/contract") ? { data: { data: current } }
      : url.includes("/assets/a1/deployments") ? { data: { data: { content: [{ id: "d0", returnedAt: "2026-01-01T00:00:00Z" }, { id: "d1", returnedAt: null }], last: true } } }
      : url.includes("/api/v1/contracts") ? { data: { data: { content: [{ id: "c9", contractNumber: "CON-0009", title: "Old hire", status: "SENT" }] } } }
      : url.includes("/settings") ? { data: { data: { contractThreshold: 5000 } } }
      : { data: { data: { content: [], last: true } } }))
  post.mockImplementation((url: string) => {
    current = url.endsWith("/draft") ? panel({ contract: { ...signed, status: "DRAFT" }, warning: "The hire contract CON-0001 is still a draft, not signed." })
      : panel({ contract: signed, addenda: [{ id: "c2", number: "CON-0002", title: "Addendum", status: "DRAFT", startDate: null, endDate: null }], warning: null })
    return Promise.resolve({ data: { data: current } })
  })
  put.mockImplementation((url: string, body: Record<string, unknown>) =>
    Promise.resolve({ data: { data: url.includes("/settings") ? body : panel({ contract: { ...signed, status: "SENT" } }) } }))
})

describe("Hire contract panel", () => {
  it("warns that a big hire has no contract and offers to make a draft", async () => {
    mount(<HireContractDialog asset={asset} onClose={() => {}} />)
    expect(await screen.findByText(/no contract is linked to it/)).toBeTruthy()
    expect(screen.getByText(/This is a warning only/)).toBeTruthy()
    fireEvent.click(screen.getByText("Make a draft contract"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/earthmoving/deployments/d1/contract/draft", {}))
    expect(await screen.findByText("Draft")).toBeTruthy()
    expect(screen.getByText(/still a draft, not signed/)).toBeTruthy()
    expect(screen.queryByText("Make a draft contract")).toBeNull()
  })

  it("gives a reader no buttons to change anything", async () => {
    perms = ["EARTHMOVING_READ"]
    mount(<HireContractDialog asset={asset} onClose={() => {}} />)
    await screen.findByText(/no contract is linked to it/)
    expect(screen.queryByText("Make a draft contract")).toBeNull()
    expect(screen.queryByText("Link an existing contract")).toBeNull()
  })

  it("links an existing contract from the hire agreements in Contracting", async () => {
    mount(<HireContractDialog asset={asset} onClose={() => {}} />)
    fireEvent.click(await screen.findByText("Link an existing contract"))
    await screen.findByRole("option", { name: /CON-0009/ })
    fireEvent.change(screen.getByLabelText("Hire agreement"), { target: { value: "c9" } })
    fireEvent.click(screen.getByText("Link contract"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/earthmoving/deployments/d1/contract", { contractId: "c9" }))
  })

  it("records an extension only with a new date and a reason, and lists the addendum", async () => {
    current = panel({ contract: signed, warning: null })
    mount(<HireContractDialog asset={asset} onClose={() => {}} />)
    fireEvent.click(await screen.findByText("Record an extension"))
    const go = screen.getByText("Create addendum") as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/New end date/), { target: { value: "2026-12-01" } })
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "no" } })
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "Customer needs it longer" } })
    expect(go.disabled).toBe(false)
    fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/earthmoving/deployments/d1/contract/extension", { newEndDate: "2026-12-01", reason: "Customer needs it longer" }))
    expect(await screen.findByText(/CON-0002/)).toBeTruthy()
  })

  it("says when the machine has no open deployment", async () => {
    get.mockImplementation((url: string) => Promise.resolve(
      url.includes("/deployments") ? { data: { data: { content: [{ id: "d0", returnedAt: "2026-01-01T00:00:00Z" }], last: true } } } : { data: { data: [] } }))
    mount(<HireContractDialog asset={asset} onClose={() => {}} />)
    expect(await screen.findByText(/no open deployment/)).toBeTruthy()
  })
})

describe("Rates on the hire contract", () => {
  const ratesOf = (rates: unknown[]) => ({ deploymentId: "d1", contractId: "c1", contractNumber: "CON-0001", rates })
  const withRates = (rates: unknown[]) => {
    current = panel({ contract: signed, warning: null })
    get.mockImplementation((url: string) => Promise.resolve(
      url.includes("/contract-rates") ? { data: { data: ratesOf(rates) } }
        : url.includes("/deployments/d1/contract") ? { data: { data: current } }
        : url.includes("/assets/a1/deployments") ? { data: { data: { content: [{ id: "d1", returnedAt: null }], last: true } } }
        : { data: { data: [] } }))
  }

  it("lists the rates and the kilometres included, with no edit buttons for a reader", async () => {
    perms = ["EARTHMOVING_READ"]
    withRates([{ basis: "MONTHLY", unitRate: 30000, includedKm: null, updatedAt: null }, { basis: "KILOMETRE", unitRate: 12.5, includedKm: 500, updatedAt: null }])
    mount(<HireContractDialog asset={asset} onClose={() => {}} />)
    expect(await screen.findByText(/Per month:/)).toBeTruthy()
    expect(screen.getByText(/500 km included/)).toBeTruthy()
    expect(screen.queryByText("Save rate")).toBeNull()
    expect(screen.queryByText("Remove")).toBeNull()
  })

  it("lets an administrator set a kilometre rate with its allowance", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    withRates([])
    put.mockResolvedValue({ data: { data: ratesOf([{ basis: "KILOMETRE", unitRate: 12.5, includedKm: 500, updatedAt: null }]) } })
    mount(<HireContractDialog asset={asset} onClose={() => {}} />)
    await screen.findByText(/No rates yet/)
    const save = screen.getByText("Save rate") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Rate for"), { target: { value: "KILOMETRE" } })
    fireEvent.change(screen.getByLabelText("Rate (rand)"), { target: { value: "12.5" } })
    fireEvent.change(screen.getByLabelText("Kilometres included"), { target: { value: "500" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/earthmoving/deployments/d1/contract-rates", { basis: "KILOMETRE", unitRate: 12.5, includedKm: 500 }))
    expect(await screen.findByText(/Per kilometre:/)).toBeTruthy()
  })

  it("only offers the allowance on the kilometre rate, and a rate must be more than nothing", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    withRates([])
    mount(<HireContractDialog asset={asset} onClose={() => {}} />)
    await screen.findByText(/No rates yet/)
    expect(screen.queryByLabelText("Kilometres included")).toBeNull()
    fireEvent.change(screen.getByLabelText("Rate (rand)"), { target: { value: "0" } })
    expect((screen.getByText("Save rate") as HTMLButtonElement).disabled).toBe(true)
  })

  it("removes a rate", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    withRates([{ basis: "WEEKLY", unitRate: 9000, includedKm: null, updatedAt: null }])
    const del = vi.fn().mockResolvedValue({ data: { data: ratesOf([]) } })
    const client = await import("../../api/client")
    ;(client.apiClient as unknown as { delete: unknown }).delete = del
    mount(<HireContractDialog asset={asset} onClose={() => {}} />)
    fireEvent.click(await screen.findByLabelText("Remove Per week rate"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/earthmoving/deployments/d1/contract-rates/WEEKLY"))
  })

  it("has no rates section until a contract is linked", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount(<HireContractDialog asset={asset} onClose={() => {}} />)
    await screen.findByText(/no contract is linked to it/)
    expect(screen.queryByText("Rates on this contract")).toBeNull()
  })
})

describe("Contract threshold", () => {
  it("lets an administrator change it and clear it", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount(<ContractSettingsDialog onClose={() => {}} />)
    const input = (await screen.findByLabelText(/Threshold/)) as HTMLInputElement
    await waitFor(() => expect(input.value).toBe("5000"))
    fireEvent.change(input, { target: { value: "8000" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/earthmoving/settings", { contractThreshold: 8000 }))
  })
  it("sends null when emptied and refuses a negative amount", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount(<ContractSettingsDialog onClose={() => {}} />)
    const input = (await screen.findByLabelText(/Threshold/)) as HTMLInputElement
    await waitFor(() => expect(input.value).toBe("5000"))
    fireEvent.change(input, { target: { value: "-1" } })
    expect((screen.getByText("Save") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(input, { target: { value: "" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/earthmoving/settings", { contractThreshold: null }))
  })
  it("shows it read-only to anyone else", async () => {
    mount(<ContractSettingsDialog onClose={() => {}} />)
    const input = (await screen.findByLabelText(/Threshold/)) as HTMLInputElement
    expect(input.disabled).toBe(true)
    expect(screen.queryByText("Save")).toBeNull()
  })
})

describe("Deploy dialog contract warning", () => {
  it("asks for warnings with the customer, dates and site", async () => {
    get.mockImplementation((url: string) => Promise.resolve(
      url.includes("/deploy-warnings") ? { data: { data: { warnings: [] } } }
        : url.includes("/assets") ? { data: { data: { content: [{ id: "a1", name: "Excavator", fleetNumber: "E-01", assetType: "EXCAVATOR", status: "AVAILABLE", currentHours: 10 }], last: true } } }
        : { data: { data: { content: [], last: true } } }))
    mount(<DeploymentsTab />)
    fireEvent.click(await screen.findByText("Deploy Equipment", { selector: "button" }))
    await screen.findByText(/E-01/)
    fireEvent.change(screen.getAllByRole("combobox")[0], { target: { value: "a1" } })
    fireEvent.change(screen.getByLabelText("Start Date"), { target: { value: "2026-11-01" } })
    fireEvent.change(screen.getByLabelText("Expected Return Date"), { target: { value: "2026-11-10" } })
    await waitFor(() => expect(get.mock.calls.some(c => String(c[0]).includes("deploy-warnings?from=2026-11-01&to=2026-11-10"))).toBe(true))
  })
})
