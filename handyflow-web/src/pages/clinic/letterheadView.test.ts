import { describe, it, expect } from "vitest"
import { choiceLabel, formFrom, kindLabel, linesFromText, linesToText, logoProblem, problems } from "./letterheadView"

const ok = { name: "Main", branchName: "", practiceName: "Sample Practice", linesText: "1 Road\nTel 000" }
const lh = { id: "1", name: "Main", branchName: null, practiceName: "P", lines: ["a", "b"], isDefault: true, active: true, hasLogo: false }

describe("letterheadView", () => {
  it("splits address text into lines and drops blanks", () => {
    expect(linesFromText(" a \r\n\n b\n  ")).toEqual(["a", "b"])
    expect(linesToText(["a", "b"])).toBe("a\nb")
  })
  it("accepts a complete form", () => { expect(problems(ok)).toEqual([]) })
  it("asks for a name and a practice name", () => {
    expect(problems({ ...ok, name: " ", practiceName: "" })).toHaveLength(2)
  })
  it("limits lines and lengths like the server", () => {
    expect(problems({ ...ok, linesText: "1\n2\n3\n4\n5\n6\n7" })[0]).toMatch(/at most 6/)
    expect(problems({ ...ok, linesText: "x".repeat(121) })[0]).toMatch(/longer than 120/)
    expect(problems({ ...ok, name: "x".repeat(121) })[0]).toMatch(/name is longer/)
  })
  it("checks a logo by type and size", () => {
    expect(logoProblem({ type: "image/png", size: 1000 })).toBeNull()
    expect(logoProblem({ type: "image/jpeg", size: 300 * 1024 })).toBeNull()
    expect(logoProblem({ type: "image/gif", size: 1000 })).toMatch(/PNG or JPEG/)
    expect(logoProblem({ type: "image/png", size: 300 * 1024 + 1 })).toMatch(/larger than 300 KB/)
    expect(logoProblem({ type: "image/png", size: 0 })).toMatch(/empty/)
  })
  it("describes each letterhead in plain words", () => {
    expect(kindLabel(lh)).toMatch(/used when none is chosen/)
    expect(kindLabel({ ...lh, isDefault: false })).toBe("Practice-wide")
    expect(kindLabel({ ...lh, branchName: "North", isDefault: false })).toBe("Branch: North")
    expect(choiceLabel(lh)).toBe("Main (default)")
    expect(choiceLabel({ ...lh, isDefault: false, branchName: "North" })).toBe("Main (North)")
  })
  it("fills a form from a letterhead, or empty for a new one", () => {
    expect(formFrom(lh)).toEqual({ name: "Main", branchName: "", practiceName: "P", linesText: "a\nb" })
    expect(formFrom(null).name).toBe("")
  })
})
