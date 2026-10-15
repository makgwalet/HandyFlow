import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"

vi.mock("../../api/auditInvestigations.api", () => ({ auditInvestigationsApi: {
  list: vi.fn(), open: vi.fn(), get: vi.fn(), verify: vi.fn(), addMember: vi.fn(), removeMember: vi.fn(), note: vi.fn(), collect: vi.fn(), transfer: vi.fn(), setReview: vi.fn(), close: vi.fn(), users: vi.fn(),
} }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: any) => sel({ user: { userId: "u2" } }) }))
import { auditInvestigationsApi } from "../../api/auditInvestigations.api"
import { InvestigationsSection } from "./InvestigationsSection"

const m = auditInvestigationsApi as any
afterEach(cleanup)

const summary = (x: any = {}) => ({ id: "c1", number: "INV-0001", title: "Till shortages", status: "OPEN", myRole: "LEAD", openedAt: "2026-10-01T00:00:00Z", members: 2, ...x })
const view = (x: any = {}, role = "LEAD") => ({
  summary: summary({ myRole: role, ...(x.summary ?? {}) }), summaryText: null, closeReason: null, closedAt: null,
  members: [{ userId: "u1", name: "Ann Lead", role: "LEAD", addedAt: "" }, { userId: "u2", name: "Bob Inv", role: "INVESTIGATOR", addedAt: "" }, { userId: "u3", name: "Obs Erver", role: "OBSERVER", addedAt: "" }],
  evidence: [{ id: "e1", ref: "EV-001", description: "Till roll for 3 October", source: null, collectedAt: "", holderId: "u1", holderName: "Ann Lead" }],
  log: [{ seq: 1, at: "2026-10-01T08:00:00Z", actorName: "Ann Lead", action: "CASE_OPENED", detail: "INV-0001: Till shortages", hash: "h1" }], ...x, ...(x.summary ? { summary: summary({ myRole: role, ...x.summary }) } : {}) })

beforeEach(() => {
  vi.clearAllMocks()
  m.list.mockResolvedValue([summary()])
  m.users.mockResolvedValue([{ id: "u1", name: "Ann Lead" }, { id: "u4", name: "New Person" }])
})
const open = async (v: any) => { m.get.mockResolvedValue(v); render(<InvestigationsSection />); fireEvent.click(await screen.findByText("Open")) }

describe("Investigations", () => {
  it("lists only the cases returned, and says nothing of others", async () => {
    m.list.mockResolvedValue([])
    render(<InvestigationsSection />)
    expect(await screen.findByText("You are not named on any case.")).toBeTruthy()
  })

  it("opens a case with a title", async () => {
    m.open.mockResolvedValue(view()); m.get.mockResolvedValue(view())
    render(<InvestigationsSection />)
    await screen.findByText("Your cases")
    const btn = screen.getByText("Open case") as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Case title"), { target: { value: "Stock losses" } })
    fireEvent.click(btn)
    await waitFor(() => expect(m.open).toHaveBeenCalledWith("Stock losses", ""))
  })

  it("an observer sees the case but no way to change it", async () => {
    await open(view({}, "OBSERVER"))
    await screen.findByText("Case record")
    expect(screen.queryByText("Add note")).toBeNull()
    expect(screen.queryByText("Register evidence")).toBeNull()
    expect(screen.queryByText("Close case")).toBeNull()
    expect(screen.queryByText("Remove")).toBeNull()
  })

  it("an investigator can add notes but cannot run the case", async () => {
    await open(view({}, "INVESTIGATOR"))
    await screen.findByText("Case record")
    expect(screen.getByText("Add note")).toBeTruthy()
    expect(screen.queryByText("Close case")).toBeNull()
    expect(screen.queryByLabelText("Person to add")).toBeNull()
  })

  it("a note under ten characters cannot be sent", async () => {
    m.note.mockResolvedValue(view())
    await open(view())
    await screen.findByText("Case record")
    const add = screen.getByText("Add note") as HTMLButtonElement
    fireEvent.change(screen.getByLabelText("Note"), { target: { value: "short" } })
    expect(add.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Note"), { target: { value: "Spoke to the cashier." } })
    fireEvent.click(add)
    await waitFor(() => expect(m.note).toHaveBeenCalledWith("c1", "Spoke to the cashier."))
  })

  it("only people not yet on the case are offered", async () => {
    await open(view())
    await screen.findByText("Case record")
    const opts = Array.from((screen.getByLabelText("Person to add") as HTMLSelectElement).options).map(o => o.text)
    expect(opts).toContain("New Person")
    expect(opts).not.toContain("Ann Lead")
  })

  it("hands evidence over only with a reason", async () => {
    m.transfer.mockResolvedValue(view())
    await open(view())
    fireEvent.click(await screen.findByText("Hand over EV-001"))
    fireEvent.change(screen.getByLabelText("Hand EV-001 to"), { target: { value: "u2" } })
    const go = screen.getByText("Hand over") as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason for handing over EV-001"), { target: { value: "Needs forensic review" } })
    fireEvent.click(go)
    await waitFor(() => expect(m.transfer).toHaveBeenCalledWith("c1", "e1", "u2", "Needs forensic review"))
  })

  it("reports whether the record is intact or where it was altered", async () => {
    m.verify.mockResolvedValueOnce({ ok: true, entries: 4, brokenAtSeq: null }).mockResolvedValueOnce({ ok: false, entries: 4, brokenAtSeq: 3 })
    await open(view())
    const check = await screen.findByText("Check the record has not been altered")
    fireEvent.click(check)
    expect(await screen.findByText("Record intact (4 entries).")).toBeTruthy()
    fireEvent.click(check)
    expect(await screen.findByText("The record was altered at entry 3.")).toBeTruthy()
  })

  it("closing needs a reason", async () => {
    m.close.mockResolvedValue(view({ summary: { status: "CLOSED" }, closeReason: "Referred to HR." }))
    await open(view())
    const close = await screen.findByText("Close case") as HTMLButtonElement
    expect(close.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason for closing"), { target: { value: "Concluded; referred to HR." } })
    fireEvent.click(close)
    await waitFor(() => expect(m.close).toHaveBeenCalledWith("c1", "Concluded; referred to HR."))
  })

  it("a non-lead can only take themselves off, and is returned to the list", async () => {
    m.removeMember.mockResolvedValue(null)
    await open(view({}, "INVESTIGATOR"))
    await screen.findByText("Case record")
    expect(screen.queryByText("Remove")).toBeNull()
    fireEvent.click(screen.getByText("Leave"))
    await waitFor(() => expect(m.removeMember).toHaveBeenCalledWith("c1", "u2"))
    expect(await screen.findByText("Your cases")).toBeTruthy()
  })

  it("a lead sees Remove for the others", async () => {
    await open(view())
    await screen.findByText("Case record")
    expect(screen.getAllByText("Remove").length).toBe(2)   // the other two; the caller (u2) gets "Leave"
    expect(screen.getAllByText("Leave").length).toBe(1)
  })
})
