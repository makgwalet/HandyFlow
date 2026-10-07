import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
import PatientSummaryPrint, { esc, summaryHtml } from "./PatientSummaryPrint"

const data = () => ({
  patient: { fullName: "Ann <b>Smith</b>", dateOfBirth: "1980-01-02", patientNumber: "P000001" },
  allergies: [{ allergen: "Penicillin", severity: "SEVERE", reaction: "Rash", status: "ACTIVE" }, { allergen: "Latex", status: "RESOLVED" }],
  conditions: [{ conditionName: "Asthma", icd10Code: "J45", status: "ACTIVE" }],
  medications: [{ medicineName: "Ventolin", dose: "100mcg", status: "ACTIVE" }, { medicineName: "Old", status: "STOPPED" }],
  notes: [{ id: "a", kind: "ALERT", severity: "CRITICAL", body: "<script>x</script>" }, { id: "b", kind: "NOTE", body: "done", resolvedAt: "2026-01-01" }],
  observations: [{ id: "o", code: "WEIGHT", label: "Weight", unit: "kg", value: 80, takenAt: "2026-10-05T09:00:00Z" }],
  printedAt: new Date("2026-10-07T10:00:00Z"),
} as any)

afterEach(cleanup)
describe("summaryHtml", () => {
  it("escapes free text, so a note cannot inject markup", () => {
    const h = summaryHtml(data())
    expect(h).not.toContain("<script>")
    expect(h).toContain("&lt;script&gt;x&lt;/script&gt;")
    expect(h).toContain("Ann &lt;b&gt;Smith&lt;/b&gt;")
    expect(esc(`a&"'`)).toBe("a&amp;&quot;&#39;")
  })
  it("prints only active items and open notes, plus the latest measurements", () => {
    const h = summaryHtml(data())
    expect(h).toContain("Penicillin"); expect(h).not.toContain("Latex")
    expect(h).toContain("Ventolin"); expect(h).not.toContain("Old")
    expect(h).toContain("Asthma"); expect(h).toContain("[J45]")
    expect(h).not.toContain("done")
    expect(h).toContain("Weight: 80 kg")
  })
  it("says so when a section is empty", () => {
    const h = summaryHtml({ ...data(), allergies: [], notes: [] })
    expect(h).toContain("None recorded"); expect(h).toContain("None open")
  })
})

describe("PatientSummaryPrint", () => {
  let win: any
  beforeEach(() => {
    get.mockReset(); get.mockResolvedValue({ data: { data: [] } })
    win = { document: { open: vi.fn(), write: vi.fn(), close: vi.fn() }, focus: vi.fn(), print: vi.fn(), close: vi.fn() }
    vi.stubGlobal("open", vi.fn(() => win))
  })
  it("reads the record fresh and prints it", async () => {
    render(<PatientSummaryPrint patient={{ id: "p1", fullName: "Ann" }} />)
    fireEvent.click(screen.getByText("Print summary"))
    await waitFor(() => expect(win.print).toHaveBeenCalled())
    expect(get).toHaveBeenCalledTimes(5)
    expect(win.document.write.mock.calls[0][0]).toContain("Ann")
  })
  it("prints nothing and says so when the record cannot be loaded", async () => {
    get.mockImplementation(() => Promise.reject(new Error("x")))
    render(<PatientSummaryPrint patient={{ id: "p1", fullName: "Ann" }} />)
    fireEvent.click(screen.getByText("Print summary"))
    expect(await screen.findByText(/nothing was printed/)).toBeTruthy()
    expect(win.print).not.toHaveBeenCalled(); expect(win.close).toHaveBeenCalled()
  })
  it("explains a blocked pop-up window", async () => {
    vi.stubGlobal("open", vi.fn(() => null))
    render(<PatientSummaryPrint patient={{ id: "p1", fullName: "Ann" }} />)
    fireEvent.click(screen.getByText("Print summary"))
    expect(await screen.findByText(/blocked the print window/)).toBeTruthy()
  })
})
