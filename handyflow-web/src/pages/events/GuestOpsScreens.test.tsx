import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WaitlistTab from "./WaitlistTab"
import GuestToolsTab from "./GuestToolsTab"

const get = vi.fn(); const post = vi.fn(); const del = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const noop = () => {}

beforeEach(() => { get.mockReset(); post.mockReset(); del.mockReset(); perms = []; post.mockResolvedValue({ data: { data: {} } }); del.mockResolvedValue({ data: {} })
  get.mockImplementation(async (url: string) => url.endsWith("/tiers") ? { data: { data: [{ id: "t1", name: "General" }] } }
    : { data: { data: { content: [{ id: "w1", fullName: "Lerato", email: "l@x.co", phone: null, company: null, tierName: null, status: "WAITING", position: 1, createdAt: "2026-11-11T08:00:00Z" }] } } }) })
afterEach(cleanup)

describe("waitlist", () => {
  it("lists entries with their place, but gives a reader no actions", async () => {
    wrap(<WaitlistTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText(/#1 · Lerato/))
    expect(screen.queryByText("Promote now")).toBeNull(); expect(screen.queryByText("Add to waitlist")).toBeNull()
  })
  it("promotes and removes for someone who can register", async () => {
    perms = ["EVENTS_REGISTRATION_MANAGE"]
    wrap(<WaitlistTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Promote now"))
    fireEvent.click(screen.getByText("Promote now"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/waitlist/w1/promote"))
    fireEvent.click(screen.getByText("Remove"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/events/e1/waitlist/w1"))
  })
  it("shows the server's reason when promotion is refused", async () => {
    perms = ["EVENTS_MANAGE"]
    post.mockRejectedValue({ response: { data: { message: "No free place" } } })
    wrap(<WaitlistTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Promote now"))
    fireEvent.click(screen.getByText("Promote now"))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toBe("No free place"))
  })
})

describe("adding guests", () => {
  it("a door volunteer with check-in rights sees walk-in only", async () => {
    perms = ["EVENTS_CHECKIN"]
    wrap(<GuestToolsTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Walk-in at the door"))
    expect(screen.queryByText("Group registration")).toBeNull(); expect(screen.queryByText("Import guests from CSV")).toBeNull()
  })
  it("someone with no rights is told so and nothing is fetched", () => {
    wrap(<GuestToolsTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    expect(screen.getByText(/do not have access/)).toBeTruthy(); expect(get).not.toHaveBeenCalled()
  })
  it("walk-in posts the person and the amount", async () => {
    perms = ["EVENTS_CHECKIN"]
    wrap(<GuestToolsTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Walk-in at the door"))
    fireEvent.change(screen.getByLabelText("Full name *"), { target: { value: "Sipho" } })
    fireEvent.change(screen.getByLabelText(/Amount paid at the door/), { target: { value: "150" } })
    fireEvent.click(screen.getByRole("button", { name: "Register and check in" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/guests/walk-in", expect.objectContaining({ fullName: "Sipho", amountPaid: 150, email: null })))
  })
  it("group registration skips blank rows and posts the members", async () => {
    perms = ["EVENTS_REGISTRATION_MANAGE"]
    wrap(<GuestToolsTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Group registration"))
    fireEvent.change(screen.getByLabelText("Person 1 name"), { target: { value: "Thandi" } })
    fireEvent.click(screen.getByRole("button", { name: /^Register 1 people$/ }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/guests/group", expect.objectContaining({ members: [expect.objectContaining({ fullName: "Thandi" })], amountPaidEach: null })))
  })
  it("import cannot run until a preview found rows and consent is ticked", async () => {
    perms = ["EVENTS_REGISTRATION_MANAGE"]
    post.mockResolvedValue({ data: { data: { dryRun: true, totalRows: 2, imported: 2, duplicates: 0, invalid: 0, rejected: 0, rows: [] } } })
    wrap(<GuestToolsTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Import guests from CSV"))
    expect((screen.getByRole("button", { name: "Preview import" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("CSV file"), { target: { files: [new File(["full_name\nA\nB\n"], "g.csv", { type: "text/csv" })] } })
    fireEvent.click(screen.getByRole("button", { name: "Preview import" }))
    await waitFor(() => screen.getByText(/2 would be imported/))
    const go = screen.getByRole("button", { name: "Import 2 guests" }) as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.click(screen.getByLabelText(/lawful basis/))
    expect(go.disabled).toBe(false)
    post.mockResolvedValue({ data: { data: { dryRun: false, totalRows: 2, imported: 2, duplicates: 0, invalid: 0, rejected: 0, rows: [] } } })
    fireEvent.click(go)
    await waitFor(() => screen.getByText(/Import complete/))
    const fd = post.mock.calls.at(-1)![1] as FormData
    expect(fd.get("dryRun")).toBe("false"); expect(fd.get("consentConfirmed")).toBe("true")
  })
})
