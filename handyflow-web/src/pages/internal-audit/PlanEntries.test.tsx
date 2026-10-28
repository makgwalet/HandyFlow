import { describe, it, expect, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import { PlanEntries } from "./PlanEntries"
import { EngagementFilters } from "./EngagementFilters"
import type { Engagement, PlanEntry } from "./auditShared"

afterEach(cleanup)
const e = (o: Partial<PlanEntry>): PlanEntry => ({ id: Math.random().toString(), universeEntryId: "u", universeEntryName: "Procurement", riskAssessmentId: null, riskLevel: "HIGH", plannedQuarter: 1, rationale: null, status: "PLANNED", createdAt: "", ...o })

describe("Plan entries", () => {
  it("summarises the plan and lists its entries", () => {
    render(<PlanEntries entries={[e({ status: "COMPLETED" }), e({ universeEntryName: "Stores", plannedQuarter: null }), e({ status: "DEFERRED" })]} />)
    expect(screen.getAllByText("Procurement", { selector: "td" })).toHaveLength(2)
    expect(screen.getByLabelText("Plan completed").getAttribute("aria-valuenow")).toBe("50")
    expect(screen.getByText("No quarter: 1")).toBeTruthy()
  })
  it("filters by text, risk and status, and says when nothing matches", () => {
    render(<PlanEntries entries={[e({ universeEntryName: "Payroll", riskLevel: "LOW" }), e({ universeEntryName: "Stores", status: "COMPLETED" })]} />)
    fireEvent.change(screen.getByLabelText("Search plan entries"), { target: { value: "pay" } })
    expect(screen.queryByText("Stores")).toBeNull()
    fireEvent.change(screen.getByLabelText("Risk level"), { target: { value: "HIGH" } })
    expect(screen.getByText("No entries match these filters.")).toBeTruthy()
  })
  it("says so when the plan has no entries", () => {
    render(<PlanEntries entries={[]} />)
    expect(screen.getByText("No universe entries selected into this plan yet.")).toBeTruthy()
  })
})

describe("Engagement filters", () => {
  it("counts engagements by stage and reports the choices", () => {
    const engs = [{ status: "CLOSED" }, { status: "FIELDWORK" }, { status: "REPORTING" }, { status: "PLANNING" }] as unknown as Engagement[]
    const seen: string[] = []
    render(<EngagementFilters engagements={engs} stage="all" onStage={g => seen.push(g)} q="" onQ={() => {}} />)
    expect(screen.getByText("In progress").nextSibling?.textContent).toBe("2")
    fireEvent.change(screen.getByLabelText("Engagement status"), { target: { value: "completed" } })
    expect(seen).toEqual(["completed"])
  })
})
