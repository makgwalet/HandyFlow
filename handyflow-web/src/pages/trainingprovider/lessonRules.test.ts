import { describe, it, expect } from "vitest"
import { approvalNote, emptyLesson, finishProblem, lessonBody, lessonProblem, reasonProblem, updateBody } from "./lessonRules"

const ok = { ...emptyLesson, programmeVersionId: "v1", title: "Intro", contentText: "A reading that is long enough to count" }

describe("lesson rules", () => {
  it("checks a reading", () => {
    expect(lessonProblem(ok, true)).toBeNull()
    expect(lessonProblem({ ...ok, programmeVersionId: "" }, true)).toMatch(/programme and version/)
    expect(lessonProblem({ ...ok, programmeVersionId: "" }, false)).toBeNull()
    expect(lessonProblem({ ...ok, title: "a" }, true)).toMatch(/title/)
    expect(lessonProblem({ ...ok, contentText: "short" }, true)).toMatch(/20 characters/)
    expect(lessonProblem({ ...ok, position: "0" }, true)).toMatch(/position/)
  })
  it("checks a link and sends only what the kind uses", () => {
    const link = { ...ok, kind: "LINK", url: "https://example.org/v" }
    expect(lessonProblem({ ...link, url: "ftp://x" }, true)).toMatch(/http/)
    expect(lessonProblem(link, true)).toBeNull()
    expect(lessonBody(link)).toMatchObject({ kind: "LINK", url: "https://example.org/v", contentText: null })
    expect(updateBody(ok)).toMatchObject({ kind: "READING", url: null, position: 1 })
  })
  it("needs a written reason of ten characters", () => {
    expect(reasonProblem("short")).not.toBeNull()
    expect(reasonProblem("Replaced by a newer reading")).toBeNull()
  })
  it("describes approval", () => {
    expect(approvalNote({ status: "DRAFT", approvedAt: null, approvalOverrideReason: null })).toMatch(/second person/)
    expect(approvalNote({ status: "APPROVED", approvedAt: "2027-01-02T10:00:00Z", approvalOverrideReason: "Only author" })).toMatch(/by its author/)
  })
  it("checks the finish day", () => {
    expect(finishProblem({ finishedOn: "2027-06-10", note: "" }, "2027-06-01", "2027-06-10")).toBeNull()
    expect(finishProblem({ finishedOn: "2027-06-11", note: "" }, "2027-06-01", "2027-06-10")).toMatch(/future/)
    expect(finishProblem({ finishedOn: "2027-05-31", note: "" }, "2027-06-01", "2027-06-10")).toMatch(/before/)
    expect(finishProblem({ finishedOn: "", note: "" }, null, "2027-06-10")).toMatch(/Enter/)
  })
})
