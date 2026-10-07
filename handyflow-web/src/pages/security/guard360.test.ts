import { describe, expect, it } from "vitest"
import { attentionItems, completionRate, daysBetween, expiryState, fileChecklist, screeningMatrix, todayIso, type ScreeningItem } from "./guard360.logic"

const TODAY = "2026-10-07"
const rec = (o: Partial<ScreeningItem>): ScreeningItem => ({ id: "r", screeningType: "CRIMINAL_RECORD_CHECK", reason: "ONBOARDING", result: "PASS", conductedBy: null, conductedAt: null, nextDueAt: null, reportRef: null, createdAt: "2026-01-01T00:00:00Z", ...o })

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

describe("screeningMatrix", () => {
  it("uses the newest record of each type", () => {
    const m = screeningMatrix([rec({ id: "old", result: "FAIL", createdAt: "2026-01-01T00:00:00Z" }), rec({ id: "new", result: "PASS", createdAt: "2026-06-01T00:00:00Z" })], TODAY)
    expect(m.find(r => r.type === "CRIMINAL_RECORD_CHECK")?.record?.id).toBe("new")
    expect(m.find(r => r.type === "CRIMINAL_RECORD_CHECK")?.state).toBe("Passed")
  })
  it("shows types with no record as not on file, and Other only when present", () => {
    const m = screeningMatrix([], TODAY)
    expect(m).toHaveLength(6)
    expect(m.every(r => r.state === "Not on file")).toBe(true)
    expect(screeningMatrix([rec({ screeningType: "OTHER" })], TODAY)).toHaveLength(7)
  })
  it("marks a pass whose renewal date has gone as overdue", () => {
    const r = screeningMatrix([rec({ nextDueAt: "2026-09-29" })], TODAY)[0]
    expect(r.tone).toBe("bad"); expect(r.state).toBe("Renewal overdue by 8 days")
  })
  it("warns for pending, inconclusive and renewals within 30 days; fails are bad", () => {
    const t = (o: Partial<ScreeningItem>) => screeningMatrix([rec(o)], TODAY)[0].tone
    expect(t({ result: "PENDING" })).toBe("warn"); expect(t({ result: "INCONCLUSIVE" })).toBe("warn")
    expect(t({ nextDueAt: "2026-10-20" })).toBe("warn"); expect(t({ result: "FAIL" })).toBe("bad")
  })
})

describe("fileChecklist and attentionItems", () => {
  const docs = [{ id: "d", category: "ID_COPY", fileUrl: "x", fileName: null, notes: null, createdAt: "2026-01-01T00:00:00Z" }]
  it("ticks only categories that have a document", () => {
    const c = fileChecklist(docs)
    expect(c.find(x => x.category === "ID_COPY")?.present).toBe(true)
    expect(c.filter(x => !x.present)).toHaveLength(4)
  })
  it("lists the worst items first and includes the gate warning", () => {
    const items = attentionItems({ psiraExpiry: "2026-09-29", psiraNumber: "123", screening: screeningMatrix([rec({ result: "FAIL" })], TODAY), docs, gate: "Guard has a FAILED screening", today: TODAY })
    expect(items[0].tone).toBe("bad")
    expect(items.some(i => i.text.startsWith("PSiRA: expired 8 days ago"))).toBe(true)
    expect(items.some(i => i.text === "Guard has a FAILED screening")).toBe(true)
    expect(items.some(i => i.text.startsWith("Not in the guard file:"))).toBe(true)
  })
  it("reports a missing PSiRA number as a problem", () => {
    expect(attentionItems({ psiraExpiry: null, psiraNumber: null, screening: [], docs, gate: null, today: TODAY })[0].text).toBe("No PSiRA number on file")
  })
})

describe("completionRate", () => {
  it("is null when nothing started and rounds otherwise", () => { expect(completionRate(0, 0)).toBeNull(); expect(completionRate(3, 2)).toBe(67) })
})
