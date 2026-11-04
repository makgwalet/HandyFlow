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

import { DOC_TYPES, docBody, docNote, docProblem, emptyDoc, emptyPpm, emptyTech, emptyVendor, everyText, ppmBody, ppmDueNote, ppmProblem, techBody, techProblem, vendorActions, vendorBody, vendorProblem } from "./fm.logic"
describe("planned maintenance", () => {
  it("needs a task name and a whole number of days", () => {
    expect(ppmProblem(emptyPpm())).toMatch(/Name/)
    expect(ppmProblem({ ...emptyPpm(), taskName: "Service", frequencyDays: "0" })).toMatch(/days/)
    expect(ppmProblem({ ...emptyPpm(), taskName: "Service", frequencyDays: "2.5" })).toMatch(/days/)
    expect(ppmProblem({ ...emptyPpm(), taskName: "Service", frequencyDays: "45" })).toBeNull()
  })
  it("sends the asset and start date only when creating", () => {
    const f = { ...emptyPpm(), taskName: " Service ", startDate: "2026-12-01" }
    expect(ppmBody(f, true, "a1")).toEqual({ assetId: "a1", taskName: "Service", description: null, frequencyDays: 30, startDate: "2026-12-01" })
    expect(ppmBody(f, false)).toEqual({ taskName: "Service", description: null, frequencyDays: 30 })
  })
  it("words the interval and flags what is late or close", () => {
    expect(everyText(30)).toBe("Monthly"); expect(everyText(45)).toBe("Every 45 days")
    expect(ppmDueNote(true, "2026-11-01", NOW)).toEqual({ text: "3 d overdue", tone: "danger" })
    expect(ppmDueNote(true, "2026-11-04", NOW)?.text).toBe("Due today")
    expect(ppmDueNote(true, "2026-11-09", NOW)).toEqual({ text: "Due in 5 d", tone: "warning" })
    expect(ppmDueNote(true, "2026-12-01", NOW)).toBeNull()
    expect(ppmDueNote(false, "2026-11-01", NOW)).toBeNull()
  })
})
describe("technicians and vendors", () => {
  it("checks the forms", () => {
    expect(techProblem(emptyTech())).toMatch(/name/)
    expect(techProblem({ ...emptyTech(), name: "S", contactEmail: "x" })).toMatch(/email/)
    expect(techProblem({ ...emptyTech(), name: "S" })).toBeNull()
    expect(techBody({ ...emptyTech(), name: " S " })).toMatchObject({ name: "S", linkedUserId: null })
    expect(vendorProblem(emptyVendor())).toMatch(/company/)
    expect(vendorProblem({ ...emptyVendor(), companyName: "C", contactEmail: "x" })).toMatch(/email/)
    expect(vendorBody({ ...emptyVendor(), companyName: " C " }).companyName).toBe("C")
  })
  it("offers approve or suspend to those who may", () => {
    expect(vendorActions("PENDING", true)).toEqual(["approve", "suspend"])
    expect(vendorActions("APPROVED", true)).toEqual(["suspend"])
    expect(vendorActions("SUSPENDED", true)).toEqual(["approve"])
    expect(vendorActions("APPROVED", false)).toEqual([])
  })
  it("needs an expiry date for every document but 'other'", () => {
    expect(docProblem(emptyDoc())).toMatch(/expiry/)
    expect(docProblem({ ...emptyDoc(), docType: "OTHER" })).toBeNull()
    expect(docProblem({ ...emptyDoc(), issueDate: "2026-05-01", expiryDate: "2026-04-01" })).toMatch(/before/)
    expect(docProblem({ ...emptyDoc(), expiryDate: "2027-01-01", coverAmount: "-1" })).toMatch(/cover/)
    expect(docProblem({ ...emptyDoc(), expiryDate: "2027-01-01", coverAmount: "5000000" })).toBeNull()
    expect(docBody({ ...emptyDoc(), expiryDate: "2027-01-01", coverAmount: "5000000" })).toMatchObject({ coverAmount: 5000000, reference: null })
    expect(DOC_TYPES.length).toBe(6)
  })
  it("describes a document's standing", () => {
    expect(docNote("REVOKED", false, "2027-01-01", NOW).text).toBe("Revoked")
    expect(docNote("VALID", false, "2026-10-01", NOW).text).toBe("Expired")
    expect(docNote("VALID", true, "2026-11-20", NOW)).toEqual({ text: "Expires in 16 d", tone: "warning" })
    expect(docNote("VALID", true, "2027-06-01", NOW).text).toBe("In force")
  })
})

