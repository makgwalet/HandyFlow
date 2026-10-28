import { describe, expect, it } from "vitest"
import { EMPTY_SAVED_FORM, mayKeep, reminderText, savedBody, savedProblem, toReportKind, toSavedKind, visibleSaved, type SavedReport } from "./savedReports.logic"

const form = (o: Partial<typeof EMPTY_SAVED_FORM>) => ({ ...EMPTY_SAVED_FORM, name: "Cost", ...o })

describe("savedProblem", () => {
  it("asks for a name", () => expect(savedProblem(form({ name: "  " }))).toMatch(/name/))
  it("refuses a very long name", () => expect(savedProblem(form({ name: "x".repeat(81) }))).toMatch(/80/))
  it("accepts a report with no reminder", () => expect(savedProblem(form({}))).toBeNull())
  it("needs a day when a reminder is chosen", () => expect(savedProblem(form({ schedule: "WEEKLY" }))).toMatch(/day/))
  it("keeps weekdays to 1 to 7 and month days to 1 to 28", () => {
    expect(savedProblem(form({ schedule: "WEEKLY", runDay: "8" }))).not.toBeNull()
    expect(savedProblem(form({ schedule: "WEEKLY", runDay: "7" }))).toBeNull()
    expect(savedProblem(form({ schedule: "MONTHLY", runDay: "29" }))).not.toBeNull()
    expect(savedProblem(form({ schedule: "MONTHLY", runDay: "28" }))).toBeNull()
    expect(savedProblem(form({ schedule: "MONTHLY", runDay: "2.5" }))).not.toBeNull()
  })
})

describe("savedBody", () => {
  it("sends no day when there is no reminder", () => expect(savedBody(form({ runDay: "3" }), "time").runDay).toBeNull())
  it("sends the kind in capitals and trims the name", () => {
    const b = savedBody(form({ name: " Cost ", schedule: "MONTHLY", runDay: "3" }), "payroll")
    expect(b).toEqual({ name: "Cost", kind: "PAYROLL", period: "LAST_MONTH", schedule: "MONTHLY", runDay: 3 })
  })
  it("converts kinds both ways", () => { expect(toSavedKind("workforce")).toBe("WORKFORCE"); expect(toReportKind("COMPLIANCE")).toBe("compliance") })
})

describe("payroll reports", () => {
  const s = (kind: SavedReport["kind"]) => ({ id: kind, kind } as SavedReport)
  it("can be kept and shown only with the payroll right", () => {
    expect(mayKeep("payroll", false)).toBe(false)
    expect(mayKeep("payroll", true)).toBe(true)
    expect(mayKeep("time", false)).toBe(true)
    expect(visibleSaved([s("PAYROLL"), s("TIME")], false).map(x => x.id)).toEqual(["TIME"])
    expect(visibleSaved([s("PAYROLL"), s("TIME")], true)).toHaveLength(2)
  })
})

describe("reminderText", () => {
  it("says it plainly", () => {
    expect(reminderText({ schedule: "WEEKLY", runDay: 3 })).toBe("Reminder every Wednesday")
    expect(reminderText({ schedule: "MONTHLY", runDay: 5 })).toBe("Reminder on day 5 of each month")
    expect(reminderText({ schedule: "NONE", runDay: null })).toBe("No reminder")
  })
})
