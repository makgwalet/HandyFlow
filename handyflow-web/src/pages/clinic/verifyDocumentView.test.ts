import { describe, it, expect } from "vitest"
import { headline, looksComplete, outcome, tidyCode } from "./verifyDocumentView"

describe("verifyDocumentView", () => {
  it("tidies a typed or pasted code into groups of four", () => {
    expect(tidyCode("abcdefghjkmnpqrs")).toBe("ABCD-EFGH-JKMN-PQRS")
    expect(tidyCode(" abcd efgh-jkmn pqrs ")).toBe("ABCD-EFGH-JKMN-PQRS")
    expect(tidyCode("ab")).toBe("AB")
    expect(tidyCode("abcdefghjkmnpqrstuvw")).toBe("ABCD-EFGH-JKMN-PQRS")
  })
  it("knows when a code is complete", () => {
    expect(looksComplete("ABCD-EFGH-JKMN-PQRS")).toBe(true)
    expect(looksComplete("ABCD-EFGH")).toBe(false)
  })
  it("sorts the answer into three outcomes", () => {
    expect(outcome({ valid: true, voided: false })).toBe("GENUINE")
    expect(outcome({ valid: true, voided: true })).toBe("WITHDRAWN")
    expect(outcome({ valid: false, voided: false })).toBe("UNKNOWN")
    expect(outcome(null)).toBe("UNKNOWN")
  })
  it("tells a withdrawn document not to be relied on", () => {
    expect(headline("WITHDRAWN")).toMatch(/Do not rely on it/)
    expect(headline("UNKNOWN")).toMatch(/No document matches/)
    expect(headline("GENUINE")).toMatch(/issued by the practice\./)
  })
})
