import { describe, it, expect } from "vitest"
import { attendanceText, endProblem, grantText, hiddenText, inviteProblem, passwordProblem, periodText, statusText } from "./learnerPortalRules"

describe("learner portal rules", () => {
  it("checks the password", () => {
    expect(passwordProblem("short", "short")).toContain("at least 8")
    expect(passwordProblem("longenough1", "different1")).toBe("The two passwords are not the same")
    expect(passwordProblem("longenough1", "longenough1")).toBeNull()
  })
  it("shows attendance as counts only", () => {
    expect(attendanceText({ recorded: 0, present: 0, late: 0, absent: 0, excused: 0 })).toBe("No attendance recorded yet")
    expect(attendanceText({ recorded: 1, present: 1, late: 0, absent: 0, excused: 0 })).toBe("1 session recorded: 1 present, 0 late, 0 absent, 0 excused")
    expect(attendanceText({ recorded: 5, present: 3, late: 1, absent: 1, excused: 0 })).toContain("5 sessions recorded")
  })
  it("words status and dates plainly", () => {
    expect(statusText("IN_PROGRESS")).toBe("In progress")
    expect(periodText("2020-03-01", null)).toBe("From 2020-03-01")
    expect(periodText(null, null)).toBe("Dates not set")
  })
  it("explains hidden enrolments", () => {
    expect(hiddenText(0)).toBe("")
    expect(hiddenText(1)).toContain("1 enrolment is not shown")
    expect(hiddenText(2)).toContain("2 enrolments are not shown")
  })
  it("checks invite and end", () => {
    expect(inviteProblem("nope", "Learner asked for access")).toContain("valid email")
    expect(inviteProblem("a@b.co", "short")).toContain("at least 10")
    expect(inviteProblem("a@b.co", "Learner asked for access")).toBeNull()
    expect(endProblem("short")).toContain("at least 10")
    expect(endProblem("Learner left the programme")).toBeNull()
    expect(grantText("ACCEPTED")).toBe("Has access")
  })
})
