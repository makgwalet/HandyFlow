import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))

import ReportRecipientsPanel from "./ReportRecipientsPanel"

const people = (...e: string[]) => ({ data: { data: e.map((x, i) => ({ id: `r${i}`, name: `Person ${i}`, email: x })) } })

function show(o: Partial<{ inProgress: boolean; reportLoaded: boolean }> = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ReportRecipientsPanel siteId="s1" month="2026-10" monthTitle="October 2026" inProgress={o.inProgress ?? false} reportLoaded={o.reportLoaded ?? true} /></QueryClientProvider>)
}
const CONTACT = { data: { data: { name: "Ops Desk", email: "help@sec.co.za", phone: null } } }
/** Answers the recipients and settings calls by address; `contact` null means none set yet. */
function serve(recipients: unknown, contact: unknown = CONTACT) {
  api.get.mockImplementation((url: string) => url.includes("report-settings") ? Promise.resolve(contact === null ? { data: { data: null } } : contact) : Promise.resolve(recipients))
}
const sendBtn = () => screen.getByRole("button", { name: "Email this report" }) as HTMLButtonElement

describe("ReportRecipientsPanel", () => {
  beforeEach(() => { api.get.mockReset(); api.post.mockReset(); api.put.mockReset(); api.delete.mockReset() })
  afterEach(cleanup)

  it("lists recipients from the server", async () => {
    serve(people("a@c.com", "b@c.com"))
    show()
    expect(await screen.findByText("Person 0 · a@c.com")).toBeTruthy()
    expect(screen.getByText("Person 1 · b@c.com")).toBeTruthy()
    expect(api.get.mock.calls[0][0]).toBe("/api/v1/security/sites/s1/report-recipients")
  })

  it("says so when there are none, and blocks sending", async () => {
    serve(people())
    show()
    expect(await screen.findByText(/No recipients yet/)).toBeTruthy()
    expect(sendBtn().disabled).toBe(true)
    expect(screen.getByText("Add a recipient first.")).toBeTruthy()
  })

  it("shows an error, not an empty list, when loading fails or the answer is malformed", async () => {
    api.get.mockImplementation((url: string) => url.includes("report-settings") ? Promise.resolve(CONTACT) : Promise.reject(new Error("x")))
    show()
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("could not be loaded"))
    expect(screen.queryByText("Email this report")).toBeNull()
    cleanup()
    serve({ data: { data: { not: "a list" } } })
    show()
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("could not be loaded"))
  })

  it("adds a recipient with the typed, trimmed values and clears the form", async () => {
    serve(people())
    api.post.mockResolvedValue({ data: { data: { id: "x" } } })
    show()
    await screen.findByText(/No recipients yet/)
    const add = screen.getByRole("button", { name: "Add" }) as HTMLButtonElement
    expect(add.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Recipient name"), { target: { value: "  Jo Client " } })
    fireEvent.change(screen.getByLabelText("Recipient email"), { target: { value: " jo@client.co.za " } })
    expect(add.disabled).toBe(false)
    fireEvent.click(add)
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1))
    expect(api.post.mock.calls[0][0]).toBe("/api/v1/security/sites/s1/report-recipients")
    expect(api.post.mock.calls[0][1]).toEqual({ name: "Jo Client", email: "jo@client.co.za" })
    await waitFor(() => expect((screen.getByLabelText("Recipient name") as HTMLInputElement).value).toBe(""))
  })

  it("will not add a bad or repeated address", async () => {
    serve(people("a@c.com"))
    show()
    await screen.findByText("Person 0 · a@c.com")
    fireEvent.change(screen.getByLabelText("Recipient name"), { target: { value: "Jo" } })
    fireEvent.change(screen.getByLabelText("Recipient email"), { target: { value: "not-an-address" } })
    expect((screen.getByRole("button", { name: "Add" }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText("Enter one email address.")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Recipient email"), { target: { value: "A@c.com" } })
    expect((screen.getByRole("button", { name: "Add" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("stops adding at five", async () => {
    serve(people("a@c.com", "b@c.com", "c@c.com", "d@c.com", "e@c.com"))
    show()
    await screen.findByText("Person 4 · e@c.com")
    fireEvent.change(screen.getByLabelText("Recipient name"), { target: { value: "Jo" } })
    fireEvent.change(screen.getByLabelText("Recipient email"), { target: { value: "f@c.com" } })
    expect((screen.getByRole("button", { name: "Add" }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/at most 5 recipients/)).toBeTruthy()
  })

  it("shows the server's reason when adding fails", async () => {
    serve(people())
    api.post.mockImplementation(() => Promise.reject({ response: { data: { message: "That address is already on the list" } } }))
    show()
    await screen.findByText(/No recipients yet/)
    fireEvent.change(screen.getByLabelText("Recipient name"), { target: { value: "Jo" } })
    fireEvent.change(screen.getByLabelText("Recipient email"), { target: { value: "jo@c.com" } })
    fireEvent.click(screen.getByRole("button", { name: "Add" }))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toBe("That address is already on the list"))
  })

  it("removes the chosen recipient", async () => {
    serve(people("a@c.com", "b@c.com"))
    api.delete.mockResolvedValue({})
    show()
    await screen.findByText("Person 1 · b@c.com")
    fireEvent.click(screen.getByRole("button", { name: "Remove Person 1" }))
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith("/api/v1/security/sites/s1/report-recipients/r1"))
  })

  it("asks before sending, and Cancel sends nothing", async () => {
    serve(people("a@c.com", "b@c.com"))
    show()
    await screen.findByText("Person 1 · b@c.com")
    fireEvent.click(sendBtn())
    expect(screen.getByText("Email the October 2026 report to 2 recipients?")).toBeTruthy()
    expect(api.post).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }))
    expect(screen.queryByText(/Email the October 2026 report/)).toBeNull()
    expect(api.post).not.toHaveBeenCalled()
  })

  it("sends the shown month and reports it as queued, not delivered", async () => {
    serve(people("a@c.com", "b@c.com"))
    api.post.mockResolvedValue({ data: { data: { queued: 2, month: "2026-10", failed: 0 } } })
    show()
    await screen.findByText("Person 1 · b@c.com")
    fireEvent.click(sendBtn())
    fireEvent.click(screen.getByRole("button", { name: "Send" }))
    await waitFor(() => expect(screen.getByRole("status").textContent).toBe("Queued for 2 recipients (October 2026). Delivery is not confirmed."))
    expect(api.post.mock.calls[0][0]).toBe("/api/v1/security/sites/s1/monthly-pack/email")
    expect(api.post.mock.calls[0][2]).toEqual({ params: { month: "2026-10" } })
    expect(screen.queryByText(/Email the October 2026 report/)).toBeNull()
  })

  it("shows the server's reason when sending is refused", async () => {
    serve(people("a@c.com"))
    api.post.mockImplementation(() => Promise.reject({ response: { data: { message: "This report was just sent. Try again in a few minutes." } } }))
    show()
    await screen.findByText("Person 0 · a@c.com")
    fireEvent.click(sendBtn())
    fireEvent.click(screen.getByRole("button", { name: "Send" }))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("just sent"))
  })

  it("will not send a month still in progress, or before the report has loaded", async () => {
    serve(people("a@c.com"))
    show({ inProgress: true })
    await screen.findByText("Person 0 · a@c.com")
    expect(sendBtn().disabled).toBe(true)
    expect(screen.getByText("Only a finished month can be emailed.")).toBeTruthy()
    cleanup()
    show({ reportLoaded: false })
    await screen.findByText("Person 0 · a@c.com")
    expect(sendBtn().disabled).toBe(true)
  })

  it("asks for the contact first when none is set, and will not send", async () => {
    serve(people("a@c.com"), null)
    show()
    await screen.findByText("Person 0 · a@c.com")
    expect(await screen.findByLabelText("Contact email")).toBeTruthy()
    expect(sendBtn().disabled).toBe(true)
    expect(screen.getByText("Set the contact address for report questions first.")).toBeTruthy()
  })

  it("saves the typed, trimmed contact and then allows sending", async () => {
    serve(people("a@c.com"), null)
    api.put.mockResolvedValue({ data: { data: {} } })
    show()
    await screen.findByText("Person 0 · a@c.com")
    const save = await screen.findByRole("button", { name: "Save contact" }) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Contact name"), { target: { value: " Ops Desk " } })
    fireEvent.change(screen.getByLabelText("Contact email"), { target: { value: " help@sec.co.za " } })
    fireEvent.change(screen.getByLabelText("Contact phone"), { target: { value: "" } })
    expect(save.disabled).toBe(false)
    serve(people("a@c.com"))
    fireEvent.click(save)
    await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1))
    expect(api.put.mock.calls[0][0]).toBe("/api/v1/security/report-settings")
    expect(api.put.mock.calls[0][1]).toEqual({ name: "Ops Desk", email: "help@sec.co.za", phone: null })
    await waitFor(() => expect(sendBtn().disabled).toBe(false))
    expect(screen.getByText(/Questions go to: Ops Desk, help@sec.co.za/)).toBeTruthy()
  })

  it("will not save a bad address or phone, and shows the server's reason on failure", async () => {
    serve(people(), null)
    api.put.mockImplementation(() => Promise.reject({ response: { data: { message: "Enter a valid email address" } } }))
    show()
    const save = await screen.findByRole("button", { name: "Save contact" }) as HTMLButtonElement
    fireEvent.change(screen.getByLabelText("Contact email"), { target: { value: "nope" } })
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Contact email"), { target: { value: "h@s.co.za" } })
    fireEvent.change(screen.getByLabelText("Contact phone"), { target: { value: "abc" } })
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Contact phone"), { target: { value: "011 555 0100" } })
    fireEvent.click(save)
    await waitFor(() => expect(screen.getByRole("alert").textContent).toBe("Enter a valid email address"))
  })

  it("lets the contact be changed", async () => {
    serve(people("a@c.com"))
    api.put.mockResolvedValue({ data: { data: {} } })
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Change contact" }))
    expect((screen.getByLabelText("Contact email") as HTMLInputElement).value).toBe("help@sec.co.za")
    fireEvent.change(screen.getByLabelText("Contact email"), { target: { value: "new@sec.co.za" } })
    fireEvent.click(screen.getByRole("button", { name: "Save contact" }))
    await waitFor(() => expect(api.put.mock.calls[0][1]).toMatchObject({ email: "new@sec.co.za" }))
  })

  it("will not send when the contact could not be loaded", async () => {
    api.get.mockImplementation((url: string) => url.includes("report-settings") ? Promise.reject(new Error("x")) : Promise.resolve(people("a@c.com")))
    show()
    await screen.findByText("Person 0 · a@c.com")
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("contact could not be loaded"))
    expect(sendBtn().disabled).toBe(true)
  })

  it("says how many addresses the mail service refused", async () => {
    serve(people("a@c.com", "b@c.com"))
    api.post.mockResolvedValue({ data: { data: { queued: 1, month: "2026-10", failed: 1 } } })
    show()
    await screen.findByText("Person 1 · b@c.com")
    fireEvent.click(sendBtn())
    fireEvent.click(screen.getByRole("button", { name: "Send" }))
    await waitFor(() => expect(screen.getByRole("status").textContent).toContain("1 address was refused"))
  })
})
