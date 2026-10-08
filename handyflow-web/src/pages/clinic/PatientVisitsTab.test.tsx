import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
const perms: Record<string, boolean> = {}
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
vi.mock("./PrescriptionForm", () => ({ default: ({ onSubmit }: any) => <button onClick={() => onSubmit({ medicationName: "Amoxicillin" })}>submit rx</button> }))
vi.mock("./ReferralLetterModal", () => ({ default: ({ consultationId }: any) => <div>referral for {consultationId}</div> }))
vi.mock("./QuickActionsCard", () => ({ SickNoteModal: ({ list }: any) => <div>sick note for {list[0].id}</div>, default: () => null }))
const downloadPdf = vi.fn()
vi.mock("./patientFile.shared", async () => ({ ...(await vi.importActual<any>("./patientFile.shared")), downloadPdf: (...a: any[]) => downloadPdf(...a) }))
import PatientVisitsTab from "./PatientVisitsTab"

const signed: any = { id: "v1", appointmentId: "a1", status: "SIGNED", consultedAt: "2026-10-07T08:00:00Z", signedAt: "2026-10-07T08:30:00Z", doctorName: "Andile Dlamini",
  team: [{ role: "Prepared by", name: "Sister Zodwa Nkosi" }, { role: "Signed by", name: "Dr Andile Dlamini" }],
  chiefComplaint: "Cough", history: "Two weeks of cough", examination: "Chest clear", diagnosis: "Acute bronchitis", icd10Codes: ["J20.9"], treatmentPlan: "Rest and fluids", followUpDays: 7,
  bloodPressure: "128/82", pulseBpm: 72, weightKg: 70, heightCm: 175, billed: false, prescriptions: [{ id: "r1", medicationName: "Amoxicillin", dosage: "500mg", frequency: "3x daily", duration: "7 days", quantity: 21, repeats: 0, dispensed: false }],
  addenda: [{ id: "ad1", text: "Patient phoned: rash settled", authorName: "Dr Andile Dlamini", createdAt: "2026-10-08T07:00:00Z" }] }
const draft: any = { ...signed, id: "v2", appointmentId: "a2", status: "DRAFT", consultedAt: "2026-10-08T09:00:00Z", signedAt: null, team: [], prescriptions: [], addenda: [], diagnosis: null }

const route = (visits: any[]) => get.mockImplementation((url: string) => url.endsWith("/visits") ? Promise.resolve({ data: { data: visits } }) : Promise.resolve({ data: { data: [] } }))
const show = (props: object = {}) => {
  const onStartSession = vi.fn()
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <PatientVisitsTab patientId="p1" appointments={[]} onStartSession={onStartSession} {...props} /></QueryClientProvider>)
  return { onStartSession }
}

afterEach(cleanup)
beforeEach(() => { get.mockReset(); post.mockReset(); downloadPdf.mockReset(); for (const k of Object.keys(perms)) delete perms[k] })

