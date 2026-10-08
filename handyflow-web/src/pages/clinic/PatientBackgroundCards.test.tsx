import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn(); const patch = vi.fn(); const put = vi.fn(); const del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: {
  get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), patch: (...a: any[]) => patch(...a), put: (...a: any[]) => put(...a), delete: (...a: any[]) => del(...a) } }))
const perms: Record<string, boolean> = {}
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
import { FamilyHistoryCard, MedicalAidCard, SocialHistoryCard } from "./PatientBackgroundCards"

const ok = (data: any) => Promise.resolve({ data: { data } })
const show = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const route = (map: Record<string, any>) => get.mockImplementation((url: string) => { const k = Object.keys(map).find(x => url.endsWith(x)); return ok(k ? map[k] : null) })

afterEach(cleanup)
beforeEach(() => {
  for (const f of [get, post, patch, put, del]) f.mockReset()
  for (const k of Object.keys(perms)) delete perms[k]
})

describe("family history", () => {
  it("is not shown, and not loaded, without permission", () => {
    show(<FamilyHistoryCard patientId="p1" />)
    expect(screen.queryByText("Family history")).toBeNull()
    expect(get).not.toHaveBeenCalled()
  })
  it("lists entries and offers no add button to a reader", async () => {
    perms.CLINIC_CLINICAL_HISTORY_READ = true
    route({ "/family-history": [{ id: "f1", relative: "MOTHER", conditionName: "Type 2 diabetes", ageAtOnset: 55 }] })
    show(<FamilyHistoryCard patientId="p1" />)
    expect(await screen.findByText("Type 2 diabetes")).toBeTruthy()
    expect(screen.getByText("Mother")).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Add" })).toBeNull()
  })
  it("says so when nothing is recorded, then adds an entry", async () => {
    perms.CLINIC_CLINICAL_HISTORY_READ = true; perms.CLINIC_CLINICAL_HISTORY_WRITE = true
    route({ "/family-history": [] }); post.mockResolvedValue({ data: { data: {} } })
    show(<FamilyHistoryCard patientId="p1" />)
    await screen.findByText("No family history recorded.")
    fireEvent.click(screen.getByRole("button", { name: "Add" }))
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    expect(await screen.findByText("Enter the condition.")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Family condition"), { target: { value: " Asthma " } })
    fireEvent.change(screen.getByLabelText("Relative"), { target: { value: "FATHER" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/family-history", { relative: "FATHER", conditionName: "Asthma", ageAtOnset: null, notes: null }))
  })
  it("marks an entry as entered in error instead of deleting it", async () => {
    perms.CLINIC_CLINICAL_HISTORY_READ = true; perms.CLINIC_CLINICAL_HISTORY_WRITE = true
    route({ "/family-history": [{ id: "f1", relative: "FATHER", conditionName: "Asthma" }] }); patch.mockResolvedValue({ data: {} })
    show(<FamilyHistoryCard patientId="p1" />)
    fireEvent.click(await screen.findByRole("button", { name: "Remove Asthma" }))
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/family-history/f1", { status: "ENTERED_IN_ERROR" }))
  })
})

describe("lifestyle and social history", () => {
  it("shows only recorded answers", async () => {
    perms.CLINIC_CLINICAL_HISTORY_READ = true
    route({ "/social-history": { recorded: true, smokingStatus: "FORMER", alcoholUse: "UNKNOWN", substanceUse: "UNKNOWN", occupation: "Teacher" } })
    show(<SocialHistoryCard patientId="p1" />)
    expect(await screen.findByText("Former")).toBeTruthy()
    expect(screen.getByText("Teacher")).toBeTruthy()
    expect(screen.queryByText("Alcohol")).toBeNull()
  })
  it("saves the whole record with the chosen values", async () => {
    perms.CLINIC_CLINICAL_HISTORY_READ = true; perms.CLINIC_CLINICAL_HISTORY_WRITE = true
    route({ "/social-history": { recorded: false, smokingStatus: "UNKNOWN", alcoholUse: "UNKNOWN", substanceUse: "UNKNOWN" } }); put.mockResolvedValue({ data: { data: {} } })
    show(<SocialHistoryCard patientId="p1" />)
    await screen.findByText("Nothing recorded yet.")
    fireEvent.click(screen.getByRole("button", { name: "Add" }))
    fireEvent.change(screen.getByLabelText("Smoking"), { target: { value: "NEVER" } })
    fireEvent.change(screen.getByLabelText("Occupation"), { target: { value: "Driver" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/social-history",
      expect.objectContaining({ smokingStatus: "NEVER", alcoholUse: "UNKNOWN", occupation: "Driver", notes: null })))
  })
})

describe("medical aid", () => {
  it("says private patient when there is none, and lets an editor add one", async () => {
    perms.CLINIC_PATIENT_READ = true; perms.CLINIC_PATIENT_UPDATE = true
    route({ "/medical-aid": null }); put.mockResolvedValue({ data: { data: {} } })
    show(<MedicalAidCard patientId="p1" />)
    await screen.findByText(/No medical aid recorded/)
    fireEvent.click(screen.getByRole("button", { name: "Add medical aid" }))
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    expect(await screen.findByText("Enter the medical scheme.")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Medical scheme"), { target: { value: "Discovery" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    expect(await screen.findByText("Enter the member number.")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Member number"), { target: { value: "998877" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/medical-aid", expect.objectContaining({ schemeName: "Discovery", memberNumber: "998877", planName: null })))
  })
  it("shows an inherited record as the principal's and offers to add their own, not to remove it", async () => {
    perms.CLINIC_PATIENT_READ = true; perms.CLINIC_PATIENT_UPDATE = true
    route({ "/medical-aid": { id: "m1", schemeName: "Bonitas", memberNumber: "123", inherited: true, inheritedFrom: "Mary Botha" } })
    show(<MedicalAidCard patientId="p1" />)
    expect(await screen.findByText("Bonitas")).toBeTruthy()
    expect(screen.getByText(/Mary Botha/)).toBeTruthy()
    expect(screen.getByRole("button", { name: "Add own" })).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Remove medical aid" })).toBeNull()
  })
  it("removes the patient's own record", async () => {
    perms.CLINIC_PATIENT_READ = true; perms.CLINIC_PATIENT_UPDATE = true
    route({ "/medical-aid": { id: "m1", schemeName: "Bonitas", memberNumber: "123", inherited: false } }); del.mockResolvedValue({ data: {} })
    show(<MedicalAidCard patientId="p1" />)
    fireEvent.click(await screen.findByRole("button", { name: "Remove medical aid" }))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/medical-aid"))
  })
})
