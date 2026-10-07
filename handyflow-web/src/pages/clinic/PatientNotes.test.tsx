import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(), post = vi.fn()
let canWrite = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => canWrite }))
import PatientNotesPanel, { PatientAlertBanner } from "./PatientNotes"

const notes = [
  { id: "a1", kind: "ALERT", severity: "CRITICAL", body: "Do not leave voicemail" },
  { id: "n1", kind: "NOTE", severity: null, body: "Prefers morning slots" },
]
const wrap = (ui: React.ReactNode) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => { get.mockReset(); post.mockReset(); canWrite = true
  get.mockResolvedValue({ data: { data: notes } }); post.mockResolvedValue({ data: { data: {} } }) })
afterEach(cleanup)

describe("PatientAlertBanner", () => {
  it("shows alerts but not plain notes", async () => {
    wrap(<PatientAlertBanner patientId="p1" />)
    expect(await screen.findByText(/Do not leave voicemail/)).toBeTruthy()
    expect(screen.queryByText("Prefers morning slots")).toBeNull()
  })
  it("renders nothing when there are no alerts", async () => {
    get.mockResolvedValue({ data: { data: [notes[1]] } })
    const { container } = wrap(<PatientAlertBanner patientId="p1" />)
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
})

describe("PatientNotesPanel", () => {
  it("lists notes and alerts, and resolves one", async () => {
    wrap(<PatientNotesPanel patientId="p1" />)
    expect(await screen.findByText("Prefers morning slots")).toBeTruthy()
    fireEvent.click(screen.getAllByText("Resolve")[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/notes/a1/resolve"))
  })
  it("adds an alert with its severity, and shows the server's refusal", async () => {
    wrap(<PatientNotesPanel patientId="p1" />)
    await screen.findByText("Prefers morning slots")
    fireEvent.change(screen.getByLabelText("Kind"), { target: { value: "ALERT" } })
    fireEvent.change(screen.getByLabelText("Severity"), { target: { value: "CRITICAL" } })
    fireEvent.change(screen.getByLabelText("Note text"), { target: { value: "Needs interpreter" } })
    fireEvent.click(screen.getByText("Add"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/notes",
      { kind: "ALERT", severity: "CRITICAL", body: "Needs interpreter" }))
  })
  it("offers no add or resolve without clinical write permission", async () => {
    canWrite = false
    wrap(<PatientNotesPanel patientId="p1" />)
    await screen.findByText("Prefers morning slots")
    expect(screen.queryByText("Resolve")).toBeNull()
    expect(screen.queryByText("Add")).toBeNull()
  })
  it("says so when there is nothing open", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    wrap(<PatientNotesPanel patientId="p1" />)
    expect(await screen.findByText("No open notes or alerts.")).toBeTruthy()
  })
})
