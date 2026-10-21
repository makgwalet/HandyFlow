import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import LettersTab from "./LettersTab"
import CaseLetters from "./CaseLetters"
import { draftProblem, gapsIn, issueProblem, letterRights, mergeFieldsIn, segments, templateProblem, templatesForCase, voidProblem } from "./letters.logic"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))

let perms: string[] = []
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "u-me", permissions: perms } }) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (data: unknown) => Promise.resolve({ data: { data } })
const BASE = "/api/v1/hr/letters"

const fields = [{ key: "employee.name", label: "Employee name", group: "Employee" }, { key: "manual.details", label: "Details", group: "To type in" }]
const tpl = (o: object = {}) => ({ id: "t1", code: "WARNING_LETTER", name: "Warning letter", category: "DISCIPLINARY", subject: "Warning for {{employee.name}}", body: "Dear {{employee.name}}",
  requiresAck: true, active: true, customised: false, standard: true, revision: 1, fields: ["employee.name"], ...o })
const row = (o: object = {}) => ({ id: "l1", number: "LTR-2026-0001", subject: "Warning for Jane", templateName: "Warning letter", employeeId: "emp1", employeeName: "Jane Dlamini",
  caseId: null, caseNumber: null, status: "DRAFT", issuedOn: null, requiresAck: true, acknowledgedAt: null, createdAt: "2026-10-22T06:00:00Z", ...o })
const detail = (o: object = {}, r: object = {}) => ({ row: row(r), body: "Dear Jane,\n\nThe hearing is at [[Venue]].", gaps: ["Venue"],
  events: [{ action: "PREPARED", note: null, doneBy: "Sam", doneAt: "2026-10-22T06:00:00Z" }], actions: ["EDIT", "ISSUE", "VOID"], fileName: null, fileSha256: null, voidReason: null, templateRevision: 1, ...o })
const mineLetter = (o: object = {}) => ({ id: "m1", number: "LTR-2026-0009", subject: "Your warning", body: "Dear Jane, this is a warning.", templateName: "Warning letter",
  issuedOn: "2026-10-20", requiresAck: true, acknowledgedAt: null, canAcknowledge: true, ...o })

function routes(map: Record<string, unknown>) {
  get.mockImplementation((url: string) => {
    for (const [k, v] of Object.entries(map)) if (url === k || url.startsWith(`${k}?`)) return v instanceof Error ? Promise.reject(v) : ok(v)
    return Promise.reject(new Error(`unmocked ${url}`))
  })
}

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); perms = [] })
afterEach(cleanup)

