import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
import QuestionAnswersReadOnly from "./QuestionAnswersReadOnly"

const show = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <QuestionAnswersReadOnly consultationId="c1" />
  </QueryClientProvider>)

const formData = {
  groups: {
    DEMO: { version: 1, answers: { reason: "ILLNESS", score: 7, improved: true, gone: "kept" } },
    OLD:  { version: 2, answers: { x: "stored value" } },
  },
}
const demoGroup = {
  code: "DEMO", name: "DEMO: intake",
  questions: [
    { code: "reason", label: "Reason for visit", answerType: "SINGLE_SELECT", options: [{ value: "ILLNESS", label: "Feeling unwell" }] },
    { code: "score", label: "Discomfort", answerType: "SCALE" },
    { code: "improved", label: "Better since last visit", answerType: "YES_NO" },
  ],
}

beforeEach(() => {
  get.mockReset()
  get.mockImplementation((url: string) => {
    if (url.endsWith("/form-data")) return Promise.resolve({ data: { data: formData } })
    if (url.endsWith("/question-groups/DEMO")) return Promise.resolve({ data: { data: demoGroup } })
    return Promise.reject(new Error("404"))        // OLD is no longer served
  })
})
afterEach(cleanup)

describe("QuestionAnswersReadOnly", () => {
  it("shows labels and readable values for groups that are still served", async () => {
    show()
    expect(await screen.findByText(/DEMO: intake/)).toBeTruthy()
    expect(screen.getByText("Reason for visit")).toBeTruthy()
    expect(screen.getByText("Feeling unwell")).toBeTruthy()
    expect(screen.getByText("Yes")).toBeTruthy()
    expect(screen.getByText("7")).toBeTruthy()
  })

  it("still shows what was recorded when a group is no longer served, or a question was removed", async () => {
    show()
    expect(await screen.findByText(/OLD \(v2\)/)).toBeTruthy()
    expect(screen.getByText("stored value")).toBeTruthy()
    expect(screen.getByText("kept")).toBeTruthy()
  })

  it("renders nothing when no answers were stored", async () => {
    get.mockImplementation(() => Promise.resolve({ data: { data: { groups: {} } } }))
    const { container } = show()
    await new Promise(r => setTimeout(r, 50))
    expect(container.textContent).toBe("")
  })
})
