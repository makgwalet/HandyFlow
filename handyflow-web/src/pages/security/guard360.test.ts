import { describe, expect, it } from "vitest"
import { completionRate, daysBetween, expiryState, fileChecklist, readinessState, readinessTone, screeningLabel, todayIso } from "./guard360.logic"

const TODAY = "2026-10-07"

describe("dates", () => {
  it("counts whole days across month ends", () => { expect(daysBetween("2026-10-07", "2026-10-15")).toBe(8); expect(daysBetween("2026-10-07", "2026-09-30")).toBe(-7) })
  it("gives today in South African time, which is ahead of UTC", () => { expect(todayIso(new Date("2026-10-06T23:30:00Z"))).toBe("2026-10-07") })
})

describe("expiryState", () => {
  it("flags missing, expired, today, soon and valid", () => {
    expect(expiryState(null, TODAY).tone).toBe("warn")
    expect(expiryState("2026-09-29", TODAY)).toMatchObject({ tone: "bad", label: "Expired 8 days ago" })
    expect(expiryState("2026-10-07", TODAY).label).toBe("Expires today")
    expect(expiryState("2026-10-20", TODAY)).toMatchObject({ tone: "warn", days: 13 })
    expect(expiryState("2027-10-07", TODAY).tone).toBe("ok")
  })
  it("treats day 30 as due soon and day 31 as valid", () => {
    expect(expiryState("2026-11-06", TODAY).tone).toBe("warn")
    expect(expiryState("2026-11-07", TODAY).tone).toBe("ok")
  })
})

describe("readiness display", () => {
  it("maps every server state to a tone", () => {
    expect(readinessState("MET").tone).toBe("ok"); expect(readinessState("EXPIRING").tone).toBe("warn")
    expect(readinessState("EXPIRED").tone).toBe("bad"); expect(readinessState("FAILED").tone).toBe("bad")
    expect(readinessState("MISSING").tone).toBe("neutral"); expect(readinessState("SOMETHING_NEW").label).toBe("something_new")
  })
  it("ring colour: ready is ok, 60% or more but blocked is warn, below that is bad", () => {
    expect(readinessTone({ percent: 100, ready: true })).toBe("ok")
    expect(readinessTone({ percent: 80, ready: false })).toBe("warn")
    expect(readinessTone({ percent: 60, ready: false })).toBe("warn")
    expect(readinessTone({ percent: 40, ready: false })).toBe("bad")
  })
  it("labels screening types, including the new verification types", () => {
    expect(screeningLabel("ID_VERIFICATION")).toBe("ID verification")
    expect(screeningLabel("QUALIFICATION_VERIFICATION")).toBe("Qualification verification")
  })
})

describe("fileChecklist", () => {
  const docs = [{ id: "d", category: "ID_COPY", fileUrl: "x", fileName: null, notes: null, createdAt: "2026-01-01T00:00:00Z" }]
  it("ticks only categories that have a document", () => {
    const c = fileChecklist(docs)
    expect(c.find(x => x.category === "ID_COPY")?.present).toBe(true)
    expect(c.filter(x => !x.present)).toHaveLength(4)
  })
})

describe("completionRate", () => {
  it("is null when nothing started and rounds otherwise", () => { expect(completionRate(0, 0)).toBeNull(); expect(completionRate(3, 2)).toBe(67) })
})

import { competencyFormError } from "./guard360.logic"
describe("competencyFormError", () => {
  const ok = { competencyType: "FIRST_AID", title: "", issueDate: "2026-01-01", expiryDate: "2027-01-01" }
  it("accepts a valid form and dates left blank", () => {
    expect(competencyFormError(ok, "2026-10-07")).toBeNull()
    expect(competencyFormError({ ...ok, issueDate: "", expiryDate: "" }, "2026-10-07")).toBeNull()
  })
  it("needs a name for Other", () => { expect(competencyFormError({ ...ok, competencyType: "OTHER", title: " " }, "2026-10-07")).toBe("Give the competency a name") })
  it("refuses a future issue date but accepts today", () => {
    expect(competencyFormError({ ...ok, issueDate: "2026-10-08" }, "2026-10-07")).toBe("The issue date cannot be in the future")
    expect(competencyFormError({ ...ok, issueDate: "2026-10-07", expiryDate: "" }, "2026-10-07")).toBeNull()
  })
  it("refuses expiry before issue", () => { expect(competencyFormError({ ...ok, expiryDate: "2025-12-31" }, "2026-10-07")).toBe("The expiry date cannot be before the issue date") })
})
