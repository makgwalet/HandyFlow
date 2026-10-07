import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
import DraftsTab from "./DraftsTab"

const drafts = [
  { id: "d1", patientId: "p1", patientName: "Thandi Mokoena", appointmentId: "a1", chiefComplaint: "Cough", status: "DRAFT", createdAt: "2026-10-07T08:00:00Z" },
  { id: "d2", patientId: "p2", patientName: "Sipho Dube", appointmentId: null, status: "NURSE_IN_PROGRESS", createdAt: "2026-10-07T09:00:00Z" },
]
const ok = (data: any) => Promise.resolve({ data: { data } })
const show = (onResume = vi.fn()) => {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <DraftsTab onResume={onResume} /></QueryClientProvider>)
  return onResume
}

beforeEach(() => {
  get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => {
    if (url.endsWith("/consultations/drafts")) return ok(drafts)
    if (url.endsWith("/patients/p1")) return ok({ id: "p1", fullName: "Thandi Mokoena" })
    if (url.endsWith("/patients/p1/appointments")) return ok([{ id: "a0" }, { id: "a1", appointmentType: "CONSULTATION" }])
    return ok(null)
  })
  post.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("DraftsTab", () => {
  it("lists drafts with their state, and disables Resume when there is no appointment", async () => {
    show()
    expect(await screen.findByText("Thandi Mokoena")).toBeTruthy()
    expect(screen.getByText("Nurse in progress")).toBeTruthy()
    const resumes = screen.getAllByText("Resume") as HTMLButtonElement[]
    expect(resumes[0].disabled).toBe(false)
    expect(resumes[1].disabled).toBe(true)
  })

  it("resumes on the draft's own appointment", async () => {
    const onResume = show()
    fireEvent.click((await screen.findAllByText("Resume"))[0])
    await waitFor(() => expect(onResume).toHaveBeenCalled())
    expect(onResume.mock.calls[0][0].id).toBe("p1")
    expect(onResume.mock.calls[0][1].id).toBe("a1")
  })

  it("discards only after confirmation", async () => {
    show()
    const discard = (await screen.findAllByText("Discard"))[0]
    fireEvent.click(discard)
    fireEvent.click(within(screen.getByRole("dialog", { name: "Discard this draft?" })).getByText("Cancel"))
    expect(post).not.toHaveBeenCalled()
    fireEvent.click(discard)
    fireEvent.click(within(screen.getByRole("dialog", { name: "Discard this draft?" })).getByText("Discard"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/consultations/d1/abandon"))
  })

  it("asks for only the user's own drafts by default, and for all of them when unticked", async () => {
    show()
    await screen.findByText("Thandi Mokoena")
    expect(get.mock.calls.find(c => String(c[0]).endsWith("/drafts"))![1]).toEqual({ params: { mine: true } })
    fireEvent.click(screen.getByLabelText("Only drafts I started"))
    await waitFor(() => expect(get.mock.calls.some(c => String(c[0]).endsWith("/drafts") && c[1]?.params?.mine === false)).toBe(true))
  })

  it("says so when there are no drafts", async () => {
    get.mockImplementation(() => ok([]))
    show()
    expect(await screen.findByText(/You have no open drafts/)).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Only drafts I started"))
    expect(await screen.findByText("No open drafts.")).toBeTruthy()
  })
})
