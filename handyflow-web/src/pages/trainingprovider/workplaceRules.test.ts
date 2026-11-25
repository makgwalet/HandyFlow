import { describe, it, expect } from "vitest"
import { confirmProblem, dayProblem, emptyLog, emptyPlacement, enrolmentOpen, logBody, logLine, logProblem, placementBody, placementProblem } from "./workplaceRules"

const ok = { ...emptyPlacement, hostName: "Acme", mentorName: "Ms Mentor", startDate: "2027-06-01" }

describe("workplace rules", () => {
  it("checks a placement", () => {
    expect(placementProblem(ok, "2027-06-01")).toBeNull()
    expect(placementProblem({ ...ok, hostName: "A" }, null)).toMatch(/host/)
    expect(placementProblem({ ...ok, mentorName: " " }, null)).toMatch(/mentor/)
    expect(placementProblem({ ...ok, startDate: "" }, null)).toMatch(/start day/)
    expect(placementProblem({ ...ok, endDate: "2027-05-01" }, null)).toMatch(/end day/)
    expect(placementProblem({ ...ok, startDate: "2027-05-31" }, "2027-06-01")).toMatch(/before the enrolment/)
    expect(placementBody({ ...ok, hostContact: " " })).toMatchObject({ hostContact: null, endDate: null, startDate: "2027-06-01" })
  })
  it("allows placements only while the enrolment is open", () => {
    expect(enrolmentOpen("IN_PROGRESS")).toBe(true)
    expect(enrolmentOpen("ENROLLED")).toBe(true)
    expect(enrolmentOpen("ON_HOLD")).toBe(false)
    expect(enrolmentOpen("CANCELLED")).toBe(false)
  })
  it("checks a logbook entry", () => {
    const f = { ...emptyLog("2027-06-10"), hours: "8", activity: "Assisted with stock counts" }
    expect(logProblem(f, "2027-06-01", null, "2027-06-10")).toBeNull()
    expect(logProblem({ ...f, logDate: "2027-06-11" }, "2027-06-01", null, "2027-06-10")).toMatch(/future/)
    expect(logProblem({ ...f, logDate: "2027-05-31" }, "2027-06-01", null, "2027-06-10")).toMatch(/before the placement/)
    expect(logProblem(f, "2027-06-01", "2027-06-05", "2027-06-10")).toMatch(/planned end/)
    expect(logProblem({ ...f, hours: "0" }, null, null, "2027-06-10")).toMatch(/Hours/)
    expect(logProblem({ ...f, hours: "25" }, null, null, "2027-06-10")).toMatch(/Hours/)
    expect(logProblem({ ...f, hours: "" }, null, null, "2027-06-10")).toMatch(/Hours/)
    expect(logProblem({ ...f, activity: "short" }, null, null, "2027-06-10")).toMatch(/10 characters/)
    expect(logBody(f)).toEqual({ logDate: "2027-06-10", hours: 8, activity: "Assisted with stock counts" })
  })
  it("checks the day for start and complete, and the mentor confirmation", () => {
    expect(dayProblem("", null, "2027-06-10", "it started")).toMatch(/Enter the day it started/)
    expect(dayProblem("2027-06-11", null, "2027-06-10", "it started")).toMatch(/future/)
    expect(dayProblem("2027-05-01", "2027-06-01", "2027-06-10", "it ended")).toMatch(/before 2027-06-01/)
    expect(confirmProblem("2027-06-09", "2027-06-10", "2027-06-10", "Ms Mentor")).toMatch(/before the day/)
    expect(confirmProblem("2027-06-10", "2027-06-10", "2027-06-10", " ")).toMatch(/name of the mentor/)
    expect(confirmProblem("2027-06-10", "2027-06-10", "2027-06-10", "Ms Mentor")).toBeNull()
  })
  it("describes an entry without claiming more than was recorded", () => {
    expect(logLine({ status: "RECORDED", confirmedOn: null, confirmedByName: null, voidReason: null })).toMatch(/Not yet confirmed/)
    expect(logLine({ status: "RECORDED", confirmedOn: "2027-06-10", confirmedByName: "Ms Mentor", voidReason: null })).toBe("Confirmed by Ms Mentor on 2027-06-10")
    expect(logLine({ status: "VOID", confirmedOn: null, confirmedByName: null, voidReason: "Wrong day" })).toMatch(/Voided/)
  })
})
