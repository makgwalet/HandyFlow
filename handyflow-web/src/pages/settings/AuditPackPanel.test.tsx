import { describe, it, expect, vi, afterEach, beforeEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { AuditPackPanel, periodProblem } from "./AuditPackPanel"

const api = vi.hoisted(() => ({ runs: vi.fn(), build: vi.fn(), verify: vi.fn() }))
vi.mock("../../api/auditPack.api", () => ({ auditPackApi: api }))
afterEach(cleanup)
beforeEach(() => { Object.values(api).forEach(f => f.mockReset()); api.runs.mockResolvedValue([]) })

const run = (o: any = {}) => ({ id: "r1", engagementId: null, from: "2025-03-01", to: "2026-02-28", generatedBy: "u", generatedAt: "2026-10-15T08:00:00Z", packSha256: "a".repeat(64), fileCount: 9, ...o })

describe("period rules", () => {
  it("says what is wrong with a period", () => {
    expect(periodProblem("", "")).toMatch(/Choose/)
    expect(periodProblem("2026-02-01", "2026-01-01")).toMatch(/end on or after/)
    expect(periodProblem("2022-01-01", "2026-01-01")).toMatch(/two years/)
    expect(periodProblem("2025-03-01", "2026-02-28")).toBeNull()
  })
})

describe("AuditPackPanel", () => {
  it("builds only for a sensible period", async () => {
    api.build.mockResolvedValue({ runId: "r2", sha256: "x" })
    render(<AuditPackPanel />)
    const build = (await screen.findByText("Build and download")) as HTMLButtonElement
    expect(build.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Pack period from"), { target: { value: "2025-03-01" } })
    fireEvent.change(screen.getByLabelText("Pack period to"), { target: { value: "2026-02-28" } })
    expect(build.disabled).toBe(false)
    fireEvent.click(build)
    await waitFor(() => expect(api.build).toHaveBeenCalledWith("2025-03-01", "2026-02-28", undefined))
  })

  it("fills in the period from the chosen engagement and passes it on", async () => {
    api.build.mockResolvedValue({ runId: "r2", sha256: "x" })
    render(<AuditPackPanel engagements={[{ id: "e1", name: "FY2026 audit", periodFrom: "2025-03-01", periodTo: "2026-02-28" }]} />)
    fireEvent.change(await screen.findByLabelText("Pack engagement"), { target: { value: "e1" } })
    expect((screen.getByLabelText("Pack period from") as HTMLInputElement).value).toBe("2025-03-01")
    fireEvent.click(screen.getByText("Build and download"))
    await waitFor(() => expect(api.build).toHaveBeenCalledWith("2025-03-01", "2026-02-28", "e1"))
  })

  it("tells the owner when an earlier pack no longer matches, and which files differ", async () => {
    api.runs.mockResolvedValue([run()])
    api.verify.mockResolvedValue({ matches: false, storedSha256: "a", currentSha256: "b", changedFiles: ["03_journals.csv"] })
    render(<AuditPackPanel />)
    fireEvent.click(await screen.findByText("Check it still matches"))
    expect(await screen.findByText(/books have changed since this pack was built/)).toBeTruthy()
    expect(screen.getByText(/03_journals\.csv/)).toBeTruthy()
  })

  it("says so when it still matches", async () => {
    api.runs.mockResolvedValue([run()])
    api.verify.mockResolvedValue({ matches: true, storedSha256: "a", currentSha256: "a", changedFiles: [] })
    render(<AuditPackPanel />)
    fireEvent.click(await screen.findByText("Check it still matches"))
    expect(await screen.findByText("Still matches the books.")).toBeTruthy()
  })

  it("shows the server's reason when building fails", async () => {
    api.build.mockRejectedValue({ response: { data: { message: "A pack covers at most two years" } } })
    render(<AuditPackPanel />)
    fireEvent.change(await screen.findByLabelText("Pack period from"), { target: { value: "2025-03-01" } })
    fireEvent.change(screen.getByLabelText("Pack period to"), { target: { value: "2026-02-28" } })
    fireEvent.click(screen.getByText("Build and download"))
    expect(await screen.findByRole("alert")).toBeTruthy()
  })
})
