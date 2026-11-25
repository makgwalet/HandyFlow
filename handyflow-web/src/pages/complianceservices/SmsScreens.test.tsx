// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ClientSmsTab from "./ClientSmsTab"
import SmsRemindersPanel from "./SmsRemindersPanel"
import { consentProblem, runText, tidyPhone, withdrawProblem } from "./smsRules"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let admin = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => admin, usePermission: () => admin }))

const consent = (o: object = {}) => ({ id: "k1", clientId: "c1", contactName: "Thandi", phone: "+27821234567", basis: "Agreed by phone on 3 Nov", givenBy: "u1", givenAt: "2026-11-03T08:00:00Z", active: true, withdrawnBy: null, withdrawnAt: null, withdrawnNote: null, ...o })
const logRow = { id: "l1", clientId: "c1", clientName: "Acme", deadlineId: "d1", kind: "DAY_BEFORE", runDate: "2026-11-25", outcome: "HANDED_OVER", outcomeText: "x", detail: null, createdAt: "2026-11-25T06:30:00Z" }
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const reply = (consents: object[], settings: object = { enabled: true, updatedAt: null }, log: object[] = []) => (url: string) =>
  Promise.resolve({ data: { data: url.endsWith("/settings") ? settings : url.endsWith("/consents") ? consents : log } })

describe("SMS reminder wording and checks", () => {
  it("checks numbers and notes before sending anything", () => {
    expect(tidyPhone(" +27 82-123 4567")).toBe("+27821234567")
    expect(consentProblem({ contactName: "", phone: "+27821234567", basis: "Agreed by phone" })).toContain("who the number")
    expect(consentProblem({ contactName: "T", phone: "0821234567", basis: "Agreed by phone" })).toContain("international")
    expect(consentProblem({ contactName: "T", phone: "+27821234567", basis: "short" })).toContain("how consent")
    expect(consentProblem({ contactName: "T", phone: "+27821234567", basis: "Agreed by phone" })).toBeNull()
    expect(withdrawProblem("no")).not.toBeNull()
    expect(withdrawProblem("Asked us to stop")).toBeNull()
  })
  it("never says delivered", () => {
    expect(runText({ handedOver: 2, failed: 0, alreadySent: 1, noConsent: 3 })).toContain("Delivery is not confirmed")
  })
})

describe("SMS reminder screens", () => {
  afterEach(cleanup)
  beforeEach(() => { admin = true; get.mockReset(); post.mockReset(); put.mockReset() })

  it("says plainly when no consent is recorded", async () => {
    get.mockImplementation(reply([]))
    wrap(<ClientSmsTab clientId="c1" />)
    expect(await screen.findByText(/No consent has been recorded/)).toBeTruthy()
  })

  it("will not save consent without a reason, then records it with a tidied number", async () => {
    get.mockImplementation(reply([]))
    post.mockResolvedValue({ data: {} })
    wrap(<ClientSmsTab clientId="c1" />)
    fireEvent.click(await screen.findByText("Record consent for a number"))
    fireEvent.change(screen.getByLabelText("Who the number belongs to"), { target: { value: "Thandi" } })
    fireEvent.change(screen.getByLabelText("Cell number"), { target: { value: "+27 82 123 4567" } })
    fireEvent.click(screen.getByText("Save consent"))
    expect(await screen.findByRole("alert")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("How consent was given"), { target: { value: "Agreed by phone on 3 Nov" } })
    fireEvent.click(screen.getByText("Save consent"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/compliance-services/sms-reminders/clients/c1/consents", { contactName: "Thandi", phone: "+27821234567", basis: "Agreed by phone on 3 Nov" }))
  })

  it("withdraws consent only with a reason", async () => {
    get.mockImplementation(reply([consent()]))
    post.mockResolvedValue({ data: {} })
    wrap(<ClientSmsTab clientId="c1" />)
    fireEvent.click(await screen.findByText("Withdraw consent"))
    fireEvent.click(screen.getByText("Confirm withdrawal"))
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Why consent is withdrawn"), { target: { value: "Asked us to stop by email" } })
    fireEvent.click(screen.getByText("Confirm withdrawal"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/compliance-services/sms-reminders/consents/k1/withdraw", { note: "Asked us to stop by email" }))
  })

  it("shows withdrawn consent with its reason and no way to withdraw again", async () => {
    get.mockImplementation(reply([consent({ active: false, withdrawnNote: "Asked us to stop by email" })]))
    wrap(<ClientSmsTab clientId="c1" />)
    expect(await screen.findByText(/Asked us to stop by email/)).toBeTruthy()
    expect(screen.queryByText("Withdraw consent")).toBeNull()
  })

  it("hides recording and withdrawing from people who may only read", async () => {
    admin = false
    get.mockImplementation(reply([consent()]))
    wrap(<ClientSmsTab clientId="c1" />)
    await screen.findByText("Thandi")
    expect(screen.queryByText("Record consent for a number")).toBeNull()
    expect(screen.queryByText("Withdraw consent")).toBeNull()
  })

  it("switches reminders on and sends now, reporting hand-over not delivery", async () => {
    get.mockImplementation(reply([], { enabled: true, updatedAt: null }, [logRow]))
    put.mockResolvedValue({ data: {} })
    post.mockResolvedValue({ data: { data: { handedOver: 2, failed: 0, alreadySent: 0, noConsent: 1 } } })
    wrap(<SmsRemindersPanel />)
    expect(await screen.findByText("Handed to the SMS provider")).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Send SMS reminders to clients"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/compliance-services/sms-reminders/settings", { enabled: false }))
    fireEvent.click(screen.getByText("Send today's reminders now"))
    expect(await screen.findByText(/2 handed to the SMS provider/)).toBeTruthy()
  })
})
