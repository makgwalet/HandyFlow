import { describe, it, expect } from "vitest"
import { dockCandidate, dockStages, isWithOtherClinician, lifecycleLabel, nextUp, startSteps, toNotes, workspacePath, type ConsultationLike, type QueueAppt } from "./workspace"

const n = { chiefComplaint: "", history: "", examination: "", diagnosis: "", icd10Codes: "", treatmentPlan: "" }

describe("startSteps", () => {
  it("brings an appointment to in-progress with the fewest calls", () => {
    expect(startSteps("IN_PROGRESS")).toEqual([])
    expect(startSteps("TRIAGED")).toEqual(["start"]); expect(startSteps("CHECKED_IN")).toEqual(["start"]); expect(startSteps("CONFIRMED")).toEqual(["start"])
    expect(startSteps("SCHEDULED")).toEqual(["check_in", "start"])
  })
  it("refuses finished appointments", () => { for (const s of ["COMPLETED", "CANCELLED", "NO_SHOW"]) expect(startSteps(s)).toBeNull() })
  it("has a route per appointment", () => expect(workspacePath("a1")).toBe("/clinic/consult/a1"))
})

describe("lifecycleLabel", () => {
  it("moves from Draft to Ready to sign", () => {
    expect(lifecycleLabel("DRAFT", n)).toBe("Draft")
    expect(lifecycleLabel("DRAFT", { ...n, chiefComplaint: "Cough" })).toBe("In progress")
    expect(lifecycleLabel("DRAFT", { ...n, chiefComplaint: "Cough", diagnosis: "URTI" })).toBe("Ready to sign")
  })
  it("names the handoff states", () => {
    expect(lifecycleLabel("READY_FOR_DOCTOR", n)).toBe("Waiting for the doctor")
    expect(lifecycleLabel("DOCTOR_REVIEWING", n)).toBe("Doctor reviewing")
    expect(isWithOtherClinician("READY_FOR_DOCTOR")).toBe(true)
    expect(isWithOtherClinician("DOCTOR_REVIEWING")).toBe(false)
  })
})

const c = (o: Partial<ConsultationLike> = {}): ConsultationLike => ({ id: "c1", patientId: "p1", status: "DRAFT", appointmentId: "a1", ...o })
describe("dock", () => {
  it("turns server numbers and nulls into notes", () => {
    expect(toNotes(c({ weightKg: 70, followUpDays: 7, icd10Codes: ["J06.9", "R05"], history: null })))
      .toMatchObject({ weightKg: "70", followUpDays: "7", icd10Codes: "J06.9, R05", history: "" })
  })
  it("shows where the doctor stopped", () => {
    const st = dockStages(c({ chiefComplaint: "Cough", history: "3 days", pulseBpm: 72 }))
    expect(st.map(s => `${s.label}:${s.state}`)).toEqual(["Symptoms:done", "Exam:done", "Assessment:todo", "Plan:todo"])
  })
  it("picks the most recent consultation that can be reopened", () => {
    expect(dockCandidate([])).toBeNull()
    const list = [{ ...c({ id: "old" }), updatedAt: "2026-10-08T07:00:00Z" }, { ...c({ id: "new" }), updatedAt: "2026-10-08T09:00:00Z" }, { ...c({ id: "none", appointmentId: null }), updatedAt: "2026-10-08T10:00:00Z" }]
    expect(dockCandidate(list)!.id).toBe("new")
  })
})

const q = (id: string, status: string, at: string, doc: string | null = "d1"): QueueAppt => ({ id, patientId: "p" + id, patientName: "P" + id, practitionerId: doc, scheduledAt: `2026-10-08T${at}:00Z`, status })
describe("nextUp", () => {
  const day = [q("1", "COMPLETED", "07:00"), q("2", "CHECKED_IN", "08:30"), q("3", "TRIAGED", "08:45"), q("4", "CONFIRMED", "09:30"), q("5", "CHECKED_IN", "08:00", "d2")]
  it("takes someone already in the building first, triaged before checked in", () => {
    const r = nextUp(day, { mine: "d1" })
    expect(r.ready!.id).toBe("3"); expect(r.waiting).toBe(2); expect(r.upcoming!.id).toBe("4")
  })
  it("without a practitioner it considers everyone, by time", () => expect(nextUp(day).ready!.id).toBe("3"))
  it("leaves out the patient just seen and other doctors' patients", () => {
    expect(nextUp(day, { mine: "d1", excludeId: "3" }).ready!.id).toBe("2")
    expect(nextUp([q("5", "CHECKED_IN", "08:00", "d2")], { mine: "d1" }).ready).toBeNull()
  })
  it("unassigned appointments count for everyone", () => expect(nextUp([q("6", "CHECKED_IN", "08:00", null)], { mine: "d1" }).ready!.id).toBe("6"))
  it("falls back to the next expected, or nothing", () => {
    expect(nextUp([q("4", "CONFIRMED", "09:30"), q("7", "SCHEDULED", "09:00")]).upcoming!.id).toBe("7")
    expect(nextUp([])).toEqual({ ready: null, upcoming: null, waiting: 0 })
  })
})
