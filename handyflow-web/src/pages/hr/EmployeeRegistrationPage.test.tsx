import { describe, it, expect, vi, beforeEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EmployeeRegistrationPage from "./EmployeeRegistrationPage"

const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "u1", permissions: ["HR_MANAGE", "HR_WRITE", "HR_READ", "HR_ADMIN", "HR_EMPLOYEE_CREATE", "EMPLOYEE_MANAGE"] } }) }))
vi.mock("./employees.logic", async orig => ({ ...(await orig<typeof import("./employees.logic")>()), employeeRights: () => ({ manage: true }) }))

const wrap = () => render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><EmployeeRegistrationPage /></MemoryRouter></QueryClientProvider>)
const type = (label: string | RegExp, v: string) => fireEvent.change(screen.getByLabelText(label), { target: { value: v } })

describe("EmployeeRegistrationPage", () => {
  beforeEach(() => { cleanup(); post.mockReset(); localStorage.clear() })

  it("blocks Next with the reason beside the field, keeps data between steps, and registers at the end", async () => {
    post.mockResolvedValue({ data: { data: { id: "e1" } } })
    wrap()
    fireEvent.click(screen.getByText("Next step →"))
    expect(await screen.findByText("Enter the first name")).toBeTruthy()
    expect(screen.getByText("Step 1 of 6")).toBeTruthy()
    type(/^First name/, "Thandi"); type(/^Last name/, "Nkosi")
    fireEvent.click(screen.getByText("Next step →"))
    expect(await screen.findByText("Step 2 of 6")).toBeTruthy()
    fireEvent.click(screen.getByText("Next step →"))                      // contact has nothing required
    fireEvent.click(screen.getByText("Next step →"))                      // employment needs the start date
    expect(await screen.findByText("Choose the start date")).toBeTruthy()
    type(/^Start date/, "2026-11-01")
    fireEvent.click(screen.getByText("Next step →"))
    type(/^Gross salary/, "25000")
    fireEvent.click(screen.getByText("Next step →")); fireEvent.click(screen.getByText("Next step →"))
    expect(await screen.findByText("Step 6 of 6")).toBeTruthy()
    fireEvent.click(screen.getByText("← Back")); fireEvent.click(screen.getByText("← Back")); fireEvent.click(screen.getByText("← Back")); fireEvent.click(screen.getByText("← Back")); fireEvent.click(screen.getByText("← Back"))
    expect((screen.getByLabelText(/^First name/) as HTMLInputElement).value).toBe("Thandi")   // nothing was lost
    for (let i = 0; i < 5; i++) fireEvent.click(screen.getByText("Next step →"))
    fireEvent.click(await screen.findByText("Register employee"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/employees", expect.objectContaining({ firstName: "Thandi", grossSalary: 25000, medicalAidContribution: 0 })))
  })

  it("saves a draft in the browser and offers it again", async () => {
    wrap()
    type(/^First name/, "Sipho")
    await waitFor(() => expect(localStorage.getItem("hf-hr-employee-draft:u1")).toContain("Sipho"), { timeout: 2000 })
    cleanup()
    wrap()
    expect(await screen.findByText(/unfinished draft/)).toBeTruthy()
    fireEvent.click(screen.getByText("Continue it"))
    expect((screen.getByLabelText(/^First name/) as HTMLInputElement).value).toBe("Sipho")
  })

  it("takes a duplicate-ID complaint from the server back to the ID field", async () => {
    post.mockRejectedValue({ response: { data: { message: "Another employee already has this ID number." } } })
    localStorage.setItem("hf-hr-employee-draft:u1", JSON.stringify({ v: 1, step: 5, savedAt: "", form: { firstName: "A", lastName: "B", startDate: "2026-11-01", grossSalary: "1000", idNumber: "8001015009087" } }))
    wrap()
    fireEvent.click(await screen.findByText("Continue it"))
    fireEvent.click(await screen.findByText("Register employee"))
    expect(await screen.findAllByText("Another employee already has this ID number.")).toBeTruthy()
    expect(screen.getByText("Step 1 of 6")).toBeTruthy()
  })
})
