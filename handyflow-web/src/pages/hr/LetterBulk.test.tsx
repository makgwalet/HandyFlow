import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import LetterBulk from "./LetterBulk"
import { bulkPrepareProblem, bulkTemplateProblem, readyToIssue } from "./letters.logic"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (data: unknown) => Promise.resolve({ data: { data } })
const BASE = "/api/v1/hr/letters"
const fields = [{ key: "employee.name", label: "Employee name", group: "Employee" }, { key: "hearing.date", label: "Hearing date", group: "Hearing" }]
const t = (o: object = {}) => ({ id: "t1", code: "X", name: "Annual notice", category: "GENERAL", subject: "s", body: "b", requiresAck: false, active: true,
  customised: false, standard: true, revision: 1, fields: ["employee.name"], ...o })
const emps = [{ id: "e1", fullName: "Jane Dlamini", employeeNumber: "E1" }, { id: "e2", fullName: "Sipho Nkosi", employeeNumber: "E2" }]

function serve(templates: object[]) {
  get.mockImplementation((url: string) => {
    if (url === `${BASE}/templates`) return ok({ templates, fields })
    if (url.startsWith("/api/v1/hr/employees")) return ok(emps)
    return Promise.reject(new Error(`unmocked ${url}`))
  })
}
beforeEach(() => { get.mockReset(); post.mockReset() })
afterEach(cleanup)

describe("bulk letter rules in the browser", () => {
  it("refuses case templates and case fields", () => {
    expect(bulkTemplateProblem({ category: "DISCIPLINARY", fields: [] }, fields)).toMatch(/case/)
    expect(bulkTemplateProblem({ category: "GENERAL", fields: ["hearing.date"] }, fields)).toMatch(/case details/)
    expect(bulkTemplateProblem({ category: "EMPLOYMENT", fields: ["employee.name"] }, fields)).toBeNull()
  })
  it("checks the request", () => {
    expect(bulkPrepareProblem(false, 3)).toMatch(/template/)
    expect(bulkPrepareProblem(true, 0)).toMatch(/at least one/)
    expect(bulkPrepareProblem(true, 201)).toMatch(/200/)
    expect(bulkPrepareProblem(true, 2)).toBeNull()
  })
  it("picks only complete drafts", () => {
    expect(readyToIssue([
      { letterId: "a", gaps: [], problem: null }, { letterId: "b", gaps: ["Venue"], problem: null },
      { letterId: null, gaps: [], problem: "No" }, { letterId: "c", gaps: [], problem: "Refused" },
    ])).toEqual(["a"])
  })
})

describe("bulk letters screen", () => {
  it("prepares drafts for the chosen people, then issues the complete ones", async () => {
    serve([t()])
    post.mockImplementation((url: string) => url.endsWith("/bulk/prepare")
      ? ok({ made: 1, refused: 1, items: [
        { employeeId: "e1", employeeName: "Jane Dlamini", letterId: "l1", number: "LTR-1", gaps: [], problem: null },
        { employeeId: "e2", employeeName: "Sipho Nkosi", letterId: null, number: null, gaps: [], problem: "No email on file" }] })
      : ok({ issued: 1, refused: 0, items: [{ letterId: "l1", number: "LTR-1", employeeName: "Jane Dlamini", problem: null }] }))
    wrap(<LetterBulk cancel={() => {}} />)
    await screen.findByText("Annual notice")
    fireEvent.change(screen.getByLabelText("Template"), { target: { value: "t1" } })
    fireEvent.click(await screen.findByLabelText(/Jane Dlamini/))
    fireEvent.click(screen.getByLabelText(/Sipho Nkosi/))
    fireEvent.click(screen.getByText("Prepare drafts"))
    await screen.findByText("No email on file")
    expect(post).toHaveBeenCalledWith(`${BASE}/bulk/prepare`, { templateId: "t1", employeeIds: ["e1", "e2"] })
    expect(screen.getByText("Ready to issue")).toBeTruthy()
    fireEvent.click(screen.getByText("Issue the complete drafts (1)"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${BASE}/bulk/issue`, { letterIds: ["l1"] }))
    await screen.findByText(/1 issued, 0 not issued/)
  })
  it("does not let a case template be used", async () => {
    serve([t({ id: "t2", name: "Hearing notice", fields: ["hearing.date"] })])
    wrap(<LetterBulk cancel={() => {}} />)
    await screen.findByText("Hearing notice")
    fireEvent.change(screen.getByLabelText("Template"), { target: { value: "t2" } })
    expect((await screen.findByRole("alert")).textContent).toMatch(/case details/)
    fireEvent.click(await screen.findByLabelText(/Jane Dlamini/))
    expect((screen.getByText("Prepare drafts") as HTMLButtonElement).disabled).toBe(true)
  })
  it("shows the server's reason when preparing fails", async () => {
    serve([t()])
    post.mockRejectedValue({ response: { data: { message: "At most 200 letters can be made at once." } } })
    wrap(<LetterBulk cancel={() => {}} />)
    await screen.findByText("Annual notice")
    fireEvent.change(screen.getByLabelText("Template"), { target: { value: "t1" } })
    fireEvent.click(await screen.findByLabelText(/Jane Dlamini/))
    fireEvent.click(screen.getByText("Prepare drafts"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/At most 200/)
  })
})
