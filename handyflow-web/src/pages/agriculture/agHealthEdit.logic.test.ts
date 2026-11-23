import { describe, it, expect } from "vitest"
import { changeWarnings, editError, editPayload, editValuesFrom, hasChanges, type HealthEventLike } from "./agHealthEdit.logic"

const ev = (o: Partial<HealthEventLike> = {}): HealthEventLike => ({
  description: "Vaccinated", productUsed: "Multivax", dosage: "2 ml", veterinarian: null, cost: 120.5,
  withdrawalPeriodDays: 14, milkWithdrawalDays: null, eggWithdrawalDays: undefined, nextDueDate: "2027-05-01", notes: null, ...o,
})

describe("starting values", () => {
  it("turns nulls into empty fields and numbers into text", () => {
    expect(editValuesFrom(ev())).toEqual({
      description: "Vaccinated", productUsed: "Multivax", dosage: "2 ml", veterinarian: "", cost: "120.5",
      withdrawalPeriodDays: "14", milkWithdrawalDays: "", eggWithdrawalDays: "", nextDueDate: "2027-05-01", notes: "",
    })
  })
})

describe("editError", () => {
  it("passes a good form", () => { expect(editError(editValuesFrom(ev()))).toBeNull() })
  it("needs a description", () => { expect(editError({ ...editValuesFrom(ev()), description: "  " })).toBe("Describe what was done.") })
  it("refuses a negative cost", () => { expect(editError({ ...editValuesFrom(ev()), cost: "-1" })).toBe("The cost cannot be negative.") })
  it("refuses a withdrawal period out of range, for each period", () => {
    expect(editError({ ...editValuesFrom(ev()), withdrawalPeriodDays: "731" })).toMatch(/Withdrawal period/)
    expect(editError({ ...editValuesFrom(ev()), milkWithdrawalDays: "-2" })).toMatch(/Milk withdrawal/)
    expect(editError({ ...editValuesFrom(ev()), eggWithdrawalDays: "9999" })).toMatch(/Egg withdrawal/)
  })
  it("accepts empty periods and zero cost", () => {
    expect(editError({ ...editValuesFrom(ev()), withdrawalPeriodDays: "", cost: "0" })).toBeNull()
  })
})

describe("changes", () => {
  it("sees nothing changed, ignoring stray spaces", () => {
    expect(hasChanges(ev(), editValuesFrom(ev()))).toBe(false)
    expect(hasChanges(ev(), { ...editValuesFrom(ev()), description: " Vaccinated " })).toBe(false)
  })
  it("sees a change in any field", () => {
    expect(hasChanges(ev(), { ...editValuesFrom(ev()), notes: "x" })).toBe(true)
    expect(hasChanges(ev(), { ...editValuesFrom(ev()), cost: "99" })).toBe(true)
    expect(hasChanges(ev(), { ...editValuesFrom(ev()), eggWithdrawalDays: "3" })).toBe(true)
  })
  it("warns about a new or cleared due date", () => {
    expect(changeWarnings(ev(), { ...editValuesFrom(ev()), nextDueDate: "2027-06-01" })).toEqual(["A new due date starts a new reminder."])
    expect(changeWarnings(ev(), { ...editValuesFrom(ev()), nextDueDate: "" })).toEqual(["Clearing the due date removes the reminder."])
  })
  it("warns about a changed withdrawal period", () => {
    expect(changeWarnings(ev(), { ...editValuesFrom(ev()), milkWithdrawalDays: "3" })).toEqual(["Changing a withdrawal period changes when this animal or its produce can be sold."])
  })
  it("has no warning for other edits", () => {
    expect(changeWarnings(ev(), { ...editValuesFrom(ev()), notes: "x", cost: "1" })).toEqual([])
  })
})

describe("editPayload", () => {
  it("sends numbers and nulls, trimmed", () => {
    expect(editPayload({ ...editValuesFrom(ev()), description: " Done ", veterinarian: " Dr Smith ", milkWithdrawalDays: "3", cost: "" })).toEqual({
      description: "Done", productUsed: "Multivax", dosage: "2 ml", veterinarian: "Dr Smith", cost: null,
      withdrawalPeriodDays: 14, milkWithdrawalDays: 3, eggWithdrawalDays: null, nextDueDate: "2027-05-01", notes: null,
    })
  })
  it("sends a cleared due date as null", () => {
    expect(editPayload({ ...editValuesFrom(ev()), nextDueDate: "" }).nextDueDate).toBeNull()
  })
})
