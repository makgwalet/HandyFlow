// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import AgreementSettingsPanel from "./AgreementSettingsPanel"
import { settingsProblem } from "./agreementSettingsRules"

const get = vi.fn()
const put = vi.fn()
let admin = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => admin, usePermission: () => admin }))
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><AgreementSettingsPanel /></QueryClientProvider>)
const U = "/api/v1/compliance-services/agreement-settings"

describe("agreement second-person setting", () => {
  afterEach(cleanup)
  beforeEach(() => { admin = true; get.mockReset(); put.mockReset(); get.mockResolvedValue({ data: { data: { requireSecondPerson: false, minValue: null, updatedAt: null } } }); put.mockResolvedValue({ data: {} }) })

  it("checks the amount", () => {
    expect(settingsProblem(false, "abc")).toBeNull()
    expect(settingsProblem(true, "")).toBeNull()
    expect(settingsProblem(true, "0")).not.toBeNull()
    expect(settingsProblem(true, "-5")).not.toBeNull()
    expect(settingsProblem(true, "50 000,50")).toBeNull()
  })

  it("switches it on for every agreement", async () => {
    wrap()
    await waitFor(() => expect(get).toHaveBeenCalled())
    fireEvent.click(await screen.findByLabelText("A different person must activate an agreement"))
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(put).toHaveBeenCalledWith(U, { requireSecondPerson: true, minValue: null }))
  })

  it("saves a minimum quoted total and refuses a bad one", async () => {
    wrap()
    await waitFor(() => expect(get).toHaveBeenCalled())
    fireEvent.click(await screen.findByLabelText("A different person must activate an agreement"))
    fireEvent.change(screen.getByLabelText("Quoted total from"), { target: { value: "0" } })
    fireEvent.click(screen.getByText("Save"))
    expect(await screen.findByRole("alert")).toBeTruthy()
    expect(put).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Quoted total from"), { target: { value: "50000" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(put).toHaveBeenCalledWith(U, { requireSecondPerson: true, minValue: 50000 }))
  })

  it("shows the current setting to someone who cannot change it", async () => {
    admin = false
    get.mockResolvedValue({ data: { data: { requireSecondPerson: true, minValue: 50000, updatedAt: null } } })
    wrap()
    const box = (await screen.findByLabelText("A different person must activate an agreement")) as HTMLInputElement
    await waitFor(() => expect(box.checked).toBe(true))
    expect(box.disabled).toBe(true)
    expect(screen.queryByText("Save")).toBeNull()
  })
})
