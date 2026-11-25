import { describe, it, expect } from "vitest"
import {
  accreditedClaim, creditTotal, emptyModule, emptyProgramme, emptyQualification, emptyVersion, moduleBody, moduleProblem, programmeBody, programmeProblem,
  qualificationBody, qualificationProblem, qualificationVersionProblem, emptyQualificationVersion, reasonProblem, typeForActivity, versionBody, versionProblem,
} from "./catalogueRules"

describe("programme checks", () => {
  const ok = { ...emptyProgramme, code: "SP-01", title: "Safety skills", qualificationId: "q1" }
  it("is valid with a code, title and qualification", () => { expect(programmeProblem(ok)).toBeNull() })
  it("an accredited programme needs its qualification", () => {
    expect(programmeProblem({ ...ok, qualificationId: "" })).toContain("qualification")
    expect(programmeProblem({ ...ok, trainingActivity: "NON_ACCREDITED", programmeType: "NON_ACCREDITED_COURSE", qualificationId: "" })).toBeNull()
  })
  it("the type must suit the activity", () => {
    expect(programmeProblem({ ...ok, trainingActivity: "NON_ACCREDITED", programmeType: "QUALIFICATION" })).toContain("cannot be")
  })
  it("changing activity moves the type to a suitable one", () => {
    expect(typeForActivity("NON_ACCREDITED", "SKILLS_PROGRAMME")).toBe("NON_ACCREDITED_COURSE")
    expect(typeForActivity("ACCREDITED", "SKILLS_PROGRAMME")).toBe("SKILLS_PROGRAMME")
  })
  it("short codes and titles are refused", () => {
    expect(programmeProblem({ ...ok, code: "A" })).toContain("code")
    expect(programmeProblem({ ...ok, title: "ab" })).toContain("title")
  })
  it("builds a trimmed body with nulls for blanks", () => {
    expect(programmeBody({ ...ok, code: " SP-01 ", description: " " })).toMatchObject({ code: "SP-01", description: null, courseId: null, qualificationId: "q1" })
  })
})

describe("never overclaims accreditation", () => {
  it("only says covered when a covered offering exists", () => {
    expect(accreditedClaim({ trainingActivity: "ACCREDITED", advertisableAsAccredited: true, status: "ACTIVE" })).toContain("covered at a site")
    expect(accreditedClaim({ trainingActivity: "ACCREDITED", advertisableAsAccredited: false, status: "ACTIVE" })).toContain("not covered anywhere")
    expect(accreditedClaim({ trainingActivity: "NON_ACCREDITED", advertisableAsAccredited: false, status: "ACTIVE" })).toBe("Non-accredited")
  })
})

describe("qualification and version checks", () => {
  it("needs a title and sane numbers", () => {
    expect(qualificationProblem(emptyQualification)).toContain("title")
    expect(qualificationProblem({ ...emptyQualification, title: "Occupational certificate", nqfLevel: "0" })).toContain("1 to 10")
    expect(qualificationProblem({ ...emptyQualification, title: "Occupational certificate", credits: "-1" })).toContain("Credits")
    expect(qualificationProblem({ ...emptyQualification, title: "Occupational certificate", nqfLevel: "4", credits: "120" })).toBeNull()
    expect(qualificationBody({ ...emptyQualification, title: " T1 ", nqfLevel: "" })).toMatchObject({ title: "T1", nqfLevel: null })
  })
  it("registration cannot end before it starts", () => {
    expect(qualificationVersionProblem({ ...emptyQualificationVersion, registrationStart: "2026-02-01", registrationEnd: "2026-01-01" })).toContain("cannot end")
    expect(qualificationVersionProblem(emptyQualificationVersion)).toBeNull()
  })
  it("programme version numbers must be sane", () => {
    expect(versionProblem({ ...emptyVersion, learningHours: "-4" })).toContain("hours")
    expect(versionProblem({ ...emptyVersion, credits: "1.5" })).toContain("Credits")
    expect(versionProblem(emptyVersion)).toBeNull()
    expect(versionBody({ ...emptyVersion, learningHours: "40", credits: "" })).toMatchObject({ learningHours: 40, credits: null })
  })
})

describe("modules", () => {
  it("need a title and sane numbers", () => {
    expect(moduleProblem(emptyModule)).toContain("title")
    expect(moduleProblem({ ...emptyModule, title: "Module one", notionalHours: "-2" })).toContain("hours")
    expect(moduleProblem({ ...emptyModule, title: "Module one", credits: "5" })).toBeNull()
    expect(moduleBody({ ...emptyModule, title: " M1 ", credits: "5" })).toMatchObject({ title: "M1", credits: 5, required: true, learningOutcomes: null })
  })
  it("totals credits, with required counted separately", () => {
    expect(creditTotal([{ credits: 5, required: true }, { credits: 3, required: false }, { credits: null, required: true }])).toEqual({ all: 8, required: 5 })
  })
  it("reasons need ten characters", () => {
    expect(reasonProblem("short")).toContain("at least 10")
    expect(reasonProblem("Replaced by the new version")).toBeNull()
  })
})
