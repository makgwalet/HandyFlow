import { describe, it, expect } from "vitest"
import { onlyTopics, topicSummary } from "./alertOps"

describe("alertOps", () => {
  const users = [{ id: "u1", name: "Thabo M" }]
  it("says administrators only when nobody was added", () => {
    expect(topicSummary({ topic: "VARIANCE", label: "Delivery variances", userIds: [] }, users)).toBe("Administrators only")
  })
  it("names the people added, and falls back to a short id for unknown users", () => {
    expect(topicSummary({ topic: "VARIANCE", label: "x", userIds: ["u1", "abcdef123456"] }, users)).toBe("Administrators and Thabo M, User abcdef12")
  })
  it("keeps only rows shaped like alert topics", () => {
    expect(onlyTopics([{ id: "x" }, { topic: "VARIANCE", label: "L", userIds: [] }])).toHaveLength(1)
    expect(onlyTopics({})).toEqual([])
  })
})
