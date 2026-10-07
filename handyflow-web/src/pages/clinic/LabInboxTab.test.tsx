import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn()
let canWrite = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => canWrite }))
import LabInboxTab, { markersProblem, toMarkerRequest, rowsFromJson, blankMarker } from "./LabInboxTab"

const row = (o: Partial<ReturnType<typeof blankMarker>>) => ({ ...blankMarker(), ...o })
const MATCHED = { id: "l1", patientId: "p1", patientNameRaw: "Nkosi S", labReference: "AMP-1", source: "AMPATH", status: "UNREVIEWED", receivedAt: "2026-10-07T07:00:00Z", parsedMarkersJson: null, hasCritical: false, hasAbnormal: false }
const UNMATCHED = { ...MATCHED, id: "l2", patientId: null, labReference: "AMP-2" }
const CRIT = { id: "l1", patientName: "Sam Nkosi", labReference: "AMP-1", receivedAt: "2026-10-07T07:00:00Z", criticalMarkers: "Potassium" }
let results: any[] = []; let critical: any[] = []
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><LabInboxTab /></QueryClientProvider>)

beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); canWrite = true; results = [MATCHED, UNMATCHED]; critical = []
  get.mockImplementation((url: string) => Promise.resolve({ data: { data:
    url.endsWith("/lab/critical") ? critical : url.endsWith("/lab/results") ? results : [{ id: "p9", fullName: "Sam Nkosi" }] } }))
  put.mockResolvedValue({ data: {} }); post.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("form helpers", () => {
  it("asks for a name and a result, numbers for limits, and sensible limits", () => {
    expect(markersProblem([row({ marker: "", value: "4" })])).toMatch(/Line 1: enter the test name/)
    expect(markersProblem([row({ marker: "K", value: "" })])).toMatch(/enter the result for K/)
    expect(markersProblem([row({ marker: "K", value: "4", refLow: "abc" })])).toMatch(/must be numbers/)
    expect(markersProblem([row({ marker: "K", value: "4", refLow: "5", refHigh: "3" })])).toMatch(/lower reference limit is above/)
    expect(markersProblem([row({ marker: "K", value: "4", refLow: "3.5", criticalLow: "4" })])).toMatch(/lower critical limit is above/)
    expect(markersProblem([row({ marker: "K", value: "4", refHigh: "5", criticalHigh: "4" })])).toMatch(/upper critical limit is below/)
    expect(markersProblem([row({ marker: "K", value: "4", criticalLow: "6", criticalHigh: "6" })])).toMatch(/overlap/)
    expect(markersProblem([row({ marker: "K", value: "4", refLow: "3,5", refHigh: "5.1" })])).toBeNull()
    expect(markersProblem([])).toBeNull()
  })

  it("sends numbers as numbers and blanks as null", () => {
    const body = toMarkerRequest([row({ marker: " K ", value: " 6,8 ", unit: "", refLow: "3,5", refHigh: "5.1", criticalHigh: "6.5", flag: "" })])
    expect(body).toEqual({ markers: [{ marker: "K", value: "6,8", unit: null, refLow: 3.5, refHigh: 5.1, criticalLow: null, criticalHigh: 6.5, flag: null }] })
  })

  it("reads stored markers back, keeping only a clinician's escalation as the flag", () => {
    const rows = rowsFromJson(JSON.stringify([{ marker: "K", value: "6.8", unit: "mmol/L", flag: "CRITICAL", refLow: 3.5, refHigh: 5.1, criticalLow: null, criticalHigh: 6.5 },
      { marker: "Na", value: "150", flag: "HIGH", refLow: 135, refHigh: 145 }]))
    expect(rows[0]).toMatchObject({ marker: "K", refLow: "3.5", criticalHigh: "6.5", criticalLow: "", flag: "CRITICAL" })
    expect(rows[1].flag).toBe("")        // HIGH was worked out from the limits, not typed
    expect(rowsFromJson("not json")).toEqual([])
    expect(rowsFromJson(null)).toEqual([])
  })
})

