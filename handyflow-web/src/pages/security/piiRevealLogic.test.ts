import { describe, it, expect } from "vitest"
import { readFileSync } from "node:fs"
import { editableValue, isMasked, reasonProblem, revealBody, MIN_REASON } from "./piiRevealLogic"

describe("piiRevealLogic", () => {
  it("recognises masked values from the server", () => {
    expect(isMasked("850101*******")).toBe(true)
    expect(isMasked("••••5678")).toBe(true)
    expect(isMasked("8501015026088")).toBe(false)
    expect(isMasked(null)).toBe(false)
  })
  it("never puts a masked value into a form", () => {
    expect(editableValue("850101*******")).toBe("")
    expect(editableValue("•••5678")).toBe("")
    expect(editableValue(null)).toBe("")
    expect(editableValue("8501015026088")).toBe("8501015026088")
  })
  it("needs a real reason", () => {
    expect(reasonProblem("short")).not.toBeNull()
    expect(reasonProblem("   " + "x".repeat(MIN_REASON - 1) + "   ")).not.toBeNull()
    expect(reasonProblem("x".repeat(MIN_REASON))).toBeNull()
    expect(reasonProblem("x".repeat(501))).not.toBeNull()
  })
  it("sends the field and the trimmed reason", () => {
    expect(revealBody("ID_NUMBER", "  PSIRA renewal  ")).toEqual({ field: "ID_NUMBER", reason: "PSIRA renewal" })
  })
  it("the guards screen fills its edit forms through editableValue", () => {
    const src = readFileSync("src/pages/security/GuardsTab.tsx", "utf8")
    expect(src).toContain("idNumber: editableValue(g.idNumber)")
    expect(src).toContain("bankAccountNumber: editableValue(g.bankAccountNumber)")
    expect(src).not.toContain('idNumber: g.idNumber ?? ""')
  })
})
