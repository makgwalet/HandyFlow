import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))

import { useAuthStore } from "../../store/auth.store"
import { todaySast } from "./weeklyHoursLogic"
vi.mock("./companyZone", async orig => ({ ...(await orig<typeof import("./companyZone")>()), useCompanyZone: () => ({ zone: "Africa/Johannesburg", ready: true }) }))
import WorkingTimePolicyPanel from "./WorkingTimePolicyPanel"

const policy = (o: Record<string, unknown> = {}) => ({ id: "p1", category: "DEFAULT", effectiveFrom: "2020-01-01", warnAt: 40, ordinaryWeekly: 45, maxOvertimeWeekly: 10, maxDaily: 12, maxDailyOvertime: 3, minRestHours: 12, legalBasis: null, ...o })
const serve = (rows: unknown) => api.get.mockResolvedValue({ data: { data: rows } })
const as = (perms: string[]) => useAuthStore.setState({ user: { permissions: perms } } as never)
function show() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><WorkingTimePolicyPanel /></QueryClientProvider>)
}
const field = (n: string) => screen.getByLabelText(n) as HTMLInputElement
const saveBtn = () => screen.getByRole("button", { name: "Save policy" }) as HTMLButtonElement

describe("WorkingTimePolicyPanel", () => {
  beforeEach(() => { api.get.mockReset(); api.post.mockReset(); as(["SECURITY_MANAGE"]) })
  afterEach(cleanup)

  it("lists policies by category with their status and legal basis", async () => {
    serve([policy({ id: "a", effectiveFrom: "2019-01-01" }), policy({ id: "d", effectiveFrom: "2020-01-01" }), policy({ id: "b", category: "NIGHT", effectiveFrom: "2020-02-01", ordinaryWeekly: 48, legalBasis: "Agreement 7, clause 4" }), policy({ id: "c", effectiveFrom: "2999-01-01" })])
    show()
    expect(await screen.findByText("Default (all guards)")).toBeTruthy()
    expect(screen.getByText("NIGHT")).toBeTruthy()
    expect(screen.getByText("Legal basis: Agreement 7, clause 4")).toBeTruthy()
    expect(screen.getByText("Starts later")).toBeTruthy()
    expect(screen.getAllByText("In force")).toHaveLength(2)
    expect(screen.getByText("Replaced")).toBeTruthy()
    expect(api.get.mock.calls[0][0]).toBe("/api/v1/security/working-time-policies")
  })

  it("says the system settings apply when there are none", async () => {
    serve([])
    show()
    expect(await screen.findByText(/No policy has been added/)).toBeTruthy()
  })

  it("shows an error, not an empty list, when loading fails or the answer is malformed", async () => {
    api.get.mockImplementation(() => Promise.reject(new Error("x")))
    show()
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("could not be loaded"))
    cleanup()
    serve({ not: "a list" })
    show()
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("could not be loaded"))
    expect(screen.queryByText(/No policy has been added/)).toBeNull()
  })

  it("lets only managers add a policy", async () => {
    as(["SECURITY_READ"])
    serve([policy()])
    show()
    await screen.findByText("Default (all guards)")
    expect(screen.queryByRole("button", { name: "Add policy" })).toBeNull()
  })

  it("saves a general-limits policy with the typed values", async () => {
    serve([])
    api.post.mockResolvedValue({ data: { data: {} } })
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Add policy" }))
    expect(field("Starts on").value).toBe(todaySast())
    expect(field("Starts on").min).toBe(todaySast())
    expect(screen.getByText(/Combined weekly ceiling: 55 h/)).toBeTruthy()
    fireEvent.change(field("Category"), { target: { value: " night shift " } })
    expect(saveBtn().disabled).toBe(false)
    fireEvent.click(saveBtn())
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1))
    expect(api.post.mock.calls[0][0]).toBe("/api/v1/security/working-time-policies")
    expect(api.post.mock.calls[0][1]).toEqual({ category: "NIGHT_SHIFT", effectiveFrom: todaySast(), warnAt: 40, ordinaryWeekly: 45, maxOvertimeWeekly: 10, maxDaily: 12, maxDailyOvertime: 3, minRestHours: 12, legalBasis: null })
    await waitFor(() => expect(screen.getByRole("status").textContent).toContain("Policy added"))
    expect(screen.queryByRole("form", { name: "New working-time policy" })).toBeNull()
  })

  it("will not save limits beyond the general figures without a legal basis", async () => {
    serve([])
    api.post.mockResolvedValue({ data: { data: {} } })
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Add policy" }))
    fireEvent.change(field("Overtime per week"), { target: { value: "15" } })
    expect(saveBtn().disabled).toBe(true)
    expect(screen.getByText(/beyond the general BCEA figures/, { selector: "div" })).toBeTruthy()
    expect(screen.getByText(/Combined weekly ceiling: 60 h/)).toBeTruthy()
    fireEvent.click(saveBtn())
    expect(api.post).not.toHaveBeenCalled()
    fireEvent.change(field("Legal basis"), { target: { value: "Bargaining council agreement 2026, clause 9" } })
    expect(saveBtn().disabled).toBe(false)
    fireEvent.click(saveBtn())
    await waitFor(() => expect(api.post.mock.calls[0][1]).toMatchObject({ maxOvertimeWeekly: 15, legalBasis: "Bargaining council agreement 2026, clause 9" }))
  })

  it("will not save a bad number or a past date", async () => {
    serve([])
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Add policy" }))
    fireEvent.change(field("Hours per day"), { target: { value: "abc" } })
    expect(saveBtn().disabled).toBe(true)
    fireEvent.change(field("Hours per day"), { target: { value: "12" } })
    fireEvent.change(field("Starts on"), { target: { value: "2020-01-01" } })
    expect(saveBtn().disabled).toBe(true)
    expect(screen.getByText("A policy cannot start in the past.")).toBeTruthy()
  })

  it("shows the server's reason when saving is refused and keeps the form", async () => {
    serve([])
    api.post.mockImplementation(() => Promise.reject({ response: { data: { message: "This category already has a policy starting on that date. Choose a later date" } } }))
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Add policy" }))
    fireEvent.click(saveBtn())
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("Choose a later date"))
    expect(screen.getByRole("form", { name: "New working-time policy" })).toBeTruthy()
  })

  it("Cancel closes the form and sends nothing", async () => {
    serve([])
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Add policy" }))
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }))
    expect(screen.queryByRole("form", { name: "New working-time policy" })).toBeNull()
    expect(api.post).not.toHaveBeenCalled()
  })
})
