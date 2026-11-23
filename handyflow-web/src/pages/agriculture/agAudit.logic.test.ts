import { describe, it, expect } from "vitest"
import { auditText, byText } from "./agAudit.logic"

describe("auditText", () => {
  it("names who added it and who last changed it", () => {
    expect(auditText({ createdByName: "Thandi", createdAt: "2026-11-02T08:00:00Z", updatedByName: "Sipho", updatedAt: "2026-11-20T10:00:00Z" }))
      .toBe("Added by Thandi on 2026-11-02 · Last changed by Sipho on 2026-11-20")
  })
  it("leaves the change out when the same person added it that day", () => {
    expect(auditText({ createdByName: "Thandi", createdAt: "2026-11-02T08:00:00Z", updatedByName: "Thandi", updatedAt: "2026-11-02T09:00:00Z" })).toBe("Added by Thandi on 2026-11-02")
  })
  it("keeps the change when the same person changed it on a later day", () => {
    expect(auditText({ createdByName: "Thandi", createdAt: "2026-11-02T08:00:00Z", updatedByName: "Thandi", updatedAt: "2026-11-05T09:00:00Z" })).toBe("Added by Thandi on 2026-11-02 · Last changed by Thandi on 2026-11-05")
  })
  it("says only when it was added for records from before names were kept", () => {
    expect(auditText({ createdByName: null, createdAt: "2026-01-02T08:00:00Z", updatedByName: null, updatedAt: "2026-03-01T08:00:00Z" })).toBe("Added 2026-01-02")
  })
  it("shows a later change by a named person even when the creator is unknown", () => {
    expect(auditText({ createdByName: null, createdAt: "2026-01-02T08:00:00Z", updatedByName: "Sipho", updatedAt: "2026-03-01T08:00:00Z" })).toBe("Added 2026-01-02 · Last changed by Sipho on 2026-03-01")
  })
  it("is empty when nothing is known", () => {
    expect(auditText(null)).toBe("")
    expect(auditText(undefined)).toBe("")
    expect(auditText({ createdByName: null, createdAt: null, updatedByName: null, updatedAt: null })).toBe("")
  })
})

describe("byText", () => {
  it("adds the name for a row", () => {
    expect(byText({ createdByName: "Thandi", createdAt: null, updatedByName: null, updatedAt: null })).toBe(" · by Thandi")
  })
  it("adds nothing when nobody is recorded", () => {
    expect(byText({ createdByName: null, createdAt: "2026-01-01", updatedByName: null, updatedAt: null })).toBe("")
    expect(byText(undefined)).toBe("")
    expect(byText(null)).toBe("")
  })
})
