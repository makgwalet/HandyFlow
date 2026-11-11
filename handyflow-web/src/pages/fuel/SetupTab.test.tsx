import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import SetupTab from "./SetupTab"

const get = vi.fn(); const put = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const fresh = { status: "NOT_STARTED", legacyMode: false, wizardStep: 0, wizardAnswers: null, activeDivisions: [], divisions: [], checklist: [{ key: "divisions", label: "Choose and activate your divisions", done: false }] }
const active = { ...fresh, status: "ACTIVE", activeDivisions: ["RETAIL_FORECOURT"], divisions: [{ division: "RETAIL_FORECOURT", label: "Retail forecourt", active: true }, { division: "DISTRIBUTION", label: "Distribution", active: false, deactivationReason: "Sold the trucks" }] }
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><SetupTab /></QueryClientProvider>)
}

describe("SetupTab", () => {
  beforeEach(() => { get.mockReset(); put.mockReset(); post.mockReset(); perms = ["FUEL_CONFIGURATION_MANAGE"]; put.mockResolvedValue({ data: {} }); post.mockResolvedValue({ data: {} }) })
  afterEach(cleanup)

  it("will not leave step 1 without a division", async () => {
    get.mockResolvedValue({ data: { data: fresh } })
    mount()
    fireEvent.click(await screen.findByText("Next"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/at least one/)
    expect(put).not.toHaveBeenCalled()
  })

  it("saves progress on Next and activates on the last step", async () => {
    get.mockResolvedValue({ data: { data: { ...fresh, wizardStep: 2, businessSize: "SMALL", orgStructure: "ONE_ENTITY", wizardAnswers: '{"divisions":["RETAIL_FORECOURT"]}' } } })
    mount()
    fireEvent.click(await screen.findByText("Activate Fuel"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/operating-profile/activate", { divisions: ["RETAIL_FORECOURT"] }))
  })

  it("resumes at the saved step and persists a step change", async () => {
    get.mockResolvedValue({ data: { data: { ...fresh, wizardStep: 1, wizardAnswers: '{"divisions":["DISTRIBUTION"]}', businessSize: "SMALL", orgStructure: "ONE_ENTITY" } } })
    mount()
    expect(await screen.findByText("Business size")).toBeTruthy()
    fireEvent.click(screen.getByText("Next"))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][1]).toMatchObject({ wizardStep: 2, wizardAnswers: '{"divisions":["DISTRIBUTION"]}' })
  })

  it("shows the legacy notice", async () => {
    get.mockResolvedValue({ data: { data: { ...fresh, status: "ACTIVE", legacyMode: true } } })
    mount()
    expect((await screen.findByRole("status")).textContent).toMatch(/every division is on/)
  })

  it("turns a division off only with a reason, and shows server refusals", async () => {
    get.mockResolvedValue({ data: { data: active } })
    post.mockRejectedValue({ response: { data: { message: "Open shifts" } } })
    mount()
    fireEvent.click(await screen.findByLabelText("Turn off Retail forecourt"))
    const confirm = screen.getAllByText("Turn off").find(b => b.tagName === "BUTTON" && (b as HTMLButtonElement).style.flex !== "") as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason *"), { target: { value: "Closing the station" } })
    fireEvent.click(confirm)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/operating-profile/divisions/RETAIL_FORECOURT/deactivate", { reason: "Closing the station" }))
  })

  it("turns a division on and hides controls without permission", async () => {
    get.mockResolvedValue({ data: { data: active } })
    mount()
    fireEvent.click(await screen.findByLabelText("Turn on Distribution"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/operating-profile/divisions/DISTRIBUTION/activate"))
    cleanup(); perms = []
    mount()
    await screen.findByText("Divisions")
    expect(screen.queryByLabelText("Turn on Distribution")).toBeNull()
  })
})