import { agreementBody, agreementProblem, agreementState, billWho, calendarBody, calendarProblem, coverageText, creditActions, creditBody, creditProblem, daysText, dueNote, emptyAgreement, emptyCredit, emptyGenerate, emptyPay, emptyQuote, emptyQuoteLine, emptyTarget, generateBody, generateProblem, glNote, holidayProblem, invoiceActions, lineTotal, minutesText, payBody, payProblem, quoteActions, quoteBody, quoteProblem, quoteSubtotal, quoteValidity, sendProblem, trimTime, type AgreementForm, type QuoteForm } from "./fm.logic"

describe("service agreements", () => {
  const ok = (): AgreementForm => ({ ...emptyAgreement(), clientId: "c1", name: "Head office", startDate: "2026-01-01" })
  it("accepts a plain time-and-materials agreement", () => expect(agreementProblem(ok(), true)).toBeNull())
  it("needs a client, name and start when created", () => {
    expect(agreementProblem({ ...ok(), clientId: "" }, true)).toMatch(/client/)
    expect(agreementProblem({ ...ok(), name: " " }, true)).toMatch(/name/)
    expect(agreementProblem({ ...ok(), startDate: "" }, true)).toMatch(/start/)
    expect(agreementProblem({ ...ok(), clientId: "", startDate: "" }, false)).toBeNull()
  })
  it("requires the end date to be after the start", () => {
    expect(agreementProblem({ ...ok(), endDate: "2026-01-01" }, true)).toMatch(/after/)
    expect(agreementProblem({ ...ok(), endDate: "2026-12-31" }, true)).toBeNull()
  })
  it("needs a positive fee for a retainer only", () => {
    expect(agreementProblem({ ...ok(), billingType: "RETAINER" }, true)).toMatch(/retainer/)
    expect(agreementProblem({ ...ok(), billingType: "RETAINER", monthlyFee: "0" }, true)).toMatch(/retainer/)
    expect(agreementProblem({ ...ok(), billingType: "RETAINER", monthlyFee: "12000" }, true)).toBeNull()
  })
  it("keeps the mark-up between 0 and 1000", () => {
    expect(agreementProblem({ ...ok(), partsMarkupPercent: "1001" }, true)).toMatch(/mark-up/)
    expect(agreementProblem({ ...ok(), partsMarkupPercent: "-1" }, true)).toMatch(/mark-up/)
    expect(agreementProblem({ ...ok(), partsMarkupPercent: "15" }, true)).toBeNull()
  })
  it("checks service targets", () => {
    const tg = (p: string, r: string, s: string) => ({ priority: p, responseMinutes: r, resolutionMinutes: s, clock: "24X7" })
    expect(agreementProblem({ ...ok(), targets: [tg("HIGH", "30", "240")] }, true)).toBeNull()
    expect(agreementProblem({ ...ok(), targets: [tg("HIGH", "0", "240")] }, true)).toMatch(/response/)
    expect(agreementProblem({ ...ok(), targets: [tg("HIGH", "60", "30")] }, true)).toMatch(/shorter/)
    expect(agreementProblem({ ...ok(), targets: [tg("HIGH", "30", "240"), tg("HIGH", "60", "300")] }, true)).toMatch(/two targets/)
  })
  it("picks an unused priority for a new target", () => expect(emptyTarget(["LOW", "NORMAL"]).priority).toBe("HIGH"))
  it("sends only editable fields when updating", () => {
    const b = agreementBody({ ...ok(), monthlyFee: "500", hourlyRate: "", endDate: "" }, false) as Record<string, unknown>
    expect(b.clientId).toBeUndefined(); expect(b.startDate).toBeUndefined(); expect(b.monthlyFee).toBe(500); expect(b.hourlyRate).toBeNull(); expect(b.endDate).toBeNull()
    expect((agreementBody(ok(), true) as Record<string, unknown>).clientId).toBe("c1")
  })
  it("words coverage", () => {
    expect(coverageText([], [], () => "")).toBe("All sites · all kinds of work")
    expect(coverageText(["s1"], ["PPM", "EMERGENCY"], () => "Depot")).toBe("Depot · Planned maintenance, Emergency")
  })
  it("words minutes", () => { expect(minutesText(45)).toBe("45 min"); expect(minutesText(240)).toBe("4 h"); expect(minutesText(90)).toBe("1 h 30 min"); expect(minutesText(1560)).toBe("1 d 2 h"); expect(minutesText(null)).toBe("—") })
  it("flags agreements near or past their end", () => {
    const now = new Date("2026-11-04T10:00:00")
    expect(agreementState("ENDED", null, now).text).toBe("Ended")
    expect(agreementState("ACTIVE", "2026-11-20", now)).toEqual({ text: "Ends in 16 d", tone: "warning" })
    expect(agreementState("ACTIVE", "2026-10-01", now).text).toBe("Past its end date")
    expect(agreementState("ACTIVE", null, now).tone).toBe("success")
  })
})

