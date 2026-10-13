import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { useEffect } from "react"

const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
const left = vi.fn()
vi.mock("./ConsultationSession", () => ({
  default: (p: any) => {
    // like the real session: when asked to step out, it saves and then reports minimise
    useEffect(() => { if (p.minimiseToken > 0) { left(p.appointment.id); p.onMinimise() } }, [p.minimiseToken])   // eslint-disable-line
    return <div>SESSION for {p.patient.fullName} / {p.appointment.id}</div>
  },
}))
import ConsultationWorkspacePage from "./ConsultationWorkspacePage"
import OpenConsultationsStrip from "./OpenConsultationsStrip"

const people: Record<string, string> = { a1: "Liam Botha", a2: "Jane Dlamini" }
const drafts = [
  { id: "c1", appointmentId: "a1", patientId: "p1", patientName: "Liam Botha", status: "DRAFT", createdAt: "2026-10-13T08:00:00Z" },
  { id: "c2", appointmentId: "a2", patientId: "p2", patientName: "Jane Dlamini", status: "DRAFT", createdAt: "2026-10-13T08:10:00Z" },
]
let draftList: any[] = drafts
beforeEach(() => {
  left.mockReset(); get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: { data: {} } })
  draftList = drafts
  get.mockImplementation((url: string) => {
    const m = /appointments\/(a\d)$/.exec(url)
    if (m) return Promise.resolve({ data: { data: { id: m[1], patientId: "p" + m[1][1], patientName: people[m[1]], status: "IN_PROGRESS", appointmentType: "CONSULTATION", scheduledAt: "2026-10-13T08:00:00Z" } } })
    const pm = /patients\/(p\d)$/.exec(url)
    if (pm) return Promise.resolve({ data: { data: { id: pm[1], fullName: people["a" + pm[1][1]] } } })
    if (url.includes("consultations/drafts")) return Promise.resolve({ data: { data: draftList } })
    return Promise.resolve({ data: { data: [] } })
  })
})
afterEach(cleanup)

const client = () => new QueryClient({ defaultOptions: { queries: { retry: false } } })

describe("open consultations strip", () => {
  it("is not shown when nothing else is open", async () => {
    draftList = [drafts[0]]
    const { container } = render(<QueryClientProvider client={client()}><OpenConsultationsStrip currentAppointmentId="a1" onSwitch={() => {}} /></QueryClientProvider>)
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
  it("lists the open patients, marks the current one, and switches on click", async () => {
    const onSwitch = vi.fn()
    render(<QueryClientProvider client={client()}><OpenConsultationsStrip currentAppointmentId="a1" onSwitch={onSwitch} /></QueryClientProvider>)
    const current = await screen.findByRole("button", { name: "Liam Botha" })
    expect(current.getAttribute("aria-current")).toBe("page")
    fireEvent.click(current); expect(onSwitch).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole("button", { name: "Jane Dlamini" }))
    expect(onSwitch).toHaveBeenCalledWith("a2")
  })
})

describe("switching between open consultations", () => {
  it("saves and steps out of the one on screen, then opens the other as its own screen", async () => {
    render(<QueryClientProvider client={client()}><MemoryRouter initialEntries={["/clinic/consult/a1"]}><Routes>
      <Route path="/clinic/consult/:appointmentId" element={<ConsultationWorkspacePage />} />
      <Route path="/clinic/dashboard" element={<div>TODAY</div>} />
    </Routes></MemoryRouter></QueryClientProvider>)
    expect(await screen.findByText("SESSION for Liam Botha / a1")).toBeTruthy()
    fireEvent.click(await screen.findByRole("button", { name: "Jane Dlamini" }))
    expect(await screen.findByText("SESSION for Jane Dlamini / a2")).toBeTruthy()
    expect(left).toHaveBeenCalledWith("a1")                               // the first one was asked to save and step out
    expect(screen.queryByText("SESSION for Liam Botha / a1")).toBeNull()  // and is no longer on screen
    expect(screen.queryByText("TODAY")).toBeNull()                        // it did not fall back to Today
  })
})
