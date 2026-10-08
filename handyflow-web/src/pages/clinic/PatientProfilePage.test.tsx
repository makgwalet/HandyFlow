import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), put: (...a: any[]) => put(...a) } }))
const perms: Record<string, boolean> = {}
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
vi.mock("./PatientBackgroundCards", () => ({ MedicalAidCard: () => <div>medical aid card</div> }))
import PatientProfilePage from "./PatientProfilePage"
import ProfileCard from "./ProfileCard"

const patient: any = { id: "p1", firstName: "Sipho", lastName: "Nkosi", fullName: "Sipho Nkosi", idNumber: "", dateOfBirth: "1980-01-01", gender: "Male",
  phone: "0821234567", email: "", emergencyContactName: "", emergencyContactPhone: "", accountType: "INDIVIDUAL", active: true }
const items = (done: string[]) => [["name", "identity"], ["id", "identity"], ["phone", "contact"], ["address", "address"], ["emergency", "emergency"]]
  .map(([key, section]) => ({ key, label: `Label ${key}`, section, done: done.includes(key) }))
const profile = (done: string[], extra: object = {}) => ({ data: { data: { city: "Pretoria", completeness: { done: done.length, total: 5, percent: done.length * 20, items: items(done) }, ...extra } } })
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

afterEach(cleanup)
beforeEach(() => { get.mockReset(); put.mockReset(); for (const k of Object.keys(perms)) delete perms[k] })

describe("change history", () => {
  it("lists corrections to name, ID and date of birth with who and when", async () => {
    perms.CLINIC_PATIENT_UPDATE = true; perms.CLINIC_PATIENT_DEMOGRAPHICS_WRITE = true
    get.mockImplementation(async (url: string) => url.endsWith("/corrections")
      ? { data: { data: [
          { field: "LAST_NAME", oldValue: "Botha", newValue: "Bothma", changedByName: "Dr Priya Govender", changedAt: "2026-10-08T08:00:00Z" },
          { field: "ID_NUMBER", oldValue: null, newValue: "A01234567", changedByName: null, changedAt: "2026-10-07T08:00:00Z" }] } }
      : profile(["name"]))
    wrap(<PatientProfilePage patient={patient} onBack={() => {}} initialSection="identity" />)
    expect(await screen.findByText(/Last name: Botha to Bothma/)).toBeTruthy()
    expect(screen.getByText(/Dr Priya Govender/)).toBeTruthy()
    expect(screen.getByText(/ID number: added A01234567/)).toBeTruthy()
    expect(screen.getByText(/A staff member/)).toBeTruthy()
  })
  it("shows no history block when there is none or it cannot be read", async () => {
    perms.CLINIC_PATIENT_UPDATE = true; perms.CLINIC_PATIENT_DEMOGRAPHICS_WRITE = true
    get.mockImplementation(async (url: string) => { if (url.endsWith("/corrections")) throw { response: { status: 403 } }; return profile(["name"]) })
    wrap(<PatientProfilePage patient={patient} onBack={() => {}} initialSection="identity" />)
    await screen.findByLabelText("First name *")
    expect(screen.queryByLabelText("Change history")).toBeNull()
  })
})

