import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: vi.fn() } }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: any) => sel({ user: { email: "doc@clinic.test" } }) }))
import PatientsTab from "./PatientsTab"

const entry = (o: any = {}) => ({ patient: { id: "p1", firstName: "Ada", lastName: "Lovelace", fullName: "Ada Lovelace", accountType: "INDIVIDUAL", phone: "0821112222" },
  visitCount: 3, lastVisitAt: "2026-10-01T08:00:00Z", nextAppointmentAt: null, sameNameCount: 1, followUpDue: false, ...o })
const dir = (rows: any[], total = rows.length) => ({ data: { data: { content: rows, page: 0, size: 25, total } } })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><PatientsTab onOpenPatient={() => {}} /></QueryClientProvider>)

beforeEach(() => {
  get.mockReset()
  get.mockImplementation(async (url: string) => url.includes("practitioners")
    ? { data: { data: [{ id: "d1", fullName: "Doc", email: "doc@clinic.test", active: true }] } }
    : dir([entry()]))
})
afterEach(cleanup)

describe("PatientsTab directory", () => {
  it("shows visit facts and flags per patient", async () => {
    get.mockImplementation(async (url: string) => url.includes("practitioners") ? { data: { data: [] } }
      : dir([entry({ followUpDue: true, sameNameCount: 2, nextAppointmentAt: "2026-10-20T08:00:00Z" })]))
    show()
    expect(await screen.findByText("Ada Lovelace")).toBeTruthy()
    expect(screen.getByText("3 visits")).toBeTruthy()
    expect(screen.getByText("Follow-up due")).toBeTruthy()
    expect(screen.getByText("Same name ×2")).toBeTruthy()
  })
  it("switches saved views on the server, and offers My patients to a logged-in doctor", async () => {
    show()
    await screen.findByText("Ada Lovelace")
    fireEvent.click(await screen.findByRole("button", { name: "My patients" }))
    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringMatching(/view=MINE.*practitionerId=d1|practitionerId=d1.*view=MINE/)))
    fireEvent.click(screen.getByRole("button", { name: "Possible duplicates" }))
    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringContaining("view=DUPLICATES")))
  })
  it("says so when a view is empty", async () => {
    get.mockImplementation(async (url: string) => url.includes("practitioners") ? { data: { data: [] } } : dir([]))
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Never seen" }))
    expect(await screen.findByText("No patients in this view")).toBeTruthy()
  })
})
