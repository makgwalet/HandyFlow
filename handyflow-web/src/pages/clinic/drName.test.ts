import { describe, expect, it } from "vitest"
import { drName } from "./drName"

describe("drName", () => {
  it("adds one Dr. to a plain name", () => expect(drName("Sarah Mokoena")).toBe("Dr. Sarah Mokoena"))
  it("does not double a stored Dr or Dr.", () => {
    expect(drName("Dr Sarah Mokoena")).toBe("Dr. Sarah Mokoena")
    expect(drName("Dr. Sarah Mokoena")).toBe("Dr. Sarah Mokoena")
    expect(drName("  dr.  Sarah Mokoena ")).toBe("Dr. Sarah Mokoena")
    expect(drName("Dr.Sarah Mokoena")).toBe("Dr. Sarah Mokoena")
  })
  it("treats Drew as a name, not a title", () => expect(drName("Drew Smith")).toBe("Dr. Drew Smith"))
  it("is empty for no name", () => {
    expect(drName(null)).toBe("")
    expect(drName("  ")).toBe("")
    expect(drName("Dr")).toBe("")
  })
})