describe("profile page", () => {
  it("opens at the first section with something missing and shows the percentage", async () => {
    perms.CLINIC_PATIENT_UPDATE = true
    get.mockResolvedValue(profile(["name", "id"]))
    wrap(<PatientProfilePage patient={patient} onBack={() => {}} />)
    await screen.findByText("40%")
    await waitFor(() => expect(screen.getByRole("button", { name: "Contact" }).getAttribute("aria-current")).toBe("page"))
    expect(screen.getByRole("progressbar").getAttribute("aria-valuenow")).toBe("40")
  })
  it("saves the address as a whole profile and refuses a bad postal code", async () => {
    perms.CLINIC_PATIENT_UPDATE = true
    get.mockResolvedValue(profile(["name"]))
    put.mockResolvedValue({ data: { data: {} } })
    wrap(<PatientProfilePage patient={patient} initialSection="address" onBack={() => {}} />)
    await screen.findByLabelText("Postal code")
    await waitFor(() => expect((screen.getByLabelText("City") as HTMLInputElement).value).toBe("Pretoria"))
    fireEvent.change(screen.getByLabelText("Postal code"), { target: { value: "21A6" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/four digits/)
    expect(put).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Postal code"), { target: { value: "0001" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/profile", expect.objectContaining({ city: "Pretoria", postalCode: "0001", title: null })))
  })
  it("saves a name and ID correction, then the profile, and reports the new patient", async () => {
    perms.CLINIC_PATIENT_UPDATE = true; perms.CLINIC_PATIENT_DEMOGRAPHICS_WRITE = true
    get.mockResolvedValue(profile([]))
    put.mockImplementation(async (url: string) => url.endsWith("/demographics")
      ? { data: { data: { firstName: "Sipho", lastName: "Dlamini", idNumber: "8001015009087", dateOfBirth: "1980-01-01" } } } : { data: { data: {} } })
    const changed = vi.fn()
    wrap(<PatientProfilePage patient={patient} initialSection="identity" onBack={() => {}} onPatientChanged={changed} />)
    await screen.findByLabelText("Last name *")
    fireEvent.change(screen.getByLabelText("Last name *"), { target: { value: "Dlamini" } })
    fireEvent.change(screen.getByLabelText("ID or passport number"), { target: { value: "8001015009087" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(changed).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toBe("/api/v1/clinic/patients/p1/demographics")
    expect(put.mock.calls[0][1]).toMatchObject({ lastName: "Dlamini", idNumber: "8001015009087" })
    expect(put.mock.calls[1][0]).toBe("/api/v1/clinic/patients/p1/profile")
    expect(changed.mock.calls[0][0].fullName).toBe("Sipho Dlamini")
  })
  it("locks name and ID without the demographics permission, and everything for a viewer", async () => {
    perms.CLINIC_PATIENT_UPDATE = true
    get.mockResolvedValue(profile([]))
    wrap(<PatientProfilePage patient={patient} initialSection="identity" onBack={() => {}} />)
    await screen.findByLabelText("First name *")
    expect((screen.getByLabelText("First name *") as HTMLInputElement).disabled).toBe(true)
    expect((screen.getByLabelText("Nationality") as HTMLInputElement).disabled).toBe(false)
    cleanup(); delete perms.CLINIC_PATIENT_UPDATE
    wrap(<PatientProfilePage patient={patient} initialSection="identity" onBack={() => {}} />)
    await screen.findByLabelText("Nationality")
    expect((screen.getByLabelText("Nationality") as HTMLInputElement).disabled).toBe(true)
    expect((screen.getByRole("button", { name: "Save" }) as HTMLButtonElement).disabled).toBe(true)
  })
  it("saves contact details to the contact endpoint", async () => {
    perms.CLINIC_PATIENT_UPDATE = true
    get.mockResolvedValue(profile([]))
    put.mockResolvedValue({ data: { data: { firstName: "Sipho", lastName: "Nkosi", phone: "0839999999" } } })
    wrap(<PatientProfilePage patient={patient} initialSection="contact" onBack={() => {}} />)
    await screen.findByLabelText("Phone")
    fireEvent.change(screen.getByLabelText("Phone"), { target: { value: "0839999999" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/contact", expect.objectContaining({ phone: "0839999999", email: null })))
  })
  it("shows the scheme card and an individual's missing family", async () => {
    perms.CLINIC_PATIENT_UPDATE = true
    get.mockResolvedValue(profile([]))
    wrap(<PatientProfilePage patient={patient} initialSection="scheme" onBack={() => {}} />)
    await screen.findByText("medical aid card")
    fireEvent.click(screen.getByRole("button", { name: "Family" }))
    expect(screen.getByText(/not linked to a family account/)).toBeTruthy()
  })
})

describe("profile card", () => {
  it("shows the percentage and opens the first missing section", async () => {
    perms.CLINIC_PATIENT_READ = true; perms.CLINIC_PATIENT_UPDATE = true
    get.mockResolvedValue(profile(["name", "id"]))
    const open = vi.fn()
    wrap(<ProfileCard patientId="p1" onOpenProfile={open} />)
    await screen.findByText("40%")
    fireEvent.click(screen.getByRole("button", { name: "Complete profile" }))
    expect(open).toHaveBeenCalledWith("contact")
  })
  it("says update when complete and view without the update permission", async () => {
    perms.CLINIC_PATIENT_READ = true
    get.mockResolvedValue(profile(["name", "id", "phone", "address", "emergency"]))
    wrap(<ProfileCard patientId="p1" onOpenProfile={() => {}} />)
    await screen.findByText("100%")
    expect(screen.getByRole("button", { name: "View profile" })).toBeTruthy()
  })
})
