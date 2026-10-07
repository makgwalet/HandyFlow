import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
import PatientBriefingPanel from "./PatientBriefingPanel"

const briefing = (over: any = {}) => ({
  patientId: "p1", visitCount: 3,
  lastVisit: { id: "c1", at: "2026-09-26T08:00:00Z", practitionerName: "Priya Govender", chiefComplaint: "Cough", diagnosis: "Acute bronchitis", icd10Codes: ["J20.9"], followUpDays: 7, status: "SIGNED" },
  daysSinceLastVisit: 12,
  lastVitals: { takenAt: "2026-09-26T08:00:00Z", weightKg: 72, heightCm: 170, bloodPressure: "128/82", pulseBpm: 76, temperatureC: 36.8, oxygenSatPct: 98 },
  nextAppointment: null, recall: { dueDate: "2026-10-03", overdueDays: 5, due: true }, openDraft: null,
  recentVisits: [{ id: "c1", at: "2026-09-26T08:00:00Z", practitionerName: "Priya Govender", chiefComplaint: "Cough", diagnosis: "Acute bronchitis", icd10Codes: ["J20.9"], followUpDays: 7, status: "SIGNED" }],
  allergies: [{ id: "a1", allergen: "Penicillin", severity: "SEVERE" }],
  conditions: [{ id: "k1", conditionName: "Hypertension", status: "ACTIVE" }],
  medications: [{ id: "m1", medicineName: "Amlodipine", dose: "5 mg", frequency: "daily" }],
  labs: { unreviewed: 1, unreviewedAbnormal: 1, unreviewedCritical: 0, latestAt: "2026-09-20T08:00:00Z",
    recent: [{ id: "l1", at: "2026-09-20T08:00:00Z", reference: "HbA1c", abnormal: true, critical: false, reviewed: false }] },
  alerts: [{ code: "SEVERE_ALLERGY", severity: "DANGER", message: "Severe allergy: Penicillin" }, { code: "RECALL_OVERDUE", severity: "WARNING", message: "Follow-up is 5 days overdue" }],
  ...over,
})
const nowIso = () => new Date().toISOString()
const show = (props: any = {}) => {
  const onStartSession = vi.fn()
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <PatientBriefingPanel patientId="p1" appointments={[]} onStartSession={onStartSession} {...props} /></QueryClientProvider>)
  return onStartSession
}

beforeEach(() => { get.mockReset(); post.mockReset(); get.mockResolvedValue({ data: { data: briefing() } }) })
afterEach(cleanup)

describe("PatientBriefingPanel", () => {
  it("puts what must not be missed first, then the story of the last visit", async () => {
    show()
    expect((await screen.findByText("Severe allergy: Penicillin")).closest("[role=alert]")).toBeTruthy()
    expect(screen.getByText("Follow-up is 5 days overdue")).toBeTruthy()
    expect(screen.getByText("12 days ago")).toBeTruthy()
    expect(screen.getAllByText("Acute bronchitis").length).toBeGreaterThan(0)
    expect(screen.getByText("J20.9")).toBeTruthy()
    expect(screen.getByText("Amlodipine")).toBeTruthy()
    expect(screen.getByText("128/82")).toBeTruthy()
    expect(screen.getByText("1 not yet reviewed")).toBeTruthy()
  })

  it("is honest about a patient who has never been seen", async () => {
    get.mockResolvedValue({ data: { data: briefing({ visitCount: 0, lastVisit: null, daysSinceLastVisit: null, lastVitals: null, recall: null,
      recentVisits: [], allergies: [], conditions: [], medications: [], alerts: [{ code: "FIRST_VISIT", severity: "INFO", message: "No finished visits on record" }],
      labs: { unreviewed: 0, unreviewedAbnormal: 0, unreviewedCritical: 0, latestAt: null, recent: [] } }) } })
    show()
    expect(await screen.findByText("First visit")).toBeTruthy()
    expect(screen.getByText("No vitals on record.")).toBeTruthy()
    expect(screen.getByText("No active medicines recorded.")).toBeTruthy()
  })

  it("checks a scheduled appointment in, starts it, and hands the started appointment to the session", async () => {
    post.mockResolvedValueOnce({ data: { data: { id: "ap1", status: "CHECKED_IN" } } }).mockResolvedValueOnce({ data: { data: { id: "ap1", status: "IN_PROGRESS", practitionerId: "d1" } } })
    const onStart = show({ appointments: [{ id: "ap1", status: "SCHEDULED", scheduledAt: nowIso() }] })
    fireEvent.click(await screen.findByText("Start consultation"))
    await waitFor(() => expect(onStart).toHaveBeenCalledWith({ id: "ap1", status: "IN_PROGRESS", practitionerId: "d1" }))
    expect(post.mock.calls.map(c => c[0])).toEqual(["/api/v1/clinic/appointments/ap1/check_in", "/api/v1/clinic/appointments/ap1/start"])
  })

  it("creates a walk-in for the logged-in practitioner when nothing is booked today", async () => {
    post.mockResolvedValueOnce({ data: { data: { id: "w1", status: "SCHEDULED" } } })
      .mockResolvedValueOnce({ data: { data: { id: "w1", status: "CHECKED_IN" } } }).mockResolvedValueOnce({ data: { data: { id: "w1", status: "IN_PROGRESS" } } })
    const onStart = show({ defaultPractitionerId: "doc9" })
    fireEvent.click(await screen.findByText("Start walk-in consultation"))
    await waitFor(() => expect(onStart).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/clinic/appointments")
    expect(post.mock.calls[0][1]).toMatchObject({ patientId: "p1", practitionerId: "doc9", appointmentType: "CONSULTATION" })
    expect(post.mock.calls.slice(1).map(c => c[0])).toEqual(["/api/v1/clinic/appointments/w1/check_in", "/api/v1/clinic/appointments/w1/start"])
  })

  it("offers to resume, without calling anything, when the consultation is already in progress", async () => {
    const appt = { id: "ap2", status: "IN_PROGRESS", scheduledAt: nowIso() }
    const onStart = show({ appointments: [appt] })
    fireEvent.click(await screen.findByText("Resume consultation"))
    await waitFor(() => expect(onStart).toHaveBeenCalledWith(appt))
    expect(post).not.toHaveBeenCalled()
  })

  it("shows the server's reason when it cannot start, and does not open the session", async () => {
    post.mockRejectedValue({ response: { data: { message: "The clinic is closed at that time" } } })
    const onStart = show()
    fireEvent.click(await screen.findByText("Start walk-in consultation"))
    expect(await screen.findByText("The clinic is closed at that time")).toBeTruthy()
    expect(onStart).not.toHaveBeenCalled()
  })

  it("says so when the briefing cannot be loaded", async () => {
    get.mockRejectedValue({ response: { status: 500 } })
    show()
    expect((await screen.findByRole("alert")).textContent).toMatch(/could not be loaded/)
  })
})