describe("visits tab", () => {
  it("opens the newest visit with the notes, vitals, medicines, people and addenda", async () => {
    route([signed])
    show()
    expect(await screen.findByText("Chest clear")).toBeTruthy()
    expect(screen.getByText("J20.9")).toBeTruthy()
    expect(screen.getByText("128/82")).toBeTruthy()
    expect(screen.getByText("Amoxicillin")).toBeTruthy()
    expect(screen.getByText("Sister Zodwa Nkosi")).toBeTruthy()
    expect(screen.getByText("Prepared by")).toBeTruthy()
    expect(screen.getByText("Patient phoned: rash settled")).toBeTruthy()
    expect(screen.getByText(/A signed visit is not changed/)).toBeTruthy()
  })
  it("says so when there are no visits, and when they cannot be loaded", async () => {
    route([]); show()
    expect(await screen.findByText("No visits yet")).toBeTruthy(); cleanup()
    get.mockRejectedValue(new Error("x")); show()
    expect(await screen.findByText("The visits could not be loaded.")).toBeTruthy()
  })
  it("resumes an unsigned visit in the consultation page, and cannot when it has no appointment", async () => {
    route([draft]); const { onStartSession } = show()
    // the top button (resume, because an unsigned visit exists) and the one inside the visit both resume
    await screen.findByText("Chest clear")
    const inside = screen.getAllByRole("button", { name: "Resume consultation" })
    fireEvent.click(inside[inside.length - 1])
    expect(onStartSession).toHaveBeenCalledWith({ id: "a2" }); cleanup()
    route([{ ...draft, appointmentId: null }]); show()
    await screen.findByText("Chest clear")
    const all = screen.getAllByRole("button", { name: "Resume consultation" })
    expect((all[all.length - 1] as HTMLButtonElement).disabled).toBe(true)
  })
  it("offers addendum and prescription on a signed visit only to those allowed, and never on an unsigned one", async () => {
    route([signed]); show()
    await screen.findByText("Chest clear")
    expect(screen.queryByRole("button", { name: "Add addendum" })).toBeNull(); expect(screen.queryByRole("button", { name: "Add prescription" })).toBeNull(); cleanup()
    perms.CLINIC_CONSULTATION_AMEND = true; perms.CLINIC_PRESCRIPTION_CREATE = true
    route([draft]); show(); await screen.findByText("Chest clear")
    expect(screen.queryByRole("button", { name: "Add addendum" })).toBeNull(); cleanup()
    route([signed]); show()
    expect(await screen.findByRole("button", { name: "Add addendum" })).toBeTruthy(); expect(screen.getByRole("button", { name: "Add prescription" })).toBeTruthy()
  })
  it("saves an addendum on the signed visit", async () => {
    perms.CLINIC_CONSULTATION_AMEND = true; post.mockResolvedValue({ data: {} })
    route([signed]); show()
    fireEvent.click(await screen.findByRole("button", { name: "Add addendum" }))
    fireEvent.change(screen.getByLabelText("Addendum"), { target: { value: " Rash settled " } })
    const buttons = screen.getAllByRole("button", { name: "Add addendum" })
    fireEvent.click(buttons[buttons.length - 1])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/consultations/v1/addenda", { text: "Rash settled" }))
  })
  it("adds a late prescription to the signed visit", async () => {
    perms.CLINIC_PRESCRIPTION_CREATE = true; post.mockResolvedValue({ data: {} })
    route([signed]); show()
    fireEvent.click(await screen.findByRole("button", { name: "Add prescription" }))
    expect(screen.getByText(/The medicine is added to it/)).toBeTruthy()
    fireEvent.click(screen.getByText("submit rx"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/consultations/v1/prescriptions", { medicationName: "Amoxicillin" }))
  })
  it("downloads the visit summary and the prescription PDF for that visit", async () => {
    route([signed]); show()
    fireEvent.click(await screen.findByRole("button", { name: "Visit summary PDF" }))
    expect(downloadPdf).toHaveBeenCalledWith("/api/v1/clinic/consultations/v1/summary-pdf", "visit-v1.pdf")
    fireEvent.click(screen.getByRole("button", { name: "Prescription PDF" }))
    expect(downloadPdf).toHaveBeenCalledWith("/api/v1/clinic/consultations/v1/prescription-pdf", "rx-v1.pdf")
  })
  it("starts a walk-in from the top button and hands the appointment to the consultation page", async () => {
    route([]); post.mockResolvedValueOnce({ data: { data: { id: "new", status: "SCHEDULED" } } }).mockResolvedValue({ data: { data: { id: "new", status: "IN_PROGRESS" } } })
    const { onStartSession } = show()
    fireEvent.click(await screen.findByRole("button", { name: "Start walk-in consultation" }))
    await waitFor(() => expect(onStartSession).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/clinic/appointments")
  })
  it("opens the referral and sick-note dialogs for that visit", async () => {
    perms.CLINIC_REFERRAL_SIGN = true; perms.CLINIC_SICK_NOTE_SIGN = true
    route([signed]); show()
    fireEvent.click(await screen.findByRole("button", { name: "Referral letter" }))
    expect(screen.getByText("referral for v1")).toBeTruthy(); cleanup()
    route([signed]); show()
    fireEvent.click(await screen.findByRole("button", { name: "Sick note" }))
    expect(screen.getByText("sick note for v1")).toBeTruthy()
  })
})
