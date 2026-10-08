import { describe, it, expect } from "vitest"
import { greeting, groupQueue, inBuilding, rowAction, displayName } from "./dashboardView"

const it_ = (id: string, status: string, practitionerId = "d1") => ({ id, patientId: "p" + id, patientName: "P" + id, practitionerId, practitionerName: "Dr", scheduledAt: "2026-10-08T06:00:00Z", durationMinutes: 15, appointmentType: "CONSULTATION", status })

describe("greeting", () => {
  it("follows the South African clock, not the viewer's", () => {
    expect(greeting(new Date("2026-10-08T05:00:00Z"))).toBe("Good morning")    // 07:00 SAST
    expect(greeting(new Date("2026-10-08T10:00:00Z"))).toBe("Good afternoon")  // 12:00 SAST
    expect(greeting(new Date("2026-10-08T16:30:00Z"))).toBe("Good evening")    // 18:30 SAST
    expect(greeting(new Date("2026-10-08T22:30:00Z"))).toBe("Good morning")    // 00:30 SAST next day
  })
})

describe("groupQueue", () => {
  const items = [it_("1", "IN_PROGRESS"), it_("2", "TRIAGED"), it_("3", "CHECKED_IN", "d2"), it_("4", "CONFIRMED"), it_("5", "COMPLETED"), it_("6", "CANCELLED"), it_("7", "NO_SHOW")]
  it("puts each patient where they are and drops cancelled and no-shows", () => {
    const g = groupQueue(items)
    expect(g.map(x => [x.id, x.items.length])).toEqual([["with", 1], ["ready", 1], ["waiting", 1], ["expected", 1], ["done", 1]])
  })
  it("can show one doctor's patients only", () => {
    const g = groupQueue(items, "d1")
    expect(g.find(x => x.id === "waiting")!.items).toHaveLength(0)
    expect(g.find(x => x.id === "with")!.items).toHaveLength(1)
  })
  it("counts people in the building", () => expect(inBuilding(items)).toBe(2))
})

describe("rowAction", () => {
  it("front desk checks people in", () => {
    expect(rowAction("SCHEDULED", false, true)).toEqual({ kind: "check_in", label: "Check in" })
    expect(rowAction("CHECKED_IN", false, true)).toBeNull()
  })
  it("a clinician sees or resumes the patient", () => {
    expect(rowAction("TRIAGED", true, false)).toEqual({ kind: "open", label: "See patient" })
    expect(rowAction("IN_PROGRESS", true, false)).toEqual({ kind: "open", label: "Resume" })
    expect(rowAction("COMPLETED", true, true)).toBeNull()
    expect(rowAction("SCHEDULED", true, false)).toBeNull()
  })
})

describe("displayName", () => {
  it("uses Dr. plus the practitioner for a clinician, else the first name", () => {
    expect(displayName({ firstName: "Thabang" }, "Thabang Mokoena", true)).toBe("Dr. Thabang Mokoena")
    expect(displayName({ firstName: "Thabang" }, "Dr. Thabang Mokoena", true)).toBe("Dr. Thabang Mokoena")
    expect(displayName({ firstName: "Sipho" }, null, false)).toBe("Sipho")
    expect(displayName(null, null, false)).toBe("")
  })
})
