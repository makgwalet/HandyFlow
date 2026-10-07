import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

let allowed = true
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => allowed }))
const get = vi.fn(), post = vi.fn(), patch = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), patch: (...a: any[]) => patch(...a) } }))
import ClinicalSummaryPanel, { bodies } from "./ClinicalSummaryPanel"

const ok = (data: any) => Promise.resolve({ data: { data } })
const data: Record<string, any[]> = {
  allergies: [{ id: "a1", allergen: "Penicillin", allergenType: "DRUG", severity: "SEVERE", reaction: "Rash", status: "ACTIVE" }],
  conditions: [{ id: "c1", conditionName: "Hypertension", icd10Code: "I10", status: "ACTIVE" }],
  medications: [{ id: "m1", medicineName: "Amlodipine", dose: "5mg", frequency: "daily", source: "PATIENT_REPORTED", status: "ACTIVE" }],
}
const show = (props: any = {}) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <ClinicalSummaryPanel patientId="p1" {...props} /></QueryClientProvider>)

beforeEach(() => {
  allowed = true
  get.mockReset(); post.mockReset(); patch.mockReset()
  get.mockImplementation((url: string) => ok(data[url.split("/").pop()!] ?? []))
  post.mockResolvedValue({ data: {} }); patch.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("bodies", () => {
  it("trims, and turns blanks into null", () => {
    expect(bodies.allergy({ allergen: " Latex ", allergenType: "", severity: "", reaction: "  " }))
      .toEqual({ allergen: "Latex", allergenType: "UNKNOWN", severity: null, reaction: null })
    expect(bodies.condition({ conditionName: " Asthma ", icd10Code: " " })).toEqual({ conditionName: "Asthma", icd10Code: null })
    expect(bodies.medication({ medicineName: " Aspirin ", dose: "", frequency: "bd" }))
      .toEqual({ medicineName: "Aspirin", dose: null, frequency: "bd", source: "PATIENT_REPORTED" })
  })
})

describe("ClinicalSummaryPanel", () => {
  it("shows allergies with severity and reaction, conditions and medicines", async () => {
    show()
    expect(await screen.findByText("Penicillin")).toBeTruthy()
    expect(screen.getAllByText("severe").some(el => el.tagName === "SPAN")).toBe(true)
    expect(screen.getByText("Rash")).toBeTruthy()
    expect(screen.getByText("Hypertension")).toBeTruthy()
    expect(screen.getByText("I10")).toBeTruthy()
    expect(screen.getByText("Amlodipine")).toBeTruthy()
  })

  it("adds an allergy with the entered details", async () => {
    show()
    await screen.findByText("Penicillin")
    fireEvent.change(screen.getByLabelText("Allergen"), { target: { value: "Latex" } })
    fireEvent.change(screen.getByLabelText("Severity"), { target: { value: "MILD" } })
    fireEvent.click(screen.getByText("Add allergy"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/allergies",
      { allergen: "Latex", allergenType: "DRUG", severity: "MILD", reaction: null }))
  })

  it("resolving an allergy keeps it on record (status change, not delete)", async () => {
    show()
    fireEvent.click((await screen.findAllByText("Resolved", { selector: "button" }))[0])
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/allergies/a1", { status: "RESOLVED" }))
  })

  it("stopping a medicine and marking a condition controlled are status changes", async () => {
    show()
    fireEvent.click(await screen.findByText("Stopped"))
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/medications/m1", { status: "STOPPED" }))
    fireEvent.click(screen.getByText("Controlled"))
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/conditions/c1", { status: "CONTROLLED" }))
  })

  it("is read-only without the clinical write permission", async () => {
    allowed = false
    show()
    await screen.findByText("Penicillin")
    expect(screen.queryByText("Add allergy")).toBeNull()
    expect(screen.queryByText("Resolved", { selector: "button" })).toBeNull()
  })

  it("falls back to the plain lists when the structured ones cannot be loaded, so no allergy is hidden", async () => {
    get.mockImplementation(() => Promise.reject(new Error("nope")))
    show({ fallbackAllergies: ["Penicillin", "Latex"], fallbackConditions: ["Asthma"] })
    expect(await screen.findByText("Penicillin, Latex")).toBeTruthy()
    expect(screen.getByText("Asthma")).toBeTruthy()
    expect(screen.queryByText("Add allergy")).toBeNull()
  })

  it("shows the server's message when a change is refused", async () => {
    patch.mockRejectedValue({ response: { data: { message: "Not allowed" } } })
    show()
    fireEvent.click((await screen.findAllByText("Resolved", { selector: "button" }))[0])
    expect(await screen.findByText("Not allowed")).toBeTruthy()
  })
})
