import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(), post = vi.fn(), put = vi.fn()
let perms: Record<string, boolean> = {}
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
import QuestionLibraryAdminTab, { actionsFor, parseJson } from "./QuestionLibraryAdminTab"

const rows = [
  { id: "g1", code: "INTAKE", version: 1, name: "Intake", status: "DRAFT", demo: true },
  { id: "g2", code: "PAIN", version: 2, name: "Pain", status: "CLINICAL_REVIEW" },
]
const show = () => render(<QueryClientProvider client={new QueryClient()}><QuestionLibraryAdminTab /></QueryClientProvider>)
beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); perms = { CLINIC_CONTENT_ADMIN: true, CLINIC_CONTENT_APPROVE: true }
  get.mockImplementation((u: string) => Promise.resolve({ data: { data: u.endsWith("/admin") ? rows
    : u.includes("/visit-types/") ? { visitType: "CONSULTATION", source: "PLATFORM", groups: [] }
    : { definition: { name: "Intake", questions: [] } } } }))
  post.mockResolvedValue({ data: {} }); put.mockResolvedValue({ data: {} })
})
afterEach(() => { cleanup(); vi.restoreAllMocks() })

describe("actionsFor", () => {
  it("offers only what the status and permissions allow", () => {
    expect(actionsFor("DRAFT", true, false)).toEqual(["view", "edit", "submit"])
    expect(actionsFor("DRAFT", false, true)).toEqual(["view"])
    expect(actionsFor("CLINICAL_REVIEW", true, true)).toEqual(["view", "approve", "changes"])
    expect(actionsFor("CLINICAL_REVIEW", true, false)).toEqual(["view"])
    expect(actionsFor("APPROVED", true, true)).toEqual(["view", "activate", "new-version"])
    expect(actionsFor("ACTIVE", true, true)).toEqual(["view", "deprecate", "new-version"])
    expect(actionsFor("RETIRED", true, true)).toEqual(["view"])
  })
})
describe("parseJson", () => {
  it("reports bad JSON in words", () => {
    expect(parseJson("{").ok).toBe(false)
    expect(parseJson('{"a":1}')).toEqual({ ok: true, value: { a: 1 } })
  })
})

describe("QuestionLibraryAdminTab", () => {
  it("lists groups, marks demo content, and submits a draft for review", async () => {
    show()
    expect(await screen.findByText("INTAKE (DEMO)")).toBeTruthy()
    fireEvent.click(screen.getByText("Submit for review"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/question-groups/g1/submit-for-review", {}))
  })
  it("edits a definition and shows the server's validation message", async () => {
    put.mockRejectedValue({ response: { data: { message: "Question 'a' has no label" } } })
    show()
    fireEvent.click(await screen.findByText("Edit"))
    const box = (await screen.findByLabelText("Definition")) as HTMLTextAreaElement
    expect(box.value).toContain('"name": "Intake"')
    fireEvent.click(screen.getByText("Save"))
    expect(await screen.findByText(/has no label/)).toBeTruthy()
    expect(put.mock.calls[0][0]).toBe("/api/v1/clinic/question-groups/g1/definition")
  })
  it("does not send invalid JSON", async () => {
    show()
    fireEvent.click(await screen.findByText("Edit"))
    fireEvent.change(await screen.findByLabelText("Definition"), { target: { value: "{ nope" } })
    fireEvent.click(screen.getByText("Save"))
    expect(await screen.findByText(/not valid JSON/)).toBeTruthy()
    expect(put).not.toHaveBeenCalled()
  })
  it("asks for a reason before requesting changes, and sends nothing without one", async () => {
    const prompt = vi.spyOn(window, "prompt").mockReturnValue("  ")
    show()
    fireEvent.click(await screen.findByText("Request changes"))
    expect(post).not.toHaveBeenCalled()
    prompt.mockReturnValue("Add a pregnancy question")
    fireEvent.click(screen.getByText("Request changes"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/question-groups/g2/status",
      { status: "CHANGES_REQUESTED", note: "Add a pregnancy question" }))
  })
  it("creates a new group with its code", async () => {
    show()
    fireEvent.click(await screen.findByText("New group"))
    fireEvent.change(screen.getByLabelText("Group code"), { target: { value: " NEW_ONE " } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/clinic/question-groups")
    expect(post.mock.calls[0][1].code).toBe("NEW_ONE")
  })
  it("explains a refusal to load", async () => {
    get.mockImplementation(() => Promise.reject(new Error("403")))
    show()
    expect(await screen.findByRole("alert")).toBeTruthy()
  })
})