describe("SLA calendar", () => {
  const f = { startTime: "08:00", endTime: "17:00", workingDays: ["MON", "FRI"], timeZone: "Africa/Johannesburg" }
  it("checks hours, days and zone", () => {
    expect(calendarProblem(f)).toBeNull()
    expect(calendarProblem({ ...f, endTime: "08:00" })).toMatch(/after/)
    expect(calendarProblem({ ...f, workingDays: [] })).toMatch(/day/)
    expect(calendarProblem({ ...f, timeZone: " " })).toMatch(/zone/)
  })
  it("sends days in week order", () => expect(calendarBody({ ...f, workingDays: ["FRI", "MON", "WED"] }).workingDays).toEqual(["MON", "WED", "FRI"]))
  it("trims seconds off times", () => expect(trimTime("08:00:00")).toBe("08:00"))
  it("rejects missing or repeated holidays", () => {
    expect(holidayProblem("", [])).toMatch(/date/); expect(holidayProblem("2026-12-25", ["2026-12-25"])).toMatch(/already/); expect(holidayProblem("2026-12-26", ["2026-12-25"])).toBeNull()
  })
  it("words working days", () => { expect(daysText(["FRI", "MON", "TUE", "WED", "THU"])).toBe("Mon – Fri"); expect(daysText(["SAT", "MON"])).toBe("Mon, Sat"); expect(daysText(["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"])).toBe("Every day") })
})

