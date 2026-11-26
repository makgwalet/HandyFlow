import { describe, it, expect } from "vitest"
import {
  actionProblem, closeProblem, describeVersion, drawProblem, emptyClose, emptyDraw, emptyRule, emptySettings, ruleBody, ruleProblem, settingsBody, settingsProblem,
  type Draw, type Version,
} from "./samplingRules"

const ok = () => ({ ...emptySettings("2026-01-05"), percentage: "10", rationale: "Agreed at the quality meeting" })

describe("settingsProblem", () => {
  it("accepts a percentage rule", () => expect(settingsProblem(ok(), null)).toBeNull())
  it("needs a percentage between 0 and 100", () => {
    expect(settingsProblem({ ...ok(), percentage: "0" }, null)).toBe("The percentage must be above 0 and no more than 100")
    expect(settingsProblem({ ...ok(), percentage: "101" }, null)).toBe("The percentage must be above 0 and no more than 100")
  })
  it("does not need a percentage when every result is moderated", () => expect(settingsProblem({ ...ok(), basis: "ALL", percentage: "" }, null)).toBeNull())
  it("checks min and max", () => {
    expect(settingsProblem({ ...ok(), minCount: "6", maxCount: "5" }, null)).toBe("The maximum cannot be below the minimum")
    expect(settingsProblem({ ...ok(), minCount: "-1" }, null)).toBe("The minimum must be a whole number, 0 or more")
  })
  it("needs risk factors for risk-based selection", () => expect(settingsProblem({ ...ok(), selectionMethod: "RISK_BASED" }, null)).toBe("Say which risk factors you look at"))
  it("needs the whole extra-sampling set", () => {
    expect(settingsProblem({ ...ok(), extra: true, extraTriggerQueries: "2" }, null)).toBe("Extra sampling needs the trigger, the look-back and the extra percentage")
    expect(settingsProblem({ ...ok(), extra: true, extraTriggerQueries: "3", extraLookback: "2", extraPercentage: "10" }, null)).toBe("The look-back must be at least the trigger, and the trigger at least 1")
    expect(settingsProblem({ ...ok(), extra: true, extraTriggerQueries: "2", extraLookback: "5", extraPercentage: "10" }, null)).toBeNull()
  })
  it("needs a reason and a later start than the latest version", () => {
    expect(settingsProblem({ ...ok(), rationale: "short" }, null)).toBe("Say why the rule is set this way (at least 10 characters)")
    expect(settingsProblem(ok(), "2026-01-05")).toBe("A new version must start after the latest one (2026-01-05)")
  })
  it("leaves out settings that do not apply when sent", () => {
    const b = settingsBody({ ...ok(), basis: "ALL", extra: false })
    expect(b.percentage).toBeNull(); expect(b.extraPercentage).toBeNull(); expect(b.riskFactors).toBeNull()
  })
})

describe("ruleProblem", () => {
  it("needs a name and what the scope needs", () => {
    expect(ruleProblem({ ...emptyRule("2026-01-05"), settings: ok() })).toBe("Give the rule a name")
    expect(ruleProblem({ ...emptyRule("2026-01-05"), name: "All", scopeType: "PROGRAMME", settings: ok() })).toBe("Choose a programme")
    expect(ruleProblem({ ...emptyRule("2026-01-05"), name: "All", settings: ok() })).toBeNull()
  })
  it("sends only the id that matches the scope", () => {
    const b = ruleBody({ ...emptyRule("2026-01-05"), name: "Prog", scopeType: "PROGRAMME", programmeId: "p1", cohortId: "c1", settings: ok() })
    expect(b.programmeId).toBe("p1"); expect(b.cohortId).toBeNull()
  })
})

describe("draw and close", () => {
  it("checks the window", () => {
    expect(drawProblem(emptyDraw("2026-01-05"), "2026-01-05")).toBe("Choose a rule")
    expect(drawProblem({ ...emptyDraw("2026-01-05"), ruleId: "r", decidedFrom: "2026-01-06", decidedTo: "2026-01-05" }, "2026-01-05")).toBe("The window must start on or before its end")
    expect(drawProblem({ ...emptyDraw("2026-01-05"), ruleId: "r", decidedTo: "2026-02-01" }, "2026-01-05")).toBe("The window cannot end in the future")
    expect(drawProblem({ ...emptyDraw("2026-01-05"), ruleId: "r", targeted: { a: "short" } }, "2026-01-05")).toContain("reason of at least 10")
    expect(drawProblem({ ...emptyDraw("2026-01-05"), ruleId: "r" }, "2026-01-05")).toBeNull()
  })
  const draw = (over: Partial<Draw> = {}): Pick<Draw, "selectedCount" | "moderatedCount" | "drawnBy" | "items" | "actions"> => ({ selectedCount: 2, moderatedCount: 2, drawnBy: "u1", items: [], actions: [], ...over })
  it("cannot close until every pick is moderated", () => expect(closeProblem({ ...emptyClose(), note: "All fine here" }, draw({ moderatedCount: 1 }), "u2")).toBe("1 picked result(s) have no moderation yet"))
  it("cannot accept with an open query", () => {
    const items = [{ selected: true, moderationOutcome: "QUERIED", moderationResolved: false }] as Draw["items"]
    expect(closeProblem({ ...emptyClose(), note: "All fine here" }, draw({ items }), "u2")).toBe("A moderation query is not answered yet")
  })
  it("needs an action before further action is chosen", () => expect(closeProblem({ ...emptyClose(), decision: "FURTHER_ACTION_REQUIRED", note: "Needs work on marking" }, draw(), "u2")).toBe("Record at least one corrective action first"))
  it("asks for a second person unless confirmed", () => {
    expect(closeProblem({ ...emptyClose(), note: "All fine here" }, draw(), "u1")).toBe("A second person should close this draw, or confirm that you are closing it yourself")
    expect(closeProblem({ ...emptyClose(), note: "All fine here", confirmAlone: true }, draw(), "u1")).toBeNull()
    expect(closeProblem({ ...emptyClose(), note: "All fine here" }, draw(), "u2")).toBeNull()
  })
})

describe("actions and wording", () => {
  it("needs a description and an owner", () => {
    expect(actionProblem({ itemId: "", description: "short", ownerName: "Sam", dueOn: "" })).toContain("at least 10")
    expect(actionProblem({ itemId: "", description: "Re-mark the practical scripts", ownerName: "", dueOn: "" })).toBe("Say who owns it")
  })
  it("describes a version in plain words without calling it a requirement", () => {
    const v = { basis: "PERCENTAGE", percentage: 10, minCount: 2, maxCount: 8, selectionMethod: "RANDOM", riskFactors: null, extraTriggerQueries: 2, extraLookback: 5, extraPercentage: 20 } as Version
    expect(describeVersion(v)).toBe("10% of eligible results, at least 2, at most 8, random, extra 20% when 2 of the last 5 moderations raised a query")
  })
})
