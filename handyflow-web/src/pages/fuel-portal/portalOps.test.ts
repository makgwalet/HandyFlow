import { describe, it, expect } from "vitest"
import { statusLabel, validateRequest, validateRegistration } from "./portalOps"

describe("portalOps", () => {
  it("labels a quote as a request", () => {
    expect(statusLabel("QUOTE")).toBe("Requested")
    expect(statusLabel("PART_DELIVERED")).toBe("Part delivered")
    expect(statusLabel("SOMETHING_NEW")).toBe("Something new")
  })
  it("validates a fuel request", () => {
    const today = new Date("2026-11-11T10:00:00")
    expect(validateRequest("", "100", "", today)).toMatch(/fuel type/)
    expect(validateRequest("DIESEL", "", "", today)).toMatch(/greater than zero/)
    expect(validateRequest("DIESEL", "0", "", today)).toMatch(/greater than zero/)
    expect(validateRequest("DIESEL", "500001", "", today)).toMatch(/500,000/)
    expect(validateRequest("DIESEL", "500000", "", today)).toBeNull()
    expect(validateRequest("DIESEL", "100", "2026-11-10", today)).toMatch(/past/)
    expect(validateRequest("DIESEL", "100", "2026-11-11", today)).toBeNull()
  })
  it("validates registration", () => {
    expect(validateRegistration("A", "password1", "password1", "")).toMatch(/missing/)
    expect(validateRegistration(" ", "password1", "password1", "t")).toMatch(/full name/)
    expect(validateRegistration("A", "short", "short", "t")).toMatch(/8 characters/)
    expect(validateRegistration("A", "password1", "password2", "t")).toMatch(/match/)
    expect(validateRegistration("A", "password1", "password1", "t")).toBeNull()
  })
})
