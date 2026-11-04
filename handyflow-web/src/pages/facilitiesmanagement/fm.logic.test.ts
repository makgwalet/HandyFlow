import { describe, it, expect } from "vitest"
import { addressLine, assetBody, assetMoves, assetProblem, assignBody, assignProblem, canCostLines, clientBody, clientProblem, completeBody, completeProblem, costBody, costProblem, costTotals, emptyAsset, emptyClient, emptyComplete, emptyCost, emptyOrder, emptyRequest, emptySite, orderProblem, requestBody, requestProblem, siteBody, siteProblem, slaNote, warrantyNote, whoFrom, woActions, type Who } from "./fm.logic"

const MANAGER: Who = { manage: true, assign: true, execute: true }
const TECH: Who = { manage: false, assign: false, execute: true }
const DISPATCH: Who = { manage: false, assign: true, execute: false }
const NOW = new Date("2026-11-04T10:00:00")

describe("work order actions", () => {
  it("requests are triaged, not worked", () => {
    expect(woActions("REQUESTED", false, MANAGER)).toEqual(["approve", "reject", "cancel"])
    expect(woActions("REQUESTED", false, TECH)).toEqual([])
  })
  it("open work is assigned or cancelled; assigned and running work can be done by a technician", () => {
    expect(woActions("OPEN", false, DISPATCH)).toEqual(["assign", "cancel"])
    expect(woActions("ASSIGNED", false, TECH)).toEqual(["start", "complete"])
    expect(woActions("IN_PROGRESS", false, TECH)).toEqual(["hold", "complete"])
    expect(woActions("ON_HOLD", false, MANAGER)).toEqual(["assign", "start", "complete", "cancel"])
  })
  it("work awaiting a check can only be verified or sent back, by someone who triages", () => {
    expect(woActions("PENDING_VERIFICATION", false, MANAGER)).toEqual(["verify", "rework"])
    expect(woActions("PENDING_VERIFICATION", false, TECH)).toEqual([])
  })
  it("finished work offers nothing, and invoiced work cannot be cancelled", () => {
    for (const s of ["COMPLETED", "CANCELLED", "REJECTED"]) expect(woActions(s, false, MANAGER)).toEqual([])
    expect(woActions("IN_PROGRESS", true, MANAGER)).not.toContain("cancel")
  })
  it("reads the authorities the way the server does", () => {
    const has = (p: string) => p === "FACILITIESMANAGEMENT_WORKORDER_ASSIGN"
    expect(whoFrom(has)).toEqual({ manage: false, assign: true, execute: false })
    expect(whoFrom(p => p === "FACILITIESMANAGEMENT_ADMIN")).toEqual(MANAGER)
    expect(whoFrom(p => p === "FACILITIESMANAGEMENT_TECHNICIAN")).toEqual(TECH)
  })
  it("allows cost lines only while the job is being done", () => {
    expect(canCostLines("IN_PROGRESS")).toBe(true)
    expect(canCostLines("OPEN")).toBe(false)
    expect(canCostLines("COMPLETED")).toBe(false)
  })
})

describe("SLA notes", () => {
  it("shows the verdict once there is one", () => {
    expect(slaNote(null, "MET", true, NOW)).toEqual({ text: "Met", tone: "success" })
    expect(slaNote(null, "BREACHED", true, NOW)?.tone).toBe("danger")
  })
  it("counts down to the due time and flags overdue", () => {
    expect(slaNote("2026-11-04T09:00:00", null, false, NOW)).toEqual({ text: "Overdue", tone: "danger" })
    expect(slaNote("2026-11-04T12:00:00", null, false, NOW)).toEqual({ text: "Due in 2 h", tone: "warning" })
    expect(slaNote("2026-11-07T10:00:00", null, false, NOW)).toEqual({ text: "Due in 3 d", tone: "neutral" })
    expect(slaNote(null, null, false, NOW)).toBeNull()
    expect(slaNote("2026-11-03T10:00:00", null, true, NOW)).toBeNull()
  })
})

describe("raising and creating work", () => {
  it("needs a client, a site and a description", () => {
    expect(requestProblem(emptyRequest())).toMatch(/client/)
    expect(requestProblem({ ...emptyRequest(), clientId: "c" })).toMatch(/site/)
    expect(requestProblem({ ...emptyRequest(), clientId: "c", siteId: "s" })).toMatch(/Describe/)
    expect(requestProblem({ ...emptyRequest(), clientId: "c", siteId: "s", description: "Leak" })).toBeNull()
    expect(orderProblem({ ...emptyOrder(), clientId: "c", siteId: "s" })).toMatch(/Describe/)
  })
  it("sends blanks as null", () => {
    expect(requestBody({ ...emptyRequest(), clientId: "c", siteId: "s", description: " Leak " })).toMatchObject({ assetId: null, reportedBy: null, description: "Leak", safetyImpact: false })
  })
  it("assigns to a technician or a vendor, not both", () => {
    expect(assignProblem("", "")).toMatch(/Choose/)
    expect(assignProblem("t", "v")).toMatch(/not both/)
    expect(assignProblem("t", "")).toBeNull()
    expect(assignBody("t", "", [{ id: "t", name: "Sipho" }], [])).toEqual({ technicianId: "t", technicianName: "Sipho", vendorId: null, vendorName: null })
  })
})

