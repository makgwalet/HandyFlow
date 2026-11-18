import { describe, it, expect } from "vitest"
import { hoursText, validateApprove, validateArrival, validateOffline, validateOverdue, validateRelease } from "./opsPolicyOps"

describe("opsPolicyOps", () => {
  it("arrival hours: whole, 1 to 72", () => {
    expect(validateArrival("12")).toBeNull(); expect(validateArrival("1")).toBeNull(); expect(validateArrival("72")).toBeNull()
    expect(validateArrival("0")).toMatch(/between 1 and 72/); expect(validateArrival("73")).toMatch(/between 1 and 72/)
    expect(validateArrival("")).toMatch(/whole number/); expect(validateArrival("1.5")).toMatch(/whole number/); expect(validateArrival("abc")).toMatch(/whole number/)
  })
  it("overdue minutes: whole, 15 to 1440, both boxes", () => {
    expect(validateOverdue("120", "60")).toBeNull(); expect(validateOverdue("15", "1440")).toBeNull()
    expect(validateOverdue("14", "60")).toMatch(/grace must be between/); expect(validateOverdue("120", "1441")).toMatch(/escalation delay must be between/)
    expect(validateOverdue("x", "60")).toMatch(/whole minutes/)
  })
  it("offline limits: 1 to 336 hours, 0 to 60 minutes", () => {
    expect(validateOffline("72", "5")).toBeNull(); expect(validateOffline("336", "0")).toBeNull()
    expect(validateOffline("0", "5")).toMatch(/between 1 and 336/); expect(validateOffline("337", "5")).toMatch(/between 1 and 336/)
    expect(validateOffline("72", "61")).toMatch(/between 0 and 60/); expect(validateOffline("72", "-1")).toMatch(/whole minutes/)
  })
  it("release needs a reason and an explanation of at least 10 characters", () => {
    expect(validateRelease("", "long enough text")).toMatch(/reason/)
    expect(validateRelease("SITE_QUEUE", "too short")).toMatch(/10 characters/)
    expect(validateRelease("SITE_QUEUE", "Queue at the gate for two hours")).toBeNull()
  })
  it("a POD approval needs a kind and a reason of at least 10 characters", () => {
    expect(validateApprove("", "a good enough reason")).toMatch(/kind/); expect(validateApprove("AFTER_HOURS_APPROVED", "short")).toMatch(/10 characters/)
    expect(validateApprove("AFTER_HOURS_APPROVED", "Customer closed, tank checked by CCTV")).toBeNull()
  })
  it("reads long waits in days", () => { expect(hoursText(72)).toBe("3 days"); expect(hoursText(12)).toBe("12 hours"); expect(hoursText(1)).toBe("1 hour"); expect(hoursText(50)).toBe("50 hours") })
})