describe("quotes", () => {
  const ok = (): QuoteForm => ({ ...emptyQuote(), clientId: "c", siteId: "s", title: "Replace pump", lines: [{ description: "Pump", quantity: "2", unitPrice: "1500.50" }] })
  it("totals lines in cents", () => {
    expect(lineTotal({ description: "x", quantity: "3", unitPrice: "0.1" })).toBe(0.3)
    expect(quoteSubtotal([{ description: "a", quantity: "2", unitPrice: "10.005" }, { description: "b", quantity: "1", unitPrice: "5" }])).toBeCloseTo(25.01, 2)
    expect(lineTotal(emptyQuoteLine())).toBe(0)
  })
  it("accepts a complete quote", () => expect(quoteProblem(ok(), true)).toBeNull())
  it("names the line that is wrong", () => {
    expect(quoteProblem({ ...ok(), lines: [{ description: "", quantity: "1", unitPrice: "5" }] }, true)).toMatch(/Line 1 needs/)
    expect(quoteProblem({ ...ok(), lines: [ok().lines[0], { description: "x", quantity: "0", unitPrice: "5" }] }, true)).toMatch(/Line 2: the quantity/)
    expect(quoteProblem({ ...ok(), lines: [{ description: "x", quantity: "1", unitPrice: "" }] }, true)).toMatch(/Line 1: enter the price/)
    expect(quoteProblem({ ...ok(), lines: [] }, true)).toMatch(/at least one/)
  })
  it("fixes the client and site after creation", () => {
    expect(quoteProblem({ ...ok(), clientId: "", siteId: "" }, false)).toBeNull()
    expect(quoteProblem({ ...ok(), clientId: "" }, true)).toMatch(/client/)
    expect((quoteBody(ok(), false) as Record<string, unknown>).clientId).toBeUndefined()
    expect((quoteBody(ok(), true) as Record<string, unknown>).siteId).toBe("s")
  })
  it("sends numbers, not text", () => expect((quoteBody(ok(), true) as { lines: { quantity: number; unitPrice: number }[] }).lines[0]).toMatchObject({ quantity: 2, unitPrice: 1500.5 }))
  it("offers actions by status", () => {
    expect(quoteActions("DRAFT", true)).toEqual(["edit", "send"]); expect(quoteActions("SENT", true)).toEqual(["withdraw"])
    expect(quoteActions("ACCEPTED", true)).toEqual([]); expect(quoteActions("DRAFT", false)).toEqual([])
  })
  it("explains why a draft cannot be sent", () => {
    expect(sendProblem(null, 0, "2026-11-04")).toMatch(/above 0/); expect(sendProblem("2026-11-01", 10, "2026-11-04")).toMatch(/passed/)
    expect(sendProblem("2026-11-04", 10, "2026-11-04")).toBeNull(); expect(sendProblem(null, 10, "2026-11-04")).toBeNull()
  })
  it("flags a sent quote past its date", () => {
    expect(quoteValidity("SENT", "2026-11-01", "2026-11-04")?.tone).toBe("warning"); expect(quoteValidity("SENT", "2026-11-10", "2026-11-04")).toBeNull(); expect(quoteValidity("ACCEPTED", "2026-11-01", "2026-11-04")).toBeNull()
  })
})

