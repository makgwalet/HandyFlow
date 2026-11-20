import { describe, it, expect } from "vitest"
import { accessClipboard, accessFindingTone, accessGroups, accessLevelText, accessLevelTone, accessSummary, accessToggle, type AccessPermission, type AccessTemplate } from "./legal.logic"
import { bucketTone, matterGrid, matterStatusText, reportAsAtProblem, reportMonthText, reportRangeProblem, reportUrl, reportYearStart } from "./legal.logic"
import { applyProblem as wfApplyProblem, emptyStep, emptyTask, emptyWorkflow, moveStep, stepDue, taskActions, taskDueText, taskEventText, taskPriorityTone, taskProblem, taskReasonProblem, taskRequest, taskStatusLabel, taskTone, taskToForm, workflowProblem, workflowRequest, workflowToForm, type TaskDetail, type WorkflowDetail } from "./legal.logic"
import { emptyLitCase, litActions, litCaseProblem, litCaseRequest, litCaseToForm, litCompliedProblem, litConcludeProblem, litDaysAway, litDaysText, litEventText, litHearingActions, litHearingProblem, litHeldProblem, litOrderProblem, litPostponeProblem, litReopenProblem, litStageProblem, litStageLabel, litTime, type LitCase } from "./legal.logic"
import { CONFLICT_STATUS, conflictHeadline, decisionProblem, emptyParty, partiesRequest, usableChecks, type ConflictCheck, approvalMode, canWithdraw, reasonProblem, nextAttempt, type TrustForm, canRevokeGrant, emptyTrust, evidenceProblem, fileSize, inviteProblem, payableByTrust, trustKindsFor, trustProblem, trustRequest, attorneyProblem, billable, canChangeWork, canLogWork, clientProblem, disbProblem, emptyAttorney, emptyClient, emptyDisb, emptyKeyDate, emptyMatter, emptyProfile, emptyRetainer, emptyTime, generateProblem, invoiceActions, keyDateNote, keyDateProblem, matterActions, matterProblem, matterRequest, paymentProblem, profileProblem, retainerProblem, timeProblem, timeRequest } from "./legal.logic"

const at = (s: string) => new Date(s + "T09:30:00")
describe("matter lifecycle", () => {
  it("offers the moves each status allows", () => {
    expect(matterActions("OPEN")).toEqual(["edit", "hold", "close"])
    expect(matterActions("ON_HOLD")).toEqual(["edit", "reopen", "close"])
    expect(matterActions("CLOSED")).toEqual(["reopen", "archive"])
    expect(matterActions("ARCHIVED")).toEqual([])
  })
  it("lets work be logged only on live matters, and changed only while unbilled", () => {
    expect(canLogWork("OPEN")).toBe(true); expect(canLogWork("ON_HOLD")).toBe(true); expect(canLogWork("CLOSED")).toBe(false); expect(canLogWork("ARCHIVED")).toBe(false)
    expect(canChangeWork("UNBILLED")).toBe(true); expect(canChangeWork("BILLED")).toBe(false); expect(canChangeWork("WRITTEN_OFF")).toBe(false)
  })
})
describe("matter form", () => {
  const ok = { ...emptyMatter(), clientId: "c", attorneyId: "a", matterNumber: "M-001", matterName: "Estate Dlamini", conflictCheckId: "cc" }
  it("needs client, attorney, number and name, and a fee for fixed fee work", () => {
    expect(matterProblem(ok, true)).toBeNull()
    expect(matterProblem({ ...ok, clientId: "" }, true)).toMatch(/client/); expect(matterProblem({ ...ok, clientId: "" }, false)).toBeNull()
    expect(matterProblem({ ...ok, matterNumber: " " }, true)).toMatch(/number/)
    expect(matterProblem({ ...ok, conflictCheckId: "" }, true)).toMatch(/conflict check/); expect(matterProblem({ ...ok, conflictCheckId: "" }, false)).toBeNull()
    expect(matterProblem({ ...ok, billingType: "FIXED_FEE" }, true)).toMatch(/fixed fee/)
    expect(matterProblem({ ...ok, billingType: "FIXED_FEE", fixedFeeAmount: "15000" }, true)).toBeNull()
  })
  it("drops the fixed fee when the matter is hourly and leaves creation-only fields out of an edit", () => {
    expect(matterRequest({ ...ok, fixedFeeAmount: "999" }, true)).toMatchObject({ fixedFeeAmount: null, clientId: "c", matterNumber: "M-001" })
    expect(matterRequest(ok, false)).not.toHaveProperty("clientId")
    expect(matterRequest(ok, true)).toMatchObject({ conflictCheckId: "cc" }); expect(matterRequest(ok, false)).not.toHaveProperty("conflictCheckId")
  })
})
describe("time entries", () => {
  const ok = { ...emptyTime("a"), hours: "1.5", description: "Drafted letter of demand" }
  it("falls back to the attorney's rate, and asks for one when neither exists", () => {
    expect(timeProblem(ok, false, 2200)).toBeNull()
    expect(timeProblem(ok, false, null)).toMatch(/no default rate/)
    expect(timeProblem({ ...ok, hourlyRate: "1800" }, false, null)).toBeNull()
    expect(timeProblem(ok, true, 2200)).toMatch(/hourly rate/)
  })
  it("checks hours and description", () => {
    expect(timeProblem({ ...ok, hours: "0" }, false, 1)).toMatch(/above 0/)
    expect(timeProblem({ ...ok, hours: "25" }, false, 1)).toMatch(/24/)
    expect(timeProblem({ ...ok, description: " " }, false, 1)).toMatch(/Describe/)
    expect(timeProblem({ ...ok, attorneyId: "" }, false, 1)).toMatch(/who did/)
    expect(timeRequest({ ...ok, hourlyRate: " " }, false)).toMatchObject({ hourlyRate: null, billable: true })
    expect(timeRequest(ok, true)).not.toHaveProperty("attorneyId")
  })
})
describe("expenses and key dates", () => {
  it("needs a description and a positive amount", () => {
    expect(disbProblem({ ...emptyDisb(), description: "Sheriff fees", amount: "450" })).toBeNull()
    expect(disbProblem({ ...emptyDisb(), amount: "450" })).toMatch(/Describe/)
    expect(disbProblem({ ...emptyDisb(), description: "x", amount: "0" })).toMatch(/above 0/)
  })
  it("needs a date and description", () => {
    expect(keyDateProblem({ ...emptyKeyDate(), dueDate: "2026-12-01", description: "Trial" })).toBeNull()
    expect(keyDateProblem(emptyKeyDate())).toMatch(/date/)
  })
  it("flags pending deadlines that are close or past, and nothing else", () => {
    expect(keyDateNote("PENDING", "2026-11-03", at("2026-11-04"))).toEqual({ text: "1 day overdue", tone: "danger" })
    expect(keyDateNote("PENDING", "2026-11-04", at("2026-11-04"))?.text).toBe("Due today")
    expect(keyDateNote("PENDING", "2026-11-07", at("2026-11-04"))).toEqual({ text: "Due in 3 days", tone: "danger" })
    expect(keyDateNote("PENDING", "2026-11-12", at("2026-11-04"))?.tone).toBe("warning")
    expect(keyDateNote("PENDING", "2026-12-12", at("2026-11-04"))).toBeNull()
    expect(keyDateNote("COMPLETED", "2026-11-03", at("2026-11-04"))).toBeNull()
  })
})
describe("clients, attorneys, retainers, profile", () => {
  it("checks names, emails and rates", () => {
    expect(clientProblem({ ...emptyClient(), name: "Dlamini" })).toBeNull(); expect(clientProblem(emptyClient())).toMatch(/name/); expect(clientProblem({ ...emptyClient(), name: "D", email: "x" })).toMatch(/email/)
    expect(attorneyProblem({ ...emptyAttorney(), name: "Thandi" })).toBeNull(); expect(attorneyProblem({ ...emptyAttorney(), name: "T", hourlyRate: "-1" })).toMatch(/rate/)
    expect(retainerProblem({ ...emptyRetainer(), clientId: "c", monthlyFee: "5000" }, true)).toBeNull()
    expect(retainerProblem({ ...emptyRetainer(), monthlyFee: "5000" }, true)).toMatch(/client/)
    expect(retainerProblem({ ...emptyRetainer(), monthlyFee: "0" }, false)).toMatch(/above 0/)
    expect(retainerProblem({ ...emptyRetainer(), monthlyFee: "5", startDate: "2026-02-01", endDate: "2026-01-01" }, false)).toMatch(/before/)
  })
  it("refuses to treat the trust and business accounts as one", () => {
    const p = { ...emptyProfile(), firmName: "Dlamini Inc", trustBankName: "FNB", trustAccountNumber: "123", businessBankName: "FNB", businessAccountNumber: "123" }
    expect(profileProblem(p)).toMatch(/cannot be the same/)
    expect(profileProblem({ ...p, businessAccountNumber: "456" })).toBeNull()
    expect(profileProblem({ ...emptyProfile() })).toMatch(/firm/)
  })
})
describe("invoices", () => {
  it("bills the chosen work, or the fixed fee when none is chosen", () => {
    expect(billable([{ id: "t", amount: 1650.1 }], [{ id: "d", amount: 450 }], "9999")).toBe(2100.1)
    expect(billable([], [], "15000")).toBe(15000)
    expect(billable([], [], "")).toBe(0)
  })
  it("needs something to bill", () => {
    const f = { dueDate: "", description: "", notes: "", fixedFeeAmount: "" }
    expect(generateProblem([], [], f)).toMatch(/Choose the work/)
    expect(generateProblem([{ id: "t", amount: 1 }], [], f)).toBeNull()
    expect(generateProblem([], [], { ...f, fixedFeeAmount: "100" })).toBeNull()
  })
  it("sends drafts, takes payment on sent ones, and keeps write-off for admins", () => {
    expect(invoiceActions("DRAFT", true)).toEqual(["send"])
    expect(invoiceActions("SENT", false)).toEqual(["pay"]); expect(invoiceActions("PARTIALLY_PAID", true)).toEqual(["pay", "writeOff"])
    expect(invoiceActions("PAID", true)).toEqual([]); expect(invoiceActions("WRITTEN_OFF", true)).toEqual([])
  })
  it("refuses a payment above what is outstanding", () => {
    expect(paymentProblem(500, "")).toMatch(/above 0/); expect(paymentProblem(500, "500.01")).toMatch(/more than/); expect(paymentProblem(500, "500")).toBeNull()
  })
})

