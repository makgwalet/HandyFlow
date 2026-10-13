import { describe, it, expect } from "vitest"
import { canConfirm, cardLine, consequence, fieldLabel, totalRows, type MergePreview } from "./mergeView"

const base: MergePreview = {
  keep: { id: "a", patientNumber: "P000001", fullName: "Thandi Nkosi", dateOfBirth: "1980-01-01" },
  mergeAway: { id: "b", patientNumber: "P000002", fullName: "Thandi Nkosi" },
  moves: [{ table: "clinic_consultations", label: "Visits", rows: 2 }, { table: "clinic_claims", label: "Claims", rows: 1 }],
  fills: ["email"], conflicts: [], blockers: [], staysBehind: [], needsAcknowledgement: false,
}

describe("mergeView", () => {
  it("describes a record on one line", () => expect(cardLine(base.keep)).toBe("P000001 · Thandi Nkosi · DOB 1980-01-01"))
  it("counts everything that moves and says what happens", () => {
    expect(totalRows(base)).toBe(3)
    expect(consequence(base)).toMatch(/^3 records will move to Thandi Nkosi\./)
    expect(consequence({ ...base, moves: [{ table: "x", label: "X", rows: 1 }] })).toMatch(/^1 record will move/)
    expect(consequence(base)).toContain("archived, not deleted")
  })
  it("needs a preview, a reason and no blockers", () => {
    expect(canConfirm(undefined, "Same person twice", false)).toBe(false)
    expect(canConfirm(base, "no", false)).toBe(false)
    expect(canConfirm({ ...base, blockers: ["archived"] }, "Same person twice", false)).toBe(false)
    expect(canConfirm(base, "Same person twice", false)).toBe(true)
  })
  it("needs the box ticked when the identities disagree", () => {
    const p = { ...base, needsAcknowledgement: true }
    expect(canConfirm(p, "Same person twice", false)).toBe(false)
    expect(canConfirm(p, "Same person twice", true)).toBe(true)
  })
  it("names the fields it fills in", () => { expect(fieldLabel("date_of_birth")).toBe("date of birth"); expect(fieldLabel("some_new_field")).toBe("some new field") })
})
