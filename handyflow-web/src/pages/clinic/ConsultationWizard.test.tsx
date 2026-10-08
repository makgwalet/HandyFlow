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
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <ConsultationSession patient={patient} appointment={appt} onComplete={() => {}} onMinimise={() => {}} onCancel={() => {}} /></QueryClientProvider>)

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
    expect(screen.getByRole("heading", { name: "3. Diagnose & prescribe" })).toBeTruthy()
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
    expect(screen.getByRole("heading", { name: "3. Diagnose & prescribe" })).toBeTruthy()
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
    expect(screen.getByRole("button", { name: /Complete consultation/ })).toBeTruthy()
    await waitFor(() => expect(post).toHaveBeenCalled())
  })
  it("keeps the question form mounted so unsaved answers survive a page change", async () => {
    show()
    await screen.findByTestId("question-form")
    fireEvent.click(screen.getByRole("button", { name: /2\. Examination/ }))
    expect(screen.getByTestId("question-form")).toBeTruthy()
  })
})