describe("completing work", () => {
  const f = { ...emptyComplete(), completionNotes: "Replaced belt" }
  it("needs notes and sensible numbers", () => {
    expect(completeProblem(emptyComplete(), null, NOW)).toMatch(/what was done/)
    expect(completeProblem({ ...f, cost: "-1" }, null, NOW)).toMatch(/cost/)
    expect(completeProblem({ ...f, downtimeMinutes: "1.5" }, null, NOW)).toMatch(/whole number/)
    expect(completeProblem(f, null, NOW)).toBeNull()
  })
  it("keeps the finish time out of the future and after the work order was raised", () => {
    expect(completeProblem({ ...f, completedAt: "2026-11-05T10:00" }, null, NOW)).toMatch(/future/)
    expect(completeProblem({ ...f, completedAt: "2026-11-01T10:00" }, "2026-11-02T08:00:00", NOW)).toMatch(/before the work order/)
    expect(completeProblem({ ...f, completedAt: "2026-11-04T09:00" }, "2026-11-02T08:00:00", NOW)).toBeNull()
  })
  it("builds the request", () => {
    expect(completeBody({ ...f, cost: "450.5", downtimeMinutes: "30" })).toMatchObject({ completionNotes: "Replaced belt", cost: 450.5, downtimeMinutes: 30, completedAt: null, failureCause: null })
  })
})

describe("cost lines", () => {
  const f = { ...emptyCost(), description: "Call-out", unitCost: "100" }
  it("checks the fields", () => {
    expect(costProblem(emptyCost())).toMatch(/Describe/)
    expect(costProblem({ ...f, quantity: "0" })).toMatch(/quantity/)
    expect(costProblem({ ...f, unitCost: "" })).toMatch(/unit cost/)
    expect(costProblem({ ...f, unitCharge: "-2" })).toMatch(/charge/)
    expect(costProblem(f)).toBeNull()
  })
  it("works out cost and charge, charging the cost again when no charge is set", () => {
    expect(costTotals({ ...f, quantity: "2.5" })).toEqual({ cost: 250, charge: 250 })
    expect(costTotals({ ...f, quantity: "2", unitCharge: "150" })).toEqual({ cost: 200, charge: 300 })
    expect(costTotals({ ...f, billable: false })).toEqual({ cost: 100, charge: 0 })
    expect(costBody({ ...f, unitCharge: "" }).unitCharge).toBeNull()
  })
})

describe("clients, sites and assets", () => {
  it("checks a client", () => {
    expect(clientProblem(emptyClient())).toMatch(/trading name/)
    expect(clientProblem({ ...emptyClient(), tradingName: "A", contactEmail: "x" })).toMatch(/email/)
    expect(clientProblem({ ...emptyClient(), tradingName: "A", vatNumber: "123" })).toMatch(/10 digits/)
    expect(clientProblem({ ...emptyClient(), tradingName: "A", vatNumber: "4123 456 789" })).toBeNull()
    expect(clientBody({ ...emptyClient(), tradingName: " A ", vatNumber: "4123 456 789" })).toMatchObject({ tradingName: "A", vatNumber: "4123456789", contactEmail: null })
  })
  it("builds a site with only the address parts that were filled in", () => {
    const f = { ...emptySite(), clientId: "c", name: " HQ ", street: "1 Main", city: "Pretoria" }
    expect(siteProblem(emptySite(), true)).toMatch(/client/)
    expect(siteProblem({ ...emptySite(), name: "HQ" }, false)).toBeNull()
    expect(siteBody(f, true)).toMatchObject({ clientId: "c", name: "HQ", address: { street: "1 Main", city: "Pretoria" } })
    expect(siteBody(f, false)).not.toHaveProperty("clientId")
    expect(addressLine({ street: "1 Main", city: "Pretoria", postalCode: "0002" })).toBe("1 Main, Pretoria, 0002")
    expect(addressLine(null)).toBe("")
  })
  it("checks an asset and sends only editable fields when editing", () => {
    expect(assetProblem(emptyAsset(), true)).toMatch(/site/)
    expect(assetProblem({ ...emptyAsset(), siteId: "s", name: "Chiller" }, true)).toMatch(/kind/)
    expect(assetProblem({ ...emptyAsset(), name: "Chiller", installDate: "2026-01-10", warrantyExpiryDate: "2025-01-01" }, false)).toMatch(/warranty/)
    const f = { ...emptyAsset(), siteId: "s", name: "Chiller", assetType: "HVAC", installDate: "2026-01-10" }
    expect(assetBody(f, true)).toMatchObject({ siteId: "s", assetType: "HVAC", installDate: "2026-01-10" })
    expect(assetBody(f, false)).not.toHaveProperty("siteId")
    expect(assetBody(f, false)).not.toHaveProperty("assetType")
  })
  it("moves an asset between working states, never out of decommissioned", () => {
    expect(assetMoves("OPERATIONAL")).toEqual(["DOWN", "MAINTENANCE"])
    expect(assetMoves("DOWN")).toEqual(["OPERATIONAL", "MAINTENANCE"])
    expect(assetMoves("DECOMMISSIONED")).toEqual([])
  })
  it("flags a warranty that has ended or ends soon", () => {
    expect(warrantyNote("2026-10-01", NOW)?.text).toBe("Warranty ended")
    expect(warrantyNote("2026-12-01", NOW)).toEqual({ text: "Warranty ends in 27 d", tone: "warning" })
    expect(warrantyNote("2027-06-01", NOW)).toBeNull()
    expect(warrantyNote(null, NOW)).toBeNull()
  })
})
