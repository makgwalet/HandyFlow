import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import LifecyclePanel from "./LifecyclePanel"
import { suggestedEvidenceType, theftEditBody, theftEditProblem, toTheftEdit, toWriteOffEdit, writeOffEditBody, writeOffEditProblem, writeOffSummary } from "./lifecycle.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a), put: (...a: Args) => put(...a), delete: (...a: Args) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const theft = { kind: "STOLEN", occurredAt: "2026-10-01T08:00:00Z", location: "Sandton", policeCaseNumber: "CAS 1/10/2026", sapsStation: "Sandton SAPS", policeReference: null, insurer: "Santam", claimNumber: null, trackingInfo: "Last ping N1", notes: null, recoveryStatus: null }
const writeOff = { assessmentDate: "2026-10-05", assessorName: "Pieter", decisionDate: "2026-10-06", decisionNote: null, preAccidentValue: 250000, salvageValue: 40000, salvageBuyer: null, salvageSoldAt: null, insuranceSettlementAmount: null, insuranceSettledAt: null }
const evidence = [{ id: "x1", type: "POLICE_REPORT", description: "Case docket", fileName: "docket.pdf", sizeBytes: 120000, uploadedAt: "2026-10-02T08:00:00Z" }]
const life = (status: string, events: object[]) => ({ vehicleId: "v1", registration: "ABC123MP", lifecycleStatus: status, operationalStatus: "UNAVAILABLE", events })
const theftEvent = { id: "e1", eventType: "THEFT_REPORTED", fromStatus: "ACTIVE", toStatus: "STOLEN", occurredAt: "2026-10-01T08:00:00Z", reason: null, disposal: null, theft, writeOff: null, evidence }
const writeOffEvent = { id: "e2", eventType: "WRITE_OFF", fromStatus: "ACTIVE", toStatus: "WRITTEN_OFF", occurredAt: "2026-10-06T08:00:00Z", reason: null, disposal: null, theft: null, writeOff, evidence: [] }
let state = life("STOLEN", [theftEvent])
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><LifecyclePanel vehicleId="v1" /></QueryClientProvider>)
const set = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } })

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE", "FLEET_ADMIN"]); state = life("STOLEN", [theftEvent]); for (const m of [get, post, put, del]) m.mockReset()
  put.mockResolvedValue({ data: { data: {} } }); post.mockResolvedValue({ data: { data: {} } }); del.mockResolvedValue({ data: { data: null } })
  get.mockImplementation((url: string) => url.includes("/download") ? Promise.resolve({ data: new Blob(["x"]) }) : ok(state))
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {}); Object.assign(URL, { createObjectURL: vi.fn(() => "blob:x"), revokeObjectURL: vi.fn() })
})
afterEach(() => { cleanup(); vi.restoreAllMocks() })

describe("edit rules", () => {
  it("round-trips the theft report and clears blanks with null", () => {
    const f = toTheftEdit(theft)
    expect(f.policeCaseNumber).toBe("CAS 1/10/2026"); expect(f.claimNumber).toBe("")
    expect(theftEditBody({ ...f, notes: "  Docket handed to insurer " })).toMatchObject({ location: "Sandton", claimNumber: null, notes: "Docket handed to insurer", trackingInfo: "Last ping N1" })
  })
  it("will not half-fill a claim or a case", () => {
    const f = toTheftEdit(theft)
    expect(theftEditProblem(f)).toBeNull()
    expect(theftEditProblem({ ...f, claimNumber: "CL-9", insurer: "" })).toMatch(/insurer/); expect(theftEditProblem({ ...f, sapsStation: "" })).toMatch(/station/)
  })
  it("checks the write-off figures and dates", () => {
    const f = toWriteOffEdit(writeOff)
    expect(writeOffEditProblem(f)).toBeNull(); expect(writeOffEditProblem({ ...f, decisionDate: "" })).toMatch(/decided/)
    expect(writeOffEditProblem({ ...f, salvageValue: "-1" })).toMatch(/Salvage value/); expect(writeOffEditProblem({ ...f, salvageValue: "", salvageSoldAt: "2026-10-10" })).toMatch(/salvage sale date/)
    expect(writeOffEditProblem({ ...f, insuranceSettledAt: "2026-10-20" })).toMatch(/settlement amount/); expect(writeOffEditProblem({ ...f, salvageBuyer: "Scrap Co", salvageValue: "" })).toMatch(/salvage value/)
    expect(writeOffEditBody(f)).toMatchObject({ decisionDate: "2026-10-06", preAccidentValue: 250000, salvageValue: 40000, salvageBuyer: null, insuranceSettlementAmount: null })
    expect(writeOffSummary(writeOff).replace(/\s/g, " ")).toMatch(/^Pre-accident R 250.000 · Salvage R 40.000 · Settlement —$/)
  })
  it("suggests the evidence usually wanted for each event", () => {
    expect(["THEFT_REPORTED", "WRITE_OFF", "DISPOSAL", "RECOVERED", "STATUS_CHANGE"].map(suggestedEvidenceType)).toEqual(["POLICE_REPORT", "ASSESSOR_REPORT", "SALE_AGREEMENT", "PHOTO", ""])
  })
})

