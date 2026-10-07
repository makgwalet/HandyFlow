import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const put = vi.fn()
let canWrite = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), put: (...a: any[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => canWrite }))
import WorkingHoursTab, { weekProblem, toWeek, toWindows, emptyWeek } from "./WorkingHoursTab"

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WorkingHoursTab /></QueryClientProvider>)
let hoursData: any[] = []
const choose = async () => {
  await screen.findByText("Dr Lee")
  fireEvent.change(screen.getByLabelText("Practitioner"), { target: { value: "p1" } })
}

beforeEach(() => { get.mockReset(); put.mockReset(); canWrite = true; hoursData = []
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("working-hours") ? hoursData : [{ id: "p1", fullName: "Dr Lee" }] } }))
  put.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("week helpers", () => {
  it("round-trips windows and weeks", () => {
    const w = toWeek([{ dayOfWeek: 3, from: "08:00", to: "12:00" }, { dayOfWeek: 3, from: "13:00", to: "17:00" }])
    expect(w[3]).toHaveLength(2)
    expect(toWindows(w)).toEqual([{ dayOfWeek: 3, from: "08:00", to: "12:00" }, { dayOfWeek: 3, from: "13:00", to: "17:00" }])
    expect(toWindows(emptyWeek())).toEqual([])
  })
  it("flags a missing time, a backwards period, overlaps and too many periods", () => {
    const w = emptyWeek()
    w[1] = [{ from: "08:00", to: "" }]; expect(weekProblem(w)).toMatch(/Monday.*enter a start and an end/)
    w[1] = [{ from: "10:00", to: "09:00" }]; expect(weekProblem(w)).toMatch(/after the start/)
    w[1] = [{ from: "08:00", to: "12:00" }, { from: "11:00", to: "15:00" }]; expect(weekProblem(w)).toMatch(/overlap/)
    w[1] = [1, 2, 3, 4, 5].map(i => ({ from: `0${i}:00`, to: `0${i}:30` })); expect(weekProblem(w)).toMatch(/at most 4/)
    w[1] = [{ from: "08:00", to: "12:00" }, { from: "12:00", to: "15:00" }]; expect(weekProblem(w)).toBeNull()
  })
})

describe("WorkingHoursTab", () => {
  it("says there is no restriction when nothing is saved", async () => {
    show(); await choose()
    expect(await screen.findByText(/can be booked at any time\./)).toBeTruthy()
  })

  it("shows the saved week", async () => {
    hoursData = [{ dayOfWeek: 3, from: "08:00", to: "12:00" }]
    show(); await choose()
    await waitFor(() => expect((screen.getByLabelText("Wednesday from 1") as HTMLInputElement).value).toBe("08:00"))
    expect((screen.getByLabelText("Wednesday to 1") as HTMLInputElement).value).toBe("12:00")
  })

  it("adds a period and saves the whole week", async () => {
    show(); await choose()
    await screen.findByText(/No hours set/)
    fireEvent.click(screen.getByLabelText("Add Monday period"))
    fireEvent.click(screen.getByText("Save working hours"))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toBe("/api/v1/clinic/practitioners/p1/working-hours")
    expect(put.mock.calls[0][1]).toEqual({ windows: [{ dayOfWeek: 1, from: "08:00", to: "17:00" }] })
  })

  it("does not send a week that does not make sense", async () => {
    show(); await choose()
    await screen.findByText(/No hours set/)
    fireEvent.click(screen.getByLabelText("Add Tuesday period"))
    fireEvent.change(screen.getByLabelText("Tuesday to 1"), { target: { value: "07:00" } })
    fireEvent.click(screen.getByText("Save working hours"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/after the start/)
    expect(put).not.toHaveBeenCalled()
  })

  it("shows the server's reason when saving fails", async () => {
    put.mockRejectedValueOnce({ response: { data: { message: "Tuesdays: working periods overlap" } } })
    show(); await choose()
    await screen.findByText(/No hours set/)
    fireEvent.click(screen.getByLabelText("Add Monday period"))
    fireEvent.click(screen.getByText("Save working hours"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/overlap/)
  })

  it("is read-only without write permission", async () => {
    canWrite = false
    show(); await choose()
    await screen.findByText(/No hours set/)
    expect(screen.queryByText("Save working hours")).toBeNull()
    expect(screen.queryByLabelText("Add Monday period")).toBeNull()
  })
})
