import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import AuditPanel from "./AuditPanel"
import { NO_AUDIT_FILTER, auditParams, auditSentence, filterProblem, pageText, type AuditEvent } from "./audit.logic"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
beforeEach(() => { get.mockReset() })
afterEach(cleanup)

const ev = (over: Partial<AuditEvent> = {}): AuditEvent => ({ id: "e1", entityType: "SCORING_RULE", entityId: "r1", entityName: "Came from website", action: "CREATED", detail: "20 points", actorId: "u2", createdAt: "2026-10-21T10:00:00Z", ...over })
const users = { content: [{ id: "u1", firstName: "Olive", lastName: "Other" }, { id: "u2", firstName: "Sam", lastName: "Seller" }] }
const page = (content: AuditEvent[], over: Record<string, number> = {}) => ({ content, totalPages: 1, totalElements: content.length, number: 0, ...over })
const serve = (p: unknown) => get.mockImplementation((url: string) => ok(url.includes("/audit") ? p : users))
const auditCalls = () => get.mock.calls.filter(c => String(c[0]).includes("/audit"))

describe("audit logic", () => {
  it("words an event as a sentence", () => {
    expect(auditSentence(ev(), "Sam Seller")).toBe('Sam Seller created Scoring rule "Came from website"')
    expect(auditSentence(ev({ actorId: null, entityType: "WORKFLOW", entityName: "Tag big", action: "UPDATED" }), null)).toBe('The system changed Workflow "Tag big"')
    expect(auditSentence(ev({ entityName: null, entityType: "ROUTING_RULE", action: "RUN" }), null)).toBe("Someone ran Routing rule")
    expect(auditSentence(ev({ action: "CONVERSION_UNDONE", entityType: "CUSTOMER", entityName: "Acme" }), "Olive Other")).toBe('Olive Other undid the conversion of Customer "Acme"')
  })
  it("checks the dates and sends only the filters that are set", () => {
    expect(filterProblem(NO_AUDIT_FILTER)).toBeNull(); expect(filterProblem({ ...NO_AUDIT_FILTER, from: "2026-10-02", to: "2026-10-01" })).toBe("The start date cannot be after the end date.")
    expect(filterProblem({ ...NO_AUDIT_FILTER, from: "2026-10-01", to: "2026-10-01" })).toBeNull()
    expect(auditParams(NO_AUDIT_FILTER, 0)).toEqual({ page: 0, size: 50 })
    expect(auditParams({ entityType: "WORKFLOW", actorId: "u1", from: "2026-10-01", to: "" }, 2)).toEqual({ page: 2, size: 50, entityType: "WORKFLOW", actorId: "u1", from: "2026-10-01" })
  })
  it("describes the page", () => {
    expect(pageText(page([]))).toBe("No events"); expect(pageText(page([ev()]))).toBe("Page 1 of 1 (1 event)"); expect(pageText(page([ev(), ev()], { totalPages: 3, number: 1, totalElements: 120 }))).toBe("Page 2 of 3 (120 events)")
  })
})

describe("AuditPanel", () => {
  it("lists events with the person's name, the detail and a system line", async () => {
    serve(page([ev(), ev({ id: "e2", actorId: null, entityType: "WORKFLOW", entityName: "Tag big", action: "UPDATED", detail: null })]))
    wrap(<AuditPanel />)
    expect(await screen.findByText('Sam Seller created Scoring rule "Came from website"')).toBeTruthy(); expect(screen.getByText(/20 points/)).toBeTruthy()
    expect(screen.getByText('The system changed Workflow "Tag big"')).toBeTruthy(); expect(screen.getByText("Page 1 of 1 (2 events)")).toBeTruthy()
  })
  it("says so when nothing matches", async () => { serve(page([])); wrap(<AuditPanel />); expect(await screen.findByText("Nothing matches.")).toBeTruthy() })
  it("asks the server again when a filter changes, and starts from the first page", async () => {
    serve(page([ev()], { totalPages: 2, totalElements: 80 }))
    wrap(<AuditPanel />)
    await screen.findByText(/Page 1 of 2/)
    fireEvent.click(screen.getByText("Older"))
    await waitFor(() => expect(auditCalls().some(c => (c[1] as { params: { page: number } }).params.page === 1)).toBe(true))
    fireEvent.change(screen.getByLabelText("What changed"), { target: { value: "WORKFLOW" } })
    await waitFor(() => expect(auditCalls().some(c => { const p = (c[1] as { params: Record<string, unknown> }).params; return p.entityType === "WORKFLOW" && p.page === 0 })).toBe(true))
    fireEvent.change(screen.getByLabelText("Who"), { target: { value: "u1" } })
    await waitFor(() => expect(auditCalls().some(c => (c[1] as { params: Record<string, unknown> }).params.actorId === "u1")).toBe(true))
  })
  it("will not search with the dates the wrong way round", async () => {
    serve(page([ev()]))
    wrap(<AuditPanel />)
    await screen.findByText(/Page 1 of 1/)
    fireEvent.change(screen.getByLabelText("From date"), { target: { value: "2026-10-05" } })
    fireEvent.change(screen.getByLabelText("To date"), { target: { value: "2026-10-01" } })
    expect((await screen.findByRole("alert")).textContent).toContain("start date cannot be after")
    expect(auditCalls().some(c => (c[1] as { params: Record<string, unknown> }).params.from === "2026-10-05" && (c[1] as { params: Record<string, unknown> }).params.to === "2026-10-01")).toBe(false)
  })
  it("explains a refusal", async () => {
    get.mockImplementation((url: string) => url.includes("/audit") ? Promise.reject(new Error("403")) : ok(users))
    wrap(<AuditPanel />)
    expect((await screen.findByRole("alert")).textContent).toContain("Couldn't load the audit log")
  })
})
