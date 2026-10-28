import { describe, expect, it } from "vitest"
import {
  attention, canCancelBooking, cellText, needsAction, orderOpenSessions, orderTeam, requirementBody, requirementProblem, seatsText, summaryText,
  teamSummary, visibleRows, type MatrixCell, type MatrixRow, type MatrixSummary, type OpenSession, type TeamMember,
} from "./trainingRequired.logic"

const cell = (status: MatrixCell["status"], expiryDate: string | null = null): MatrixCell => ({ status, label: { OK: "Valid", EXPIRING: "Expiring soon", EXPIRED: "Expired", MISSING: "Not held", BOOKED: "Booked" }[status], expiryDate })
const row = (name: string, ...cells: (MatrixCell | null)[]): MatrixRow => ({ employeeId: name, employeeNumber: null, name, jobTitle: null, department: null, cells })
const member = (name: string, ...statuses: MatrixCell["status"][]): TeamMember => ({
  employeeId: name, name, jobTitle: null, certificates: [], bookings: [],
  required: statuses.map((s, i) => ({ courseId: String(i), courseTitle: `Course ${i}`, status: s, label: s, expiryDate: null })),
})
const summary = (o: Partial<MatrixSummary>): MatrixSummary => ({ people: 0, required: 0, valid: 0, expiring: 0, expired: 0, missing: 0, booked: 0, ...o })

describe("required training matrix", () => {
  it("counts only missing and expired as needing action", () => {
    expect(needsAction("MISSING")).toBe(true)
    expect(needsAction("EXPIRED")).toBe(true)
    expect(["OK", "EXPIRING", "BOOKED"].some(s => needsAction(s as MatrixCell["status"]))).toBe(false)
  })

  it("puts people with the most gaps first, then by name", () => {
    const rows = [row("Zodwa", cell("OK")), row("Anele", cell("MISSING")), row("Bongani", cell("EXPIRED"), cell("MISSING")), row("Cebo", null, cell("BOOKED"))]
    expect(visibleRows(rows, false).map(r => r.name)).toEqual(["Bongani", "Anele", "Cebo", "Zodwa"])
  })

  it("can show only people with something to put right", () => {
    const rows = [row("Zodwa", cell("OK")), row("Anele", cell("MISSING")), row("Cebo", cell("BOOKED"), null)]
    expect(visibleRows(rows, true).map(r => r.name)).toEqual(["Anele"])
  })

  it("does not change the list it is given", () => {
    const rows = [row("B", cell("OK")), row("A", cell("MISSING"))]
    visibleRows(rows, false)
    expect(rows.map(r => r.name)).toEqual(["B", "A"])
  })

  it("words a cell with its date", () => {
    expect(cellText(cell("OK", "2027-01-31"))).toBe("Valid · until 2027-01-31")
    expect(cellText(cell("EXPIRED", "2026-10-01"))).toBe("Expired on 2026-10-01")
    expect(cellText(cell("MISSING"))).toBe("Not held")
    expect(cellText(cell("OK"))).toBe("Valid")
    expect(cellText(cell("BOOKED"))).toBe("Booked")
  })

  it("summarises in plain words", () => {
    expect(summaryText(summary({}))).toBe("Nothing is required yet.")
    expect(summaryText(summary({ people: 1, required: 2, valid: 2 }))).toBe("1 person has every required certificate in date.")
    expect(summaryText(summary({ people: 5, required: 6, valid: 5, expiring: 1 }))).toBe("5 people have every required certificate in date (1 expiring soon).")
    expect(summaryText(summary({ people: 4, required: 6, valid: 3, expired: 1, missing: 2 }))).toBe("3 of 6 required certificates for 4 people are missing or expired.")
    expect(summaryText(summary({ people: 1, required: 3, valid: 2, missing: 1 }))).toBe("1 of 3 required certificates for 1 person is missing or expired.")
  })
})

describe("adding a requirement", () => {
  it("needs a course", () => {
    expect(requirementProblem("", "", "")).toMatch(/course/)
    expect(requirementProblem("c1", "", "")).toBeNull()
  })
  it("limits the length of the job title and department", () => {
    expect(requirementProblem("c1", "x".repeat(151), "")).toMatch(/150/)
    expect(requirementProblem("c1", "", "x".repeat(151))).toMatch(/150/)
    expect(requirementProblem("c1", "x".repeat(150), "")).toBeNull()
  })
  it("sends blank parts as nothing", () => {
    expect(requirementBody("c1", "  Driver ", "   ")).toEqual({ courseId: "c1", jobTitle: "Driver", department: null })
  })
})

describe("a manager's team", () => {
  it("says who needs to act, who has something expiring and who is in order", () => {
    expect(attention(member("A", "OK", "MISSING"))).toBe("ACTION")
    expect(attention(member("B", "OK", "EXPIRING"))).toBe("EXPIRING")
    expect(attention(member("C", "OK", "BOOKED"))).toBe("OK")
    expect(attention(member("D"))).toBe("OK")
  })
  it("lists those who need to act first", () => {
    const team = [member("Zed", "OK"), member("Amy", "EXPIRING"), member("Bea", "EXPIRED"), member("Cal", "OK")]
    expect(orderTeam(team).map(m => m.name)).toEqual(["Bea", "Amy", "Cal", "Zed"])
  })
  it("summarises the team", () => {
    expect(teamSummary([])).toBe("Nobody reports to you.")
    expect(teamSummary([member("A", "OK")])).toBe("1 person on your team, all in order.")
    expect(teamSummary([member("A", "MISSING"), member("B", "EXPIRING"), member("C", "OK")])).toBe("3 people on your team: 1 to act on, 1 with something expiring soon.")
  })
})

describe("booking yourself on", () => {
  const session = (id: string, startDate: string, required: boolean, seatsLeft: number | null = 3): OpenSession =>
    ({ id, courseId: "c", courseTitle: id, startDate, endDate: startDate, venue: null, seatsLeft, required })
  it("shows required training first, then by date", () => {
    const rows = [session("late", "2026-12-01", false), session("req-late", "2026-12-05", true), session("early", "2026-11-01", false), session("req-early", "2026-11-20", true)]
    expect(orderOpenSessions(rows).map(r => r.id)).toEqual(["req-early", "req-late", "early", "late"])
  })
  it("words the places left", () => {
    expect(seatsText(null)).toBe("Places available")
    expect(seatsText(1)).toBe("1 place left")
    expect(seatsText(12)).toBe("12 places left")
  })
  it("lets a booking be cancelled only before the day the session starts", () => {
    expect(canCancelBooking({ status: "ENROLLED", startDate: "2026-11-02" }, "2026-11-01")).toBe(true)
    expect(canCancelBooking({ status: "ENROLLED", startDate: "2026-11-01" }, "2026-11-01")).toBe(false)
    expect(canCancelBooking({ status: "ATTENDED", startDate: "2026-12-01" }, "2026-11-01")).toBe(false)
    expect(canCancelBooking({ status: "ENROLLED", startDate: null }, "2026-11-01")).toBe(false)
  })
})
