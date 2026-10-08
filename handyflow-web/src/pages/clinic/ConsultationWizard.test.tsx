import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn(); const patch = vi.fn(); const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), patch: (...a: any[]) => patch(...a), put: (...a: any[]) => put(...a), delete: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
vi.mock("./QuestionForm", () => ({ default: () => <div data-testid="question-form" /> }))
vi.mock("./PatientNotes", () => ({ PatientAlertBanner: () => null }))
vi.mock("./PrescriptionAllergyCheck", () => ({ missingReasons: () => [], useAllergyChecks: () => ({}) }))
import ConsultationSession from "./ConsultationSession"

const patient: any = { id: "p1", fullName: "Ada Lovelace", allergies: ["Penicillin"] }
const appt: any = { id: "a1", appointmentType: "CONSULTATION", reason: "Cough", practitionerName: "Who", scheduledAt: "2026-10-08T08:00:00Z", status: "IN_PROGRESS" }
const show = (onComplete: (...a: any[]) => void = () => {}) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <ConsultationSession patient={patient} appointment={appt} onComplete={onComplete} onMinimise={() => {}} onCancel={() => {}} /></QueryClientProvider>)

beforeEach(() => {
  for (const m of [get, post, patch, put]) m.mockReset()
  get.mockResolvedValue({ data: { data: [] } }); post.mockResolvedValue({ data: { data: { id: "d1" } } }); patch.mockResolvedValue({ data: {} }); put.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("consultation wizard pages", () => {
  it("starts on Symptoms with only the symptom fields, behind a patient header with allergies", async () => {
    show()
    expect(await screen.findByRole("heading", { name: "1. Symptoms" })).toBeTruthy()
    expect(screen.getByText(/Penicillin/)).toBeTruthy()
    expect(screen.getByText("Chief complaint *")).toBeTruthy()
    expect(screen.getByText("History (S)")).toBeTruthy()
    expect(screen.queryByText("Diagnosis (A)")).toBeNull()
    expect(screen.queryByText("Vitals")).toBeNull()
  })
  it("Next moves page by page, each with different content", async () => {
    show()
    await screen.findByRole("heading", { name: "1. Symptoms" })
    fireEvent.click(screen.getByRole("button", { name: "Next →" }))
    expect(screen.getByRole("heading", { name: "2. Examination" })).toBeTruthy()
    expect(screen.getByText("Vitals")).toBeTruthy()
    expect(screen.queryByText("Chief complaint *")).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Next →" }))
    expect(screen.getByRole("heading", { name: "3. Assessment & treatment" })).toBeTruthy()
    expect(screen.getByText("Diagnosis (A)")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Next →" }))
    expect(screen.getByRole("heading", { name: "4. Plan" })).toBeTruthy()
    expect(screen.getByText("Treatment plan (P)")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Next →" }))
    expect(screen.getByRole("heading", { name: "5. Review & sign" })).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Next →" })).toBeNull()
  })
  it("steps are never locked: the strip jumps anywhere and Back returns", async () => {
    show()
    await screen.findByRole("heading", { name: "1. Symptoms" })
    fireEvent.click(screen.getByRole("button", { name: /4\. Plan/ }))
    expect(screen.getByRole("heading", { name: "4. Plan" })).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "← Back" }))
    expect(screen.getByRole("heading", { name: "3. Assessment & treatment" })).toBeTruthy()
  })
  it("the Plan page sets the follow-up with one tap, and the summary shows what was entered", async () => {
    show()
    await screen.findByRole("heading", { name: "1. Symptoms" })
    fireEvent.change(document.getElementById("soap-history")!, { target: { value: "3 days of cough" } })
    fireEvent.click(screen.getByRole("button", { name: /4\. Plan/ }))
    fireEvent.click(screen.getByRole("button", { name: "14 days" }))
    fireEvent.click(screen.getByRole("button", { name: /5\. Sign/ }))
    expect(screen.getByText("3 days of cough")).toBeTruthy()
    expect(screen.getByText("In 14 days")).toBeTruthy()
    // The complaint is there but no diagnosis: signing is not offered, the reason is.
    expect(screen.getByText("Cannot sign yet")).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Sign & complete/ })).toBeNull()
    await waitFor(() => expect(post).toHaveBeenCalled())
  })
  it("keeps the question form mounted so unsaved answers survive a page change", async () => {
    show()
    await screen.findByTestId("question-form")
    fireEvent.click(screen.getByRole("button", { name: /2\. Examination/ }))
    expect(screen.getByTestId("question-form")).toBeTruthy()
  })
})

