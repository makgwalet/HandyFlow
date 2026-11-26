import { describe, it, expect } from "vitest"
import { actionProblem, actionState, answerProblem, auditBody, auditProblem, completeProblem, emptyAudit, itemsProblem, parseItems, templateProblem } from "./auditRules"

describe("checklists", () => {
  it("parses one item per line", () => expect(parseItems(" Is it kept? \n\n Are files locked?\r\n")).toEqual(["Is it kept?", "Are files locked?"]))
  it("checks the items and the template", () => {
    expect(itemsProblem("")).toBe("Add at least one item, one per line")
    expect(itemsProblem("ok item\nx")).toBe("Each item needs at least two characters")
    expect(itemsProblem("Same one\nsame ONE")).toBe("Two items are the same")
    expect(templateProblem({ name: "A", scopeType: "SITE", items: "ok item" })).toBe("Give the checklist a name")
    expect(templateProblem({ name: "Site check", scopeType: "SITE", items: "ok item" })).toBeNull()
  })
})
describe("setting up an audit", () => {
  const f = { ...emptyAudit("2020-03-01"), templateId: "t1", title: "Q1 site audit" }
  it("needs a target unless general", () => {
    expect(auditProblem(f, "GENERAL")).toBeNull()
    expect(auditProblem({ ...f, scopeType: "SITE" }, "SITE")).toBe("Choose the site being audited")
    expect(auditProblem({ ...f, scopeType: "COHORT", targetId: "c1" }, "SITE")).toBe("That checklist is for a site audit")
    expect(auditProblem({ ...f, templateId: "" }, null)).toBe("Choose the checklist")
  })
  it("sends only the matching target", () => {
    expect(auditBody({ ...f, scopeType: "COHORT", targetId: "c1" })).toEqual({ templateId: "t1", title: "Q1 site audit", scopeType: "COHORT", plannedOn: "2020-03-01", siteId: null, cohortId: "c1", programmeId: null })
  })
})
describe("answers and completion", () => {
  it("needs the auditor's words unless no issue was seen", () => {
    expect(answerProblem("NO_ISSUE", "")).toBeNull()
    expect(answerProblem("ISSUE", "short")).toBe("Say what you saw, in at least 10 characters")
    expect(answerProblem("COMPLIANT", "whatever it was")).toBe("Choose an outcome")
  })
  it("will not complete with unanswered items or issues without actions", () => {
    const item = (id: string, outcome: string | null) => ({ id, position: 1, question: "q", outcome, note: null, answeredAt: null })
    const action = (itemId: string, status = "OPEN") => ({ id: "a", itemId, description: "d", ownerName: "o", dueOn: "2020-04-01", status, overdue: false, doneOn: null, doneNote: null, verified: false, verifiedAlone: false, verifiedAloneReason: null, cancelledReason: null })
    expect(completeProblem({ items: [item("i1", null)], actions: [] }, "A summary of the audit")).toBe("1 item(s) are not answered yet")
    expect(completeProblem({ items: [item("i1", "ISSUE")], actions: [action("i1", "CANCELLED")] }, "A summary of the audit")).toBe("1 issue(s) have no corrective action yet")
    expect(completeProblem({ items: [item("i1", "ISSUE")], actions: [action("i1")] }, "short")).toContain("at least 10")
    expect(completeProblem({ items: [item("i1", "ISSUE")], actions: [action("i1")] }, "A summary of the audit")).toBeNull()
  })
})
describe("actions", () => {
  it("checks an action", () => {
    expect(actionProblem({ description: "Fix", ownerName: "Thandi", dueOn: "2020-04-01" }, "2020-03-01")).toBe("Say what has to be done")
    expect(actionProblem({ description: "Sign the register", ownerName: "T", dueOn: "2020-04-01" }, "2020-03-01")).toBe("Say who owns the action")
    expect(actionProblem({ description: "Sign the register", ownerName: "Thandi", dueOn: "2020-02-01" }, "2020-03-01")).toBe("The due date cannot be in the past")
    expect(actionProblem({ description: "Sign the register", ownerName: "Thandi", dueOn: "2020-04-01" }, "2020-03-01")).toBeNull()
  })
  it("names an action's state without calling it a finding", () => {
    expect(actionState({ status: "OPEN", overdue: true, verified: false })).toBe("Open, past its due day")
    expect(actionState({ status: "DONE", overdue: false, verified: false })).toBe("Done, not verified")
    expect(actionState({ status: "DONE", overdue: false, verified: true })).toBe("Done and verified")
  })
})