describe("trust account", () => {
  const f = (o: Partial<TrustForm> = {}): TrustForm => ({ ...emptyTrust(), amount: "100", ...o })
  it("shows receipts to those who may record them and requests to those who may ask for payments", () => {
    expect(trustKindsFor(true, false)).toEqual(["receipt"])
    expect(trustKindsFor(false, true)).toEqual(["transfer", "disbursement", "refund"])
    expect(trustKindsFor(true, true)).toEqual(["receipt", "transfer", "disbursement", "refund"])
    expect(trustKindsFor(false, false)).toEqual([])
  })
  it("sends a payment out of trust as a request that names its type, and a receipt as a plain receipt", () => {
    expect(trustRequest("refund", f({ payee: "A" }))).toMatchObject({ paymentType: "REFUND", payee: "A" })
    expect(trustRequest("transfer", f({ invoiceId: "i" }))).toMatchObject({ paymentType: "TRANSFER_TO_BUSINESS", invoiceId: "i" })
    expect(trustRequest("receipt", f())).not.toHaveProperty("paymentType")
  })
  it("accepts a receipt of any size but needs a sensible amount", () => {
    expect(trustProblem("receipt", f({ amount: "1000000" }), 0)).toBeNull()
    expect(trustProblem("receipt", f({ amount: "" }), 0)).toMatch(/amount/)
    expect(trustProblem("receipt", f({ amount: "-5" }), 0)).toMatch(/amount/)
    expect(trustProblem("receipt", f({ amount: "10.505" }), 0)).toMatch(/decimal/)
  })
  it("never lets a withdrawal exceed what the client has in trust", () => {
    expect(trustProblem("refund", f({ amount: "500.01", payee: "A" }), 500)).toMatch(/in trust/)
    expect(trustProblem("refund", f({ amount: "500", payee: "A" }), 500)).toBeNull()
    expect(trustProblem("disbursement", f({ payee: "Sheriff" }), 0)).toMatch(/in trust/)
  })
  it("needs an invoice for a transfer and a payee for payouts", () => {
    expect(trustProblem("transfer", f(), 500)).toMatch(/invoice/)
    expect(trustProblem("transfer", f({ invoiceId: "i", amount: "300" }), 500, 250)).toMatch(/still owes/)
    expect(trustProblem("transfer", f({ invoiceId: "i", amount: "250" }), 500, 250)).toBeNull()
    expect(trustProblem("disbursement", f(), 500)).toMatch(/who is being paid/)
    expect(trustProblem("refund", f(), 500)).toMatch(/refund is paid to/)
  })
  it("builds the request each endpoint expects", () => {
    expect(trustRequest("receipt", f({ reference: " R1 " }))).toEqual({ amount: 100, transactionDate: null, reference: "R1", notes: null, matterId: null })
    expect(trustRequest("transfer", f({ invoiceId: "i" }))).toMatchObject({ invoiceId: "i", amount: 100 })
    expect(trustRequest("refund", f({ payee: " Mr A " }))).toMatchObject({ payee: "Mr A" })
  })
  it("only offers sent invoices that still owe money", () => {
    expect(payableByTrust("SENT", 10)).toBe(true)
    expect(payableByTrust("PARTIALLY_PAID", 10)).toBe(true)
    expect(payableByTrust("PAID", 0)).toBe(false)
    expect(payableByTrust("DRAFT", 10)).toBe(false)
    expect(payableByTrust("SENT", 0)).toBe(false)
  })
})

describe("portal access and documents", () => {
  const grants = [{ inviteEmail: "Jo@x.co", status: "PENDING" }, { inviteEmail: "old@x.co", status: "REVOKED" }]
  it("checks an invite", () => {
    expect(inviteProblem("", grants)).toMatch(/Enter/)
    expect(inviteProblem("nope", grants)).toMatch(/look right/)
    expect(inviteProblem("jo@x.co", grants)).toMatch(/already/)
    expect(inviteProblem("old@x.co", grants)).toBeNull()
    expect(inviteProblem("new@x.co", grants)).toBeNull()
  })
  it("lets only pending or active grants be revoked", () => {
    expect(canRevokeGrant("PENDING")).toBe(true)
    expect(canRevokeGrant("ACTIVE")).toBe(true)
    expect(canRevokeGrant("REVOKED")).toBe(false)
  })
  it("checks a document before it is sent", () => {
    expect(evidenceProblem(null, "DOCUMENT")).toMatch(/Choose a file/)
    expect(evidenceProblem({ size: 0 }, "DOCUMENT")).toMatch(/empty/)
    expect(evidenceProblem({ size: 11 * 1024 * 1024 }, "DOCUMENT")).toMatch(/10 MB/)
    expect(evidenceProblem({ size: 1000 }, "")).toMatch(/kind/)
    expect(evidenceProblem({ size: 1000 }, "CONTRACT")).toBeNull()
  })
  it("writes file sizes plainly", () => {
    expect(fileSize(512)).toBe("512 B")
    expect(fileSize(2048)).toBe("2 KB")
    expect(fileSize(5 * 1024 * 1024)).toBe("5 MB")
  })
})

import { dashboardStats, dateTone, groupByDay, gridRange, keyDateActions, monthGrid, shiftMonth, upcomingDates } from "./legal.logic"

describe("calendar", () => {
  it("lays November 2026 out Monday first", () => {
    const g = monthGrid(2026, 10)
    expect(g[0][0]).toEqual({ date: "2026-10-26", inMonth: false }); expect(g[0][6]).toEqual({ date: "2026-11-01", inMonth: true }); expect(g.every(w => w.length === 7)).toBe(true)
  })
  it("pads with the neighbouring months", () => {
    const g = monthGrid(2026, 11) // December 2026 starts on a Tuesday
    expect(g[0][0]).toEqual({ date: "2026-11-30", inMonth: false }); expect(g[0][1]).toEqual({ date: "2026-12-01", inMonth: true })
    expect(g[g.length - 1][6].date >= "2026-12-31").toBe(true)
  })
  it("covers a whole month in four to six weeks", () => { for (let m = 0; m < 12; m++) { const g = monthGrid(2026, m); expect(g.length).toBeGreaterThanOrEqual(4); expect(g.length).toBeLessThanOrEqual(6); expect(g.flat().filter(c => c.inMonth).length).toBe(new Date(2026, m + 1, 0).getDate()) } })
  it("gives the range the grid shows", () => { const r = gridRange(2026, 11); expect(r.from).toBe("2026-11-30"); expect(r.to >= "2026-12-31").toBe(true) })
  it("moves between months across a year end", () => { expect(shiftMonth(2026, 11, 1)).toEqual({ year: 2027, month: 0 }); expect(shiftMonth(2026, 0, -1)).toEqual({ year: 2025, month: 11 }) })
  it("groups by day", () => { const g = groupByDay([{ dueDate: "2026-11-02" }, { dueDate: "2026-11-02" }, { dueDate: "2026-11-05" }]); expect(g["2026-11-02"].length).toBe(2); expect(Object.keys(g).length).toBe(2) })
  it("colours deadlines by how close they are", () => {
    const now = new Date("2026-11-04T10:00:00")
    expect(dateTone("COMPLETED", "2026-10-01", now)).toBe("success"); expect(dateTone("MISSED", "2026-10-01", now)).toBe("neutral"); expect(dateTone("PENDING", "2026-10-01", now)).toBe("danger")
    expect(dateTone("PENDING", "2026-11-06", now)).toBe("danger"); expect(dateTone("PENDING", "2026-11-12", now)).toBe("warning"); expect(dateTone("PENDING", "2026-12-25", now)).toBe("info")
  })
  it("offers actions only on pending dates to people who may write", () => {
    expect(keyDateActions("PENDING", false, true)).toEqual(["acknowledge", "complete", "mark-missed"]); expect(keyDateActions("PENDING", true, true)).toEqual(["complete", "mark-missed"])
    expect(keyDateActions("COMPLETED", false, true)).toEqual([]); expect(keyDateActions("PENDING", false, false)).toEqual([])
  })
  it("lists the next pending dates, soonest first", () => {
    const xs = [{ dueDate: "2026-12-01", status: "PENDING" }, { dueDate: "2026-11-05", status: "COMPLETED" }, { dueDate: "2026-11-10", status: "PENDING" }, { dueDate: "2026-11-02", status: "PENDING" }]
    expect(upcomingDates(xs, 2).map(x => x.dueDate)).toEqual(["2026-11-02", "2026-11-10"])
  })
})

describe("dashboard", () => {
  const inv = (status: string, outstandingBalance: number, dueDate: string | null) => ({ status, outstandingBalance, dueDate })
  it("counts matters by status", () => {
    const s = dashboardStats([{ status: "OPEN" }, { status: "OPEN" }, { status: "ON_HOLD" }, { status: "CLOSED" }, { status: "ARCHIVED" }], [], [], "2026-11-04")
    expect(s).toMatchObject({ total: 5, open: 2, onHold: 1, closed: 2 })
  })
  it("adds up what is owed and what is late, ignoring drafts and paid invoices", () => {
    const s = dashboardStats([], [inv("SENT", 100.1, "2026-10-01"), inv("PARTIALLY_PAID", 50.2, "2026-12-01"), inv("PAID", 0, "2026-10-01"), inv("DRAFT", 999, null), inv("SENT", 0, "2026-10-01")], [], "2026-11-04")
    expect(s).toMatchObject({ outstanding: 150.3, overdueCount: 1, overdue: 100.1, drafts: 1 })
  })
  it("adds up the trust money held", () => expect(dashboardStats([], [], [{ trustBalance: 1000.5, status: "ACTIVE" }, { trustBalance: 250.25, status: "ACTIVE" }], "2026-11-04").trustHeld).toBe(1250.75))
})

describe("trust request keys", () => {
  const f = (o: Partial<TrustForm> = {}): TrustForm => ({ ...emptyTrust(), amount: "100", ...o })
  it("sends the key with the request, and none when there is no key", () => {
    expect(trustRequest("receipt", f(), "abc")).toMatchObject({ amount: 100, idempotencyKey: "abc" })
    expect(trustRequest("receipt", f())).not.toHaveProperty("idempotencyKey")
  })
  it("reuses the key when the same request is sent again, so a double click posts once", () => {
    let n = 0
    const make = () => `key-${++n}`
    const a = nextAttempt(null, "receipt", f(), make)
    expect(nextAttempt(a, "receipt", f(), make)).toBe(a)
    expect(n).toBe(1)
  })
  it("takes a fresh key when anything changes, so a corrected amount is not refused as a clash", () => {
    let n = 0
    const make = () => `key-${++n}`
    const a = nextAttempt(null, "receipt", f(), make)
    expect(nextAttempt(a, "receipt", f({ amount: "150" }), make).key).toBe("key-2")
    expect(nextAttempt(a, "refund", f({ payee: "A" }), make).key).toBe("key-3")
  })
})

