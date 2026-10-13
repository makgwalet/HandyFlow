import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { blockSummary } from "./questionForm.logic"

describe("blockSummary", () => {
  it("counts groups and started groups only", () => {
    const s = blockSummary([
      { code: "A", required: false, answered: 2, missing: 0, urgent: false },
      { code: "B", required: false, answered: 0, missing: 0, urgent: false },
    ])
    expect(s.text).toBe("2 groups · 1 started")
    expect(s.attention).toBe(false)
  })
  it("says '1 group' and nothing started", () => {
    expect(blockSummary([{ code: "A", required: false, answered: 0, missing: 0, urgent: false }]).text).toBe("1 group")
  })
  it("counts required answers only for groups that are started or required", () => {
    const s = blockSummary([
      { code: "A", required: false, answered: 0, missing: 3, urgent: false },
      { code: "B", required: false, answered: 1, missing: 2, urgent: false },
    ])
    expect(s.missing).toBe(2)
    expect(s.attention).toBe(true)
  })
  it("flags an urgent group", () => {
    expect(blockSummary([{ code: "A", required: false, answered: 1, missing: 0, urgent: true }]).urgent).toBe(true)
  })
})

const group = (code: string, required: boolean) => ({
  code, name: `Group ${code}`, version: 1, required, demo: false,
  questions: [{ code: "q", label: `Question ${code}`, answerType: "TEXT", defaultRequired: false, rules: [], options: [] }],
})
let groups: any[] = []
const get = vi.fn(), post = vi.fn(), put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a) } }))
import QuestionForm from "./QuestionForm"

const show = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <QuestionForm consultationId="c1" patientId="p1" visitType="CONSULTATION" />
  </QueryClientProvider>)

beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset()
  get.mockImplementation((url: string) =>
    url.endsWith("/question-groups") ? Promise.resolve({ data: { data: groups } }) : Promise.resolve({ data: { data: {} } }))
  post.mockImplementation((_u: string, body: any) => Promise.resolve({ data: { data: {
    visible: ["q"], required: [], disabled: [], warnings: {}, triggeredGroups: [], redFlags: [], urgent: false,
    effectiveAnswers: body.answers, missingRequired: [], problems: {} } } }))
  put.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("Questionnaires block", () => {
  it("is one block, folded when nothing is required, and opens on click", async () => {
    groups = [group("A", false), group("B", false)]
    show()
    const head = await screen.findByRole("button", { name: "Questionnaires" })
    expect(head.getAttribute("aria-expanded")).toBe("false")
    expect(screen.getByText("2 groups")).toBeTruthy()
    fireEvent.click(head)
    expect(head.getAttribute("aria-expanded")).toBe("true")
    expect(screen.getByText("Group A")).toBeTruthy()
    expect(screen.getByText("Group B")).toBeTruthy()
  })
  it("starts open when a group is required", async () => {
    groups = [group("A", true)]
    show()
    const head = await screen.findByRole("button", { name: "Questionnaires" })
    expect(head.getAttribute("aria-expanded")).toBe("true")
  })
  it("keeps the groups mounted while folded so saving is never cut off", async () => {
    groups = [group("A", false)]
    show()
    await screen.findByRole("button", { name: "Questionnaires" })
    expect(screen.getByText("Group A", { selector: "span" })).toBeTruthy()
  })
})
