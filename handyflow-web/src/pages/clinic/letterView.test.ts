import { describe, it, expect } from "vitest"
import { addDays, applyReferral, applySickNote, insertAt, mergeToken, templateProblem } from "./letterView"

describe("letter templates", () => {
  it("counts days across a month end", () => { expect(addDays("2026-10-30", 3)).toBe("2026-11-02"); expect(addDays("2026-10-08", 0)).toBe("2026-10-08") })
  it("fills a sick note: notes, and first and last day from the number of days", () => {
    const f = { consultationId: "c1", unfitFrom: "", unfitTo: "", notes: "old" }
    expect(applySickNote(f, { body: "Seen and examined.", unfitDays: 3 }, "2026-10-08")).toEqual({ consultationId: "c1", unfitFrom: "2026-10-08", unfitTo: "2026-10-10", notes: "Seen and examined." })
    expect(applySickNote({ ...f, unfitFrom: "2026-10-12" }, { body: "x", unfitDays: 1 }, "2026-10-08").unfitTo).toBe("2026-10-12")
  })
  it("leaves the dates alone when the template has no days, and keeps the notes when it has no text", () => {
    const f = { consultationId: "c1", unfitFrom: "2026-10-01", unfitTo: "2026-10-05", notes: "keep" }
    expect(applySickNote(f, { body: null, unfitDays: null }, "2026-10-08")).toEqual(f)
  })
  it("fills a referral without wiping what the template does not set", () => {
    const f = { specialistName: "Dr Smith", specialty: "", reason: "", urgency: "ROUTINE", additionalNotes: "n" }
    expect(applyReferral(f, { title: "Chest pain work-up", body: null, specialty: "Cardiology", urgency: "URGENT" }))
      .toEqual({ specialistName: "Dr Smith", specialty: "Cardiology", reason: "Chest pain work-up", urgency: "URGENT", additionalNotes: "n" })
    expect(applyReferral(f, { title: null, body: null, specialty: null, urgency: null })).toEqual(f)
  })
  it("inserts a merge field at the cursor or at the end", () => {
    expect(insertAt("Dear ,", mergeToken("patient.name"), 5)).toEqual({ text: "Dear {{patient.name}},", cursor: 21 })
    expect(insertAt("Hi ", "{{today}}", null).text).toBe("Hi {{today}}")
    expect(insertAt("Hi", "{{today}}", 99).text).toBe("Hi{{today}}")
  })
  it("checks what each kind needs", () => {
    expect(templateProblem("SICK_NOTE", "", "", "x")).toMatch(/name/)
    expect(templateProblem("SICK_NOTE", "n", "", " ")).toMatch(/text/)
    expect(templateProblem("GENERAL_LETTER", "n", " ", "x")).toMatch(/title/)
    expect(templateProblem("REFERRAL", "n", "Reason", "")).toBeNull()
    expect(templateProblem("REFERRAL", "n", "", "")).toMatch(/reason/)
    expect(templateProblem("PRESCRIPTION_LETTER", "n", "T", "x")).toBeNull()
  })
})
