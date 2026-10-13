import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const put = vi.fn(); const del = vi.fn()
let can = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), put: (...a: any[]) => put(...a), delete: (...a: any[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => can }))
import VisitStagesAdminTab from "./VisitStagesAdminTab"

const STAGES = (src: string, req: boolean[]) => ({ data: { data: { visitType: "CONSULTATION", source: src,
  stages: ["SYMPTOMS", "EXAMINATION", "DIAGNOSIS", "PLAN"].map((s, i) => ({ stage: s, required: req[i] })) } } })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><VisitStagesAdminTab /></QueryClientProvider>)
const box = (name: RegExp) => screen.getByLabelText(name) as HTMLInputElement

beforeEach(() => { get.mockReset(); put.mockReset(); del.mockReset(); can = true
  get.mockResolvedValue(STAGES("PLATFORM", [true, false, true, false])); put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("VisitStagesAdminTab", () => {
  it("shows the current stages and where they come from", async () => {
    show()
    expect(await screen.findByText(/Using the platform's setting/)).toBeTruthy()
    expect(box(/Symptoms is required/).checked).toBe(true)
    expect(box(/Examination is required/).checked).toBe(false)
    expect(get).toHaveBeenCalledWith("/api/v1/clinic/visit-types/CONSULTATION/stages")
  })
  it("saves all four stages for the practice once something changed", async () => {
    show()
    await screen.findByText(/Using the platform's setting/)
    const save = screen.getByRole("button", { name: "Save for this practice" }) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.click(box(/Plan is required/))
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/clinic/visit-types/CONSULTATION/stages",
      { stages: [{ stage: "SYMPTOMS", required: true }, { stage: "EXAMINATION", required: false }, { stage: "DIAGNOSIS", required: true }, { stage: "PLAN", required: true }] }))
  })
  it("warns, but allows, when Diagnosis is switched off", async () => {
    show()
    await screen.findByText(/Using the platform's setting/)
    fireEvent.click(box(/Diagnosis is required/))
    expect((await screen.findByRole("alert")).textContent).toMatch(/without Diagnosis/)
    expect((screen.getByRole("button", { name: "Save for this practice" }) as HTMLButtonElement).disabled).toBe(false)
  })
  it("offers to go back to the default only when the practice has its own setting", async () => {
    show()
    await screen.findByText(/Using the platform's setting/)
    expect(screen.queryByRole("button", { name: "Go back to the default" })).toBeNull()
    cleanup(); get.mockResolvedValue(STAGES("TENANT", [true, true, true, false])); show()
    fireEvent.click(await screen.findByRole("button", { name: "Go back to the default" }))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/clinic/visit-types/CONSULTATION/stages"))
  })
  it("loads another visit type when it is chosen", async () => {
    show()
    await screen.findByText(/Using the platform's setting/)
    fireEvent.change(screen.getByLabelText("Visit type"), { target: { value: "ANTENATAL" } })
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/clinic/visit-types/ANTENATAL/stages"))
  })
  it("is read-only for someone who cannot edit clinical content", async () => {
    can = false; show()
    await screen.findByText(/Using the platform's setting/)
    expect(box(/Symptoms is required/).disabled).toBe(true)
    expect(screen.queryByRole("button", { name: "Save for this practice" })).toBeNull()
    expect(screen.getByText(/see this setting but not change it/)).toBeTruthy()
  })
})
