// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvAssessmentsTab from "./TrainProvAssessmentsTab"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const inst = (over: object = {}) => ({
  id: "i1", instrumentNo: "ASI-00001", programmeId: "p1", programmeTitle: "Safety skills", programmeVersionId: "v1", versionNo: 1, moduleId: null, moduleTitle: null,
  title: "Safety theory test", assessmentType: "KNOWLEDGE", maxMark: 50, guidance: null, requiresModeration: true, status: "DRAFT", approvedAt: null,
  approvalOverrideReason: null, retiredReason: null, nextStatuses: ["APPROVED", "RETIRED"], history: [{ id: "h1", action: "CREATED", note: null, actedBy: null, actedAt: "2026-11-25T08:00:00Z" }], ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset()
  get.mockImplementation((url: string) => {
    let data: unknown = []
    if (url.endsWith("/assessment-instruments")) data = [inst()]
    else if (url.endsWith("/assessment-instruments/i1")) data = inst()
    else if (url.endsWith("/programmes")) data = [{ id: "p1", code: "SP1", title: "Safety skills", trainingActivity: "ACCREDITED" }]
    else if (url.endsWith("/programmes/p1")) data = { programme: {}, offerings: [], versions: [{ id: "v1", versionNo: 1, status: "APPROVED", modules: [{ id: "m1", seq: 1, title: "Module one" }] }] }
    return Promise.resolve({ data: { data } })
  })
  post.mockResolvedValue({ data: { data: inst({ status: "APPROVED" }) } })
})
afterEach(cleanup)

describe("assessment instruments screen", () => {
  it("lists instruments and says a mark is only a scale", async () => {
    wrap(<TrainProvAssessmentsTab />)
    expect(await screen.findByText("Safety theory test")).toBeTruthy()
    expect(screen.getAllByText(/only sets the scale/).length).toBeGreaterThan(0)
    expect(screen.getAllByText("Draft").length).toBeGreaterThan(1)
  })

  it("approves with no reason when a second person does it", async () => {
    wrap(<TrainProvAssessmentsTab />)
    fireEvent.click(await screen.findByText("Safety theory test"))
    fireEvent.click(await screen.findByText("Approve"))
    fireEvent.click(screen.getByText("Approve instrument"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringContaining("/i1/approve"), { overrideReason: null }))
  })

  it("refuses a short override reason before sending", async () => {
    wrap(<TrainProvAssessmentsTab />)
    fireEvent.click(await screen.findByText("Safety theory test"))
    fireEvent.click(await screen.findByText("Approve"))
    fireEvent.change(screen.getByLabelText("Why no second person"), { target: { value: "short" } })
    fireEvent.click(screen.getByText("Approve instrument"))
    expect(await screen.findByText(/at least 10/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("retires only with a reason", async () => {
    wrap(<TrainProvAssessmentsTab />)
    fireEvent.click(await screen.findByText("Safety theory test"))
    fireEvent.click(await screen.findByText("Retire"))
    fireEvent.click(screen.getByText("Retire instrument"))
    expect(await screen.findByText(/at least 10/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for retiring"), { target: { value: "Replaced by the revised instrument" } })
    fireEvent.click(screen.getByText("Retire instrument"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringContaining("/i1/retire"), { reason: "Replaced by the revised instrument" }))
  })

  it("offers no actions on an approved instrument except retire, and shows no edit", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith("/i1") ? inst({ status: "APPROVED", nextStatuses: ["RETIRED"], approvedAt: "2026-11-25T09:00:00Z" }) : url.endsWith("/programmes") ? [] : [inst({ status: "APPROVED" })] } }))
    wrap(<TrainProvAssessmentsTab />)
    fireEvent.click(await screen.findByText("Safety theory test"))
    expect(await screen.findByText("Retire")).toBeTruthy()
    expect(screen.queryByText("Edit")).toBeNull()
    expect(screen.queryByText("Approve")).toBeNull()
  })

  it("creates an instrument against a chosen version and module", async () => {
    post.mockResolvedValue({ data: { data: inst() } })
    wrap(<TrainProvAssessmentsTab />)
    fireEvent.click(await screen.findByText("Add an instrument"))
    await waitFor(() => expect(screen.getAllByText("SP1 · Safety skills").length).toBe(2))
    const progs = screen.getAllByLabelText("Programme")
    fireEvent.change(progs[progs.length - 1], { target: { value: "p1" } })
    await screen.findByText(/Version 1/)
    fireEvent.change(screen.getByLabelText("Version"), { target: { value: "v1" } })
    await screen.findByText(/Module one/)
    fireEvent.change(screen.getByLabelText("Module"), { target: { value: "m1" } })
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Practical task" } })
    fireEvent.change(screen.getByLabelText("Maximum mark"), { target: { value: "20" } })
    fireEvent.click(screen.getByText("Create instrument"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringContaining("/assessment-instruments"), expect.objectContaining({ programmeVersionId: "v1", moduleId: "m1", title: "Practical task", maxMark: 20 })))
  })
})
