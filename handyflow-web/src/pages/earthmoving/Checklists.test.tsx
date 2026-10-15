import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), patch: vi.fn(), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import ChecklistsTab from "./ChecklistsTab"

afterEach(cleanup)

const BUILT = ["Tyres or tracks", "Brakes", "Steering", "Hydraulic hoses and leaks", "Lights, horn and reverse alarm",
  "Seat belt and roll-over protection", "Fire extinguisher", "Engine oil, coolant and fuel", "Cab glass and mirrors", "Guards and covers in place"]
const q = (text: string, over: Record<string, unknown> = {}) => ({
  id: null, section: null, text, answerType: "PASS_FAIL", required: true, builtIn: true, min: null, max: null, unit: null, failOutcome: "DEFECT", ...over,
})
const builtIn = (type: string) => ({ versionId: null, versionNo: null, inspectionType: type, requiresSignoff: false, effectiveFrom: null, source: "BUILT_IN", questions: BUILT.map(t => q(t)) })
const overview = (draft: unknown = null) => [
  { inspectionType: "PRE_USE", active: builtIn("PRE_USE"), draft, history: [] },
  { inspectionType: "MAINTENANCE", active: null, draft: null, history: [] },
]
const draftV = { id: "d1", inspectionType: "PRE_USE", versionNo: 1, status: "DRAFT", effectiveFrom: null, requiresSignoff: false, note: null, createdAt: "2026-10-15T06:00:00Z", publishedAt: null, questions: BUILT.map(t => q(t)) }

const mount = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ChecklistsTab /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = []
  get.mockResolvedValue({ data: { data: overview() } })
  post.mockResolvedValue({ data: { data: draftV } })
  put.mockResolvedValue({ data: { data: draftV } })
})

describe("Checklists", () => {
  it("shows what is in force for each kind and gives a reader no way to change it", async () => {
    perms = ["EARTHMOVING_READ"]
    mount()
    expect(await screen.findByText("Built-in walk-round (10 questions)")).toBeTruthy()
    expect(screen.getByText(/No checklist yet/)).toBeTruthy()
    expect(screen.queryByText("Start a new version")).toBeNull()
    expect(screen.queryByText("Write a checklist")).toBeNull()
  })

  it("lets an administrator start a draft and opens it with the built-in questions that cannot be removed", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount()
    fireEvent.click(await screen.findByText("Start a new version"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringContaining("/checklists/draft?type=PRE_USE")))
    expect(await screen.findByText(/version 1 \(draft\)/)).toBeTruthy()
    expect((screen.getByLabelText("Remove 1") as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByLabelText("Question 1") as HTMLInputElement).disabled).toBe(true)
  })

  it("adds a numeric question and saves the draft with its range", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    get.mockResolvedValue({ data: { data: overview(draftV) } })
    mount()
    fireEvent.click(await screen.findByText("Continue draft v1"))
    fireEvent.click(await screen.findByText("Add a question"))
    const save = screen.getByText("Save draft") as HTMLButtonElement
    expect(save.disabled).toBe(true) // the new question has no wording yet
    fireEvent.change(screen.getByLabelText("Question 11"), { target: { value: "Hydraulic pressure" } })
    fireEvent.change(screen.getByLabelText("Answer type 11"), { target: { value: "NUMERIC" } })
    expect(save.disabled).toBe(true) // a number needs a lowest or highest value
    fireEvent.change(screen.getByLabelText("Lowest 11"), { target: { value: "180" } })
    fireEvent.change(screen.getByLabelText("Highest 11"), { target: { value: "220" } })
    fireEvent.change(screen.getByLabelText("Outside range 11"), { target: { value: "UNSAFE" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toContain("/checklists/versions/d1")
    const body = put.mock.calls[0][1]
    expect(body.questions).toHaveLength(11)
    expect(body.questions[10]).toMatchObject({ text: "Hydraulic pressure", answerType: "NUMERIC", min: 180, max: 220, failOutcome: "UNSAFE" })
  })

  it("saves and then publishes the draft from the chosen date", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    get.mockResolvedValue({ data: { data: overview(draftV) } })
    mount()
    fireEvent.click(await screen.findByText("Continue draft v1"))
    fireEvent.click(await screen.findByText("Publish"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(put).toHaveBeenCalled()
    expect(post.mock.calls[0][0]).toContain("/checklists/versions/d1/publish")
    expect(post.mock.calls[0][1].effectiveFrom).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})