describe("approving payments out of trust", () => {
  const r = (o: Partial<{ status: string; requestedBy: string }> = {}) => ({ status: "PENDING", requestedBy: "maker", ...o })
  it("lets a different person approve", () => expect(approvalMode(r(), "checker", false)).toBe("approve"))
  it("blocks the requester from approving their own request", () => expect(approvalMode(r(), "maker", false)).toBe("blocked"))
  it("allows an excepted self-approval only when the firm has switched it on", () => {
    expect(approvalMode(r(), "maker", true)).toBe("self")
    expect(approvalMode(r(), "checker", true)).toBe("approve")
  })
  it("offers nothing once a request is decided or when nobody is signed in", () => {
    for (const status of ["APPROVED", "REJECTED", "CANCELLED"]) expect(approvalMode(r({ status }), "checker", true)).toBe("none")
    expect(approvalMode(r(), undefined, true)).toBe("none")
  })
  it("lets only the requester withdraw a pending request", () => {
    expect(canWithdraw(r(), "maker")).toBe(true)
    expect(canWithdraw(r(), "checker")).toBe(false)
    expect(canWithdraw(r({ status: "APPROVED" }), "maker")).toBe(false)
  })
  it("asks for a reason of at least ten characters", () => {
    expect(reasonProblem("too short")).toMatch(/at least 10/)
    expect(reasonProblem("   padded    ")).toMatch(/at least 10/)
    expect(reasonProblem("Sole practitioner")).toBeNull()
  })
})

describe("conflict checks", () => {
  const check = (o: Partial<ConflictCheck> = {}): ConflictCheck => ({ id: "x", clientId: "c1", clientName: "Dlamini", status: "CLEAR", truncated: false, subjects: [], hits: [], runByName: null, runAt: "2026-11-01T10:00:00Z", decidedByName: null, decidedAt: null, decisionReason: null, selfReviewed: false, validUntil: null, matterId: null, usable: true, ...o })
  it("drops blank party rows and trims the rest", () => {
    expect(partiesRequest([emptyParty(), { name: " Nkosi Builders ", idNumber: " ", role: "CO_PARTY" }])).toEqual([{ name: "Nkosi Builders", idNumber: null, role: "CO_PARTY" }])
    expect(partiesRequest([])).toEqual([])
  })
  it("defaults a new party to the other side", () => expect(emptyParty().role).toBe("OPPOSING_PARTY"))
  it("asks for an outcome and a reason of at least ten characters", () => {
    expect(decisionProblem("", "A proper reason here")).toMatch(/outcome/)
    expect(decisionProblem("DECLINED", "too short")).toMatch(/at least 10/)
    expect(decisionProblem("DECLINED", "We act for the other side")).toBeNull()
  })
  it("tells the person what to do next", () => {
    expect(conflictHeadline(check())).toMatch(/open the matter/)
    const hit = { subjectName: "a", matchKind: "SIMILAR", matchedKind: "CLIENT", matchedName: "b", matchedRole: null, matchedDetail: null, matchedClientId: null, matchedMatterId: null }
    expect(conflictHeadline(check({ status: "PENDING_REVIEW", hits: [hit] }))).toMatch(/1 possible conflict found.*second person/)
    expect(conflictHeadline(check({ status: "PENDING_REVIEW", hits: [hit, hit] }))).toMatch(/2 possible conflicts/)
    expect(conflictHeadline(check({ status: "PENDING_REVIEW", truncated: true }))).toMatch(/too broad/)
    expect(conflictHeadline(check({ status: "DECLINED" }))).toMatch(/declined/)
    expect(conflictHeadline(check({ status: "CLEARED_AFTER_REVIEW" }))).toMatch(/cleared/)
  })
  it("offers only usable checks for the chosen client, newest first", () => {
    const list = [check({ id: "old", runAt: "2026-10-01T00:00:00Z" }), check({ id: "new", runAt: "2026-11-02T00:00:00Z" }), check({ id: "used", usable: false }), check({ id: "other", clientId: "c2" })]
    expect(usableChecks(list, "c1").map(c => c.id)).toEqual(["new", "old"])
    expect(usableChecks(list, "none")).toEqual([])
  })
  it("has a label for every status", () => { for (const s of ["CLEAR", "PENDING_REVIEW", "CLEARED_AFTER_REVIEW", "PROCEED_WITH_CONSENT", "DECLINED"]) expect(CONFLICT_STATUS[s]).toBeDefined() })
})

import { DEADLINE_ROLLOVERS, calendarProblem, calendarYears, correctionProblem, describeRule, emptyRule, previewMatches, recessLabel, ruleProblem, ruleRequest, ruleToForm, triggerProblem, type DeadlineRule } from "./legal.logic"

describe("deadline rules", () => {
  const base = { amount: 20, unit: "COURT_DAYS", direction: "AFTER", rollover: "NONE", recessStart: "12-16", recessEnd: "01-15" }
  it("writes a rule as a sentence", () => {
    expect(describeRule(base)).toBe("20 court days after the trigger date; 16 Dec to 15 Jan not counted")
    expect(describeRule({ ...base, amount: 1, recessStart: null, recessEnd: null })).toBe("1 court day after the trigger date")
    expect(describeRule({ amount: 30, unit: "CALENDAR_DAYS", direction: "AFTER", rollover: "SKIP_SUNDAY_AND_HOLIDAYS", recessStart: null, recessEnd: null })).toBe("30 calendar days after the trigger date; a Sunday or public holiday moves it later")
    expect(describeRule({ amount: 10, unit: "CALENDAR_DAYS", direction: "BEFORE", rollover: "SKIP_NON_COURT_DAYS", recessStart: null, recessEnd: null })).toBe("10 calendar days before the trigger date; a weekend, holiday or closure moves it earlier")
    expect(describeRule({ amount: 3, unit: "YEARS", direction: "AFTER", rollover: "NONE", recessStart: "12-16", recessEnd: "01-15" })).toBe("3 years after the trigger date")
  })
  it("does not mention a rollover for court days, which always land on a court day", () => {
    expect(describeRule({ ...base, rollover: "SKIP_NON_COURT_DAYS", recessStart: null, recessEnd: null })).toBe("20 court days after the trigger date")
  })
  it("shows the recess window and ignores a broken one", () => {
    expect(recessLabel("12-16", "01-15")).toBe("16 Dec to 15 Jan")
    expect(recessLabel("12-16", null)).toBeNull()
    expect(recessLabel("13-40", "01-15")).toBeNull()
  })
  it("checks the rule form the way the server does", () => {
    const ok = { ...emptyRule(), code: "HC_PLEA", name: "Plea", triggerLabel: "Notice delivered", amount: "20" }
    expect(ruleProblem(ok)).toBeNull()
    expect(ruleProblem({ ...ok, code: "H" })).toMatch(/code/)
    expect(ruleProblem({ ...ok, name: " " })).toMatch(/name/)
    expect(ruleProblem({ ...ok, triggerLabel: "" })).toMatch(/trigger/)
    for (const bad of ["", "0", "-3", "2.5", "abc", "3661"]) expect(ruleProblem({ ...ok, amount: bad })).toMatch(/whole number from 1 to 3660/)
    expect(ruleProblem({ ...ok, unit: "YEARS", amount: "31" })).toMatch(/1 to 30/)
    expect(ruleProblem({ ...ok, unit: "YEARS", amount: "30" })).toBeNull()
    expect(ruleProblem({ ...ok, unit: "MONTHS", amount: "361" })).toMatch(/1 to 360/)
    expect(ruleProblem({ ...ok, recessStart: "12-16" })).toMatch(/both ends/)
    expect(ruleProblem({ ...ok, recessStart: "12-16", recessEnd: "1-15" })).toMatch(/MM-DD/)
    expect(ruleProblem({ ...ok, recessStart: "12-16", recessEnd: "01-15" })).toBeNull()
  })
  it("sends clean values and round-trips a rule through the form", () => {
    const rule: DeadlineRule = { id: "r", code: "HC_PLEA", name: "Plea", description: null, court: "HIGH_COURT", triggerLabel: "Notice delivered", keyDateType: "FILING_DEADLINE", amount: 20, unit: "COURT_DAYS", direction: "AFTER", rollover: "NONE", recessStart: "12-16", recessEnd: "01-15", sourceReference: "Rule 22(1)", active: true }
    const form = ruleToForm(rule)
    expect(form.amount).toBe("20")
    expect(ruleRequest(form)).toEqual({ code: "HC_PLEA", name: "Plea", description: null, court: "HIGH_COURT", triggerLabel: "Notice delivered", keyDateType: "FILING_DEADLINE", amount: 20, unit: "COURT_DAYS", direction: "AFTER", rollover: "NONE", recessStart: "12-16", recessEnd: "01-15", sourceReference: "Rule 22(1)" })
    expect(ruleRequest({ ...form, recessStart: " ", recessEnd: "", description: "  " })).toMatchObject({ recessStart: null, recessEnd: null, description: null })
  })
  it("offers every rollover with a label", () => { expect(DEADLINE_ROLLOVERS.map(r => r[0])).toEqual(["NONE", "SKIP_SUNDAY_AND_HOLIDAYS", "SKIP_NON_COURT_DAYS"]) })
})

describe("working out a deadline", () => {
  it("needs a rule and a trigger date in the supported years", () => {
    expect(triggerProblem("", "2026-03-06")).toMatch(/rule/)
    expect(triggerProblem("r", "")).toMatch(/trigger date/)
    expect(triggerProblem("r", "1999-12-31")).toMatch(/2000 and 2100/)
    expect(triggerProblem("r", "2101-01-01")).toMatch(/2000 and 2100/)
    expect(triggerProblem("r", "2000-01-01")).toBeNull()
    expect(triggerProblem("r", "2100-12-31")).toBeNull()
  })
  it("only offers to add the date once the person has seen it worked out from the same inputs", () => {
    const p = { ruleId: "r", triggerDate: "2026-03-06" }
    expect(previewMatches(p, "r", "2026-03-06")).toBe(true)
    expect(previewMatches(p, "r", "2026-03-09")).toBe(false)
    expect(previewMatches(p, "other", "2026-03-06")).toBe(false)
    expect(previewMatches(null, "r", "2026-03-06")).toBe(false)
  })
  it("needs a reason of at least 10 characters to correct a date", () => {
    expect(correctionProblem("", "Served on the 9th")).toMatch(/trigger date/)
    expect(correctionProblem("2026-03-09", "typo")).toMatch(/at least 10/)
    expect(correctionProblem("2026-03-09", "         x          ")).toMatch(/at least 10/)
    expect(correctionProblem("2026-03-09", "Served on the 9th")).toBeNull()
    expect(correctionProblem("2101-03-09", "Served on the 9th")).toMatch(/2000 and 2100/)
  })
  it("checks a calendar entry", () => {
    expect(calendarProblem("", "Election day")).toMatch(/date/)
    expect(calendarProblem("2026-11-04", "ab")).toMatch(/reason/)
    expect(calendarProblem("1999-01-01", "Election day")).toMatch(/2000 and 2100/)
    expect(calendarProblem("2026-11-04", "Election day")).toBeNull()
  })
  it("offers a short list of years around today", () => { expect(calendarYears(new Date(2026, 10, 20))).toEqual(["2025", "2026", "2027", "2028"]) })
})

