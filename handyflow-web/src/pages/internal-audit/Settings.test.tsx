import { describe, it, expect, vi, afterEach, beforeEach } from "vitest"
import { render, screen, fireEvent, cleanup, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

vi.mock("../../api/auditSettings.api", () => ({ auditSettingsApi: { get: vi.fn(), save: vi.fn(), reset: vi.fn() } }))
import { auditSettingsApi } from "../../api/auditSettings.api"
import { useAuthStore } from "../../store/auth.store"
import { SettingsSection } from "./SettingsSection"

const m = auditSettingsApi as unknown as Record<"get" | "save" | "reset", ReturnType<typeof vi.fn>>
afterEach(cleanup)
beforeEach(() => { vi.clearAllMocks(); useAuthStore.setState({ user: null }) })
const mount = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SettingsSection /></QueryClientProvider>)
const as = (...permissions: string[]) => useAuthStore.setState({ user: { permissions } as never })

const std = { lowMax: 10, mediumMax: 15, highMax: 20, escalateAfterDays: 14, repeatEveryDays: 7, firstReviewReminderDay: 3 }
const settings = (v = std, custom = false) => ({ scoreMin: 5, scoreMax: 25, riskInputs: ["Inherent risk"], escalateAfterDays: v.escalateAfterDays, firstReviewReminderDay: v.firstReviewReminderDay, repeatEveryDays: v.repeatEveryDays,
  riskBands: [{ level: "LOW", from: 5, to: v.lowMax }, { level: "MEDIUM", from: v.lowMax + 1, to: v.mediumMax }, { level: "HIGH", from: v.mediumMax + 1, to: v.highMax }, { level: "CRITICAL", from: v.highMax + 1, to: 25 }],
  engagementRoles: [{ key: "AUDITOR", label: "Auditor" }], values: v, defaults: std, custom })

describe("Audit settings", () => {
  it("is read only for someone without audit admin rights", async () => {
    as("AUDIT_READ"); m.get.mockResolvedValue(settings())
    mount()
    expect(await screen.findByText(/The Head of Internal Audit can change them/)).toBeTruthy()
    expect(screen.queryByLabelText("Low ends at")).toBeNull()
    expect(screen.queryByText("Save changes")).toBeNull()
  })

  it("lets the admin change the bands, blocks bad numbers and saves good ones", async () => {
    as("AUDIT_READ", "AUDIT_ADMIN"); m.get.mockResolvedValue(settings())
    m.save.mockResolvedValue(settings({ ...std, lowMax: 12 }, true))
    mount()
    const low = await screen.findByLabelText("Low ends at")
    const save = screen.getByText("Save changes") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(low, { target: { value: "16" } })
    expect((await screen.findByRole("alert")).textContent).toContain("Medium must end above where Low ends")
    expect(save.disabled).toBe(true)
    fireEvent.change(low, { target: { value: "12" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(m.save).toHaveBeenCalled())
    expect(m.save.mock.calls[0][0]).toEqual({ ...std, lowMax: 12 })
    expect((await screen.findByRole("status")).textContent).toContain("Saved")
  })

  it("offers to go back to the standard rules only when they have been changed", async () => {
    as("AUDIT_ADMIN"); m.get.mockResolvedValue(settings({ ...std, lowMax: 12 }, true)); m.reset.mockResolvedValue(settings())
    mount()
    fireEvent.click(await screen.findByText("Go back to standard rules"))
    await waitFor(() => expect(m.reset).toHaveBeenCalled())
    expect((await screen.findByRole("status")).textContent).toContain("standard rules")
  })

  it("says so when the settings cannot be loaded", async () => {
    as("AUDIT_ADMIN"); m.get.mockRejectedValue(new Error("x"))
    mount()
    expect((await screen.findByRole("alert")).textContent).toContain("could not be loaded")
  })
})
