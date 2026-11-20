import { describe, it, expect } from "vitest"
import { startForm, formProblem, sameAsCurrent, changes, percentOk, writtenLabel, type CharterForm } from "./charterLogic"

const ok = (o: Partial<CharterForm> = {}): CharterForm => ({ ...startForm(null, "2026-11-20"), adoptedOn: "2026-10-01", reason: "Board resolved to adopt the new charter", ...o })

describe("startForm", () => {
  it("uses the house defaults when no charter is recorded", () => {
    const f = startForm(null, "2026-11-20")
    expect(f).toMatchObject({ castingVote: false, writtenMode: "EITHER", quorumAfterRecusal: true, percent: "75", effectiveFrom: "2026-11-20" })
  })
  it("starts from the rules in force", () => {
    expect(startForm({ castingVote: true, writtenMode: "UNANIMOUS", quorumAfterRecusal: false, specialPercent: 80 }, "2026-11-20")).toMatchObject({ castingVote: true, writtenMode: "UNANIMOUS", quorumAfterRecusal: false, percent: "80" })
  })
})
describe("formProblem", () => {
  it("accepts a complete form", () => expect(formProblem(ok())).toBeNull())
  it("needs both dates in order", () => {
    expect(formProblem(ok({ adoptedOn: "" }))).toMatch(/adopted/)
    expect(formProblem(ok({ effectiveFrom: "" }))).toMatch(/take effect/)
    expect(formProblem(ok({ adoptedOn: "2026-12-01", effectiveFrom: "2026-11-20" }))).toMatch(/before the charter was adopted/)
    expect(formProblem(ok({ adoptedOn: "2026-11-20", effectiveFrom: "2026-11-20" }))).toBeNull()
  })
  it("keeps the percentage between 51 and 100 as a whole number", () => {
    for (const p of ["50", "101", "", "7x", "75.5", "-60"]) expect(formProblem(ok({ percent: p }))).toMatch(/51 to 100/)
    for (const p of ["51", "100", " 75 "]) expect(formProblem(ok({ percent: p }))).toBeNull()
  })
  it("needs a reason of ten characters and a reference of at most 120", () => {
    expect(formProblem(ok({ reason: "too short" }))).toMatch(/10 characters/)
    expect(formProblem(ok({ reason: "         padded    " }))).toMatch(/10 characters/)
    expect(formProblem(ok({ reference: "x".repeat(121) }))).toMatch(/too long/)
    expect(formProblem(ok({ reference: "x".repeat(120) }))).toBeNull()
  })
})
describe("sameAsCurrent", () => {
  const cur = { castingVote: true, writtenMode: "EITHER" as const, quorumAfterRecusal: true, specialPercent: 75 }
  it("spots a form that changes nothing", () => expect(sameAsCurrent(ok({ castingVote: true }), cur)).toBe(true))
  it("spots each kind of change", () => {
    expect(sameAsCurrent(ok({ castingVote: false }), cur)).toBe(false)
    expect(sameAsCurrent(ok({ castingVote: true, writtenMode: "UNANIMOUS" }), cur)).toBe(false)
    expect(sameAsCurrent(ok({ castingVote: true, quorumAfterRecusal: false }), cur)).toBe(false)
    expect(sameAsCurrent(ok({ castingVote: true, percent: "80" }), cur)).toBe(false)
  })
  it("is false when nothing is recorded", () => expect(sameAsCurrent(ok(), null)).toBe(false))
})
describe("changes", () => {
  it("lists only what differs", () => {
    const from = { castingVote: false, writtenMode: "EITHER" as const, quorumAfterRecusal: true, specialPercent: 75 }
    expect(changes(from, { ...from })).toEqual([])
    expect(changes(from, { castingVote: true, writtenMode: "NOT_ALLOWED", quorumAfterRecusal: false, specialPercent: 66 })).toEqual([
      "Casting vote: no → yes", "Written resolutions: either way → not allowed", "Quorum after recusal: yes → no", "Special resolutions: 75% → 66%"])
  })
  it("compares a first version with the house rules", () => {
    expect(changes(null, { castingVote: false, writtenMode: "EITHER", quorumAfterRecusal: true, specialPercent: 75 })).toEqual([])
    expect(changes(null, { castingVote: true, writtenMode: "EITHER", quorumAfterRecusal: true, specialPercent: 75 })).toEqual(["Casting vote: no → yes"])
  })
})
describe("labels", () => {
  it("names each written mode", () => { expect(writtenLabel("UNANIMOUS")).toMatch(/every member/); expect(percentOk("75")).toBe(true) })
})