import { RECON_KINDS, RECON_STATUS, approveProblem, canEditRecon, canSubmitRecon, emptyReconItem, reconItemProblem, reconItemRequest, reconNextStep, startReconProblem } from "./legal.logic"

describe("trust reconciliation", () => {
  const base = { status: "DRAFT", difference: 0, cashBookBalance: 9000, clientLedgerTotal: 9000, changedSinceSubmitted: false, periodEnd: "2026-10-31" }
  it("checks the start of a reconciliation", () => {
    expect(startReconProblem("", "100", "2026-11-20", null)).toMatch(/date/)
    expect(startReconProblem("2026-11-21", "100", "2026-11-20", null)).toMatch(/future/)
    expect(startReconProblem("2026-09-30", "100", "2026-11-20", "2026-09-30")).toMatch(/already reconciled up to 2026-09-30/)
    expect(startReconProblem("2026-09-29", "100", "2026-11-20", "2026-09-30")).toMatch(/already reconciled/)
    expect(startReconProblem("2026-10-31", "", "2026-11-20", "2026-09-30")).toMatch(/closing balance/)
    expect(startReconProblem("2026-10-31", "abc", "2026-11-20", null)).toMatch(/closing balance/)
    expect(startReconProblem("2026-10-31", "0", "2026-11-20", "2026-09-30")).toBeNull()
    expect(startReconProblem("2026-10-31", "-250.50", "2026-11-20", null)).toBeNull()
    expect(startReconProblem("2026-11-20", "10", "2026-11-20", null)).toBeNull()
  })
  it("checks an item", () => {
    const ok = { ...emptyReconItem(), amount: "1500", itemDate: "2026-10-30", description: "Deposit in transit" }
    expect(reconItemProblem(ok, "2026-10-31")).toBeNull()
    for (const bad of ["", "0", "-1", "abc"]) expect(reconItemProblem({ ...ok, amount: bad }, "2026-10-31")).toMatch(/above nil/)
    expect(reconItemProblem({ ...ok, itemDate: "" }, "2026-10-31")).toMatch(/date/)
    expect(reconItemProblem({ ...ok, itemDate: "2026-11-01" }, "2026-10-31")).toMatch(/on or before 2026-10-31/)
    expect(reconItemProblem({ ...ok, description: "ab" }, "2026-10-31")).toMatch(/Describe/)
    expect(reconItemRequest({ ...ok, reference: " " })).toEqual({ kind: "OUTSTANDING_DEPOSIT", amount: 1500, itemDate: "2026-10-30", description: "Deposit in transit", reference: null, trustTransactionId: null })
  })
  it("says what to do about a difference, in the right direction", () => {
    expect(reconNextStep({ ...base, difference: 1000 })).toMatchObject({ tone: "danger" })
    expect(reconNextStep({ ...base, difference: 1000 }).text).toMatch(/R1.000[,.]00 more in the ledger.*deposit the bank has not yet credited/)
    expect(reconNextStep({ ...base, difference: -500 }).text).toMatch(/R500[,.]00 less in the ledger.*payment the bank has not yet debited/)
    expect(reconNextStep({ ...base, difference: 0.004 }).tone).toBe("success")
    expect(reconNextStep({ ...base, clientLedgerTotal: 8999 }).text).toMatch(/client ledgers do not add up/)
    expect(reconNextStep(base)).toEqual({ text: "It balances. Submit it for approval.", tone: "success" })
  })
  it("says where a submitted or approved one stands", () => {
    expect(reconNextStep({ ...base, status: "SUBMITTED" }).text).toMatch(/different person/)
    expect(reconNextStep({ ...base, status: "SUBMITTED", changedSinceSubmitted: true })).toMatchObject({ tone: "danger" })
    expect(reconNextStep({ ...base, status: "APPROVED" }).text).toMatch(/locked through 2026-10-31/)
  })
  it("offers submit only when balanced and editing only on a draft", () => {
    expect(canSubmitRecon({ status: "DRAFT", balanced: true }, true)).toBe(true)
    expect(canSubmitRecon({ status: "DRAFT", balanced: false }, true)).toBe(false)
    expect(canSubmitRecon({ status: "DRAFT", balanced: true }, false)).toBe(false)
    expect(canSubmitRecon({ status: "SUBMITTED", balanced: true }, true)).toBe(false)
    expect(canEditRecon({ status: "DRAFT" }, true)).toBe(true)
    expect(canEditRecon({ status: "SUBMITTED" }, true)).toBe(false)
    expect(canEditRecon({ status: "APPROVED" }, true)).toBe(false)
  })
  it("needs an explanation to approve when client balances disagree", () => {
    expect(approveProblem(0, "")).toBeNull()
    expect(approveProblem(2, "")).toMatch(/at least 10/)
    expect(approveProblem(2, "short")).toMatch(/at least 10/)
    expect(approveProblem(2, "Checked each against the statements")).toBeNull()
  })
  it("has labels for every status and kind", () => {
    for (const s of ["DRAFT", "SUBMITTED", "APPROVED"]) expect(RECON_STATUS[s]).toBeDefined()
    expect(RECON_KINDS.map(k => k[0])).toEqual(["OUTSTANDING_DEPOSIT", "OUTSTANDING_PAYMENT"])
  })
})

import { WALL_SCOPES, canLiftWall, emptyWall, liftProblem, wallHeadline, wallProblem, wallRequest, type Wall } from "./legal.logic"

const aWall = (over: Partial<Wall> = {}): Wall => ({ id: "w1", scope: "MATTER", matterId: "m1", clientId: null, targetLabel: "M-1 Dlamini v Nkosi", userId: "u-barred", userName: "Thandi Mokoena", reason: "Acted for the other side", createdByName: "Admin", createdAt: "2026-11-01T08:00:00Z", active: true, liftedByName: null, liftedAt: null, liftReason: null, ...over })

describe("ethical walls", () => {
  it("needs a target that matches the scope, a person and a real reason", () => {
    const base = { ...emptyWall(), userId: "u1", reason: "Acted for the other side" }
    expect(wallProblem({ ...base, matterId: "" })).toMatch(/matter/i)
    expect(wallProblem({ ...base, matterId: "m1" })).toBeNull()
    expect(wallProblem({ ...base, scope: "CLIENT", clientId: "" })).toMatch(/client/i)
    expect(wallProblem({ ...base, scope: "CLIENT", clientId: "c1" })).toBeNull()
    expect(wallProblem({ ...base, matterId: "m1", userId: "" })).toMatch(/person/i)
    expect(wallProblem({ ...base, matterId: "m1", reason: "too short" })).toMatch(/10 characters/)
    expect(wallProblem({ ...base, matterId: "m1", reason: "          " })).toMatch(/10 characters/)
    expect(wallProblem({ ...base, scope: "FIRM", matterId: "m1" })).toMatch(/covers/i)
  })
  it("sends only the target for the chosen scope, and a trimmed reason", () => {
    const f = { scope: "CLIENT", matterId: "stale-matter", clientId: "c1", userId: "u1", reason: "  Related to the director  " }
    expect(wallRequest(f, "Thandi")).toEqual({ scope: "CLIENT", matterId: null, clientId: "c1", userId: "u1", userName: "Thandi", reason: "Related to the director" })
    const g = { ...f, scope: "MATTER", matterId: "m1" }
    expect(wallRequest(g, "Thandi")).toMatchObject({ matterId: "m1", clientId: null })
  })
  it("offers lifting only to a manager, only while active, and never to the person it bars", () => {
    expect(canLiftWall(aWall(), "u-admin", true)).toBe(true)
    expect(canLiftWall(aWall(), "u-barred", true)).toBe(false)
    expect(canLiftWall(aWall(), "u-admin", false)).toBe(false)
    expect(canLiftWall(aWall({ active: false }), "u-admin", true)).toBe(false)
    expect(canLiftWall(aWall(), undefined, true)).toBe(false)
  })
  it("needs a real reason to lift", () => {
    expect(liftProblem("ok")).toMatch(/10 characters/)
    expect(liftProblem("   ")).not.toBeNull()
    expect(liftProblem("Consent given in writing")).toBeNull()
  })
  it("describes who is barred from what", () => {
    expect(wallHeadline(aWall())).toBe("Thandi Mokoena is barred from M-1 Dlamini v Nkosi")
    expect(wallHeadline(aWall({ scope: "CLIENT", targetLabel: "Dlamini Trading" }))).toBe("Thandi Mokoena is barred from all of Dlamini Trading's matters")
    expect(wallHeadline(aWall({ userName: null }))).toMatch(/^This person is barred/)
  })
  it("has both scopes", () => { expect(WALL_SCOPES.map(s => s[0])).toEqual(["MATTER", "CLIENT"]) })
})

import { KYC_STATUS, assessmentProblem, assessmentRequest, canOpenReview, emptyAssessment, emptyKycItem, emptyKycParty, kycActions, kycApproveProblem, kycDays, kycItemProblem, kycItemRequest, kycKindsFor, kycNextStep, kycPartyProblem, kycPartyRequest, kycReturnProblem, kycRolesFor, kycSettingProblem, kycSubjectsFor, levelLabel, liveRisk, needsSource, profileToAssessment, reviewText, splitCycles, type KycProfile } from "./legal.logic"

const TODAY = "2026-11-20"
const kycProfile = (o: Partial<KycProfile> = {}): KycProfile => ({
  id: "p1", clientId: "c1", clientName: "Thandi", subjectKind: "INDIVIDUAL", status: "DRAFT",
  riskGeography: null, riskService: null, riskClient: null, riskChannel: null, riskRating: null, dueDiligenceLevel: "STANDARD", reviewMonths: 24,
  pepStatus: null, pepCheckedOn: null, pepNote: null, sanctionsCheckedOn: null, sanctionsResult: null, businessNature: null, sourceOfFunds: null, sourceOfWealth: null,
  startedByName: "Prep", startedAt: "2026-11-01T08:00:00Z", submittedBy: null, submittedByName: null, submittedAt: null, returnReason: null,
  approvedByName: null, approvedAt: null, approvalNote: null, selfApproved: false, seniorApproved: false, validUntil: null,
  needsSenior: false, missing: [], parties: [], items: [], ...o,
})

