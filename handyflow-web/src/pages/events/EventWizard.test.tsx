import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EventWizard from "./EventWizard"

const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const dlg = () => within(screen.getByRole("dialog"))
const type = (label: RegExp | string, v: string) => fireEvent.change(dlg().getByLabelText(label), { target: { value: v } })
const next = () => fireEvent.click(dlg().getByText("Next"))
const mount = () => { const onCreated = vi.fn(), onClose = vi.fn(); render(<QueryClientProvider client={new QueryClient()}><EventWizard onClose={onClose} onCreated={onCreated} /></QueryClientProvider>); return { onCreated, onClose } }
const details = () => { type(/Event title/, "Summit"); type(/Starts/, "2026-12-01T08:00"); type(/Ends/, "2026-12-01T17:00") }

beforeEach(() => { post.mockReset(); perms = []; post.mockImplementation(async (u: string) => ({ data: { data: u === "/api/v1/events" ? { id: "E1" } : {} } })) })
afterEach(cleanup)

describe("create-event wizard", () => {
  it("cannot leave Details until the event is valid", () => {
    mount(); expect((dlg().getByText("Next") as HTMLButtonElement).disabled).toBe(true); expect(dlg().getByText(/Give the event a title/)).toBeTruthy(); details(); expect((dlg().getByText("Next") as HTMLButtonElement).disabled).toBe(false)
  })
  it("a user who can only write events sees just Details and Review, and creates a draft", async () => {
    perms = ["EVENTS_WRITE"]; const { onCreated } = mount(); expect(within(screen.getByLabelText("Steps")).getAllByRole("listitem")).toHaveLength(2)
    details(); next(); fireEvent.click(dlg().getByText("Create event"))
    await waitFor(() => expect(onCreated).toHaveBeenCalledWith("E1", "Summit")); expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0][1]).toMatchObject({ title: "Summit", startDatetime: "2026-12-01T08:00:00", venueCapacity: null, description: null })
  })
  it("creates the event then its ticket types, vendors and budget lines in order", async () => {
    perms = ["EVENTS_MANAGE"]; const { onCreated } = mount(); details(); next()
    fireEvent.click(dlg().getByText("Add ticket type")); type(/^Name/, "General"); type(/Places/, "50"); type(/^Price/, "150"); next()
    fireEvent.click(dlg().getByText("Add vendor")); type(/Company/, "Sound Co"); next()
    fireEvent.click(dlg().getByText("Add budget line")); type(/Planned amount/, "9000"); type(/What is it for/, "Hall hire"); next()
    expect(dlg().getByText(/R\s9[\s ]?000[.,]00 planned/)).toBeTruthy(); fireEvent.click(dlg().getByText("Create event"))
    await waitFor(() => expect(onCreated).toHaveBeenCalled())
    expect(post.mock.calls.map(c => c[0])).toEqual(["/api/v1/events", "/api/v1/events/E1/tiers", "/api/v1/events/E1/vendors", "/api/v1/events/E1/budget/lines"])
    expect(post.mock.calls[1][1]).toMatchObject({ name: "General", price: 150, quantity: 50 }); expect(post.mock.calls[3][1]).toMatchObject({ plannedAmount: 9000, description: "Hall hire" })
  })
  it("blocks the next step on a bad row and says which", () => {
    perms = ["EVENTS_MANAGE"]; mount(); details(); next(); fireEvent.click(dlg().getByText("Add ticket type")); expect((dlg().getByText("Next") as HTMLButtonElement).disabled).toBe(true); expect(dlg().getByText(/Ticket type 1: Give the ticket type a name/)).toBeTruthy()
    fireEvent.click(dlg().getByText("Remove ticket type 1")); expect((dlg().getByText("Next") as HTMLButtonElement).disabled).toBe(false)
  })
  it("when an extra is refused, keeps the event, says so, and retries only the rest", async () => {
    perms = ["EVENTS_MANAGE"]; post.mockImplementation(async (u: string) => { if (u.endsWith("/tiers")) throw { response: { data: { message: "Tier price is too high" } } }; return { data: { data: u === "/api/v1/events" ? { id: "E1" } : {} } } })
    const { onCreated } = mount(); details(); next(); fireEvent.click(dlg().getByText("Add ticket type")); type(/^Name/, "VIP"); type(/Places/, "5"); next(); next(); next(); fireEvent.click(dlg().getByText("Create event"))
    await waitFor(() => dlg().getByText(/Tier price is too high/)); expect(dlg().getByText(/has been created. 0 of 1/)).toBeTruthy(); expect(onCreated).not.toHaveBeenCalled()
    post.mockImplementation(async () => ({ data: { data: {} } })); fireEvent.click(dlg().getByText("Retry the rest")); await waitFor(() => expect(onCreated).toHaveBeenCalledWith("E1", "Summit"))
    expect(post.mock.calls.filter(c => c[0] === "/api/v1/events")).toHaveLength(1)
  })
  it("shows the server's message when the event itself is refused, and creates nothing else", async () => {
    perms = ["EVENTS_MANAGE"]; post.mockRejectedValueOnce({ response: { data: { message: "An event with that title already starts then" } } }); mount(); details(); next(); next(); next(); next()
    fireEvent.click(dlg().getByText("Create event")); await waitFor(() => dlg().getByText(/already starts then/)); expect(post).toHaveBeenCalledTimes(1)
  })
})
