import { describe, it, expect } from "vitest"
import {
  attendanceTone, cohortBody, cohortProblem, correctionBody, correctionProblem, emptyCohort, emptySlot, emptyVenue, rateText, registerBody, registerProblem, slotBody, slotProblem,
  standingWarning, transitionBody, transitionProblem, venueBody, venueProblem,
} from "./cohortRules"

const cohort = { startDate: "2027-02-01", endDate: "2027-06-30" }
describe("cohort form", () => {
  const ok = { ...emptyCohort, name: "Feb intake", programmeId: "p", siteId: "s", startDate: "2027-02-01" }
  it("needs a name, programme, site and start date", () => {
    expect(cohortProblem(emptyCohort, true)).toContain("name")
    expect(cohortProblem({ ...ok, programmeId: "" }, true)).toContain("programme")
    expect(cohortProblem({ ...ok, siteId: "" }, true)).toContain("site")
    expect(cohortProblem({ ...ok, startDate: "" }, true)).toContain("start date")
    expect(cohortProblem(ok, true)).toBeNull()
  })
  it("does not ask for programme or site when editing", () => { expect(cohortProblem({ ...ok, programmeId: "", siteId: "" }, false)).toBeNull() })
  it("checks dates and capacity and sends blanks as null", () => {
    expect(cohortProblem({ ...ok, endDate: "2027-01-01" }, true)).toContain("end date")
    expect(cohortProblem({ ...ok, capacity: "0" }, true)).toContain("Capacity")
    expect(cohortProblem({ ...ok, capacity: "2.5" }, true)).toContain("Capacity")
    expect(cohortBody(ok)).toMatchObject({ endDate: null, capacity: null, notes: null })
    expect(cohortBody({ ...ok, capacity: "20" }).capacity).toBe(20)
  })
})
describe("venue form", () => {
  it("needs a site and name", () => {
    expect(venueProblem(emptyVenue)).toContain("site")
    expect(venueProblem({ ...emptyVenue, siteId: "s", name: "R" })).toContain("name")
    expect(venueProblem({ ...emptyVenue, siteId: "s", name: "Room 1", capacity: "x" })).toContain("Capacity")
    expect(venueBody({ ...emptyVenue, siteId: "s", name: " Room 1 " })).toMatchObject({ name: "Room 1", capacity: null })
  })
})
describe("slot form", () => {
  const ok = { ...emptySlot, title: "Module 1", slotDate: "2027-02-10" }
  it("checks times and the cohort dates", () => {
    expect(slotProblem(ok, cohort)).toBeNull()
    expect(slotProblem({ ...ok, title: "M" }, cohort)).toContain("title")
    expect(slotProblem({ ...ok, endTime: "09:00" }, cohort)).toContain("after the start")
    expect(slotProblem({ ...ok, slotDate: "2027-01-31" }, cohort)).toContain("before the cohort starts")
    expect(slotProblem({ ...ok, slotDate: "2027-07-01" }, cohort)).toContain("after the cohort ends")
    expect(slotProblem({ ...ok, slotDate: "2030-01-01" }, { ...cohort, endDate: null })).toBeNull()
  })
  it("sends empty choices as null", () => { expect(slotBody(ok)).toMatchObject({ moduleId: null, venueId: null, practitionerId: null }) })
  it("warns when a facilitator is not in force on the date", () => {
    expect(standingWarning("VERIFIED_IN_FORCE")).toBeNull()
    expect(standingWarning(null)).toBeNull()
    expect(standingWarning("EXPIRED")).toContain("not checked and in force")
  })
})
describe("cohort status", () => {
  it("cancelling needs a reason and only allowed moves are offered", () => {
    expect(transitionProblem({ toStatus: "RUNNING", reason: "" }, ["RUNNING", "CANCELLED"])).toBeNull()
    expect(transitionProblem({ toStatus: "COMPLETED", reason: "" }, ["RUNNING", "CANCELLED"])).toContain("Choose")
    expect(transitionProblem({ toStatus: "CANCELLED", reason: "short" }, ["CANCELLED"])).toContain("at least 10")
    expect(transitionBody({ toStatus: "RUNNING", reason: "left over" }).reason).toBeNull()
    expect(transitionBody({ toStatus: "CANCELLED", reason: " Too few learners enrolled " }).reason).toBe("Too few learners enrolled")
  })
})
describe("register", () => {
  it("needs at least one mark and a reason for every excused learner", () => {
    expect(registerProblem({})).toContain("at least one")
    expect(registerProblem({ a: { status: "PRESENT", note: "" } })).toBeNull()
    expect(registerProblem({ a: { status: "EXCUSED", note: "sick" } })).toContain("excused")
    expect(registerProblem({ a: { status: "EXCUSED", note: "Medical note sighted" } })).toBeNull()
    expect(registerBody({ a: { status: "PRESENT", note: "" } }).entries).toEqual([{ memberId: "a", status: "PRESENT", note: null }])
  })
  it("a correction needs a change and a reason", () => {
    const from = { status: "ABSENT", note: null }
    expect(correctionProblem(from, { status: "ABSENT", note: "", reason: "Recorded in error today" })).toContain("Nothing")
    expect(correctionProblem(from, { status: "PRESENT", note: "", reason: "short" })).toContain("at least 10")
    expect(correctionProblem(from, { status: "EXCUSED", note: "", reason: "Note handed in later" })).toContain("excused")
    expect(correctionProblem(from, { status: "PRESENT", note: "", reason: "Arrived after the register closed" })).toBeNull()
    expect(correctionBody({ status: "PRESENT", note: " ", reason: " Arrived late " })).toEqual({ status: "PRESENT", note: null, reason: "Arrived late" })
  })
  it("never presents a rate as a result", () => {
    expect(rateText({ ratePercent: null })).toBe("no rate yet")
    expect(rateText({ ratePercent: 75 })).toBe("75% of recorded sessions")
    expect(attendanceTone("ABSENT")).toBe("bad")
    expect(attendanceTone(null)).toBe("neutral")
  })
})