describe("client due diligence: ratings and forms", () => {
  it("shows the rating as the form is filled in, the same way the server works it out", () => {
    const f = { geography: "LOW", service: "LOW", client: "LOW", channel: "LOW", pep: "NONE" }
    expect(liveRisk(f)).toBe("LOW")
    expect(liveRisk({ ...f, service: "MEDIUM" })).toBe("MEDIUM")
    expect(liveRisk({ ...f, channel: "HIGH", service: "MEDIUM" })).toBe("HIGH")
    expect(liveRisk({ ...f, client: "" })).toBeNull()
    expect(liveRisk({ ...f, pep: "PIP" })).toBe("HIGH")
    expect(needsSource({ ...f, pep: "FPEP" })).toBe(true)
    expect(needsSource(f)).toBe(false)
  })
  it("labels the level of due diligence", () => {
    expect(levelLabel("SIMPLIFIED")).toBe("Simplified due diligence")
    expect(levelLabel("ENHANCED")).toBe("Enhanced due diligence")
    expect(levelLabel("STANDARD")).toBe("Standard due diligence")
  })
  it("sends blanks as null and round-trips a profile into the form", () => {
    expect(assessmentRequest(emptyAssessment())).toEqual({ geography: null, service: null, client: null, channel: null, pep: null, pepCheckedOn: null, pepNote: null, sanctionsCheckedOn: null, sanctionsResult: null, businessNature: null, sourceOfFunds: null, sourceOfWealth: null })
    const form = profileToAssessment(kycProfile({ riskGeography: "LOW", pepStatus: "NONE", pepCheckedOn: "2026-11-01", businessNature: " Conveyancing " }))
    expect(form.geography).toBe("LOW")
    expect(form.service).toBe("")
    expect(assessmentRequest(form).businessNature).toBe("Conveyancing")
  })
  it("wants a date with each check and none in the future", () => {
    const f = { ...emptyAssessment(), pep: "NONE" }
    expect(assessmentProblem(f, TODAY)).toMatch(/politically exposed check was done/)
    expect(assessmentProblem({ ...f, pepCheckedOn: "2026-12-01" }, TODAY)).toMatch(/future/)
    expect(assessmentProblem({ ...emptyAssessment(), sanctionsResult: "CLEAR" }, TODAY)).toMatch(/sanctions screening was done/)
    expect(assessmentProblem({ ...f, pepCheckedOn: TODAY, sanctionsResult: "CLEAR", sanctionsCheckedOn: TODAY }, TODAY)).toBeNull()
  })
  it("offers the roles and documents that fit each kind of client", () => {
    expect(kycRolesFor("INDIVIDUAL")).toEqual([])
    expect(kycRolesFor("COMPANY").map(r => r[0])).toEqual(["AUTHORISED_PERSON", "BENEFICIAL_OWNER"])
    expect(kycRolesFor("TRUST").map(r => r[0])).toContain("FOUNDER")
    expect(kycRolesFor("PARTNERSHIP").map(r => r[0])).toContain("PARTNER")
    expect(kycKindsFor("TRUST").map(r => r[0])).toContain("TRUST_DEED")
    expect(kycKindsFor("COMPANY").map(r => r[0])).toContain("REGISTRATION")
    expect(kycKindsFor("INDIVIDUAL").map(r => r[0])).toEqual(["IDENTITY", "ADDRESS", "OTHER"])
  })
  it("lets an individual client be verified only as an individual", () => {
    expect(kycSubjectsFor("INDIVIDUAL").map(s => s[0])).toEqual(["INDIVIDUAL"])
    expect(kycSubjectsFor("ENTITY").map(s => s[0])).toEqual(["COMPANY", "TRUST", "PARTNERSHIP"])
  })
})

describe("client due diligence: people and documents", () => {
  const owner = { ...emptyKycParty("COMPANY"), role: "BENEFICIAL_OWNER", name: "B. Owner", method: "OWNERSHIP", percent: "60" }
  it("needs a name, and for an owner by ownership a percentage up to 100", () => {
    expect(kycPartyProblem({ ...owner, name: "" }, TODAY)).toMatch(/full name/)
    expect(kycPartyProblem({ ...owner, percent: "" }, TODAY)).toMatch(/percentage/)
    expect(kycPartyProblem({ ...owner, percent: "0" }, TODAY)).toMatch(/percentage/)
    expect(kycPartyProblem({ ...owner, percent: "101" }, TODAY)).toMatch(/percentage/)
    expect(kycPartyProblem(owner, TODAY)).toBeNull()
  })
  it("does not need a percentage for an owner found by control", () => {
    expect(kycPartyProblem({ ...owner, method: "CONTROL", percent: "" }, TODAY)).toBeNull()
  })
  it("sends the percentage and method only for beneficial owners", () => {
    expect(kycPartyRequest(owner)).toMatchObject({ role: "BENEFICIAL_OWNER", percent: 60, method: "OWNERSHIP" })
    expect(kycPartyRequest({ ...owner, method: "CONTROL", percent: "60" })).toMatchObject({ percent: null, method: "CONTROL" })
    expect(kycPartyRequest({ ...owner, role: "AUTHORISED_PERSON" })).toMatchObject({ percent: null, method: null })
  })
  it("refuses a verification date in the future", () => {
    expect(kycPartyProblem({ ...owner, verifiedOn: "2026-12-01" }, TODAY)).toMatch(/future/)
  })
  it("asks what was checked and when, and never in the future", () => {
    const f = emptyKycItem("INDIVIDUAL", TODAY)
    expect(f.kind).toBe("IDENTITY")
    expect(kycItemProblem(f, TODAY)).toMatch(/what was checked/)
    expect(kycItemProblem({ ...f, description: "Green ID book, original seen", verifiedOn: "2026-12-01" }, TODAY)).toMatch(/future/)
    expect(kycItemProblem({ ...f, description: "Green ID book, original seen" }, TODAY)).toBeNull()
    expect(kycItemRequest({ ...f, description: " Green ID book ", partyId: "" })).toMatchObject({ description: "Green ID book", partyId: null, documentRef: null })
  })
})

describe("client due diligence: who can do what", () => {
  const all = { prepare: true, approve: true, senior: true }
  it("lets the preparer edit and submit a complete draft only", () => {
    expect(kycActions(kycProfile({ missing: ["x"] }), all, "u1")).toMatchObject({ canEdit: true, canSubmit: false })
    expect(kycActions(kycProfile(), all, "u1")).toMatchObject({ canEdit: true, canSubmit: true })
    expect(kycActions(kycProfile(), { ...all, prepare: false }, "u1")).toMatchObject({ canEdit: false, canSubmit: false })
    expect(kycActions(kycProfile({ status: "SUBMITTED" }), all, "u1").canEdit).toBe(false)
    expect(kycActions(kycProfile({ status: "VERIFIED" }), all, "u1")).toMatchObject({ canEdit: false, canSubmit: false, canReturn: false, canApprove: false })
  })
  it("lets an approver approve or return a submitted cycle", () => {
    const p = kycProfile({ status: "SUBMITTED", submittedBy: "u2" })
    expect(kycActions(p, all, "u1")).toMatchObject({ canApprove: true, canReturn: true, selfSubmitted: false, approveHint: null })
    expect(kycActions(p, { ...all, approve: false }, "u1")).toMatchObject({ canApprove: false, canReturn: false })
  })
  it("needs senior management for a high-risk or politically exposed client", () => {
    const p = kycProfile({ status: "SUBMITTED", submittedBy: "u2", needsSenior: true })
    expect(kycActions(p, all, "u1").canApprove).toBe(true)
    const a = kycActions(p, { ...all, senior: false }, "u1")
    expect(a.canApprove).toBe(false)
    expect(a.approveHint).toMatch(/Senior management/)
  })
  it("never offers approval of your own work on a high-risk client", () => {
    const a = kycActions(kycProfile({ status: "SUBMITTED", submittedBy: "u1", needsSenior: true }), all, "u1")
    expect(a.canApprove).toBe(false)
    expect(a.approveHint).toMatch(/someone else/)
  })
  it("offers approving your own ordinary client, with a note, and says why", () => {
    const a = kycActions(kycProfile({ status: "SUBMITTED", submittedBy: "u1" }), all, "u1")
    expect(a.canApprove).toBe(true)
    expect(a.selfSubmitted).toBe(true)
    expect(a.approveHint).toMatch(/Another person must approve/)
    expect(kycApproveProblem(true, "ok")).toMatch(/written reason/)
    expect(kycApproveProblem(true, "Sole practitioner, seen in person")).toBeNull()
    expect(kycApproveProblem(false, "")).toBeNull()
  })
  it("needs a reason to return a cycle", () => {
    expect(kycReturnProblem("no")).toMatch(/10 characters/)
    expect(kycReturnProblem("Proof of address is too old")).toBeNull()
  })
})

