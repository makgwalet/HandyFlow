import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn(); const patch = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), patch: (...a: any[]) => patch(...a) } }))
const perms: Record<string, boolean> = {}
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
vi.mock("./PatientNotes", () => ({ default: () => null }))
vi.mock("./ObservationMatrix", () => ({ default: () => null }))
vi.mock("./PatientSummaryPrint", () => ({ default: () => null }))
vi.mock("./ClinicalSummaryPanel", () => ({ default: () => null }))
vi.mock("./PatientBriefingPanel", () => ({ default: () => null }))
import OverviewTab from "./PatientOverviewTab"

const patient: any = { id: "p1", firstName: "Thandi", lastName: "Dube", fullName: "Thandi Dube", idNumber: "9001014000087", dateOfBirth: "1990-01-01",
  gender: "FEMALE", phone: "0820000000", email: "", emergencyContactName: "", emergencyContactPhone: "", notes: "", active: true,
  accountType: "PRINCIPAL", allergies: [], chronicConditions: [], bloodType: "" }

let qc: QueryClient
const show = (over: object = {}, props: object = {}) => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onOpenTab = vi.fn()
  render(<QueryClientProvider client={qc}><OverviewTab patient={{ ...patient, ...over }} idInfo={null} familyMembers={[]} qc={qc} onOpenTab={onOpenTab} {...props}/></QueryClientProvider>)
  return { onOpenTab }
}

afterEach(cleanup)
beforeEach(() => {
  get.mockReset(); post.mockReset(); patch.mockReset()
  for (const k of Object.keys(perms)) delete perms[k]
  get.mockResolvedValue({ data: { data: {} } })
  patch.mockResolvedValue({ data: { data: { sexAtBirth: "FEMALE" } } })
})

describe("overview: sex at birth", () => {
  it("says when it is not recorded and why it matters", () => {
    show()
    expect(screen.getByText("Not recorded")).toBeTruthy()
    expect(screen.getByText(/Growth charts and sex-specific questions need this/)).toBeTruthy()
    expect(screen.getByText(/ID number suggests female/)).toBeTruthy()
  })
  it("offers no edit button without update permission", () => {
    show()
    expect(screen.queryByRole("button", { name: "Set" })).toBeNull()
  })
  it("saves sex at birth and pregnancy status together", async () => {
    perms.CLINIC_PATIENT_UPDATE = true
    show()
    fireEvent.click(screen.getByRole("button", { name: "Set" }))
    expect((screen.getByLabelText("Sex at birth") as HTMLSelectElement).value).toBe("FEMALE")   // from the ID suggestion
    fireEvent.change(screen.getByLabelText("Pregnancy status"), { target: { value: "NOT_PREGNANT" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/clinic/patients/p1", { sexAtBirth: "FEMALE", pregnancyStatus: "NOT_PREGNANT" }))
  })
  it("hides pregnancy status for a male and sends it cleared", async () => {
    perms.CLINIC_PATIENT_UPDATE = true
    show({ idNumber: "" })
    fireEvent.click(screen.getByRole("button", { name: "Set" }))
    fireEvent.change(screen.getByLabelText("Sex at birth"), { target: { value: "MALE" } })
    expect(screen.queryByLabelText("Pregnancy status")).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/clinic/patients/p1", { sexAtBirth: "MALE", pregnancyStatus: null }))
  })
  it("shows the server's message when saving fails", async () => {
    perms.CLINIC_PATIENT_UPDATE = true
    patch.mockRejectedValue({ response: { data: { message: "sexAtBirth must be one of [...]" } } })
    show()
    fireEvent.click(screen.getByRole("button", { name: "Set" }))
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await screen.findByText(/sexAtBirth must be one of/)
  })
  it("links to the growth chart only with growth permission", () => {
    const a = show(); expect(screen.queryByRole("button", { name: "Growth chart" })).toBeNull(); cleanup()
    perms.CLINIC_GROWTH_READ = true
    const b = show()
    fireEvent.click(screen.getByRole("button", { name: "Growth chart" }))
    expect(b.onOpenTab).toHaveBeenCalledWith("growth"); expect(a.onOpenTab).not.toHaveBeenCalled()
  })
})

describe("overview: add dependant", () => {
  const open = async () => { show(); fireEvent.click(screen.getByRole("button", { name: /Add/ })) }

  it("explains a missing name instead of doing nothing", async () => {
    await open()
    fireEvent.click(screen.getByRole("button", { name: "Add dependant" }))
    await screen.findByText(/First and last name are required/)
    expect(post).not.toHaveBeenCalled()
  })
  it("refuses a bad ID number", async () => {
    await open()
    fireEvent.change(screen.getByPlaceholderText("Alex"), { target: { value: "Alex" } })
    fireEvent.change(screen.getByPlaceholderText("Smith"), { target: { value: "Smith" } })
    fireEvent.change(screen.getByPlaceholderText("ID number"), { target: { value: "9019994800087" } })
    fireEvent.click(screen.getByRole("button", { name: "Add dependant" }))
    await screen.findByText(/not a valid SA ID/)
    expect(post).not.toHaveBeenCalled()
  })
  it("adds the dependant and then saves their sex at birth", async () => {
    post.mockResolvedValue({ data: { data: { id: "p2" } } })
    await open()
    fireEvent.change(screen.getByPlaceholderText("Alex"), { target: { value: "Alex" } })
    fireEvent.change(screen.getByPlaceholderText("Smith"), { target: { value: "Smith" } })
    fireEvent.change(screen.getByPlaceholderText("ID number"), { target: { value: "2501015000081" } })
    expect((screen.getByLabelText("Dependant sex at birth") as HTMLSelectElement).value).toBe("MALE")
    fireEvent.click(screen.getByRole("button", { name: "Add dependant" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/patients", expect.objectContaining({ firstName: "Alex", accountType: "DEPENDANT", principalId: "p1", dateOfBirth: "2025-01-01" })))
    await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/clinic/patients/p2", { sexAtBirth: "MALE" }))
  })
  it("keeps the dialog open and says so when only the sex-at-birth save fails", async () => {
    post.mockResolvedValue({ data: { data: { id: "p2" } } })
    patch.mockRejectedValue(new Error("x"))
    await open()
    fireEvent.change(screen.getByPlaceholderText("Alex"), { target: { value: "Alex" } })
    fireEvent.change(screen.getByPlaceholderText("Smith"), { target: { value: "Smith" } })
    fireEvent.change(screen.getByLabelText("Dependant sex at birth"), { target: { value: "FEMALE" } })
    fireEvent.click(screen.getByRole("button", { name: "Add dependant" }))
    await screen.findByText(/dependant was added, but their sex at birth could not be saved/)
  })
})

describe("overview: layout", () => {
  it("shows details, contact and notes as separate cards, with the family card only for family accounts", () => {
    show({ emergencyContactName: "Sipho Dube", notes: "Prefers morning slots" })
    for (const t of ["Personal details", "Contact and emergency", "Notes and alerts", "Registration notes", "Family account"]) expect(screen.getByText(t)).toBeTruthy()
    expect(screen.getByText("Sipho Dube")).toBeTruthy(); cleanup()
    show({ accountType: "INDIVIDUAL" })
    expect(screen.queryByText("Family account")).toBeNull()
  })
})
