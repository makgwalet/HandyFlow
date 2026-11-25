import { describe, it, expect } from "vitest"
import { problem, formOf, body, same, available, runText, staffName, levelLabel, type Settings, type Form } from "./reminderRules"

const settings = (o: Partial<Settings> = {}): Settings => ({ enabled: false, minSeverity: "HIGH", maxItems: 25, recipients: [], updatedAt: null, ...o })
const form = (o: Partial<Form> = {}): Form => ({ ...formOf(settings()), ...o })

describe("reminderRules", () => {
  it("will not switch on with nobody to send to", () => {
    expect(problem(form({ enabled: true }))).toMatch(/at least one person/)
    expect(problem(form({ enabled: true, recipients: [{ userId: "u", adminItems: false }] }))).toBeNull()
    expect(problem(form())).toBeNull()
  })
  it("keeps the length between 5 and 100", () => {
    expect(problem(form({ maxItems: "4" }))).toMatch(/between 5 and 100/)
    expect(problem(form({ maxItems: "101" }))).not.toBeNull()
    expect(problem(form({ maxItems: "abc" }))).not.toBeNull()
    expect(problem(form({ maxItems: "50" }))).toBeNull()
  })
  it("limits the number of people", () => {
    const many = Array.from({ length: 26 }, (_, i) => ({ userId: "u" + i, adminItems: false }))
    expect(problem(form({ recipients: many }))).toMatch(/at most 25/)
  })
  it("sends numbers as numbers", () => {
    expect(body(form({ maxItems: "30" })).maxItems).toBe(30)
  })
  it("tells when the form has changed", () => {
    const f = formOf(settings({ recipients: [{ userId: "u", name: "A", email: null, adminItems: false }] }))
    expect(same(f, { ...f })).toBe(true)
    expect(same(f, { ...f, minSeverity: "LOW" })).toBe(false)
  })
  it("offers only people not already chosen", () => {
    const staff = [{ id: "a", firstName: "Thandi" }, { id: "b", firstName: "Sipho" }]
    expect(available(staff, form({ recipients: [{ userId: "a", adminItems: false }] })).map(s => s.id)).toEqual(["b"])
  })
  it("words a run, and never says delivered", () => {
    const t = runText({ handedOver: 2, nothingDue: 1, noAddress: 0, failed: 1, alreadyDone: 0 })
    expect(t).toBe("2 handed to the notification service, 1 had nothing due, 1 failed")
    expect(t.includes("delivered")).toBe(false)
    expect(runText({ handedOver: 0, nothingDue: 0, noAddress: 0, failed: 0, alreadyDone: 0 })).toBe("Nobody to send to")
  })
  it("names people and levels", () => {
    expect(staffName({ id: "x", firstName: "Thandi", lastName: "Nkosi" })).toBe("Thandi Nkosi")
    expect(staffName({ id: "x", email: "a@b.org" })).toBe("a@b.org")
    expect(levelLabel("MEDIUM")).toBe("Urgent and soon")
  })
})
