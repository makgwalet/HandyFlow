import { describe, it, expect } from "vitest"
import { bodyProblem, fileProblem, filterQueries, noteProblem, queryStatusLabel, requestStatusLabel, sortQueries, type StaffQuery } from "./contactRules"

const q = (id: string, status: string, last: string, subject = "S", client = "Acme"): StaffQuery => ({ id, clientId: "c", clientName: client, subject, status, raisedByName: null, createdAt: last, lastMessageAt: last, messageCount: 1 })

describe("contactRules", () => {
  it("puts queries waiting for us first, then newest", () => {
    const out = sortQueries([q("a", "CLOSED", "2027-03-05"), q("b", "OPEN", "2027-03-01"), q("c", "OPEN", "2027-03-03"), q("d", "ANSWERED", "2027-03-09")])
    expect(out.map(x => x.id)).toEqual(["c", "b", "d", "a"])
  })
  it("filters by status and search", () => {
    const list = [q("a", "OPEN", "x", "VAT query", "Acme"), q("b", "CLOSED", "x", "Other", "Beta")]
    expect(filterQueries(list, "OPEN", "").length).toBe(1)
    expect(filterQueries(list, "ALL", "beta").map(x => x.id)).toEqual(["b"])
  })
  it("wants a real reason and a real message", () => {
    expect(noteProblem("          ")).not.toBe("")
    expect(noteProblem("Received by email")).toBe("")
    expect(bodyProblem("  ")).not.toBe("")
    expect(bodyProblem("x".repeat(4001))).not.toBe("")
    expect(bodyProblem("Hello")).toBe("")
  })
  it("checks a file's kind, emptiness and size", () => {
    expect(fileProblem("a.PDF", 100)).toBe("")
    expect(fileProblem("a.exe", 100)).not.toBe("")
    expect(fileProblem("a", 100)).not.toBe("")
    expect(fileProblem("a.pdf", 0)).not.toBe("")
    expect(fileProblem("a.pdf", 11 * 1024 * 1024)).not.toBe("")
  })
  it("uses plain words for statuses", () => {
    expect(queryStatusLabel("OPEN")).toBe("Waiting for us")
    expect(requestStatusLabel("RECEIVED")).toBe("File received")
  })
})
