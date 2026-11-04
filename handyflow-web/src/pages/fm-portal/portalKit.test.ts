import { describe, it, expect } from "vitest"
import { greetingName, initialOf } from "./portalKit"

describe("greetingName", () => {
  it("uses the first name of a full name", () => expect(greetingName("Naledi Khumalo")).toBe("Naledi"))
  it("reads a name out of an email, including a +tag", () => {
    expect(greetingName("makgwalet+naledi@gmail.com")).toBe("Naledi")
    expect(greetingName("thabo.molefe@zeta.co.za")).toBe("Thabo")
  })
  it("is empty when there is nothing to use", () => {
    expect(greetingName("")).toBe("")
    expect(greetingName(null)).toBe("")
    expect(greetingName("@x.com")).toBe("")
  })
  it("gives an initial, or a question mark", () => {
    expect(initialOf("naledi k")).toBe("N")
    expect(initialOf(undefined)).toBe("?")
  })
})
