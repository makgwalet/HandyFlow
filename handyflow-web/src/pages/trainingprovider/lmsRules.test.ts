import { describe, it, expect } from "vitest"
import { emptyLink, endProblem, linkBody, linkProblem } from "./lmsRules"

const ok = { ...emptyLink, programmeVersionId: "v1", systemName: "Moodle", launchUrl: "https://lms.example.org/course/7" }

describe("lms link rules", () => {
  it("accepts a plain https address", () => {
    expect(linkProblem(ok, true)).toBeNull()
    expect(linkBody({ ...ok, notes: " " })).toMatchObject({ notes: null, systemName: "Moodle" })
  })
  it("needs a version only for a new link", () => {
    expect(linkProblem({ ...ok, programmeVersionId: "" }, true)).toMatch(/programme and version/)
    expect(linkProblem({ ...ok, programmeVersionId: "" }, false)).toBeNull()
  })
  it("refuses http, embedded credentials and secrets in the address", () => {
    expect(linkProblem({ ...ok, launchUrl: "http://lms.example.org" }, true)).toMatch(/https/)
    expect(linkProblem({ ...ok, launchUrl: "https://u:p@lms.example.org" }, true)).toMatch(/https/)
    expect(linkProblem({ ...ok, launchUrl: "https://lms.example.org/c?token=abc" }, true)).toMatch(/token/)
    expect(linkProblem({ ...ok, launchUrl: "https://lms.example.org/c?id=1&Password=x" }, true)).toMatch(/token/)
  })
  it("needs a name and a written end reason", () => {
    expect(linkProblem({ ...ok, systemName: " " }, true)).toMatch(/name/)
    expect(endProblem("short")).not.toBeNull()
    expect(endProblem("Contract with the system ended")).toBeNull()
  })
})
