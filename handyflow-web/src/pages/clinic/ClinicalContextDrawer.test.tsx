import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
import ClinicalContextDrawer from "./ClinicalContextDrawer"
import SafetyBar from "./SafetyBar"

const briefing: any = {
  patientId: "p1", visitCount: 2, lastVisit: null, daysSinceLastVisit: 20, nextAppointment: null, recall: null, openDraft: null,
  lastVitals: { takenAt: "2026-09-20T08:00:00Z", bloodPressure: "142/88", pulseBpm: 80, weightKg: 70, heightCm: 170 },
  recentVisits: [{ id: "v1", at: "2026-09-20T08:00:00Z", practitionerName: "Khumalo", chiefComplaint: "Cough", diagnosis: "Bronchitis", icd10Codes: ["J20.9"], followUpDays: 14, status: "SIGNED" }],
  allergies: [{ id: "a1", allergen: "Penicillin", severity: "SEVERE", reaction: "Rash" }],
  conditions: [{ id: "c1", conditionName: "Asthma", status: "ACTIVE" }],
  medications: [{ id: "m1", medicineName: "Salbutamol", dose: "100mcg", frequency: "PRN" }],
  labs: { unreviewed: 2, unreviewedAbnormal: 1, unreviewedCritical: 1, recent: [{ id: "l1", at: "2026-10-07T08:00:00Z", reference: "HbA1c", abnormal: true, critical: false, reviewed: false }] },
  alerts: [{ code: "FOLLOW_UP_DUE", severity: "WARNING", message: "Follow-up due today" }],
}
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: { data: briefing } }) })
afterEach(cleanup)

describe("ClinicalContextDrawer", () => {
  it("puts everything about the patient beside the consultation", async () => {
    wrap(<ClinicalContextDrawer patientId="p1" open onToggle={() => {}} />)
    expect(await screen.findByText("Penicillin")).toBeTruthy()
    expect(screen.getByText("Asthma")).toBeTruthy()
    expect(screen.getByText("Salbutamol")).toBeTruthy()
    expect(screen.getByText(/BP 142\/88/)).toBeTruthy()
    expect(screen.getByText("Follow-up due today")).toBeTruthy()
    expect(screen.getByText(/2 not yet reviewed · 1 abnormal · 1 critical/)).toBeTruthy()
  })
  it("peeks at a previous visit without leaving", async () => {
    wrap(<ClinicalContextDrawer patientId="p1" open onToggle={() => {}} />)
    const visit = await screen.findByRole("button", { name: /Bronchitis/ })
    expect(screen.queryByText("J20.9")).toBeNull()
    fireEvent.click(visit)
    expect(screen.getByText("J20.9")).toBeTruthy()
    expect(screen.getByText("in 14 days")).toBeTruthy()
  })
  it("collapses to a tab and reopens, and keeps the bill in its own tab", async () => {
    const toggle = vi.fn()
    const { rerender } = wrap(<ClinicalContextDrawer patientId="p1" open onToggle={toggle} billSlot={<div>BILL LINES</div>} billSummary="R 520.00" />)
    await screen.findByText("Penicillin")
    fireEvent.click(screen.getByRole("tab", { name: /Bill · R 520/ }))
    expect(screen.getByText("BILL LINES")).toBeTruthy()
    expect(screen.queryByText("Penicillin")).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Hide clinical context" }))
    expect(toggle).toHaveBeenCalled()
    rerender(<QueryClientProvider client={new QueryClient()}><ClinicalContextDrawer patientId="p1" open={false} onToggle={toggle} /></QueryClientProvider>)
    expect(screen.getByRole("button", { name: "Show clinical context" })).toBeTruthy()
  })
  it("says so, but does not break, when the history cannot be loaded", async () => {
    get.mockRejectedValue(new Error("down"))
    wrap(<ClinicalContextDrawer patientId="p1" open onToggle={() => {}} />)
    expect(await screen.findByText(/history could not be loaded/)).toBeTruthy()
  })
})

describe("SafetyBar", () => {
  it("leads with allergies in red, then conditions, medicines and results to review", () => {
    render(<SafetyBar patient={{ fullName: "Liam Botha", idNumber: "" }} briefing={briefing} visit="Consultation · Cough" stateLabel="In progress" timer="03:10" saveText="Auto-saved" />)
    expect(screen.getByText("Allergy: Penicillin")).toBeTruthy()
    expect(screen.getByText("Asthma")).toBeTruthy()
    expect(screen.getByText("1 current medicine")).toBeTruthy()
    expect(screen.getByText("1 critical result to review")).toBeTruthy()
    expect(screen.getByTestId("lifecycle").textContent).toBe("In progress")
    expect(screen.getByText("Auto-saved")).toBeTruthy()
  })
  it("does not claim 'no allergies' before the history has loaded", () => {
    render(<SafetyBar patient={{ fullName: "Liam Botha", allergies: ["Peanuts"] }} visit="" stateLabel="Draft" timer="00:00" saveText="" />)
    expect(screen.getByText("Allergy: Peanuts")).toBeTruthy()
    expect(screen.queryByText("No allergies recorded")).toBeNull()
  })
  it("says plainly when none are recorded", () => {
    render(<SafetyBar patient={{ fullName: "Liam Botha" }} briefing={{ ...briefing, allergies: [], conditions: [], medications: [], labs: { ...briefing.labs, unreviewed: 0, unreviewedCritical: 0 } }} visit="" stateLabel="Draft" timer="00:00" saveText="" />)
    expect(screen.getByText("No allergies recorded")).toBeTruthy()
    expect(screen.getByText("No current medicines")).toBeTruthy()
  })
})
