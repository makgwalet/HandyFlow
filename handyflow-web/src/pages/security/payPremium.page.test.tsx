// @vitest-environment jsdom
import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

let settings = { enabled: false, sundayMultiplier: 1.5, holidayMultiplier: 2 }
let settingsFail = false
const holidays = [
  { date: "2026-04-03", name: "Good Friday", source: "REMOVED" },
  { date: "2026-12-25", name: "Christmas Day", source: "NATIONAL" },
  { date: "2026-12-30", name: "Proclaimed day", source: "ADDED" },
]
const get = vi.fn((url: string) => {
  if (url.includes("/premiums/holidays")) return Promise.resolve({ data: { data: holidays } })
  if (settingsFail) return Promise.reject({ response: { data: { message: "Only the whole company can see this." } } })
  return Promise.resolve({ data: { data: settings } })
})
const put = vi.fn((...args: [string, object]) => Promise.resolve({ data: { data: settings }, args }))
const post = vi.fn((...args: [string, object]) => Promise.resolve({ data: { data: holidays }, args }))
const del = vi.fn((...args: [string]) => Promise.resolve({ data: { data: holidays }, args }))
vi.mock("../../api/client", () => ({ apiClient: { get: (u: string) => get(u), put: (u: string, b: object) => put(u, b), post: (u: string, b: object) => post(u, b), delete: (u: string) => del(u) } }))
import PayPremiumPanel from "./PayPremiumPanel"

const show = (canManage = true) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><PayPremiumPanel canManage={canManage} /></QueryClientProvider>)
afterEach(() => { cleanup(); get.mockClear(); put.mockClear(); post.mockClear(); del.mockClear(); settingsFail = false; settings = { enabled: false, sundayMultiplier: 1.5, holidayMultiplier: 2 } })

describe("Sunday and public-holiday pay panel", () => {
  it("shows the saved settings with what they mean in money", async () => {
    settings = { enabled: true, sundayMultiplier: 1.75, holidayMultiplier: 2.5 }
    show()
    await waitFor(() => expect((screen.getByLabelText("Sunday rate") as HTMLInputElement).value).toBe("1.75"))
    expect((screen.getByLabelText("Public holiday rate") as HTMLInputElement).value).toBe("2.50")
    expect((screen.getByLabelText("Pay Sunday and public-holiday rates") as HTMLInputElement).checked).toBe(true)
    expect(screen.getByTestId("sunday-example").textContent).toContain("R 175.00")
    expect(screen.getByTestId("holiday-example").textContent).toContain("R 250.00")
  })

  it("will not send a rate below the overtime rate", async () => {
    show()
    await waitFor(() => expect((screen.getByLabelText("Sunday rate") as HTMLInputElement).disabled).toBe(false))
    fireEvent.change(screen.getByLabelText("Sunday rate"), { target: { value: "1.2" } })
    fireEvent.click(screen.getByText("Save"))
    expect((await screen.findByRole("alert")).textContent).toContain("Sunday rate")
    expect(put).not.toHaveBeenCalled()
  })

  it("saves the switch and rates as numbers", async () => {
    show()
    await waitFor(() => expect((screen.getByLabelText("Sunday rate") as HTMLInputElement).disabled).toBe(false))
    fireEvent.click(screen.getByLabelText("Pay Sunday and public-holiday rates"))
    fireEvent.change(screen.getByLabelText("Public holiday rate"), { target: { value: "2,25" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(put).toHaveBeenCalledTimes(1))
    expect(put.mock.calls[0][0]).toBe("/api/v1/security/payroll/premiums")
    expect(put.mock.calls[0][1]).toEqual({ enabled: true, sundayMultiplier: 1.5, holidayMultiplier: 2.25 })
    expect((await screen.findByRole("status")).textContent).toContain("periods approved from now on")
  })

  it("shows the server's words when saving is refused", async () => {
    put.mockImplementationOnce(() => Promise.reject({ response: { data: { message: "Public holiday multiplier must be between 1.50 and 4.00." } } }))
    show()
    await waitFor(() => expect((screen.getByLabelText("Sunday rate") as HTMLInputElement).disabled).toBe(false))
    fireEvent.click(screen.getByText("Save"))
    expect((await screen.findByRole("alert")).textContent).toContain("Public holiday multiplier")
    expect(screen.queryByRole("status")).toBeNull()
  })

  it("lists the year's days, each with the right button", async () => {
    show()
    const good = await screen.findByTestId("holiday-2026-04-03")
    expect(good.textContent).toContain("Taken off by you")
    expect(good.textContent).toContain("Put back")
    expect(screen.getByTestId("holiday-2026-12-25").textContent).toContain("Take off")
    expect(screen.getByTestId("holiday-2026-12-30").textContent).toContain("Delete")
  })

  it("takes a national day off, deletes an added one, and puts a removed one back", async () => {
    show()
    await screen.findByTestId("holiday-2026-12-25")
    fireEvent.click(screen.getByTestId("holiday-2026-12-25").querySelector("button")!)
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/security/payroll/premiums/holidays?date=2026-12-25"))
    fireEvent.click(screen.getByTestId("holiday-2026-12-30").querySelector("button")!)
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/security/payroll/premiums/holidays?date=2026-12-30"))
    fireEvent.click(screen.getByTestId("holiday-2026-04-03").querySelector("button")!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/security/payroll/premiums/holidays", { date: "2026-04-03", name: "Good Friday" }))
  })

  it("adds a company day with a trimmed name, and asks for a name first", async () => {
    show()
    await screen.findByTestId("holiday-2026-12-25")
    fireEvent.change(screen.getByLabelText("Day to add"), { target: { value: "2026-11-24" } })
    fireEvent.click(screen.getByText("Add day"))
    expect((await screen.findByRole("alert")).textContent).toContain("name")
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Day name"), { target: { value: "  Election day  " } })
    fireEvent.click(screen.getByText("Add day"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/security/payroll/premiums/holidays", { date: "2026-11-24", name: "Election day" }))
  })

  it("loads the chosen year", async () => {
    show()
    await screen.findByTestId("holiday-2026-12-25")
    fireEvent.change(screen.getByLabelText("Year"), { target: { value: String(new Date().getFullYear() + 1) } })
    await waitFor(() => expect(get).toHaveBeenCalledWith(`/api/v1/security/payroll/premiums/holidays?year=${new Date().getFullYear() + 1}`))
  })

  it("is read only without the manage permission", async () => {
    show(false)
    await screen.findByTestId("holiday-2026-12-25")
    expect(screen.queryByText("Save")).toBeNull()
    expect(screen.queryByText("Add day")).toBeNull()
    expect(screen.queryByText("Take off")).toBeNull()
    expect((screen.getByLabelText("Sunday rate") as HTMLInputElement).disabled).toBe(true)
    expect(screen.getByText(/can see these settings but not change them/)).toBeTruthy()
  })

  it("says so, in the server's words, when the user may not see it", async () => {
    settingsFail = true
    show()
    expect(await screen.findByText("Only the whole company can see this.")).toBeTruthy()
    expect(screen.queryByTestId("pay-premium-panel")).toBeNull()
  })
})
