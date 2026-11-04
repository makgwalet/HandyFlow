import { describe, it, expect } from "vitest"
import { serviceStatus } from "./serviceStatus"

const m = (cur: number, last: number, every?: number | null) => ({ currentHours: cur, lastServiceHours: last, serviceIntervalHours: every })

describe("serviceStatus", () => {
  it("is healthy well inside the interval", () => {
    const s = serviceStatus(m(1100, 1000, 250))
    expect(s.state).toBe("HEALTHY"); expect(s.used).toBe(100); expect(s.pct).toBe(40)
  })
  it("warns from 80% of the interval", () => {
    expect(serviceStatus(m(1200, 1000, 250)).state).toBe("APPROACHING")
  })
  it("is due at the interval and overdue 20% past it", () => {
    expect(serviceStatus(m(1250, 1000, 250)).state).toBe("DUE")
    expect(serviceStatus(m(1299, 1000, 250)).state).toBe("DUE")
    expect(serviceStatus(m(1300, 1000, 250)).state).toBe("OVERDUE")
  })
  it("caps the bar at 100", () => {
    expect(serviceStatus(m(5000, 1000, 250)).pct).toBe(100)
  })
  it("uses 250 when no interval is set", () => {
    expect(serviceStatus(m(100, 0, null)).interval).toBe(250)
    expect(serviceStatus(m(100, 0, 0)).interval).toBe(250)
  })
  it("says to check the meter when it reads below the last service", () => {
    const s = serviceStatus(m(300, 5000, 250))
    expect(s.state).toBe("CHECK_METER"); expect(s.pct).toBe(0); expect(s.tone).toBe("unknown")
  })
  it("accepts numbers sent as text", () => {
    expect(serviceStatus({ currentHours: "1250", lastServiceHours: "1000", serviceIntervalHours: "250" }).state).toBe("DUE")
  })
})
