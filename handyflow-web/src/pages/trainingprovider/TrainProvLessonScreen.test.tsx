// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvLessonsTab from "./TrainProvLessonsTab"
import TrainProvProgressPanel from "./TrainProvProgressPanel"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const lesson = (over: object = {}) => ({
  id: "l1", lessonNo: "LSN-00001", programmeVersionId: "v1", versionNo: 1, moduleId: null, moduleTitle: null, title: "Safety basics", kind: "READING",
  contentText: "A reading that is long enough to count", url: null, position: 1, status: "DRAFT", approvedAt: null, approvalOverrideReason: null, retiredReason: null,
  nextStatuses: ["APPROVED", "RETIRED"], ...over,
})
const progress = (rows: unknown[], finished = 0) => ({ enrolmentId: "e1", enrolmentNo: "ENR-00001", learnerName: "T L", programmeTitle: "P", versionNo: 1, rows, finished, total: rows.length, summary: `${finished} of ${rows.length} lessons marked as finished. This is progress only. It is not an assessment outcome.` })
const prow = (over: object = {}) => ({ lessonId: "l1", lessonNo: "LSN-00001", title: "Safety basics", kind: "READING", moduleId: null, position: 1, progressId: null, finishedOn: null, note: null, ...over })
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let lessons: unknown[] = []
let prog = progress([prow()])
beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); lessons = [lesson()]; prog = progress([prow()])
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith("/lessons") ? lessons : url.includes("lesson-progress") ? prog : [] } }))
  post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("lessons tab", () => {
  it("lists lessons and says a second person approves", async () => {
    wrap(<TrainProvLessonsTab />)
    expect(await screen.findByText(/Safety basics/)).toBeTruthy()
    expect(screen.getByText(/A second person should approve it/)).toBeTruthy()
  })

  it("approves without a reason and sends none", async () => {
    wrap(<TrainProvLessonsTab />)
    fireEvent.click(await screen.findByText("Approve"))
    fireEvent.click(screen.getByText("Approve lesson"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/lessons/l1/approve", { overrideReason: null }))
  })

  it("rejects a short retire reason and then retires", async () => {
    wrap(<TrainProvLessonsTab />)
    fireEvent.click(await screen.findByText("Retire"))
    fireEvent.click(screen.getByText("Retire lesson"))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for retiring"), { target: { value: "Replaced by a newer reading" } })
    fireEvent.click(screen.getByText("Retire lesson"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/lessons/l1/retire", { reason: "Replaced by a newer reading" }))
  })

  it("does not offer edit on an approved lesson", async () => {
    lessons = [lesson({ status: "APPROVED", approvedAt: "2027-01-02T10:00:00Z", nextStatuses: ["RETIRED"] })]
    wrap(<TrainProvLessonsTab />)
    await screen.findByText(/Approved on 2027-01-02/)
    expect(screen.queryByText("Edit")).toBeNull()
    expect(screen.queryByText("Approve")).toBeNull()
  })

  it("checks the new lesson form before sending", async () => {
    wrap(<TrainProvLessonsTab />)
    fireEvent.click(await screen.findByText("New lesson"))
    fireEvent.click(screen.getByText("Create lesson"))
    expect(await screen.findByText(/programme and version/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })
})

describe("progress panel", () => {
  it("loads nothing until opened and says it is progress only", async () => {
    wrap(<TrainProvProgressPanel enrolmentId="e1" startDate="2020-01-01" />)
    expect(get).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Learning progress"))
    expect(await screen.findByText(/records progress only/)).toBeTruthy()
    expect(await screen.findByText(/not an assessment outcome/)).toBeTruthy()
  })

  it("marks a lesson as finished", async () => {
    wrap(<TrainProvProgressPanel enrolmentId="e1" startDate="2020-01-01" />)
    fireEvent.click(screen.getByText("Learning progress"))
    fireEvent.click(await screen.findByText("Mark as finished"))
    fireEvent.click(screen.getByText("Save as finished"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/lesson-progress", expect.objectContaining({ enrolmentId: "e1", lessonId: "l1", note: null })))
  })

  it("needs a reason to reverse", async () => {
    prog = progress([prow({ progressId: "p1", finishedOn: "2027-06-01" })], 1)
    wrap(<TrainProvProgressPanel enrolmentId="e1" startDate="2020-01-01" />)
    fireEvent.click(screen.getByText("Learning progress"))
    fireEvent.click(await screen.findByText("Reverse this record"))
    fireEvent.click(screen.getByText("Reverse the record"))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Reason for reversing"), { target: { value: "Marked on the wrong enrolment" } })
    fireEvent.click(screen.getByText("Reverse the record"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/lesson-progress/p1/reverse", { reason: "Marked on the wrong enrolment" }))
  })
})

describe("learning system links", () => {
  const link = (over: object = {}) => ({ id: "k1", programmeVersionId: "v1", versionNo: 1, programmeId: "p1", programmeTitle: "Safety skills", systemName: "Moodle", launchUrl: "https://lms.example.org/c/7", notes: null, status: "ACTIVE", endedReason: null, createdAt: "", ...over })

  it("shows the open link on the progress panel when the version has an active link", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("lms-links") ? [link()] : url.includes("lesson-progress") ? prog : [] } }))
    wrap(<TrainProvProgressPanel enrolmentId="e1" startDate="2020-01-01" versionId="v1" />)
    fireEvent.click(screen.getByText("Learning progress"))
    const a = await screen.findByText("Open Moodle")
    expect(a.getAttribute("href")).toBe("https://lms.example.org/c/7")
    expect(screen.getByText(/Progress there is not synced/)).toBeTruthy()
  })

  it("does not look for a link without a version", async () => {
    wrap(<TrainProvProgressPanel enrolmentId="e1" startDate="2020-01-01" />)
    fireEvent.click(screen.getByText("Learning progress"))
    await screen.findByText(/records progress only/)
    expect(get.mock.calls.some(c => String(c[0]).includes("lms-links"))).toBe(false)
  })

  it("refuses an address with a token and needs a reason to end a link", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("lms-links") ? [link()] : lessons } }))
    wrap(<TrainProvLessonsTab />)
    fireEvent.click(await screen.findByText("End link"))
    fireEvent.click(screen.getByText("End the link"))
    expect(await screen.findAllByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for ending the link"), { target: { value: "Contract with the system ended" } })
    fireEvent.click(screen.getByText("End the link"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/lms-links/k1/end", { reason: "Contract with the system ended" }))
  })

  it("checks the new link before sending", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("lms-links") ? [] : lessons } }))
    wrap(<TrainProvLessonsTab />)
    fireEvent.click(await screen.findByText("Add a link"))
    fireEvent.change(screen.getByLabelText("Learning system name"), { target: { value: "Moodle" } })
    fireEvent.change(screen.getByLabelText("Launch link"), { target: { value: "https://lms.example.org/c?token=abc" } })
    fireEvent.click(screen.getByText("Add link"))
    expect(await screen.findByText(/programme and version/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })
})
