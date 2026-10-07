import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
import RecallsTab from "./RecallsTab"

const rec = (o: any = {}) => ({ consultationId: "c1", patientId: "p1", patientName: "Ada Lovelace", patientPhone: "0821112222", practitionerId: "d1", practitionerName: "Who",
  consultedAt: "2026-09-20T08:00:00Z", followUpDays: 7, dueDate: "2026-09-27", overdueDays: 11, diagnosis: "Flu", status: "OPEN", snoozedUntil: null,
  contactAttempts: 0, lastContactAt: null, lastContactOutcome: null, ...o })
const page = (rows: any[], total = rows.length) => ({ data: { data: { content: rows, page: 0, size: 25, total, counts: { open: total, overdue: 1, dueToday: 0, notContacted: 1, snoozed: 2 } } } })
const show = (props: any = {}) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><RecallsTab {...props} /></QueryClientProvider>)

beforeEach(() => {
  get.mockReset(); post.mockReset()
  get.mockImplementation(async (url: string) => url.includes("practitioners") ? { data: { data: [{ id: "d1", fullName: "Who" }] } } : page([rec()]))
  post.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("RecallsTab", () => {
  it("lists recalls with tab counts and what has been tried", async () => {
    get.mockImplementation(async (url: string) => url.includes("practitioners") ? { data: { data: [] } } : page([rec({ contactAttempts: 2, lastContactOutcome: "NO_ANSWER" })]))
    show()
    expect(await screen.findByText("Ada Lovelace")).toBeTruthy()
    expect(screen.getByText("11d overdue")).toBeTruthy()
    expect(screen.getByText(/2 calls · last: No answer/)).toBeTruthy()
    expect(screen.getByRole("button", { name: /Overdue\s*·\s*1/ })).toBeTruthy()
  })
  it("asks the server to search and filter", async () => {
    show()
    await screen.findByText("Ada Lovelace")
    fireEvent.change(screen.getByLabelText("Search recalls"), { target: { value: "ada" } })
    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringContaining("q=ada")))
    fireEvent.click(screen.getByRole("button", { name: /^Overdue/ }))
    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringContaining("filter=OVERDUE")))
  })
  it("logs a call with its outcome", async () => {
    show()
    await screen.findByText("Ada Lovelace")
    fireEvent.click(screen.getByRole("button", { name: /Log call/ }))
    const save = screen.getByRole("button", { name: "Save" }) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.click(screen.getByLabelText("No answer"))
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/recalls/c1/actions", { type: "CONTACT", outcome: "NO_ANSWER", note: undefined }))
  })
  it("snoozes until a chosen date", async () => {
    show()
    await screen.findByText("Ada Lovelace")
    fireEvent.click(screen.getByRole("button", { name: "Snooze" }))
    fireEvent.click(screen.getByRole("button", { name: "14 days" }))
    fireEvent.click(screen.getAllByRole("button", { name: "Snooze" }).pop()!)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1].type).toBe("SNOOZE")
    expect(post.mock.calls[0][1].snoozeUntil).toMatch(/^\d{4}-\d\d-\d\d$/)
  })
  it("will not dismiss without a reason", async () => {
    show()
    await screen.findByText("Ada Lovelace")
    fireEvent.click(screen.getByRole("button", { name: "Dismiss" }))
    const ok = await screen.findByRole("button", { name: "Dismiss recall" })
    fireEvent.click(ok)
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByPlaceholderText(/moved away/), { target: { value: "Moved away" } })
    fireEvent.click(screen.getByRole("button", { name: "Dismiss recall" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/recalls/c1/actions", { type: "DISMISS", note: "Moved away" }))
  })
  it("hands the patient to the booking screen", async () => {
    const onBook = vi.fn()
    show({ onBook })
    await screen.findByText("Ada Lovelace")
    fireEvent.click(screen.getByRole("button", { name: /Book follow-up/ }))
    expect(onBook).toHaveBeenCalledWith({ patientId: "p1", patientName: "Ada Lovelace", practitionerId: "d1", reason: "Follow-up" })
  })
  it("offers reopen for snoozed recalls and pages long lists", async () => {
    get.mockImplementation(async (url: string) => url.includes("practitioners") ? { data: { data: [] } } : page([rec({ status: "SNOOZED", snoozedUntil: "2026-10-20" })], 60))
    show()
    expect(await screen.findByText(/Snoozed until/)).toBeTruthy()
    expect(screen.getByRole("button", { name: /Reopen/ })).toBeTruthy()
    expect(screen.getByText(/Page 1 of 3 · 60 patients/)).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Next page"))
    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringContaining("page=1")))
  })
})
