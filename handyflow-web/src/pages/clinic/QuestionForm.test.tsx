import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const group = {
  code: "DEMO", name: "DEMO: intake", version: 1, required: true, demo: true,
  questions: [
    { code: "reason", label: "Reason for visit", answerType: "SINGLE_SELECT", defaultRequired: true, rules: [],
      options: [{ value: "ROUTINE", label: "Routine" }, { value: "ILLNESS", label: "Unwell" }] },
    { code: "score", label: "Discomfort", answerType: "SCALE", min: 0, max: 10, defaultRequired: false, options: [],
      rules: [{ kind: "SHOW_WHEN" }] },
  ],
}
const evaluation = (answers: any) => ({
  visible: answers.reason === "ILLNESS" ? ["reason", "score"] : ["reason"],
  required: ["reason"], disabled: [], warnings: {}, triggeredGroups: [],
  redFlags: answers.score === 10 ? [{ code: "TOP", label: "Top score entered", severity: "URGENT", message: "DEMO ONLY" }] : [],
  urgent: answers.score === 10, effectiveAnswers: answers, missingRequired: answers.reason ? [] : ["reason"], problems: {},
})

const get = vi.fn(), post = vi.fn(), put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a) } }))
import QuestionForm from "./QuestionForm"

const show = (consultationId: string | null = "c1") => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <QuestionForm consultationId={consultationId} patientId="p1" visitType="CONSULTATION" />
  </QueryClientProvider>)

beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset()
  get.mockImplementation((url: string) => {
    if (url.endsWith("/question-groups")) return Promise.resolve({ data: { data: [group] } })
    return Promise.resolve({ data: { data: {} } })           // form-data: nothing saved yet
  })
  post.mockImplementation((_u: string, body: any) => Promise.resolve({ data: { data: evaluation(body.answers) } }))
  put.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("QuestionForm with fixed group codes (doctor review)", () => {
  it("loads exactly the named groups by code, skipping the visit-type list", async () => {
    get.mockImplementation((url: string) => {
      if (url.endsWith("/question-groups/DEMO")) return Promise.resolve({ data: { data: group } })
      if (url.endsWith("/question-groups/GONE")) return Promise.reject(new Error("404"))
      return Promise.resolve({ data: { data: {} } })
    })
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <QuestionForm consultationId="c1" patientId="p1" visitType="CONSULTATION" groupCodes={["DEMO", "GONE"]} />
      </QueryClientProvider>)
    expect(await screen.findByText(/DEMO: intake/)).toBeTruthy()
    expect(get.mock.calls.some(c => String(c[0]).endsWith("/question-groups"))).toBe(false)
  })
})

describe("QuestionForm", () => {
  it("renders nothing when no group is served for the visit", async () => {
    get.mockImplementation(() => Promise.resolve({ data: { data: [] } }))
    const { container } = show()
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })

  it("shows the group, marks demo content, and hides a dependent question until it is revealed", async () => {
    show()
    expect(await screen.findByText(/DEMO: intake/)).toBeTruthy()
    expect(screen.getByText("DEMO")).toBeTruthy()
    expect(screen.getByText(/Reason for visit/)).toBeTruthy()
    expect(screen.queryByText("Discomfort")).toBeNull()
  })

  it("reveals the dependent question after the answer, saves it, and shows an urgent flag", async () => {
    show()
    fireEvent.click(await screen.findByText("Unwell"))
    expect(await screen.findByText("Discomfort", {}, { timeout: 3000 })).toBeTruthy()
    await waitFor(() => expect(put).toHaveBeenCalledWith(
      "/api/v1/clinic/consultations/c1/form-data/DEMO", { answers: { reason: "ILLNESS" } }), { timeout: 3000 })

    fireEvent.click(screen.getByText("10"))
    const banner = await screen.findByRole("alert", {}, { timeout: 3000 })
    expect(banner.textContent).toContain("Top score entered")
  })

  it("does not save before the consultation exists", async () => {
    show(null)
    fireEvent.click(await screen.findByText("Unwell"))
    await screen.findByText("Discomfort", {}, { timeout: 3000 })
    expect(put).not.toHaveBeenCalled()
  })

  it("does not save answers the server calls invalid, and shows the reason", async () => {
    post.mockImplementation((_u: string, body: any) => Promise.resolve({ data: { data: { ...evaluation(body.answers), problems: { reason: "Not one of the options" } } } }))
    show()
    fireEvent.click(await screen.findByText("Routine"))
    expect(await screen.findByText("Not one of the options", {}, { timeout: 3000 })).toBeTruthy()
    expect(put).not.toHaveBeenCalled()
  })
})
