import { describe, it, expect, vi, afterEach, beforeEach } from "vitest"
import { render, screen, fireEvent, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"

vi.mock("../../api/auditEngagementSummary.api", () => ({ auditEngagementApi: { summary: vi.fn(), folders: vi.fn(), workpapers: vi.fn(), download: vi.fn() } }))
vi.mock("./ReviewNotesPanel", () => ({ default: ({ workpaperId }: { workpaperId: string }) => <div>notes for {workpaperId}</div> }))
vi.mock("./RemediationSection", () => ({ RemediationSection: ({ engagementId }: { engagementId?: string }) => <div>remediation for {engagementId}</div> }))
import { auditEngagementApi } from "../../api/auditEngagementSummary.api"
import { useAuthStore } from "../../store/auth.store"
import { EngagementDetail } from "./EngagementDetail"

const m = auditEngagementApi as unknown as Record<"summary" | "folders" | "workpapers" | "download", ReturnType<typeof vi.fn>>
afterEach(cleanup)
beforeEach(() => { vi.clearAllMocks(); useAuthStore.setState({ user: null }) })

const eng = {
  id: "e1", name: "Payroll audit", status: "FIELDWORK", startDate: "2026-10-01", endDate: "2026-12-01", universeEntryName: "Payroll",
  objectives: "Test pay accuracy", scope: null, auditCriteria: "Policy 4", overallMateriality: 1250000, performanceMateriality: null, clearlyTrivialThreshold: 5000,
  specificMateriality: [{ id: "s1", accountOrGlSegment: "Wages", threshold: 40000 }],
  assignments: [{ id: "a1", userId: "u1", userName: "Sarah Johnson", role: "HEAD_OF_INTERNAL_AUDIT", assignedBy: null, assignedAt: "2026-10-01" }],
} as never
const summary = { overallPct: 55, phases: [{ key: "planning", label: "Planning", pct: 100, done: 5, total: 5, basis: "5 of 5 set" }], milestones: [{ key: "s", label: "Start", date: "2026-10-01", status: "DONE" }],
  counts: { workpapers: 2, workpapersSignedOff: 1, findings: 1, openFindings: 1, findingsPastDue: 0, actions: 0, actionsValidated: 0, actionsOverdue: 0, team: 1 }, reportStatus: null }
const wp = (o: object) => ({ id: "w1", folderId: "f1", fileName: "Wage test.xlsx", mimeType: null, fileSizeBytes: 2048, reviewStatus: "PREPARED", preparedBy: "u1", preparedAt: "2026-10-20T08:00:00Z", reviewedBy: null, reviewedAt: null, signedOffBy: null, signedOffAt: null, versionNumber: 1, supersededBy: null, createdAt: "2026-10-20T08:00:00Z", ...o })

const mount = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter>
  <EngagementDetail engagement={eng} users={[{ id: "u1", firstName: "Sarah", lastName: "Johnson", email: "s@x.co" }]} onBack={() => {}} /></MemoryRouter></QueryClientProvider>)

describe("Engagement detail", () => {
  beforeEach(() => { m.summary.mockResolvedValue(summary); m.folders.mockResolvedValue([{ id: "f1", name: "Fieldwork" }]); m.workpapers.mockResolvedValue([wp({}), wp({ id: "w2", fileName: "Old.xlsx", supersededBy: "w1" })]) })

  it("shows the overview figures and milestones", async () => {
    mount()
    expect(await screen.findByText("55%")).toBeTruthy()
    expect(screen.getByText("1 of 2")).toBeTruthy()
    expect(screen.getByText("Completed")).toBeTruthy()
  })

  it("shows planning with what is set and says so for what is not", async () => {
    mount()
    fireEvent.click(await screen.findByRole("tab", { name: "Planning" }))
    expect(screen.getByText("Test pay accuracy")).toBeTruthy()
    expect(screen.getByText("Policy 4")).toBeTruthy()
    expect(screen.getAllByText("Not set").length).toBeGreaterThan(0)
    expect(screen.getByText(/Wages/)).toBeTruthy()
  })

  it("lists the team with readable roles", async () => {
    mount()
    fireEvent.click(await screen.findByRole("tab", { name: "Team" }))
    expect(screen.getByText("Sarah Johnson")).toBeTruthy()
    expect(screen.getByText("Head of internal audit")).toBeTruthy()
  })

  it("loads all workpapers in one call, hides replaced versions, and opens one with its comments and download", async () => {
    mount()
    fireEvent.click(await screen.findByRole("tab", { name: "Workpapers" }))
    fireEvent.click(await screen.findByText("Wage test.xlsx"))
    expect(screen.queryByText("Old.xlsx")).toBeNull()
    expect(screen.getByText("notes for w1")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Download Wage test.xlsx" }))
    expect(m.download).toHaveBeenCalledWith("w1", "Wage test.xlsx")
    expect(m.workpapers).toHaveBeenCalledTimes(1)
  })

  it("offers upload only to people who can manage audits", async () => {
    mount()
    fireEvent.click(await screen.findByRole("tab", { name: "Workpapers" }))
    await screen.findByText("Wage test.xlsx")
    expect(screen.queryByLabelText("Upload workpaper")).toBeNull()
    cleanup(); useAuthStore.setState({ user: { permissions: ["AUDIT_MANAGE"] } as never })
    mount()
    fireEvent.click(await screen.findByRole("tab", { name: "Workpapers" }))
    expect(await screen.findByLabelText("Upload workpaper")).toBeTruthy()
  })

  it("passes the engagement to the findings tab so only its findings load", async () => {
    mount()
    fireEvent.click(await screen.findByRole("tab", { name: "Findings & actions" }))
    expect(within(document.body).getByText("remediation for e1")).toBeTruthy()
  })
})