describe("letter rules in the browser", () => {
  it("works out rights", () => {
    expect(letterRights(["HR_LETTER_READ"])).toEqual({ read: true, manage: false, issue: false })
    expect(letterRights(["HR_LETTER_ISSUE"]).read).toBe(true)
    expect(letterRights([])).toEqual({ read: false, manage: false, issue: false })
  })
  it("finds gaps and splits text around them", () => {
    expect(gapsIn("At [[Venue]] on [[Date]] and [[Venue]]")).toEqual(["Venue", "Date"])
    expect(segments("a [[X]] b").map(s => s.gap)).toEqual([false, true, false])
    expect(segments("plain")).toEqual([{ text: "plain", gap: false }])
  })
  it("finds merge fields", () => {
    expect(mergeFieldsIn("Dear {{ employee.name }}, {{employee.name}} {{case.number}}")).toEqual(["employee.name", "case.number"])
  })
  it("checks a draft and whether it may be issued", () => {
    expect(draftProblem("", "x")).toMatch(/subject/)
    expect(draftProblem("S", "")).toMatch(/wording/)
    expect(draftProblem("S", "Hi {{employee.name}}")).toMatch(/merge fields/)
    expect(draftProblem("S", "Fine")).toBeNull()
    expect(issueProblem("S", "At [[Venue]]")).toMatch(/Venue/)
    expect(issueProblem("S", "Fine")).toBeNull()
  })
  it("checks a template", () => {
    const known = ["employee.name"]
    expect(templateProblem({ name: "", subject: "s", body: "b" }, known)).toMatch(/name/)
    expect(templateProblem({ name: "n", subject: "s", body: "Hi {{salary}}" }, known)).toMatch(/salary/)
    expect(templateProblem({ name: "n", subject: "s", body: "At [[Venue]]" }, known)).toMatch(/\[\[/)
    expect(templateProblem({ name: "n", subject: "s", body: "Hi {{employee.name" }, known)).toMatch(/not written correctly/)
    expect(templateProblem({ name: "n", subject: "s", body: "Hi {{employee.name}}" }, known)).toBeNull()
  })
  it("offers templates that fit a case", () => {
    const ts = [{ category: "DISCIPLINARY", active: true }, { category: "GRIEVANCE", active: true }, { category: "GENERAL", active: true }, { category: "GENERAL", active: false }]
    expect(templatesForCase(ts, "DISCIPLINARY")).toHaveLength(2)
  })
  it("checks a void reason", () => {
    expect(voidProblem(" ")).toMatch(/reason/)
    expect(voidProblem("Wrong person")).toBeNull()
  })
})

describe("letters screen", () => {
  it("lets a plain employee see only their own letters", async () => {
    routes({ [`${BASE}/mine`]: [mineLetter()] })
    wrap(<LettersTab />)
    await screen.findByText("LTR-2026-0009")
    expect(screen.queryByRole("tab", { name: "Templates" })).toBeNull()
    expect(screen.getByText("Please confirm")).toBeTruthy()
    expect(get).not.toHaveBeenCalledWith(BASE)
  })

  it("confirms receipt of an issued letter and says it is not agreement", async () => {
    let current = mineLetter()
    get.mockImplementation((url: string) => ok(url === `${BASE}/mine` ? [current] : current))
    post.mockImplementation(() => { current = mineLetter({ acknowledgedAt: "2026-10-22T07:00:00Z", canAcknowledge: false }); return ok(current) })
    wrap(<LettersTab />)
    fireEvent.click(await screen.findByText("LTR-2026-0009"))
    await screen.findByText(/does not mean you agree/)
    fireEvent.click(screen.getByRole("button", { name: "I have received this letter" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${BASE}/mine/m1/acknowledge`, {}))
    await screen.findByText(/recorded that you received/)
    expect(screen.queryByRole("button", { name: "I have received this letter" })).toBeNull()
  })

  it("blocks issuing while gaps remain and shows them", async () => {
    perms = ["HR_LETTER_READ", "HR_LETTER_ISSUE"]
    routes({ [BASE]: [row()], [`${BASE}/l1`]: detail(), [`${BASE}/mine`]: [] })
    wrap(<LettersTab />)
    fireEvent.click(await screen.findByText("LTR-2026-0001"))
    const notice = await screen.findByText(/Gaps to fill in before this can be issued/)
    expect(notice.textContent).toContain("Venue")
    expect((screen.getByRole("button", { name: "Issue the letter" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("issues a complete draft", async () => {
    perms = ["HR_LETTER_READ", "HR_LETTER_ISSUE"]
    let state = detail({ body: "Dear Jane,\n\nThe hearing is at Room 2.", gaps: [] })
    routes({ [BASE]: [row()], [`${BASE}/mine`]: [] })
    get.mockImplementation((url: string) => url === `${BASE}/l1` ? ok(state) : url.startsWith(BASE) && !url.includes("mine") ? ok([row()]) : ok([]))
    post.mockImplementation(() => {
      state = detail({ body: state.body, gaps: [], actions: ["DOWNLOAD", "VOID"], fileName: "LTR-2026-0001.pdf", fileSha256: "abc123",
        events: [...state.events, { action: "ISSUED", note: null, doneBy: "Sam", doneAt: "2026-10-22T07:00:00Z" }] }, { status: "ISSUED", issuedOn: "2026-10-22" })
      return ok(state)
    })
    wrap(<LettersTab />)
    fireEvent.click(await screen.findByText("LTR-2026-0001"))
    const btn = await screen.findByRole("button", { name: "Issue the letter" })
    await waitFor(() => expect((btn as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(btn)
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${BASE}/l1/issue`, {}))
    await screen.findByRole("button", { name: "Download PDF" })
    expect(screen.queryByRole("button", { name: "Issue the letter" })).toBeNull()
    expect(screen.getByText(/abc123/)).toBeTruthy()
  })

  it("asks for a reason before voiding", async () => {
    perms = ["HR_LETTER_READ", "HR_LETTER_ISSUE"]
    get.mockImplementation((url: string) => url === `${BASE}/l1` ? ok(detail()) : ok([row()]))
    post.mockResolvedValue(ok(detail({ actions: [] }, { status: "VOID" })))
    wrap(<LettersTab />)
    fireEvent.click(await screen.findByText("LTR-2026-0001"))
    fireEvent.click(await screen.findByRole("button", { name: "Void" }))
    const submit = screen.getByRole("button", { name: "Void the letter" }) as HTMLButtonElement
    expect(submit.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Why is this letter being voided?"), { target: { value: "Wrong employee" } })
    expect(submit.disabled).toBe(false)
    fireEvent.click(submit)
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${BASE}/l1/void`, { reason: "Wrong employee" }))
  })

  it("lets a template be changed only by someone who manages them", async () => {
    perms = ["HR_LETTER_READ"]
    routes({ [BASE]: [], [`${BASE}/templates`]: { templates: [tpl()], fields }, [`${BASE}/mine`]: [] })
    wrap(<LettersTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "Templates" }))
    fireEvent.click(await screen.findByText("Warning letter"))
    await screen.findByText(/Only people who manage letter templates/)
    expect(screen.queryByRole("button", { name: "Save template" })).toBeNull()
  })

  it("rejects an unknown merge field and saves a good template", async () => {
    perms = ["HR_LETTER_READ", "HR_LETTER_MANAGE"]
    routes({ [BASE]: [], [`${BASE}/templates`]: { templates: [tpl()], fields }, [`${BASE}/mine`]: [] })
    put.mockResolvedValue(ok(tpl({ revision: 2, customised: true })))
    wrap(<LettersTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "Templates" }))
    fireEvent.click(await screen.findByText("Warning letter"))
    const body = await screen.findByLabelText("Wording")
    fireEvent.change(body, { target: { value: "Pay is {{salary}}" } })
    await screen.findByText(/do not exist: salary/)
    expect((screen.getByRole("button", { name: "Save template" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(body, { target: { value: "Dear {{employee.name}}, please note." } })
    fireEvent.click(screen.getByRole("button", { name: "Save template" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith(`${BASE}/templates/t1`, expect.objectContaining({ body: "Dear {{employee.name}}, please note." })))
  })
})

describe("letters on a case", () => {
  it("lists a case's letters and prepares another from the case", async () => {
    get.mockImplementation((url: string) => url.startsWith(`${BASE}/templates`) ? ok({ templates: [tpl(), tpl({ id: "t2", name: "Grievance outcome", category: "GRIEVANCE" })], fields })
      : url.startsWith(`${BASE}?caseId=c1`) ? ok([row({ caseId: "c1", caseNumber: "ER-1" })]) : ok(detail()))
    post.mockResolvedValue(ok(detail()))
    wrap(<CaseLetters caseId="c1" caseType="DISCIPLINARY" canIssue />)
    await screen.findByText("LTR-2026-0001")
    const select = await screen.findByRole("combobox")
    await waitFor(() => expect(screen.getAllByRole("option").length).toBeGreaterThan(1))
    expect(screen.queryByRole("option", { name: "Grievance outcome" })).toBeNull()
    fireEvent.change(select, { target: { value: "t1" } })
    fireEvent.click(screen.getByRole("button", { name: /Prepare/ }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${BASE}/prepare`, { templateId: "t1", employeeId: null, caseId: "c1" }))
  })

  it("leaves the section out when letters cannot be read", async () => {
    get.mockRejectedValue(new Error("403"))
    const { container } = wrap(<CaseLetters caseId="c1" caseType="DISCIPLINARY" canIssue={false} />)
    await waitFor(() => expect(get).toHaveBeenCalled())
    await waitFor(() => expect(container.textContent).toBe(""))
  })
})
