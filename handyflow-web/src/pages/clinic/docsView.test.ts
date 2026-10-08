import { describe, it, expect } from "vitest"
import { fileProblem, kindCounts, sizeLabel, sourceLabel, typeLabel, uploadProblem, type RegisterItem } from "./docsView"

const item = (o: Partial<RegisterItem>): RegisterItem => ({ origin: "STORED", id: "1", docType: "LETTER", source: "UPLOADED", title: "t", downloadPath: "/x", removable: true, ...o })
const pdf = { name: "a.pdf", size: 1000, type: "application/pdf" }

describe("documents view", () => {
  it("words types and sources plainly, and shows an unknown type as words", () => {
    expect(typeLabel("PAPER_NOTES")).toBe("Paper notes"); expect(typeLabel("X_RAY_FILM")).toBe("x ray film")
    expect(sourceLabel(item({}))).toBe("Uploaded"); expect(sourceLabel(item({ source: "ISSUED" }))).toBe("Issued here"); expect(sourceLabel(item({ origin: "RECORD", source: "RECORD" }))).toBe("From the record")
  })
  it("sizes", () => { expect(sizeLabel(null)).toBe(""); expect(sizeLabel(500)).toBe("500 B"); expect(sizeLabel(2048)).toBe("2 KB"); expect(sizeLabel(3 * 1024 * 1024)).toBe("3.0 MB") })
  it("refuses a missing, empty, huge or wrong-type file", () => {
    expect(fileProblem(null)).toMatch(/Choose a file/)
    expect(fileProblem({ ...pdf, size: 0 })).toMatch(/empty/)
    expect(fileProblem({ ...pdf, size: 11 * 1024 * 1024 })).toMatch(/10 MB/)
    expect(fileProblem({ name: "a.exe", size: 5, type: "application/x-msdownload" })).toMatch(/Only PDF/)
    expect(fileProblem(pdf)).toBeNull()
    expect(fileProblem({ name: "a.jpg", size: 5, type: "" })).toBeNull()
  })
  it("needs a type, a title and a date that is not in the future", () => {
    const base = { file: pdf, type: "LETTER", title: "Letter", date: "2026-10-01" }
    expect(uploadProblem(base, "2026-10-08")).toBeNull()
    expect(uploadProblem({ ...base, type: "" }, "2026-10-08")).toMatch(/kind of document/)
    expect(uploadProblem({ ...base, title: " " }, "2026-10-08")).toMatch(/title/)
    expect(uploadProblem({ ...base, date: "" }, "2026-10-08")).toMatch(/date/)
    expect(uploadProblem({ ...base, date: "2026-10-09" }, "2026-10-08")).toMatch(/future/)
  })
  it("counts the kinds present in a fixed order", () => {
    expect(kindCounts([item({ docType: "LAB_REPORT" }), item({ docType: "SICK_NOTE" }), item({ docType: "SICK_NOTE" })])).toEqual([{ type: "SICK_NOTE", count: 2 }, { type: "LAB_REPORT", count: 1 }])
  })
})