describe("client due diligence: dates, next step, setting", () => {
  it("counts whole days", () => {
    expect(kycDays("2026-11-21", TODAY)).toBe(1)
    expect(kycDays("2026-11-19", TODAY)).toBe(-1)
    expect(kycDays("2027-11-20", TODAY)).toBe(365)
  })
  it("words the review date", () => {
    expect(reviewText(null, TODAY)).toBe("")
    expect(reviewText("2026-12-20", TODAY)).toBe("Review due in 30 days")
    expect(reviewText("2026-11-21", TODAY)).toBe("Review due in 1 day")
    expect(reviewText(TODAY, TODAY)).toBe("Review due today")
    expect(reviewText("2026-11-19", TODAY)).toBe("Review overdue by 1 day")
    expect(reviewText("2026-11-10", TODAY)).toBe("Review overdue by 10 days")
  })
  it("says what to do next", () => {
    expect(kycNextStep(kycProfile({ missing: ["a", "b"] }), TODAY).text).toBe("2 things still to do before it can be submitted.")
    expect(kycNextStep(kycProfile({ missing: ["a"] }), TODAY).text).toBe("1 thing still to do before it can be submitted.")
    expect(kycNextStep(kycProfile({ missing: ["a"], returnReason: "Address is too old" }), TODAY)).toMatchObject({ tone: "danger" })
    expect(kycNextStep(kycProfile(), TODAY)).toEqual({ text: "Complete. Submit it for approval.", tone: "success" })
    expect(kycNextStep(kycProfile({ status: "SUBMITTED" }), TODAY).tone).toBe("warning")
    expect(kycNextStep(kycProfile({ status: "VERIFIED", validUntil: "2027-11-20" }), TODAY).tone).toBe("success")
    expect(kycNextStep(kycProfile({ status: "VERIFIED", validUntil: "2026-11-01" }), TODAY)).toMatchObject({ tone: "danger" })
  })
  it("has a label and tone for every overview status", () => {
    for (const s of ["NOT_STARTED", "DRAFT", "SUBMITTED", "VERIFIED", "DUE_SOON", "EXPIRED"]) expect(KYC_STATUS[s].label.length).toBeGreaterThan(3)
    expect(KYC_STATUS.EXPIRED.tone).toBe("danger")
    expect(KYC_STATUS.VERIFIED.tone).toBe("success")
  })
  it("refuses a no-change or unexplained setting change", () => {
    expect(kycSettingProblem(true, true, "The board resolved this")).toMatch(/already/)
    expect(kycSettingProblem(true, false, "short")).toMatch(/10 characters/)
    expect(kycSettingProblem(true, false, "The board resolved this")).toBeNull()
  })
  it("splits the newest cycle from the history and allows a review only after verification", () => {
    const a = kycProfile({ id: "new", status: "VERIFIED" }), b = kycProfile({ id: "old", status: "VERIFIED" })
    expect(splitCycles([a, b])).toEqual({ current: a, earlier: [b] })
    expect(splitCycles([])).toEqual({ current: null, earlier: [] })
    expect(canOpenReview([a, b], true)).toBe(true)
    expect(canOpenReview([a, b], false)).toBe(false)
    expect(canOpenReview([kycProfile({ status: "DRAFT" })], true)).toBe(false)
    expect(canOpenReview([], true)).toBe(false)
  })
})

import { DOC_ACCEPT, MAX_DOC_BYTES, docActions, docBadges, docCategoryLabel, docEventText, docFileProblem, docReasonProblem, docUploadProblem, emptyDocUpload, shortHash, titleFromFile } from "./legal.logic"

describe("matter documents", () => {
  const doc = (o: Partial<{ status: string; legalHold: boolean; checkedOutBy: string | null; checkedOutByName: string | null }> = {}) => ({ status: "ACTIVE", legalHold: false, checkedOutBy: null, checkedOutByName: null, ...o })
  const all = { write: true, hold: true, override: true }

  it("catches a bad file before it is sent", () => {
    expect(docFileProblem(null)).toMatch(/Choose a file/)
    expect(docFileProblem({ name: "a.pdf", size: 0 })).toMatch(/empty/)
    expect(docFileProblem({ name: "a.pdf", size: MAX_DOC_BYTES + 1 })).toMatch(/20 MB/)
    expect(docFileProblem({ name: "run.exe", size: 10 })).toMatch(/PDF, Word/)
    expect(docFileProblem({ name: "noextension", size: 10 })).toMatch(/PDF, Word/)
    expect(docFileProblem({ name: "Brief.PDF", size: 10 })).toBeNull()
    expect(docFileProblem({ name: "a.docx", size: MAX_DOC_BYTES })).toBeNull()
  })
  it("offers the same extensions to the file picker", () => {
    expect(DOC_ACCEPT).toContain(".pdf")
    expect(DOC_ACCEPT).toContain(".docx")
    expect(DOC_ACCEPT).not.toContain(".exe")
  })
  it("needs a matter, a title, a kind and a good file", () => {
    const f = { ...emptyDocUpload(), matterId: "m1", title: "Letter" }
    const file = { name: "a.pdf", size: 10 }
    expect(docUploadProblem({ ...f, matterId: "" }, file)).toMatch(/matter/)
    expect(docUploadProblem({ ...f, title: " " }, file)).toMatch(/title/)
    expect(docUploadProblem({ ...f, category: "MEMES" }, file)).toMatch(/kind/)
    expect(docUploadProblem(f, null)).toMatch(/Choose a file/)
    expect(docUploadProblem(f, file)).toBeNull()
  })
  it("suggests a title from the file name", () => {
    expect(titleFromFile("Letter_of_demand.pdf")).toBe("Letter of demand")
    expect(titleFromFile("noext")).toBe("noext")
  })
  it("wants a reason for the sensitive steps", () => {
    expect(docReasonProblem("no", "archiving it")).toMatch(/archiving it/)
    expect(docReasonProblem("Matter closed and filed", "archiving it")).toBeNull()
  })
  it("labels the kinds", () => {
    expect(docCategoryLabel("COURT_ORDER")).toBe("Court order")
    expect(docCategoryLabel("X")).toBe("X")
  })

  it("lets a writer add versions, check out, archive and edit an ordinary document", () => {
    expect(docActions(doc(), all, "u1")).toMatchObject({ canAddVersion: true, canCheckOut: true, canCheckIn: false, canCancelCheckout: false, canOverride: false, canHold: true, canReleaseHold: false, canArchive: true, canRestore: false, canEdit: true, archiveBlockedBy: null })
  })
  it("gives a read-only person nothing", () => {
    const a = docActions(doc(), { write: false, hold: false, override: false }, "u1")
    expect(Object.entries(a).filter(([k, v]) => k.startsWith("can") && v)).toEqual([])
  })
  it("lets only the holder add a version, check in or cancel when checked out", () => {
    const mine = docActions(doc({ checkedOutBy: "u1", checkedOutByName: "Me" }), all, "u1")
    expect(mine).toMatchObject({ canAddVersion: true, canCheckIn: true, canCancelCheckout: true, canCheckOut: false, canOverride: false, canArchive: false, archiveBlockedBy: "It is checked out." })
    const theirs = docActions(doc({ checkedOutBy: "u2", checkedOutByName: "Them" }), all, "u1")
    expect(theirs).toMatchObject({ canAddVersion: false, canCheckIn: false, canCancelCheckout: false, canCheckOut: false, canOverride: true })
    expect(docActions(doc({ checkedOutBy: "u2" }), { ...all, override: false }, "u1").canOverride).toBe(false)
  })
  it("blocks archiving while on legal hold and offers release to those who may", () => {
    const a = docActions(doc({ legalHold: true }), all, "u1")
    expect(a).toMatchObject({ canArchive: false, canHold: false, canReleaseHold: true, archiveBlockedBy: "It is under legal hold." })
    expect(docActions(doc({ legalHold: true }), { ...all, hold: false }, "u1").canReleaseHold).toBe(false)
  })
  it("makes an archived document read-only apart from restoring it", () => {
    const a = docActions(doc({ status: "ARCHIVED" }), all, "u1")
    expect(a).toMatchObject({ canAddVersion: false, canCheckOut: false, canEdit: false, canArchive: false, canHold: false, canRestore: true, archiveBlockedBy: null })
  })

  it("shows what is going on with a document", () => {
    expect(docBadges(doc())).toEqual([])
    expect(docBadges(doc({ status: "ARCHIVED", legalHold: true, checkedOutByName: "Anele" }))).toEqual([
      { label: "Archived", tone: "neutral" }, { label: "Legal hold", tone: "danger" }, { label: "Checked out by Anele", tone: "warning" }])
  })
  it("reads the event log as sentences", () => {
    expect(docEventText({ eventType: "UPLOADED", versionNo: 1, actorName: "Anele", detail: "brief.pdf" })).toBe("Anele added the document")
    expect(docEventText({ eventType: "NEW_VERSION", versionNo: 3, actorName: "Buhle", detail: "c.pdf (checked in)" })).toBe("Buhle added a new version (version 3): c.pdf (checked in)")
    expect(docEventText({ eventType: "DOWNLOADED", versionNo: 2, actorName: "Buhle", detail: "c.pdf" })).toBe("Buhle downloaded it (version 2)")
    expect(docEventText({ eventType: "HOLD_SET", versionNo: null, actorName: "Admin", detail: "Litigation is likely" })).toBe("Admin placed it under legal hold: Litigation is likely")
    expect(docEventText({ eventType: "SOMETHING_NEW", versionNo: null, actorName: null, detail: null })).toBe("Someone something_new")
  })
  it("shortens a hash for display", () => {
    expect(shortHash("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")).toBe("ba7816bf…0015ad")
  })
})

