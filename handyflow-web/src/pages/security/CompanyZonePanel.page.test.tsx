// @vitest-environment jsdom
import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))

import { useAuthStore } from "../../store/auth.store"
import CompanyZonePanel from "./CompanyZonePanel"

const VIEW = { timeZone: "Africa/Johannesburg", abbreviation: "SAST", now: "22 Nov 2026 19:40 SAST", today: "2026-11-22" }
const EFFECT = { from: "Africa/Johannesburg", to: "Pacific/Auckland", offsetChangeMinutes: 660, todayChangesNow: false, warning: "From now on days start in Pacific/Auckland. Nothing already approved is rewritten." }
const as = (perms: string[]) => useAuthStore.setState({ user: { permissions: perms } } as never)
function serve(view: unknown = VIEW, effect: unknown = EFFECT) {
  api.get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith("/preview") ? effect : view } }))
}
function show() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><CompanyZonePanel /></QueryClientProvider>)
}
const zoneField = () => screen.getByLabelText("New time zone") as HTMLInputElement
async function openAndType(z: string) {
  fireEvent.click(await screen.findByRole("button", { name: "Change" }))
  fireEvent.change(zoneField(), { target: { value: z } })
}

describe("CompanyZonePanel", () => {
  beforeEach(() => { api.get.mockReset(); api.put.mockReset(); as(["SECURITY_MANAGE"]) })
  afterEach(cleanup)

  it("shows the zone, its abbreviation and the local time", async () => {
    serve(); show()
    expect(await screen.findByText("Africa/Johannesburg")).toBeTruthy()
    expect(screen.getByText(/\(SAST\)/)).toBeTruthy()
    expect(screen.getByText(/22 Nov 2026 19:40 SAST/)).toBeTruthy()
    expect(api.get.mock.calls[0][0]).toBe("/api/v1/security/company-time-zone")
  })

  it("a reader cannot change it", async () => {
    as(["SECURITY_READ"]); serve(); show()
    await screen.findByText("Africa/Johannesburg")
    expect(screen.queryByRole("button", { name: "Change" })).toBeNull()
  })

  it("says so when the zone cannot be loaded or is malformed", async () => {
    api.get.mockRejectedValue(new Error("x")); show()
    expect((await screen.findByRole("alert")).textContent).toMatch(/could not be loaded/)
    cleanup(); api.get.mockReset(); api.get.mockResolvedValue({ data: { data: { nope: 1 } } }); show()
    expect((await screen.findByRole("alert")).textContent).toMatch(/could not be loaded/)
    expect(screen.queryByRole("button", { name: "Change" })).toBeNull()
  })

  it("will not preview an unusable zone", async () => {
    serve(); show(); await openAndType("Mars/Olympus")
    expect(screen.getByText(/not a known time zone/)).toBeTruthy()
    expect((screen.getByRole("button", { name: "Show what changes" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(zoneField(), { target: { value: "Africa/Johannesburg" } })
    expect(screen.getByText("The company is already on that time zone.")).toBeTruthy()
  })

  it("previews, needs the box ticked, then changes with confirm", async () => {
    serve(); api.put.mockResolvedValue({ data: {} }); show(); await openAndType("Pacific/Auckland")
    fireEvent.click(screen.getByRole("button", { name: "Show what changes" }))
    expect(await screen.findByText(/the clock moves 11 hours forward/)).toBeTruthy()
    expect(screen.getByText(/Nothing already approved is rewritten/)).toBeTruthy()
    expect(api.get).toHaveBeenCalledWith("/api/v1/security/company-time-zone/preview", { params: { timeZone: "Pacific/Auckland" } })
    const go = screen.getByRole("button", { name: "Change time zone" }) as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.click(screen.getByLabelText("I have read what this changes"))
    expect(go.disabled).toBe(false)
    fireEvent.click(go)
    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/api/v1/security/company-time-zone", { timeZone: "Pacific/Auckland", confirm: true }))
    expect((await screen.findByRole("status")).textContent).toMatch(/now on Pacific\/Auckland.*5 minutes/)
    expect(screen.queryByLabelText("New time zone")).toBeNull()
  })

  it("sends the zone trimmed, in the preview and in the change", async () => {
    serve(); api.put.mockResolvedValue({ data: {} }); show(); await openAndType("  Pacific/Auckland  ")
    fireEvent.click(screen.getByRole("button", { name: "Show what changes" }))
    await screen.findByText(/the clock moves/)
    expect(api.get).toHaveBeenCalledWith("/api/v1/security/company-time-zone/preview", { params: { timeZone: "Pacific/Auckland" } })
    fireEvent.click(screen.getByLabelText("I have read what this changes"))
    fireEvent.click(screen.getByRole("button", { name: "Change time zone" }))
    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/api/v1/security/company-time-zone", { timeZone: "Pacific/Auckland", confirm: true }))
  })

  it("never writes without a preview", async () => {
    serve(); show(); await openAndType("Pacific/Auckland")
    expect(screen.queryByRole("button", { name: "Change time zone" })).toBeNull()
    expect(api.put).not.toHaveBeenCalled()
  })

  it("editing the zone after a preview throws the preview away", async () => {
    serve(); show(); await openAndType("Pacific/Auckland")
    fireEvent.click(screen.getByRole("button", { name: "Show what changes" }))
    await screen.findByText(/the clock moves/)
    fireEvent.change(zoneField(), { target: { value: "UTC" } })
    expect(screen.queryByText(/the clock moves/)).toBeNull()
    expect(screen.queryByRole("button", { name: "Change time zone" })).toBeNull()
  })

  it("shows the server's reason when the change is refused, and stays open", async () => {
    serve(); api.put.mockRejectedValue({ response: { data: { message: "The company is already on that time zone." } } })
    show(); await openAndType("Pacific/Auckland")
    fireEvent.click(screen.getByRole("button", { name: "Show what changes" }))
    await screen.findByText(/the clock moves/)
    fireEvent.click(screen.getByLabelText("I have read what this changes"))
    fireEvent.click(screen.getByRole("button", { name: "Change time zone" }))
    expect((await screen.findByRole("alert")).textContent).toBe("The company is already on that time zone.")
    expect(screen.getByLabelText("New time zone")).toBeTruthy()
  })

  it("a preview that cannot be read changes nothing", async () => {
    serve(VIEW, { junk: true }); show(); await openAndType("Pacific/Auckland")
    fireEvent.click(screen.getByRole("button", { name: "Show what changes" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/Nothing was changed/)
    expect(screen.queryByRole("button", { name: "Change time zone" })).toBeNull()
  })

  it("cancel closes the form without writing", async () => {
    serve(); show(); await openAndType("Pacific/Auckland")
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }))
    expect(screen.queryByLabelText("New time zone")).toBeNull()
    expect(api.put).not.toHaveBeenCalled()
  })
})
