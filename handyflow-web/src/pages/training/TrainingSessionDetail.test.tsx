import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainingSessionDetail from "./TrainingSessionDetail"
import { apiMessage } from "./training.logic"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

const session = (o: object = {}) => ({ id: "s1", courseId: "c1", courseTitle: "First Aid", startDate: "2026-10-20", endDate: "2026-10-21", venue: "Head Office", trainerName: "Trainer",
  capacity: 10, enrolledCount: 1, status: "IN_PROGRESS", notes: null, cancelReason: null, createdAt: "2026-10-01T00:00:00Z", ...o })
const enrolment = (o: object = {}) => ({ id: "e1", sessionId: "s1", employeeId: "emp1", employeeNameSnapshot: "Jane Dlamini", employeeNumberSnapshot: "E1", status: "ENROLLED",
  enrolledAt: "2026-10-05T00:00:00Z", completedAt: null, score: null, passed: null, notes: null, cancelReason: null, ...o })

function serve(s: object, rows: object[]) {
  get.mockImplementation((url: string) => {
    if (url === "/api/v1/training/sessions/s1") return Promise.resolve({ data: s })
    if (url.startsWith("/api/v1/training/courses/")) return Promise.resolve({ data: { id: "c1", title: "First Aid", certificationOffered: false } })
    if (url === "/api/v1/training/enrollments") return Promise.resolve({ data: { content: rows } })
    if (url.endsWith("/evidence")) return Promise.resolve({ data: [] })
    return Promise.reject(new Error(`unmocked ${url}`))
  })
}
const show = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <TrainingSessionDetail sessionId="s1" onBack={() => {}} />
  </QueryClientProvider>)

beforeEach(() => { get.mockReset(); post.mockReset() })
afterEach(cleanup)

describe("training session screen", () => {
  it("reads the server's reason for a message", () => {
    expect(apiMessage({ response: { data: { message: "No." } } }, "x")).toBe("No.")
    expect(apiMessage({ response: { data: {} } }, "fallback")).toBe("fallback")
    expect(apiMessage(null, "fallback")).toBe("fallback")
  })

  it("shows why the session cannot be completed yet", async () => {
    serve(session(), [enrolment()])
    post.mockRejectedValue({ response: { data: { message: "Record attendance for the 1 person still shown as enrolled before completing the session." } } })
    show()
    fireEvent.click(await screen.findByText("Mark complete"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/Record attendance for the 1 person/)
  })

  it("shows why attendance could not be recorded, and clears it when the next step works", async () => {
    serve(session({ status: "SCHEDULED", startDate: "2026-12-01" }), [enrolment()])
    post.mockRejectedValueOnce({ response: { data: { message: "This session has not started yet (it starts on 2026-12-01)." } } })
    show()
    fireEvent.click(await screen.findByText("Attended"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/has not started yet/)
    post.mockResolvedValueOnce({ data: {} })
    fireEvent.click(screen.getByText("Attended"))
    await waitFor(() => expect(screen.queryByRole("alert")).toBeNull())
  })
})
