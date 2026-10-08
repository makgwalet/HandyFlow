import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { applyNormal, hasNormalPreset } from "./questionForm.logic"

const chest = (withPreset: boolean) => ({
  code: "RESP", name: "Respiratory", version: 1, required: false, demo: false,
  questions: [
    { code: "air", label: "Air entry", answerType: "SINGLE_SELECT", defaultRequired: false, rules: [],
      options: [{ value: "equal", label: "Equal" }, { value: "reduced", label: "Reduced" }], ...(withPreset ? { normalValue: "equal" } : {}) },
    { code: "note", label: "Other", answerType: "TEXT", defaultRequired: false, rules: [], options: [] },
  ],
})

describe("applyNormal", () => {
  const qs = chest(true).questions as any[]
  it("fills unanswered findings that have a preset and leaves the rest", () => {
    expect(applyNormal(qs, {})).toEqual({ air: "equal" })
  })
  it("never overwrites an answer the clinician gave", () => {
    expect(applyNormal(qs, { air: "reduced" })).toEqual({ air: "reduced" })
  })
  it("treats an empty answer as unanswered", () => {
    expect(applyNormal(qs, { air: "" })).toEqual({ air: "equal" })
  })
  it("knows whether a group has any preset", () => {
    expect(hasNormalPreset(qs)).toBe(true)
    expect(hasNormalPreset(chest(false).questions as any[])).toBe(false)
  })
})

const get = vi.fn(), post = vi.fn(), put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a) } }))
import QuestionForm from "./QuestionForm"

const show = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <QuestionForm kind="EXAMINATION" consultationId="c1" patientId="p1" visitType="CONSULTATION" />
  </QueryClientProvider>)

let preset = true
beforeEach(() => {
  preset = true
  get.mockReset(); post.mockReset(); put.mockReset()
  get.mockImplementation((url: string) =>
    url.endsWith("/question-groups") ? Promise.resolve({ data: { data: [chest(preset)] } }) : Promise.resolve({ data: { data: {} } }))
  post.mockImplementation((_u: string, body: any) => Promise.resolve({ data: { data: {
    visible: ["air", "note"], required: [], disabled: [], warnings: {}, triggeredGroups: [], redFlags: [], urgent: false,
    effectiveAnswers: body.answers, missingRequired: [], problems: {} } } }))
  put.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("examination libraries in the form", () => {
  it("asks the server for the examination groups", async () => {
    show()
    await screen.findByText("Respiratory")
    expect(get.mock.calls[0][1].params.kind).toBe("EXAMINATION")
  })
  it("Mark all normal applies the preset and saves it", async () => {
    show()
    fireEvent.click(await screen.findByText("Mark all normal"))
    await waitFor(() => expect(put).toHaveBeenCalled(), { timeout: 3000 })
    expect(put.mock.calls[0][1]).toEqual({ answers: { air: "equal" } })
  })
  it("offers no button when the reviewer wrote no normal answers", async () => {
    preset = false
    show()
    await screen.findByText("Respiratory")
    expect(screen.queryByText("Mark all normal")).toBeNull()
  })
})
