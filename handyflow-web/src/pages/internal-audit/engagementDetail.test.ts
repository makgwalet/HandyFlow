import { describe, it, expect } from "vitest"
import { filterWorkpapers, fileSize, nameMap, personName, phaseColor, workpaperRows, workpaperStatus } from "./engagementDetail.logic"
import type { WorkpaperFileRow } from "../../api/auditEngagementSummary.api"

const w = (o: Partial<WorkpaperFileRow>): WorkpaperFileRow => ({ id: "w", folderId: "f1", fileName: "Revenue schedule.xlsx", mimeType: null, fileSizeBytes: 2048, reviewStatus: "DRAFT", preparedBy: "u1", preparedAt: null, reviewedBy: null, reviewedAt: null, signedOffBy: null, signedOffAt: null, versionNumber: 1, supersededBy: null, createdAt: "2026-10-01T08:00:00Z", ...o })
const folders = [{ id: "f1", name: "Planning" }, { id: "f2", name: "Fieldwork" }]
const names = nameMap([{ id: "u1", firstName: "Sarah", lastName: "Johnson" }, { id: "u2", firstName: "Mike", lastName: "Brown" }])

describe("workpaper list", () => {
  it("joins folder and preparer names, leaves out replaced versions, and puts the latest change first", () => {
    const rows = workpaperRows(folders, {
      f1: [w({ id: "a", fileName: "A.pdf" }), w({ id: "old", fileName: "B.pdf", supersededBy: "new" })],
      f2: [w({ id: "c", fileName: "C.pdf", folderId: "f2", preparedAt: "2026-10-20T08:00:00Z", reviewStatus: "PREPARED" }), w({ id: "d", fileName: "D.pdf", folderId: "f2", signedOffAt: "2026-10-25T08:00:00Z", preparedBy: null })],
    }, names)
    expect(rows.map(r => r.id)).toEqual(["d", "c", "a"])
    expect(rows[1].folder).toBe("Fieldwork")
    expect(rows[1].preparer).toBe("Sarah Johnson")
    expect(rows[0].preparer).toBeNull()
    expect(workpaperRows(folders, {}, names)).toEqual([])
  })
  it("filters by status, folder and text", () => {
    const rows = workpaperRows(folders, { f1: [w({ id: "a", fileName: "Payroll.pdf" }), w({ id: "b", fileName: "Stores.pdf", reviewStatus: "SIGNED_OFF" })], f2: [w({ id: "c", folderId: "f2", fileName: "Fleet.pdf", preparedBy: "u2" })] }, names)
    const all = { status: "all", folder: "all", q: "" }
    expect(filterWorkpapers(rows, all)).toHaveLength(3)
    expect(filterWorkpapers(rows, { ...all, status: "SIGNED_OFF" }).map(r => r.id)).toEqual(["b"])
    expect(filterWorkpapers(rows, { ...all, folder: "f2" }).map(r => r.id)).toEqual(["c"])
    expect(filterWorkpapers(rows, { ...all, q: " mike " }).map(r => r.id)).toEqual(["c"])
    expect(filterWorkpapers(rows, { ...all, q: "fieldwork" }).map(r => r.id)).toEqual(["c"])
  })
})

describe("labels", () => {
  it("words the workpaper statuses", () => {
    expect(workpaperStatus("SIGNED_OFF").label).toBe("Approved")
    expect(workpaperStatus("PREPARED").label).toBe("In review")
    expect(workpaperStatus("ODD").label).toBe("ODD")
  })
  it("sizes files", () => {
    expect(fileSize(null)).toBe("")
    expect(fileSize(512)).toBe("512 B")
    expect(fileSize(310 * 1024)).toBe("310 KB")
    expect(fileSize(2.4 * 1024 * 1024)).toBe("2.4 MB")
  })
  it("names people and says when it does not know", () => {
    expect(personName("u2", names)).toBe("Mike Brown")
    expect(personName("zz", names)).toBe("Unknown user")
    expect(personName(null, names)).toBe("—")
  })
  it("colours phase bars", () => {
    const p = (pct: number) => ({ key: "planning", label: "x", pct, done: 0, total: 0, basis: "" }) as never
    expect(phaseColor(p(0))).toContain("border-strong")
    expect(phaseColor(p(40))).toContain("primary")
    expect(phaseColor(p(100))).toContain("success")
  })
})
