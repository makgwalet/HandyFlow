import { describe, it, expect } from "vitest"
import { detectDelimiter, doneMessage, importButtonLabel, parseCsv, readAnimalCsv, resultSummary, rowsNeedingAttention, TEMPLATE_CSV, type ImportResult } from "./agImport.logic"

describe("parseCsv", () => {
  it("splits commas, CRLF and a final line without a newline", () => {
    expect(parseCsv("a,b\r\n1,2\r\n3,4")).toEqual([["a", "b"], ["1", "2"], ["3", "4"]])
  })
  it("keeps commas, doubled quotes and line breaks inside quotes", () => {
    expect(parseCsv('a,b\n"x, y","say ""hi"""\n"two\nlines",z')).toEqual([["a", "b"], ["x, y", 'say "hi"'], ["two\nlines", "z"]])
  })
  it("drops a byte-order mark", () => { expect(parseCsv("\uFEFFa,b\n1,2")[0]).toEqual(["a", "b"]) })
  it("keeps empty cells", () => { expect(parseCsv("a,b,c\n1,,3")[1]).toEqual(["1", "", "3"]) })
})

describe("detectDelimiter", () => {
  it("finds comma, semicolon and tab", () => {
    expect(detectDelimiter("a,b,c")).toBe(",")
    expect(detectDelimiter("a;b;c")).toBe(";")
    expect(detectDelimiter("a\tb\tc")).toBe("\t")
  })
  it("ignores delimiters inside quotes and prefers comma on a tie", () => {
    expect(detectDelimiter('"a;b;c",d')).toBe(",")
    expect(detectDelimiter("a")).toBe(",")
  })
  it("reads a semicolon file (South African Excel)", () => {
    const r = readAnimalCsv("Tag;Species;Sex\n1;Cattle;F")
    expect(r.problems).toEqual([])
    expect(r.rows[0]).toMatchObject({ tagNumber: "1", species: "Cattle", sex: "F" })
  })
})

describe("readAnimalCsv", () => {
  it("reads the template as shipped", () => {
    const r = readAnimalCsv(TEMPLATE_CSV)
    expect(r.problems).toEqual([])
    expect(r.rows).toHaveLength(2)
    expect(r.rows[0]).toEqual({ tagNumber: "1001", name: "Daisy", species: "Cattle", sex: "Female", breed: "Nguni", dateOfBirth: "2024-03-15", area: "North camp", acquisitionType: "Born on farm", acquisitionDate: "" })
    expect(r.rows[1].acquisitionDate).toBe("2024-01-10")
  })
  it("accepts headers in any order, case and the usual spellings", () => {
    const r = readAnimalCsv("SEX,Ear Tag,Animal,DOB,Camp\nM,77,Sheep,2025-01-02,Kraal")
    expect(r.problems).toEqual([])
    expect(r.rows[0]).toMatchObject({ tagNumber: "77", species: "Sheep", sex: "M", dateOfBirth: "2025-01-02", area: "Kraal" })
  })
  it("lists unused columns", () => {
    expect(readAnimalCsv("Tag,Species,Sex,Colour,Weight\n1,Cattle,F,Red,300").ignored).toEqual(["Colour", "Weight"])
  })
  it("names the missing columns", () => {
    const r = readAnimalCsv("Tag,Breed\n1,Nguni")
    expect(r.rows).toEqual([])
    expect(r.problems[0]).toBe("The first row must name these columns: Species, Sex.")
  })
  it("refuses an empty file, a header only, and too many rows", () => {
    expect(readAnimalCsv("").problems).toEqual(["The file is empty."])
    expect(readAnimalCsv("  \n\n").problems).toEqual(["The file is empty."])
    expect(readAnimalCsv("Tag,Species,Sex").problems).toEqual(["The file has a header but no animals."])
    const big = "Tag,Species,Sex\n" + Array.from({ length: 501 }, (_, i) => `${i},Cattle,F`).join("\n")
    expect(readAnimalCsv(big).problems[0]).toMatch(/at most 500 animals/)
    const ok = "Tag,Species,Sex\n" + Array.from({ length: 500 }, (_, i) => `${i},Cattle,F`).join("\n")
    expect(readAnimalCsv(ok).rows).toHaveLength(500)
  })
  it("skips blank lines and trims cells", () => {
    const r = readAnimalCsv("Tag,Species,Sex\n\n  5 , Cattle , F \n,,\n")
    expect(r.rows).toEqual([expect.objectContaining({ tagNumber: "5", species: "Cattle", sex: "F" })])
  })
  it("uses the first of two columns that mean the same thing", () => {
    const r = readAnimalCsv("Tag,ID,Species,Sex\n1,99,Cattle,F")
    expect(r.rows[0].tagNumber).toBe("1")
    expect(r.ignored).toEqual(["ID"])
  })
})

describe("results", () => {
  const res = (o: Partial<ImportResult> = {}): ImportResult => ({
    dryRun: true, total: 4, ready: 2, duplicates: 1, errors: 1,
    rows: [
      { line: 1, tagNumber: "1", status: "OK", message: null }, { line: 2, tagNumber: "2", status: "DUPLICATE", message: "dup" },
      { line: 3, tagNumber: "3", status: "ERROR", message: "bad" }, { line: 4, tagNumber: "4", status: "OK", message: null },
    ], ...o,
  })
  it("sums up", () => {
    expect(resultSummary(res())).toBe("2 ready, 1 already on the farm or repeated, 1 with a problem.")
    expect(resultSummary(res({ duplicates: 0, errors: 0 }))).toBe("2 ready.")
  })
  it("labels the button, or hides it when nothing is ready", () => {
    expect(importButtonLabel(res())).toBe("Import 2 animals")
    expect(importButtonLabel(res({ ready: 1 }))).toBe("Import 1 animal")
    expect(importButtonLabel(res({ ready: 0 }))).toBeNull()
    expect(importButtonLabel(null)).toBeNull()
  })
  it("lists only what needs attention, in file order", () => {
    expect(rowsNeedingAttention(res()).map(r => r.line)).toEqual([2, 3])
  })
  it("says what was done", () => {
    expect(doneMessage(res({ dryRun: false }))).toBe("2 animals imported, 2 skipped.")
    expect(doneMessage(res({ ready: 1, duplicates: 0, errors: 0 }))).toBe("1 animal imported.")
  })
})