describe("litigation: the case form", () => {
  const today = "2026-11-20"
  const ok = { ...emptyLitCase(), matterId: "m", courtName: "Gauteng Division, Johannesburg" }
  it("needs a matter when opening, and a court", () => {
    expect(litCaseProblem(ok, true, today)).toBeNull()
    expect(litCaseProblem({ ...ok, matterId: "" }, true, today)).toMatch(/matter/i)
    expect(litCaseProblem({ ...ok, matterId: "" }, false, today)).toBeNull()
    expect(litCaseProblem({ ...ok, courtName: " x " }, true, today)).toMatch(/court/i)
  })
  it("needs the case number once the case has left pre-action", () => {
    expect(litCaseProblem({ ...ok, stage: "PLEADINGS" }, true, today)).toMatch(/case number/i)
    expect(litCaseProblem({ ...ok, stage: "PLEADINGS", caseNumber: "123/2026" }, true, today)).toBeNull()
  })
  it("keeps the dates in order and out of the future", () => {
    expect(litCaseProblem({ ...ok, issuedOn: "2026-11-21" }, true, today)).toMatch(/issued/i)
    expect(litCaseProblem({ ...ok, servedOn: "2026-11-21" }, true, today)).toMatch(/served/i)
    expect(litCaseProblem({ ...ok, issuedOn: "2026-02-10", servedOn: "2026-02-01" }, true, today)).toMatch(/before it was issued/i)
    expect(litCaseProblem({ ...ok, issuedOn: "2026-02-10", servedOn: "2026-02-10" }, true, today)).toBeNull()
  })
  it("sends blanks as null and the matter and stage only when opening", () => {
    const r = litCaseRequest({ ...ok, judge: "  ", caseNumber: " 12/26 " }, true) as Record<string, unknown>
    expect(r.judge).toBeNull(); expect(r.caseNumber).toBe("12/26"); expect(r.matterId).toBe("m"); expect(r.stage).toBe("PRE_ACTION"); expect(r.issuedOn).toBeNull()
    const e = litCaseRequest(ok, false) as Record<string, unknown>
    expect(e.matterId).toBeUndefined(); expect(e.stage).toBeUndefined()
  })
  it("fills the form back from a case", () => {
    const c = { matterId: "m", courtLevel: "LABOUR", courtName: "Labour Court", ourRole: "APPLICANT", caseNumber: null, stage: "TRIAL", opposingParty: null, opposingAttorney: "Smith", opposingAttorneyContact: null, judge: null, issuedOn: "2026-01-01", servedOn: null, notes: null } as LitCase
    const f = litCaseToForm(c)
    expect(f.caseNumber).toBe(""); expect(f.opposingAttorney).toBe("Smith"); expect(f.issuedOn).toBe("2026-01-01"); expect(f.courtLevel).toBe("LABOUR")
  })
})
describe("litigation: stage, conclude and reopen", () => {
  it("lets a case move forward freely and back only with a reason", () => {
    expect(litStageProblem("PLEADINGS", "TRIAL", "")).toBeNull()
    expect(litStageProblem("TRIAL", "PLEADINGS", "")).toMatch(/why the case is moving back/)
    expect(litStageProblem("TRIAL", "PLEADINGS", "short")).not.toBeNull()
    expect(litStageProblem("TRIAL", "PLEADINGS", "Amended pleadings were filed")).toBeNull()
    expect(litStageProblem("TRIAL", "TRIAL", "")).toMatch(/already/)
    expect(litStageProblem("TRIAL", "CONCLUDED", "Because it is over")).toMatch(/Choose a stage/)
    expect(litStageProblem("TRIAL", "", "")).toMatch(/Choose a stage/)
  })
  it("needs an outcome, a date in range and a note to conclude", () => {
    const t = "2026-11-20"
    expect(litConcludeProblem("WON", t, "Judgment for the plaintiff", "2026-01-01", t)).toBeNull()
    expect(litConcludeProblem("", t, "Judgment for the plaintiff", null, t)).toMatch(/outcome/)
    expect(litConcludeProblem("WON", "", "Judgment for the plaintiff", null, t)).toMatch(/date/)
    expect(litConcludeProblem("WON", "2026-11-21", "Judgment for the plaintiff", null, t)).toMatch(/future/)
    expect(litConcludeProblem("WON", "2025-12-31", "Judgment for the plaintiff", "2026-01-01", t)).toMatch(/before it was issued/)
    expect(litConcludeProblem("WON", t, "won", null, t)).toMatch(/how it ended/)
  })
  it("needs a stage and a reason to reopen", () => {
    expect(litReopenProblem("", "Leave to appeal was granted")).toMatch(/stage/)
    expect(litReopenProblem("APPEAL", "no")).toMatch(/reopened/)
    expect(litReopenProblem("APPEAL", "Leave to appeal was granted")).toBeNull()
  })
})
describe("litigation: hearings and orders", () => {
  const t = "2026-11-20"
  it("needs a kind and a date to set a hearing", () => {
    expect(litHearingProblem("TRIAL", "2026-12-01")).toBeNull()
    expect(litHearingProblem("", "2026-12-01")).toMatch(/kind/)
    expect(litHearingProblem("TRIAL", "")).toMatch(/date/)
  })
  it("refuses a postponement to the same date and time, or without a reason", () => {
    const h = { hearingDate: "2026-12-01", hearingTime: "09:30:00" }
    expect(litPostponeProblem(h, "2026-12-01", "09:30", "Court roll was full that day")).toMatch(/already/)
    expect(litPostponeProblem(h, "2026-12-01", "10:00", "Court roll was full that day")).toBeNull()
    expect(litPostponeProblem(h, "2026-12-15", "09:30", "no")).toMatch(/postponed/)
    expect(litPostponeProblem(h, "", "", "Court roll was full that day")).toMatch(/new date/)
    expect(litPostponeProblem({ hearingDate: "2026-12-01", hearingTime: null }, "2026-12-01", "", "Court roll was full that day")).toMatch(/already/)
  })
  it("will not mark a future hearing as held, and wants what happened", () => {
    expect(litHeldProblem("2026-11-21", "Argued and judgment reserved", t)).toMatch(/not happened yet/)
    expect(litHeldProblem(t, "short", t)).toMatch(/what happened/)
    expect(litHeldProblem(t, "Argued and judgment reserved", t)).toBeNull()
  })
  it("checks an order's dates and summary", () => {
    expect(litOrderProblem(t, "Defendant to deliver the plea", "2026-12-04", t)).toBeNull()
    expect(litOrderProblem(t, "Defendant to deliver the plea", "", t)).toBeNull()
    expect(litOrderProblem("", "Defendant to deliver the plea", "", t)).toMatch(/date of the order/)
    expect(litOrderProblem("2026-11-21", "Defendant to deliver the plea", "", t)).toMatch(/future/)
    expect(litOrderProblem(t, "Defendant to deliver the plea", "2026-11-01", t)).toMatch(/before the order/)
    expect(litOrderProblem(t, "plea", "", t)).toMatch(/what the court ordered/)
  })
  it("checks the date an order was complied with", () => {
    expect(litCompliedProblem("2026-11-01", t, t)).toBeNull()
    expect(litCompliedProblem("2026-11-01", "", t)).toMatch(/Enter/)
    expect(litCompliedProblem("2026-11-01", "2026-11-21", t)).toMatch(/future/)
    expect(litCompliedProblem("2026-11-01", "2026-10-31", t)).toMatch(/before it was made/)
  })
})
describe("litigation: what a person can do", () => {
  it("lets writers change an open case and only concluders conclude or reopen", () => {
    const all = { write: true, conclude: true }
    expect(litActions({ open: true }, all)).toEqual({ canEdit: true, canStage: true, canSchedule: true, canOrder: true, canConclude: true, canReopen: false })
    expect(litActions({ open: false }, all)).toEqual({ canEdit: false, canStage: false, canSchedule: false, canOrder: false, canConclude: false, canReopen: true })
    expect(litActions({ open: true }, { write: true, conclude: false }).canConclude).toBe(false)
    expect(litActions({ open: false }, { write: true, conclude: false }).canReopen).toBe(false)
    expect(litActions({ open: true }, { write: false, conclude: false })).toEqual({ canEdit: false, canStage: false, canSchedule: false, canOrder: false, canConclude: false, canReopen: false })
  })
  it("lets only a scheduled hearing on an open case be changed", () => {
    expect(litHearingActions({ status: "SCHEDULED" }, true, true).canChange).toBe(true)
    expect(litHearingActions({ status: "HELD" }, true, true).canChange).toBe(false)
    expect(litHearingActions({ status: "SCHEDULED" }, false, true).canChange).toBe(false)
    expect(litHearingActions({ status: "SCHEDULED" }, true, false).canChange).toBe(false)
  })
})
describe("litigation: wording", () => {
  it("reads the history as sentences", () => {
    expect(litEventText({ eventType: "STAGE_CHANGED", actorName: "Anele", detail: "PLEADINGS to TRIAL" })).toBe("Anele moved the case: PLEADINGS to TRIAL")
    expect(litEventText({ eventType: "HEARING_SET", actorName: null, detail: null })).toBe("Someone set a hearing")
    expect(litEventText({ eventType: "NEW_KIND", actorName: "A", detail: null })).toBe("A new_kind")
  })
  it("labels stages, times and days away", () => {
    expect(litStageLabel("PRE_TRIAL")).toBe("Pre-trial"); expect(litStageLabel("ODD")).toBe("ODD")
    expect(litTime("09:30:00")).toBe("09:30"); expect(litTime(null)).toBe("")
    expect(litDaysAway("2026-11-23", "2026-11-20")).toBe(3)
    expect(litDaysText(0)).toBe("Today"); expect(litDaysText(1)).toBe("Tomorrow"); expect(litDaysText(5)).toBe("In 5 days"); expect(litDaysText(-3)).toBe("3 days ago"); expect(litDaysText(-1)).toBe("Yesterday")
  })
})