describe("signing", () => {
  const goSign = async () => {
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/consultations/draft", expect.anything()))
    fireEvent.click(screen.getByRole("button", { name: /5\. Sign/ }))
  }
  const signCalls = () => post.mock.calls.filter(c => String(c[0]).endsWith("/sign"))

  it("signs straight away when the complaint and diagnosis are there, and hands the summary on", async () => {
    const done = vi.fn()
    show(done)
    await screen.findByRole("heading", { name: "1. Symptoms" })
    fireEvent.click(screen.getByRole("button", { name: /3\. Assessment/ }))
    fireEvent.change(document.getElementById("soap-diagnosis")!, { target: { value: "Acute URTI" } })
    await goSign()
    fireEvent.click(await screen.findByRole("button", { name: /Sign & complete/ }))
    await waitFor(() => expect(done).toHaveBeenCalled())
    expect(signCalls()).toHaveLength(1)
    expect(signCalls()[0][1]).toEqual({})
    expect(done.mock.calls[0][0]).toBe("d1")
    expect(done.mock.calls[0][1]).toMatchObject({ diagnosis: "Acute URTI", rxCount: 0 })
    // The appointment is completed by the server when it signs; the screen no longer asks for that a second time.
    expect(post.mock.calls.some(c => String(c[0]).endsWith("/complete"))).toBe(false)
  })

  it("without a diagnosis it can only be signed by overriding the requirement with a reason", async () => {
    const done = vi.fn()
    show(done)
    await screen.findByRole("heading", { name: "1. Symptoms" })
    await goSign()
    expect(screen.getByText("Cannot sign yet")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: /Override requirement/ }))
    fireEvent.click(screen.getByRole("button", { name: /Confirm override and sign/ }))
    expect(await screen.findByText("Give a reason to override the requirement.")).toBeTruthy()
    expect(signCalls()).toHaveLength(0)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Results review only" } })
    fireEvent.click(screen.getByRole("button", { name: /Confirm override and sign/ }))
    await waitFor(() => expect(done).toHaveBeenCalled())
    expect(signCalls()[0][1]).toEqual({ overrideReason: "Results review only" })
  })

  it("a consultation the nurse handed over must be accepted before it can be edited", async () => {
    get.mockImplementation((url: string) => String(url).endsWith("/consultation")
      ? Promise.resolve({ data: { data: { id: "c9", status: "READY_FOR_DOCTOR", chiefComplaint: "Cough", bloodPressure: "120/80" } } })
      : String(url).endsWith("/transitions") ? Promise.resolve({ data: { data: [{ toStatus: "READY_FOR_DOCTOR", comment: "Fever overnight", createdAt: "2026-10-08T07:00:00Z" }] } })
      : Promise.resolve({ data: { data: [] } }))
    show()
    expect(await screen.findByText(/Handed over by the nurse/)).toBeTruthy()
    expect(screen.getByText(/Fever overnight/)).toBeTruthy()
    expect(post).not.toHaveBeenCalledWith("/api/v1/clinic/patients/p1/consultations/draft", expect.anything())
    fireEvent.click(screen.getByRole("button", { name: "Accept handoff" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/consultations/c9/accept", expect.anything()))
    await waitFor(() => expect(screen.queryByRole("button", { name: "Accept handoff" })).toBeNull())
  })

  it("a consultation under doctor review finishes the review, then signs", async () => {
    get.mockImplementation((url: string) => String(url).endsWith("/consultation")
      ? Promise.resolve({ data: { data: { id: "c9", status: "DOCTOR_REVIEWING", chiefComplaint: "Cough", diagnosis: "URTI" } } })
      : Promise.resolve({ data: { data: [] } }))
    const done = vi.fn()
    show(done)
    await screen.findByRole("heading", { name: "1. Symptoms" })
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/clinic/appointments/a1/consultation"))
    fireEvent.click(screen.getByRole("button", { name: /5\. Sign/ }))
    fireEvent.click(await screen.findByRole("button", { name: /Sign & complete/ }))
    await waitFor(() => expect(done).toHaveBeenCalled())
    const urls = post.mock.calls.map(c => String(c[0]))
    expect(urls.indexOf("/api/v1/clinic/consultations/c9/doctor-complete")).toBeGreaterThan(-1)
    expect(urls.indexOf("/api/v1/clinic/consultations/c9/doctor-complete")).toBeLessThan(urls.indexOf("/api/v1/clinic/consultations/c9/sign"))
  })
})