describe("Lifecycle edits", () => {
  it("edits the open theft report with every detail and saves it", async () => {
    show(); fireEvent.click(await screen.findByText("Edit theft report"))
    expect((screen.getByLabelText("SAPS case number") as HTMLInputElement).value).toBe("CAS 1/10/2026")
    set("Claim number", "CL-9"); set("Notes", "Docket handed to Santam"); fireEvent.click(screen.getByText("Save report"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/vehicles/v1/lifecycle/theft", expect.objectContaining({ claimNumber: "CL-9", notes: "Docket handed to Santam", insurer: "Santam", policeReference: null })))
  })
  it("blocks a half-filled claim and shows a server refusal", async () => {
    put.mockRejectedValue({ response: { data: { message: "No open theft report" } } })
    show(); fireEvent.click(await screen.findByText("Edit theft report")); set("Insurer", ""); set("Claim number", "CL-9")
    expect((screen.getByText("Save report") as HTMLButtonElement).disabled).toBe(true); set("Insurer", "Santam"); fireEvent.click(screen.getByText("Save report"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/No open theft report/)
  })
  it("offers no theft edit once it is recovered", async () => {
    state = life("ACTIVE", [{ ...theftEvent, theft: { ...theft, recoveryStatus: "RECOVERED" } }]); show(); await screen.findByText(/Theft reported/); expect(screen.queryByText("Edit theft report")).toBeNull()
  })
  it("edits the write-off for admins only", async () => {
    state = life("WRITTEN_OFF", [writeOffEvent]); show(); fireEvent.click(await screen.findByText("Edit write-off"))
    set("Insurance settlement (R)", "180000"); set("Settled on", "2026-10-15"); fireEvent.click(screen.getByText("Save write-off"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/vehicles/v1/lifecycle/write-off", expect.objectContaining({ insuranceSettlementAmount: 180000, insuranceSettledAt: "2026-10-15", salvageValue: 40000, assessorName: "Pieter" })))
    cleanup(); perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); show(); await screen.findByText(/assessor Pieter/); expect(screen.queryByText("Edit write-off")).toBeNull()
  })
  it("shows the police and insurer details and the write-off figures in the history", async () => {
    show(); expect((await screen.findByText(/SAPS CAS 1\/10\/2026 \(Sandton SAPS\)/)).textContent).toContain("Santam")
    cleanup(); state = life("WRITTEN_OFF", [writeOffEvent]); show(); expect(await screen.findByText(/Pre-accident R.*250.*Salvage R.*40.*assessor Pieter/)).toBeTruthy()
  })
})

describe("Evidence on events", () => {
  it("lists, downloads and uploads evidence with the type pre-selected", async () => {
    show(); expect((await screen.findByLabelText("docket.pdf")).textContent).toContain("Police report")
    fireEvent.click(screen.getByLabelText("Download docket.pdf")); await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/fleet/vehicles/v1/lifecycle/events/e1/evidence/x1/download", { responseType: "blob" }))
    expect((screen.getByLabelText("Evidence type") as HTMLSelectElement).value).toBe("POLICE_REPORT")
    fireEvent.change(screen.getByLabelText("Evidence file"), { target: { files: [new File([new Uint8Array(500)], "saps.pdf", { type: "application/pdf" })] } }); fireEvent.click(screen.getByText("Attach"))
    await waitFor(() => expect(post).toHaveBeenCalled()); const [url, body] = post.mock.calls[0] as [string, FormData]
    expect(url).toBe("/api/v1/fleet/vehicles/v1/lifecycle/events/e1/evidence"); expect(body.get("type")).toBe("POLICE_REPORT")
  })
  it("rejects a bad file before upload, and deletes only for admins after a confirm", async () => {
    show(); await screen.findByLabelText("docket.pdf")
    fireEvent.change(screen.getByLabelText("Evidence file"), { target: { files: [new File(["x"], "x.zip", { type: "application/zip" })] } }); expect((screen.getByText("Attach") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByLabelText("Delete docket.pdf")); expect(del).not.toHaveBeenCalled(); fireEvent.click(screen.getByLabelText("Confirm delete docket.pdf"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/fleet/vehicles/v1/lifecycle/events/e1/evidence/x1"))
    cleanup(); perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); show(); await screen.findByLabelText("docket.pdf"); expect(screen.queryByLabelText("Delete docket.pdf")).toBeNull()
  })
  it("read-only users see and download files but cannot attach", async () => {
    perms = new Set(["FLEET_READ"]); show(); await screen.findByLabelText("docket.pdf"); expect(screen.queryByText("Attach")).toBeNull(); expect(screen.getByLabelText("Download docket.pdf")).toBeTruthy()
  })
})
