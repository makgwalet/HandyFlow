import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import MyTraining from "./MyTraining"
import TeamTraining from "./TeamTraining"
import RequiredTrainingTab from "../training/RequiredTrainingTab"

const get = vi.fn()
const post = vi.fn()
const del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a) } }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (data: unknown) => Promise.resolve({ data: { data } })

const open = [
  { id: "s1", courseId: "c1", courseTitle: "First Aid", startDate: "2099-03-02", endDate: "2099-03-03", venue: "Boardroom", seatsLeft: 1, required: true },
  { id: "s2", courseId: "c2", courseTitle: "Fire Marshal", startDate: "2099-02-01", endDate: "2099-02-01", venue: null, seatsLeft: null, required: false },
]
const mine = {
  bookings: [{ id: "b1", courseTitle: "Forklift", startDate: "2099-06-01", endDate: "2099-06-01", venue: "Yard", status: "ENROLLED", statusLabel: "Booked", score: null, passed: null }],
  certificates: [],
}

beforeEach(() => {
  get.mockReset(); post.mockReset(); del.mockReset()
})
afterEach(cleanup)

describe("booking yourself onto training", () => {
  beforeEach(() => {
    get.mockImplementation((url: string) => ok(url.endsWith("/sessions") ? open : mine))
  })

  it("lists open sessions with required ones first and the places left", async () => {
    wrap(<MyTraining />)
    expect(await screen.findByRole("button", { name: "Book First Aid" })).toBeTruthy()
    expect(screen.getByText("Required for your role")).toBeTruthy()
    expect(screen.getByText("1 place left")).toBeTruthy()
    expect(screen.getByText("Places available")).toBeTruthy()
  })

  it("books a session and says so", async () => {
    post.mockResolvedValue({ data: {} })
    wrap(<MyTraining />)
    fireEvent.click(await screen.findByRole("button", { name: "Book First Aid" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training/mine/bookings", { sessionId: "s1" }))
    expect(await screen.findByText("You are booked on First Aid.")).toBeTruthy()
  })

  it("shows the reason when the booking is refused", async () => {
    post.mockRejectedValue({ response: { data: { message: "This session is full." } } })
    wrap(<MyTraining />)
    fireEvent.click(await screen.findByRole("button", { name: "Book First Aid" }))
    expect(await screen.findByText("This session is full.")).toBeTruthy()
  })

  it("cancels a booking that has not started", async () => {
    del.mockResolvedValue({ data: {} })
    wrap(<MyTraining />)
    fireEvent.click(await screen.findByRole("button", { name: "Cancel booking for Forklift" }))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/training/mine/bookings/b1"))
  })
})

describe("a manager's team", () => {
  it("lists people who need to act first and says what is wrong", async () => {
    get.mockReturnValue(ok({
      linked: true,
      members: [
        { employeeId: "e1", name: "Zodwa Ndlovu", jobTitle: "Clerk", certificates: [], bookings: [], required: [{ courseId: "c1", courseTitle: "First Aid", status: "OK", label: "Valid", expiryDate: "2099-01-01" }] },
        { employeeId: "e2", name: "Anele Dube", jobTitle: "Driver", certificates: [], bookings: [], required: [{ courseId: "c1", courseTitle: "First Aid", status: "MISSING", label: "Not held", expiryDate: null }] },
      ],
    }))
    wrap(<TeamTraining />)
    expect(await screen.findByText("2 people on your team: 1 to act on, 0 with something expiring soon.")).toBeTruthy()
    const names = screen.getAllByRole("heading", { level: 3 }).map(h => h.textContent)
    expect(names).toEqual(["Anele Dube", "Zodwa Ndlovu"])
    expect(screen.getByText("Needs action")).toBeTruthy()
  })

  it("says so when the login has no employee record", async () => {
    get.mockReturnValue(ok({ linked: false, members: [] }))
    wrap(<TeamTraining />)
    expect(await screen.findByText(/not linked to an employee record/)).toBeTruthy()
  })
})

describe("required training", () => {
  const courses = { content: [
    { id: "c1", title: "First Aid", certificationOffered: true, status: "ACTIVE" },
    { id: "c2", title: "Induction", certificationOffered: false, status: "ACTIVE" },
  ] }
  const matrix = {
    columns: [{ courseId: "c1", courseTitle: "First Aid" }],
    rows: [
      { employeeId: "e1", employeeNumber: "E1", name: "Zodwa Ndlovu", jobTitle: "Clerk", department: "Admin", cells: [{ status: "OK", label: "Valid", expiryDate: "2099-01-01" }] },
      { employeeId: "e2", employeeNumber: "E2", name: "Anele Dube", jobTitle: "Driver", department: "Logistics", cells: [{ status: "MISSING", label: "Not held", expiryDate: null }] },
    ],
    summary: { people: 2, required: 2, valid: 1, expiring: 0, expired: 0, missing: 1, booked: 0 },
  }
  beforeEach(() => {
    get.mockImplementation((url: string) => {
      if (url.endsWith("/requirements")) return ok([{ id: "r1", courseId: "c1", courseTitle: "First Aid", jobTitle: null, department: null, appliesTo: "Everyone" }])
      if (url.endsWith("/matrix")) return ok(matrix)
      return Promise.resolve({ data: courses })
    })
  })

  it("offers only courses that give a certificate", async () => {
    wrap(<RequiredTrainingTab />)
    await screen.findByText("1 of 2 required certificates for 2 people is missing or expired.")
    const options = Array.from((screen.getByLabelText("Course") as HTMLSelectElement).options).map(o => o.textContent)
    expect(options).toContain("First Aid")
    expect(options).not.toContain("Induction")
  })

  it("adds a requirement with blank parts sent as nothing", async () => {
    post.mockResolvedValue({ data: {} })
    wrap(<RequiredTrainingTab />)
    await screen.findByText(/required certificates/)
    fireEvent.change(screen.getByLabelText("Course"), { target: { value: "c1" } })
    fireEvent.change(screen.getByLabelText("Job title (optional)"), { target: { value: " Driver " } })
    fireEvent.click(screen.getByRole("button", { name: "Add requirement" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training/requirements", { courseId: "c1", jobTitle: "Driver", department: null }))
  })

  it("cannot add without a course", async () => {
    wrap(<RequiredTrainingTab />)
    await screen.findByText(/required certificates/)
    expect((screen.getByRole("button", { name: "Add requirement" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("lists people with something to put right first and can hide the rest", async () => {
    wrap(<RequiredTrainingTab />)
    await screen.findByText(/required certificates/)
    const names = () => screen.getAllByRole("row").slice(2).map(r => r.textContent ?? "")
    expect(names()[0]).toContain("Anele Dube")
    fireEvent.click(screen.getByLabelText("Only people with something to put right"))
    expect(screen.queryByText("Zodwa Ndlovu")).toBeNull()
  })

  it("removes a requirement", async () => {
    del.mockResolvedValue({ data: {} })
    wrap(<RequiredTrainingTab />)
    fireEvent.click(await screen.findByRole("button", { name: "Remove requirement First Aid for Everyone" }))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/training/requirements/r1"))
  })
})
