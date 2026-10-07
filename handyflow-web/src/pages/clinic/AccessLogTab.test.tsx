import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
import AccessLogTab, { matches } from "./AccessLogTab"

const rows = [
  { id: "1", user_name: "Thandi Nkosi", user_id: "u1", patient_name: "Ann Smith", patient_id: "p1", resource_type: "PATIENT", http_method: "GET", ip_address: "10.0.0.1", accessed_at: "2026-10-07T08:00:00Z" },
  { id: "2", user_name: null, user_id: "u2", patient_name: null, patient_id: "p2", resource_type: "LAB_RESULT", http_method: "GET", impersonated: true, accessed_at: "2026-10-07T07:00:00Z" },
]
const show = () => render(<QueryClientProvider client={new QueryClient()}><AccessLogTab /></QueryClientProvider>)
beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: { data: rows } }) })
afterEach(cleanup)

describe("AccessLogTab", () => {
  it("shows who opened what, with fallbacks for people who no longer resolve", async () => {
    show()
    expect(await screen.findByText("Thandi Nkosi")).toBeTruthy()
    expect(screen.getByText("Ann Smith")).toBeTruthy()
    expect(screen.getByText(/Unknown user/)).toBeTruthy()
    expect(screen.getByText(/impersonated/)).toBeTruthy()
    expect(screen.getByText("Unknown patient")).toBeTruthy()
    expect(screen.getByText("Lab result")).toBeTruthy()
  })
  it("filters by name and asks the server for the chosen number of entries", async () => {
    show()
    await screen.findByText("Thandi Nkosi")
    fireEvent.change(screen.getByLabelText("Filter by name"), { target: { value: "smith" } })
    expect(screen.queryByText("Lab result")).toBeNull()
    fireEvent.change(screen.getByLabelText("How many entries"), { target: { value: "500" } })
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/clinic/access-log?limit=500"))
  })
  it("explains a refusal instead of showing an empty log", async () => {
    get.mockImplementation(() => Promise.reject(new Error("403")))
    show()
    expect(await screen.findByRole("alert")).toBeTruthy()
  })
})
describe("matches", () => {
  it("is case-insensitive and treats a blank filter as everything", () => {
    expect(matches(rows[0] as any, "ANN")).toBe(true)
    expect(matches(rows[0] as any, "zzz")).toBe(false)
    expect(matches(rows[1] as any, "  ")).toBe(true)
  })
})
