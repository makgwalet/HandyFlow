import { describe, it, expect, vi, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import MergePatientModal from "./MergePatientModal"

const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
vi.mock("./PatientPicker", () => ({ default: ({ onChange }: any) => <button type="button" onClick={() => onChange({ id: "b", fullName: "Thandi N" })}>pick duplicate</button> }))
afterEach(() => { cleanup(); get.mockReset(); post.mockReset() })

const here = { id: "a", fullName: "Thandi Nkosi", patientNumber: "P000001" }
const preview = (over: any = {}) => ({
  keep: { id: "a", patientNumber: "P000001", fullName: "Thandi Nkosi" }, mergeAway: { id: "b", patientNumber: "P000002", fullName: "Thandi N" },
  moves: [{ table: "clinic_consultations", label: "Visits", rows: 2 }], fills: ["email"], conflicts: [], blockers: [], staysBehind: [], needsAcknowledgement: false, ...over,
})
const ui = (onMerged = vi.fn()) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <MergePatientModal here={here} onClose={() => {}} onMerged={onMerged} /></QueryClientProvider>)
const choose = async () => fireEvent.click(await screen.findByText("pick duplicate"))
const btn = () => screen.getByRole("button", { name: "Merge records" }) as HTMLButtonElement

describe("MergePatientModal", () => {
  it("changes nothing until a duplicate is chosen", async () => {
    ui(); expect(await screen.findByText("pick duplicate")).toBeTruthy()
    expect(get).not.toHaveBeenCalled(); expect(btn().disabled).toBe(true)
  })
  it("shows what moves and merges into this record by default, with the reason", async () => {
    get.mockResolvedValue({ data: { data: preview() } }); post.mockResolvedValue({ data: { data: {} } }); const done = vi.fn(); ui(done)
    await choose()
    expect(await screen.findByText("Visits: 2")).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/clinic/patients/merge-preview?survivorId=a&mergedId=b")
    expect(btn().disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Why are these records being merged?"), { target: { value: "Registered twice" } })
    expect(btn().disabled).toBe(false)
    fireEvent.click(btn())
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/patients/merge", { survivorId: "a", mergedId: "b", reason: "Registered twice", acknowledgeConflicts: false }))
    await waitFor(() => expect(done).toHaveBeenCalledWith("a"))
  })
  it("can keep the other record instead", async () => {
    get.mockResolvedValue({ data: { data: preview() } }); post.mockResolvedValue({ data: { data: {} } }); const done = vi.fn(); ui(done)
    await choose(); await screen.findByText("Visits: 2")
    fireEvent.click(screen.getByLabelText(/Keep the other record/))
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/clinic/patients/merge-preview?survivorId=b&mergedId=a"))
    await screen.findByText("Visits: 2")
    fireEvent.change(screen.getByLabelText("Why are these records being merged?"), { target: { value: "Registered twice" } })
    fireEvent.click(btn())
    await waitFor(() => expect(done).toHaveBeenCalledWith("b"))
  })
  it("will not merge until a disagreement on identity is confirmed", async () => {
    get.mockResolvedValue({ data: { data: preview({ needsAcknowledgement: true, conflicts: [{ field: "ID number", text: "ID numbers differ: 1 and 2", blocking: true }] }) } }); ui()
    await choose()
    expect(await screen.findByText(/ID numbers differ/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Why are these records being merged?"), { target: { value: "Registered twice" } })
    expect(btn().disabled).toBe(true)
    fireEvent.click(screen.getByLabelText(/I have checked/))
    expect(btn().disabled).toBe(false)
  })
  it("shows why a merge is refused and offers no reason box", async () => {
    get.mockResolvedValue({ data: { data: preview({ blockers: ["The record to merge away has 1 visit still open. Sign or discard it first."] }) } }); ui()
    await choose()
    expect(await screen.findByText(/still open/)).toBeTruthy()
    expect(screen.queryByLabelText("Why are these records being merged?")).toBeNull(); expect(btn().disabled).toBe(true)
  })
  it("shows the server's message when the merge fails", async () => {
    get.mockResolvedValue({ data: { data: preview() } }); post.mockRejectedValue({ response: { data: { message: "Nothing was merged: Claims could not be moved (x)." } } }); ui()
    await choose(); await screen.findByText("Visits: 2")
    fireEvent.change(screen.getByLabelText("Why are these records being merged?"), { target: { value: "Registered twice" } })
    fireEvent.click(btn())
    expect((await screen.findByRole("alert")).textContent).toMatch(/Nothing was merged/)
  })
})
