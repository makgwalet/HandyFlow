import { describe, it, expect } from "vitest"
import { linkFor, typeCounts, filterItems, typeLabel, severityLabel, type QueueItem } from "./queueRules"

const item = (over: Partial<QueueItem>): QueueItem => ({ type: "DEADLINE", severity: "HIGH", clientId: "c1", clientName: "Acme", title: "Annual return", detail: "2 days overdue", dueDate: "2026-11-22", tab: "deadlines", ...over })

describe("linkFor", () => {
  it("opens the client on the right tab", () => expect(linkFor(item({}))).toBe("/complianceservices/clients/c1?tab=deadlines"))
  it("sends catalogue reviews to the catalogue", () => expect(linkFor(item({ type: "CATALOGUE_REVIEW", clientId: null, tab: "catalogue" }))).toBe("/complianceservices/catalogue"))
})

describe("typeCounts", () => {
  it("counts each type in a steady order", () => {
    const out = typeCounts([item({ type: "ASSESSMENT" }), item({}), item({}), item({ type: "DOCUMENT_REVIEW" })])
    expect(out).toEqual([["DEADLINE", 2], ["DOCUMENT_REVIEW", 1], ["ASSESSMENT", 1]])
  })
})

describe("filterItems", () => {
  const list = [item({}), item({ type: "DOCUMENT_REVIEW", clientName: "Beta", title: "Tax Clearance", detail: "Waiting" })]
  it("filters by type", () => expect(filterItems(list, "DOCUMENT_REVIEW", "")).toHaveLength(1))
  it("searches client, title and detail, ignoring case", () => {
    expect(filterItems(list, "ALL", "beta")).toHaveLength(1)
    expect(filterItems(list, "ALL", "overdue")).toHaveLength(1)
    expect(filterItems(list, "ALL", "  ")).toHaveLength(2)
  })
  it("handles items with no client", () => expect(filterItems([item({ clientName: null, clientId: null })], "ALL", "annual")).toHaveLength(1))
})

describe("labels", () => {
  it("names types and severities and falls back to the raw value", () => {
    expect(typeLabel("APPROVAL")).toBe("Approvals")
    expect(typeLabel("NEW")).toBe("NEW")
    expect(severityLabel("HIGH")).toBe("Urgent")
  })
})
