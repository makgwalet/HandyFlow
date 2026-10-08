import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
vi.mock("./MyDayPanel", () => ({ default: () => null }))
const perms = { CLINIC_CLINICAL_WRITE: true, CLINIC_WRITE: true } as Record<string, boolean>
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: any) => sel({ user: { email: "doc@clinic.test", firstName: "Thabang" } }) }))
import ClinicDashboard, { dayOf } from "./ClinicDashboard"

const item = (over: object) => ({ id: "x", patientId: "p-x", practitionerId: "d1", patientName: "Ann One", practitionerName: "Lee", scheduledAt: "2026-10-07T06:00:00Z",
  durationMinutes: 15, appointmentType: "CONSULTATION", status: "SCHEDULED", ...over })
const SUMMARY = { date: "2026-10-07", todayTotal: 120, awaiting: 40, inProgress: 2, completed: 70, cancelled: 5, noShow: 3, totalPatients: 5432,
  today: [item({ id: "1", patientName: "Ann One", status: "CHECKED_IN" }), item({ id: "2", patientName: "Bob Two", status: "COMPLETED" }),
    item({ id: "4", patientName: "Dan Four", status: "CONFIRMED" }), item({ id: "5", patientName: "Eve Other", status: "CHECKED_IN", practitionerId: "d2" })],
  next: item({ id: "3", patientName: "Cara Three", scheduledAt: "2026-10-08T06:00:00Z" }) }
const PRACT = [{ id: "d1", fullName: "Thabang Mokoena", email: "doc@clinic.test", active: true }]

const show = (props: any = {}) => { const nav = vi.fn(); return { nav, ...render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <ClinicDashboard onNavigate={nav} {...props} /></QueryClientProvider>) } }

beforeEach(() => {
  get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: {} })
  perms.CLINIC_CLINICAL_WRITE = true; perms.CLINIC_WRITE = true
  get.mockImplementation((url: string) => Promise.resolve({ data: { data:
    url.includes("dashboard/summary") ? SUMMARY : url.includes("practitioners") ? PRACT
    : url.includes("recalls") ? { counts: { open: 9, overdue: 4, notContacted: 6 } } : [] } }))
})
afterEach(cleanup)

describe("dayOf", () => {
  it("uses the South African day, not the UTC day", () => {
    expect(dayOf("2026-10-07T22:30:00Z")).toBe("2026-10-08")
    expect(dayOf("2026-10-07T21:30:00Z")).toBe("2026-10-07")
  })
})

describe("ClinicDashboard", () => {
  it("greets the signed-in doctor by their practitioner name", async () => {
    show()
    expect(await screen.findByRole("heading", { name: /Dr\. Thabang Mokoena/ })).toBeTruthy()
  })
  it("shows server counts, not the number of rows it received", async () => {
    show()
    expect(await screen.findByText("120")).toBeTruthy()
    expect(screen.getByText("70")).toBeTruthy()
    expect(screen.getByText("5432")).toBeTruthy()
    expect(screen.getAllByText("4").length).toBeGreaterThan(0)   // follow-ups overdue
  })
  it("a doctor sees their own patients first, and can widen to everyone", async () => {
    show()
    expect(await screen.findByText("Ann One")).toBeTruthy()
    expect(screen.queryByText("Eve Other")).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Everyone" }))
    expect(await screen.findByText("Eve Other")).toBeTruthy()
  })
  it("groups the queue by where the patient is", async () => {
    show()
    await screen.findByText("Ann One")
    expect(screen.getByRole("region", { name: "Checked in — waiting" })).toBeTruthy()
    expect(screen.getByRole("region", { name: "Expected later" })).toBeTruthy()
    expect(screen.getByRole("region", { name: "Seen today" })).toBeTruthy()
  })
  it("a clinician opens the waiting patient; the front desk checks people in", async () => {
    const onOpenPatient = vi.fn()
    show({ onOpenPatient })
    fireEvent.click(await screen.findByRole("button", { name: "See patient" }))
    expect(onOpenPatient).toHaveBeenCalledWith("p-x")
    fireEvent.click(screen.getByRole("button", { name: "Check in" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/appointments/4/check_in"))
  })
  it("a receptionist gets Check in but no clinical buttons or doctor filter", async () => {
    perms.CLINIC_CLINICAL_WRITE = false
    show()
    await screen.findByText("Dan Four")
    expect(screen.getByRole("button", { name: "Check in" })).toBeTruthy()
    expect(screen.queryByRole("button", { name: "See patient" })).toBeNull()
    expect(screen.queryByRole("button", { name: "My patients" })).toBeNull()
    expect(screen.getByText("Eve Other")).toBeTruthy()
  })
  it("asks for the summary and no longer pages through appointments", async () => {
    show()
    await screen.findByText("120")
    const urls = get.mock.calls.map(c => String(c[0]))
    expect(urls.some(u => u.includes("/api/v1/clinic/dashboard/summary"))).toBe(true)
    expect(urls.some(u => u.includes("/appointments"))).toBe(false)
  })
  it("shows the date on the next-up card when it is not today", async () => {
    show()
    await screen.findByText("Cara Three")
    expect(screen.getByText("2026-10-08")).toBeTruthy()
  })
  it("sends the tiles to their screens", async () => {
    const { nav } = show()
    fireEvent.click((await screen.findAllByText("Follow-ups overdue"))[0].closest("button")!)
    expect(nav).toHaveBeenCalledWith("recalls")
  })
  it("shows dashes while the summary has not loaded", () => {
    get.mockImplementation(() => new Promise(() => {}))
    show()
    expect(screen.getAllByText("—").length).toBeGreaterThanOrEqual(4)
  })
})