describe("billing", () => {
  const has = (...ps: string[]) => (p: string) => ps.includes(p)
  const prep = billWho(has("FACILITIESMANAGEMENT_BILLING_MANAGE")), appr = billWho(has("FACILITIESMANAGEMENT_BILLING_APPROVE")), admin = billWho(has("FACILITIESMANAGEMENT_ADMIN")), none = billWho(has())
  it("reads who may prepare and approve", () => { expect(prep).toEqual({ prepare: true, approve: false }); expect(appr).toEqual({ prepare: false, approve: true }); expect(admin).toEqual({ prepare: true, approve: true }); expect(none).toEqual({ prepare: false, approve: false }) })
  it("follows the invoice lifecycle", () => {
    expect(invoiceActions("DRAFT", 0, admin)).toEqual(["approve", "cancel"]); expect(invoiceActions("DRAFT", 0, prep)).toEqual(["cancel"]); expect(invoiceActions("DRAFT", 0, appr)).toEqual(["approve"])
    expect(invoiceActions("APPROVED", 100, admin)).toEqual(["send", "credit"])
    expect(invoiceActions("SENT", 100, prep)).toEqual(["pay", "credit"]); expect(invoiceActions("PARTIAL", 40, prep)).toEqual(["pay", "credit"])
    expect(invoiceActions("PAID", 0, admin)).toEqual([]); expect(invoiceActions("CANCELLED", 0, admin)).toEqual([]); expect(invoiceActions("SENT", 100, none)).toEqual([])
  })
  it("defaults the period to last month", () => expect(emptyGenerate("2026-11-04")).toMatchObject({ periodStart: "2026-10-01", periodEnd: "2026-10-31", basis: "WORK_ORDERS" }))
  it("rolls the default period over a year end", () => expect(emptyGenerate("2026-01-15")).toMatchObject({ periodStart: "2025-12-01", periodEnd: "2025-12-31" }))
  it("checks the period and sends the agreement only for a retainer", () => {
    expect(generateProblem({ ...emptyGenerate("2026-11-04"), periodEnd: "2026-09-30" })).toMatch(/cannot end/); expect(generateProblem(emptyGenerate("2026-11-04"))).toBeNull()
    expect(generateBody({ basis: "WORK_ORDERS", periodStart: "a", periodEnd: "b", agreementId: "x" }).agreementId).toBeNull()
    expect(generateBody({ basis: "RETAINER", periodStart: "a", periodEnd: "b", agreementId: "x" }).agreementId).toBe("x")
  })
  it("limits a payment to what is owed", () => {
    const f = emptyPay(250, "2026-11-04"); expect(f.amount).toBe("250.00"); expect(payProblem(f, 250)).toBeNull()
    expect(payProblem({ ...f, amount: "250.01" }, 250)).toMatch(/more than/); expect(payProblem({ ...f, amount: "0" }, 250)).toMatch(/at least/); expect(payProblem({ ...f, amount: "" }, 250)).toMatch(/amount/)
    expect(payBody({ ...f, reference: " " })).toMatchObject({ amount: 250, method: "EFT", reference: null })
  })
  it("asks for a reason and, for a part credit, an amount", () => {
    expect(creditProblem(emptyCredit())).toMatch(/why/); expect(creditProblem({ mode: "full", subtotal: "", reason: "Wrong site" })).toBeNull()
    expect(creditProblem({ mode: "part", subtotal: "", reason: "r" })).toMatch(/amount/); expect(creditProblem({ mode: "part", subtotal: "50", reason: "r" })).toBeNull()
    expect(creditBody({ mode: "full", subtotal: "9", reason: " r " })).toEqual({ creditFullBalance: true, reason: "r" })
    expect(creditBody({ mode: "part", subtotal: "50", reason: "r" })).toEqual({ subtotal: 50, creditFullBalance: false, reason: "r" })
  })
  it("offers credit note actions on drafts only", () => { expect(creditActions("DRAFT", admin)).toEqual(["approve", "cancel"]); expect(creditActions("APPROVED", admin)).toEqual([]); expect(creditActions("DRAFT", prep)).toEqual(["cancel"]) })
  it("flags what is overdue", () => {
    expect(dueNote("SENT", 100, "2026-11-01", "2026-11-04")).toEqual({ text: "3 d overdue", tone: "danger" }); expect(dueNote("SENT", 100, "2026-11-10", "2026-11-04")).toBeNull()
    expect(dueNote("PAID", 0, "2026-11-01", "2026-11-04")).toBeNull(); expect(dueNote("DRAFT", 100, "2026-11-01", "2026-11-04")).toBeNull()
  })
  it("words ledger state", () => { expect(glNote("FAILED")?.tone).toBe("danger"); expect(glNote(null)).toBeNull(); expect(glNote("WEIRD_THING")?.label).toBe("weird thing") })
})

import { PORTAL_STATUS, accessSummary, accessWarning, auditLabel, barShare, canWithdraw, complianceTone, declineProblem, defaultRange, emptyRaise, grantCanRevoke, hoursText, inviteProblem, marginTone, minutesToHours, pct, portalOwing, priorityRows, raiseBody, raiseProblem, rangeProblem, statusRows } from "./fm.logic"