describe("LabInboxTab", () => {
  it("lists results and asks for the unreviewed ones by default", async () => {
    show()
    expect(await screen.findByText("AMP-1")).toBeTruthy()
    expect(screen.getByText("AMP-2")).toBeTruthy()
    expect(get.mock.calls.some(c => String(c[0]).endsWith("/lab/results") && c[1]?.params?.status === "UNREVIEWED")).toBe(true)
  })

  it("shows a banner naming each critical result waiting for review", async () => {
    critical = [CRIT]
    show()
    const banner = await screen.findByText(/1 critical result waiting for review/)
    expect(banner).toBeTruthy()
    expect(screen.getByText(/Potassium/)).toBeTruthy()
    expect(screen.getByText("Sam Nkosi")).toBeTruthy()
  })

  it("saves typed results and sends the lab's limits", async () => {
    show(); await screen.findByText("AMP-1")
    fireEvent.click(screen.getAllByText("Enter results")[0])
    fireEvent.change(screen.getByLabelText("Test 1"), { target: { value: "Potassium" } })
    fireEvent.change(screen.getByLabelText("Result 1"), { target: { value: "6.8" } })
    fireEvent.change(screen.getByLabelText("Ref low 1"), { target: { value: "3.5" } })
    fireEvent.change(screen.getByLabelText("Ref high 1"), { target: { value: "5.1" } })
    fireEvent.change(screen.getByLabelText("Critical high 1"), { target: { value: "6.5" } })
    fireEvent.click(screen.getByText("Save results"))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toBe("/api/v1/clinic/lab/results/l1/markers")
    expect(put.mock.calls[0][1].markers[0]).toMatchObject({ marker: "Potassium", value: "6.8", refLow: 3.5, refHigh: 5.1, criticalHigh: 6.5, criticalLow: null })
  })

  it("does not send a line with no result", async () => {
    show(); await screen.findByText("AMP-1")
    fireEvent.click(screen.getAllByText("Enter results")[0])
    fireEvent.change(screen.getByLabelText("Test 1"), { target: { value: "Potassium" } })
    fireEvent.click(screen.getByText("Save results"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/enter the result for Potassium/)
    expect(put).not.toHaveBeenCalled()
  })

  it("shows the server's reason when saving fails", async () => {
    put.mockRejectedValueOnce({ response: { data: { message: "A result that has been reviewed can no longer be changed" } } })
    show(); await screen.findByText("AMP-1")
    fireEvent.click(screen.getAllByText("Enter results")[0])
    fireEvent.change(screen.getByLabelText("Test 1"), { target: { value: "K" } })
    fireEvent.change(screen.getByLabelText("Result 1"), { target: { value: "4" } })
    fireEvent.click(screen.getByText("Save results"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/can no longer be changed/)
  })

  it("matches an unmatched result to a patient found by name", async () => {
    show(); await screen.findByText("AMP-2")
    fireEvent.click(screen.getByText("Match patient"))
    fireEvent.change(screen.getByLabelText("Search patients"), { target: { value: "Sam" } })
    fireEvent.click(await screen.findByText("Match to this patient", {}, { timeout: 2000 }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/lab/results/l2/match-patient", null, { params: { patientId: "p9" } }))
  })

  it("only lets a matched result be marked reviewed", async () => {
    show(); await screen.findByText("AMP-1")
    const buttons = screen.getAllByText("Mark reviewed") as HTMLButtonElement[]
    expect(buttons[0].disabled).toBe(false)   // AMP-1 is matched
    expect(buttons[1].disabled).toBe(true)    // AMP-2 is not
    fireEvent.click(buttons[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/lab/results/l1/review"))
  })

  it("is read-only without lab write permission", async () => {
    canWrite = false
    show(); await screen.findByText("AMP-1")
    expect(screen.queryByText("Enter results")).toBeNull()
    expect(screen.queryByText("Mark reviewed")).toBeNull()
    expect(screen.queryByText("Match patient")).toBeNull()
  })
})
