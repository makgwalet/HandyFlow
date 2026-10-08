import { describe, it, expect, vi, afterEach } from "vitest"
import { render, screen, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"

let perms: string[] = []
vi.mock("../../hooks/usePermission", () => ({
  usePermission: (p: string) => perms.includes(p),
  usePermissions: () => false,
}))
vi.mock("../../api/client", () => ({ apiClient: { get: vi.fn().mockResolvedValue({ data: { data: [] } }), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() } }))
vi.mock("./PatientOverviewTab", () => ({ default: () => <div>overview</div> }))

import PatientFilePage from "./PatientFilePage"

afterEach(cleanup)
const patient: any = { id: "p1", firstName: "A", lastName: "B", fullName: "A B", accountType: "INDIVIDUAL" }
const renderIt = () => render(
  <MemoryRouter><QueryClientProvider client={new QueryClient()}>
    <PatientFilePage patient={patient} onClose={() => {}} onNavigate={() => {}} />
  </QueryClientProvider></MemoryRouter>)
const names = () => ["Account", "Prescriptions", "Lab results", "Documents", "Timeline", "Consent"]
  .filter(n => screen.queryByRole("button", { name: new RegExp(n) }))

describe("patient file tabs follow permission", () => {
  it("shows only the tabs the person may open", () => {
    perms = ["CLINIC_PRESCRIPTION_READ", "CLINIC_TIMELINE_READ"]
    renderIt()
    expect(names()).toEqual(["Prescriptions", "Timeline"])
  })
  it("the coarse permissions still count", () => {
    perms = ["CLINIC_READ", "CLINIC_BILLING_READ"]
    renderIt()
    expect(names()).toEqual(["Account", "Prescriptions", "Lab results", "Documents", "Timeline", "Consent"])
  })
  it("no permission shows none of them", () => {
    perms = []
    renderIt()
    expect(names()).toEqual([])
  })
})