describe("reports", () => {
  it("ends the default range today and counts days inclusively", () => { expect(defaultRange("2026-11-04", 90)).toEqual({ from: "2026-08-07", to: "2026-11-04" }); expect(defaultRange("2026-03-01", 30).from).toBe("2026-01-31") })
  it("checks the range", () => { expect(rangeProblem("", "2026-01-01")).toMatch(/both/); expect(rangeProblem("2026-02-01", "2026-01-01")).toMatch(/before/); expect(rangeProblem("2026-01-01", "2026-01-01")).toBeNull() })
  it("words percentages and hours", () => { expect(pct(92.345)).toBe("92.3%"); expect(pct(null)).toBe("—"); expect(hoursText(1.84)).toBe("1.8 h"); expect(hoursText(72)).toBe("3 d"); expect(minutesToHours(90)).toBe("1.5 h"); expect(hoursText(null)).toBe("—") })
  it("reads compliance and margin", () => { expect(complianceTone(95)).toBe("success"); expect(complianceTone(80)).toBe("warning"); expect(complianceTone(60)).toBe("danger"); expect(complianceTone(null)).toBe("neutral"); expect(marginTone(-1)).toBe("danger"); expect(marginTone(0)).toBe("success") })
  it("sizes bars", () => { expect(barShare(0, 10)).toBe(0); expect(barShare(1, 1000)).toBe(3); expect(barShare(5, 10)).toBe(50); expect(barShare(5, 0)).toBe(0) })
  it("orders statuses by the job's journey and hides empty ones", () => {
    expect(statusRows({ COMPLETED: 4, OPEN: 2, IN_PROGRESS: 0, ON_HOLD: 1 }).map(r => r.key)).toEqual(["OPEN", "ON_HOLD", "COMPLETED"]); expect(statusRows(null)).toEqual([])
  })
  it("lists priorities most urgent first", () => expect(priorityRows({ LOW: 1, URGENT: 2, NORMAL: 0 }).map(r => r.key)).toEqual(["URGENT", "LOW"]))
})

describe("audit, access and portal users", () => {
  it("words actions", () => expect(auditLabel("WORK_ORDER_ASSIGNED")).toBe("Work order assigned"))
  it("summarises access and warns about widening it", () => {
    expect(accessSummary(false, 0)).toBe("Sees every client"); expect(accessSummary(true, 1)).toBe("Restricted to 1 client"); expect(accessSummary(true, 3)).toBe("Restricted to 3 clients")
    expect(accessWarning(0, true)).toMatch(/every client/); expect(accessWarning(0, false)).toBeNull(); expect(accessWarning(2, true)).toBeNull()
  })
  it("checks invites and revocation", () => { expect(inviteProblem(" ")).toMatch(/Enter/); expect(inviteProblem("nope")).toMatch(/look right/); expect(inviteProblem("a@b.co")).toBeNull(); expect(grantCanRevoke("ACTIVE", true)).toBe(true); expect(grantCanRevoke("REVOKED", true)).toBe(false); expect(grantCanRevoke("PENDING", false)).toBe(false) })
})

describe("client portal", () => {
  it("checks a fault report", () => {
    const f = { ...emptyRaise(), siteId: "s1", description: "The lift is stuck on level 2" }
    expect(raiseProblem(f)).toBeNull(); expect(raiseProblem({ ...f, siteId: "" })).toMatch(/site/); expect(raiseProblem({ ...f, description: "too short" })).toMatch(/10 characters/); expect(raiseProblem({ ...f, description: "x".repeat(2001) })).toMatch(/2000/)
    expect(raiseBody({ ...f, assetId: " ", contactName: "Thabo" })).toMatchObject({ assetId: null, contactName: "Thabo", category: "CORRECTIVE", safetyImpact: false })
  })
  it("lets a client withdraw only a request nobody has picked up", () => { expect(canWithdraw("REQUESTED")).toBe(true); expect(canWithdraw("OPEN")).toBe(false); expect(canWithdraw("COMPLETED")).toBe(false) })
  it("needs a reason to decline", () => { expect(declineProblem(" ")).toMatch(/why/); expect(declineProblem("Too dear")).toBeNull(); expect(declineProblem("x".repeat(1001))).toMatch(/1000/) })
  it("shows internal states as plain ones", () => { expect(PORTAL_STATUS.PENDING_VERIFICATION.label).toBe("In progress"); expect(PORTAL_STATUS.REQUESTED.label).toBe("Received") })
  it("reads the amounts owing", () => { expect(portalOwing(null)).toBeNull(); expect(portalOwing({ outstandingBalance: 100, overdueBalance: null, overdueInvoices: 0 })).toEqual({ owing: 100, overdue: 0, overdueCount: 0 }) })
})