describe("tasks: the form", () => {
  const ok = { ...emptyTask("m1"), title: "Draft particulars" }
  it("needs a matter when creating and a sensible title", () => {
    expect(taskProblem(ok, true)).toBeNull()
    expect(taskProblem({ ...ok, matterId: "" }, true)).toMatch(/matter/); expect(taskProblem({ ...ok, matterId: "" }, false)).toBeNull()
    expect(taskProblem({ ...ok, title: " a " }, true)).toMatch(/title/)
    expect(taskProblem({ ...ok, title: "x".repeat(201) }, true)).toMatch(/200/)
    expect(taskProblem({ ...ok, description: "x".repeat(2001) }, true)).toMatch(/2000/)
  })
  it("sends the matter and assignee only when creating, and nulls what is blank", () => {
    expect(taskRequest({ ...ok, assigneeId: "u1", dueDate: "2026-07-01", description: " " }, true, "Thandi")).toEqual({ matterId: "m1", assigneeId: "u1", assigneeName: "Thandi", title: "Draft particulars", description: null, priority: "NORMAL", dueDate: "2026-07-01" })
    expect(taskRequest(ok, true, null)).toMatchObject({ assigneeId: null, assigneeName: null, dueDate: null })
    expect(taskRequest(ok, false, null)).toEqual({ title: "Draft particulars", description: null, priority: "NORMAL", dueDate: null })
  })
  it("fills the form from a task", () => {
    const d = { task: { matterId: "m1", title: "T", priority: "HIGH", dueDate: "2026-07-01", assigneeId: "u1" }, description: "Per counsel" } as unknown as TaskDetail
    expect(taskToForm(d)).toEqual({ matterId: "m1", title: "T", description: "Per counsel", priority: "HIGH", dueDate: "2026-07-01", assigneeId: "u1" })
  })
  it("wants a reason of ten characters", () => {
    expect(taskReasonProblem("too short", "why")).toMatch(/10/); expect(taskReasonProblem("          x ", "why")).toMatch(/10/); expect(taskReasonProblem("Matter settled early", "why")).toBeNull()
  })
})
describe("tasks: what can be done", () => {
  it("follows the status", () => {
    expect(taskActions("OPEN", true)).toEqual({ canEdit: true, canAssign: true, canStart: true, canStop: false, canComplete: true, canReopen: false, canCancel: true })
    expect(taskActions("IN_PROGRESS", true)).toMatchObject({ canStart: false, canStop: true, canComplete: true })
    expect(taskActions("DONE", true)).toEqual({ canEdit: false, canAssign: false, canStart: false, canStop: false, canComplete: false, canReopen: true, canCancel: false })
    expect(Object.values(taskActions("CANCELLED", true)).every(v => v === false)).toBe(true)
  })
  it("offers nothing without the permission", () => { expect(Object.values(taskActions("OPEN", false)).every(v => v === false)).toBe(true); expect(taskActions("DONE", false).canReopen).toBe(false) })
  it("says how late or soon a task is, and nothing for finished ones", () => {
    expect(taskDueText("2026-06-10", "OPEN", "2026-06-10")).toBe("Due today"); expect(taskDueText("2026-06-11", "OPEN", "2026-06-10")).toBe("Due tomorrow")
    expect(taskDueText("2026-06-15", "OPEN", "2026-06-10")).toBe("Due in 5 days"); expect(taskDueText("2026-06-09", "OPEN", "2026-06-10")).toBe("Overdue by 1 day")
    expect(taskDueText("2026-06-01", "IN_PROGRESS", "2026-06-10")).toBe("Overdue by 9 days")
    expect(taskDueText("2026-06-01", "DONE", "2026-06-10")).toBe(""); expect(taskDueText(null, "OPEN", "2026-06-10")).toBe("No due date")
  })
  it("colours overdue first, then by status", () => {
    expect(taskTone("OPEN", true)).toBe("danger"); expect(taskTone("DONE", false)).toBe("success"); expect(taskTone("IN_PROGRESS", false)).toBe("info"); expect(taskTone("CANCELLED", false)).toBe("neutral"); expect(taskTone("OPEN", false)).toBe("warning")
    expect(taskPriorityTone("URGENT")).toBe("danger"); expect(taskPriorityTone("HIGH")).toBe("warning"); expect(taskPriorityTone("LOW")).toBe("neutral"); expect(taskStatusLabel("IN_PROGRESS")).toBe("In progress")
  })
  it("words the history", () => {
    expect(taskEventText({ eventType: "COMPLETED", actorName: "Anele", detail: null })).toBe("Anele completed the task")
    expect(taskEventText({ eventType: "REASSIGNED", actorName: null, detail: "Anele to Thandi" })).toBe("Someone reassigned the task: Anele to Thandi")
    expect(taskEventText({ eventType: "WEIRD", actorName: "A", detail: null })).toBe("A weird")
  })
})
describe("workflows: the form", () => {
  const ok = { ...emptyWorkflow(), name: "Open litigation file", steps: [{ ...emptyStep(), title: "Open file" }, { ...emptyStep(), title: "Letter of demand", offsetDays: "14" }] }
  it("needs a name and at least one good step", () => {
    expect(workflowProblem(ok)).toBeNull()
    expect(workflowProblem({ ...ok, name: "x" })).toMatch(/name/); expect(workflowProblem({ ...ok, steps: [] })).toMatch(/at least one/)
    expect(workflowProblem({ ...ok, steps: [{ ...emptyStep(), title: "" }] })).toMatch(/Step 1: give it a title/)
    expect(workflowProblem({ ...ok, steps: [ok.steps[0], { ...emptyStep(), title: "Two", offsetDays: "-1" }] })).toMatch(/Step 2: .*0 to 730/)
    expect(workflowProblem({ ...ok, steps: [{ ...emptyStep(), title: "Two", offsetDays: "731" }] })).toMatch(/730/)
    expect(workflowProblem({ ...ok, steps: [{ ...emptyStep(), title: "Two", offsetDays: "1.5" }] })).toMatch(/whole number/)
    expect(workflowProblem({ ...ok, steps: [{ ...emptyStep(), title: "Two", offsetDays: "" }] })).toMatch(/whole number/)
    expect(workflowProblem({ ...ok, steps: Array.from({ length: 31 }, (_, i) => ({ ...emptyStep(), title: `Step ${i}` })) })).toMatch(/30 steps/)
  })
  it("sends numbers, and nulls what is blank", () => {
    expect(workflowRequest(ok)).toEqual({ name: "Open litigation file", description: null, matterType: null, steps: [
      { title: "Open file", description: null, priority: "NORMAL", offsetDays: 0, assignTo: "STARTER" }, { title: "Letter of demand", description: null, priority: "NORMAL", offsetDays: 14, assignTo: "STARTER" }] })
  })
  it("fills the form from a workflow", () => {
    const w = { template: { name: "W", description: null, matterType: "LITIGATION" }, steps: [{ stepNo: 1, title: "A", description: "d", priority: "HIGH", offsetDays: 3, assignTo: "NONE" }] } as unknown as WorkflowDetail
    expect(workflowToForm(w)).toEqual({ name: "W", description: "", matterType: "LITIGATION", steps: [{ title: "A", description: "d", priority: "HIGH", offsetDays: "3", assignTo: "NONE" }] })
  })
  it("moves steps without losing any", () => {
    expect(moveStep(["a", "b", "c"], 2, 0)).toEqual(["c", "a", "b"]); expect(moveStep(["a", "b", "c"], 0, 1)).toEqual(["b", "a", "c"])
    expect(moveStep(["a", "b"], 0, -1)).toEqual(["a", "b"]); expect(moveStep(["a", "b"], 1, 2)).toEqual(["a", "b"])
  })
  it("checks the start date and shows each due date", () => {
    expect(wfApplyProblem("", "2026-06-10", "2026-06-10")).toMatch(/matter/); expect(wfApplyProblem("m", "", "2026-06-10")).toMatch(/start/)
    expect(wfApplyProblem("m", "2026-06-10", "2026-06-10")).toBeNull(); expect(wfApplyProblem("m", "2027-07-01", "2026-06-10")).toMatch(/within a year/); expect(wfApplyProblem("m", "2025-06-10", "2026-06-10")).toBeNull()
    expect(stepDue("2026-06-10", 7)).toBe("2026-06-17"); expect(stepDue("2026-12-30", 3)).toBe("2027-01-02")
  })
})

describe("firm reports", () => {
  it("checks the billing range", () => {
    expect(reportRangeProblem("2026-01-01", "2026-06-30", "2026-06-30")).toBeNull()
    expect(reportRangeProblem("", "2026-06-30", "2026-06-30")).toMatch(/start date/)
    expect(reportRangeProblem("2026-06-30", "2026-06-01", "2026-06-30")).toMatch(/before/)
    expect(reportRangeProblem("2026-01-01", "2026-07-01", "2026-06-30")).toMatch(/future/)
    expect(reportRangeProblem("2010-01-01", "2026-06-30", "2026-06-30")).toMatch(/ten years/)
  })
  it("checks the trust date", () => {
    expect(reportAsAtProblem("2026-06-30", "2026-06-30")).toBeNull(); expect(reportAsAtProblem("", "2026-06-30")).toMatch(/date/); expect(reportAsAtProblem("2026-07-01", "2026-06-30")).toMatch(/future/)
  })
  it("builds the request for each report and its CSV", () => {
    expect(reportUrl("billing", { from: "2026-01-01", to: "2026-06-30" })).toEqual({ path: "billing", params: { from: "2026-01-01", to: "2026-06-30" } })
    expect(reportUrl("billing", { from: "2026-01-01", to: "2026-06-30" }, true).path).toBe("billing.csv")
    expect(reportUrl("trust", { asAt: "" }, true)).toEqual({ path: "trust.csv", params: { asAt: undefined } })
    expect(reportUrl("debtors", { from: "x" })).toEqual({ path: "debtors", params: {} })
  })
  it("words months, statuses and colours", () => {
    expect(reportYearStart("2026-06-30")).toBe("2026-01-01"); expect(reportMonthText("2026-04")).toBe("April 2026"); expect(matterStatusText("ON_HOLD")).toBe("On hold"); expect(matterStatusText("X")).toBe("X")
    expect(bucketTone("CURRENT")).toBe("success"); expect(bucketTone("D31_60")).toBe("warning"); expect(bucketTone("D90_PLUS")).toBe("danger")
  })
  it("lays matters out as a grid", () => {
    const g = matterGrid([{ status: "OPEN", matterType: "LITIGATION", count: 3 }, { status: "CLOSED", matterType: "ESTATES", count: 2 }, { status: "OPEN", matterType: "ESTATES", count: 1 }])
    expect(g.kinds).toEqual(["ESTATES", "LITIGATION"]); expect(g.statuses).toEqual(["OPEN", "CLOSED"])
    expect(g.cell("OPEN", "LITIGATION")).toBe(3); expect(g.cell("CLOSED", "LITIGATION")).toBe(0)
  })
})

describe("access", () => {
  const perms: AccessPermission[] = [
    { code: "LEGALPRACTICE_READ", group: "Core", kind: "VIEW", level: "NORMAL", secondPerson: false, description: "r" },
    { code: "LEGALPRACTICE_TRUST_RECEIVE", group: "Trust account", kind: "WORK", level: "FINANCIAL", secondPerson: false, description: "t" },
    { code: "LEGALPRACTICE_MANAGE", group: "Core", kind: "WORK", level: "NORMAL", secondPerson: false, description: "m" },
    { code: "LEGALPRACTICE_WALL_MANAGE", group: "Ethical walls", kind: "ADMIN", level: "SENSITIVE", secondPerson: false, description: "w" },
  ]
  it("groups in the order they first appear", () => {
    const g = accessGroups(perms)
    expect(g.map(x => x.group)).toEqual(["Core", "Trust account", "Ethical walls"]); expect(g[0].items.map(p => p.code)).toEqual(["LEGALPRACTICE_READ", "LEGALPRACTICE_MANAGE"])
  })
  it("colours and words the levels", () => {
    expect(accessLevelTone("FINANCIAL")).toBe("danger"); expect(accessLevelTone("SENSITIVE")).toBe("warning"); expect(accessLevelTone("NORMAL")).toBe("neutral")
    expect(accessLevelText("FINANCIAL")).toBe("Financial"); expect(accessLevelText("NORMAL")).toBe("Normal"); expect(accessFindingTone("WARNING")).toBe("warning"); expect(accessFindingTone("NOTE")).toBe("info")
  })
  it("summarises a template and writes it out for pasting", () => {
    const t: AccessTemplate = { key: "k", name: "Bookkeeper", description: "", permissions: ["LEGALPRACTICE_TRUST_RECEIVE", "LEGALPRACTICE_READ", "LEGALPRACTICE_WALL_MANAGE"], findings: [] }
    expect(accessSummary(t, perms)).toBe("3 permissions, 1 financial, 1 sensitive")
    expect(accessSummary({ ...t, permissions: ["LEGALPRACTICE_READ"] }, perms)).toBe("1 permission")
    expect(accessClipboard(t)).toBe("Bookkeeper\nLEGALPRACTICE_READ\nLEGALPRACTICE_TRUST_RECEIVE\nLEGALPRACTICE_WALL_MANAGE")
    expect(t.permissions[0]).toBe("LEGALPRACTICE_TRUST_RECEIVE")
  })
  it("toggles without changing the original", () => {
    const a = ["x"]; expect(accessToggle(a, "y")).toEqual(["x", "y"]); expect(accessToggle(["x", "y"], "x")).toEqual(["y"]); expect(a).toEqual(["x"])
  })
})
