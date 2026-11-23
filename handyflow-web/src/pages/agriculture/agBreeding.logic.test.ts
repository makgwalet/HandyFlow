import { describe, it, expect } from "vitest"
import { breedingActions, birthFormError, birthPayload, breedingTypeLabel, outcomeLabel } from "./agBreeding.logic"
import { apiErrorMessage } from "./agErrors"

describe("breedingActions", () => {
  it("offers every action on a new record (outcome PENDING, as the server sends it)", () => {
    expect(breedingActions("PENDING")).toEqual({ confirm: true, notPregnant: true, birth: true, aborted: true, failed: true })
  })
  it("treats a missing outcome as PENDING", () => {
    expect(breedingActions(null).confirm).toBe(true)
  })
  it("after confirmation, offers birth, aborted and failed only", () => {
    expect(breedingActions("CONFIRMED_PREGNANT")).toEqual({ confirm: false, notPregnant: false, birth: true, aborted: true, failed: true })
  })
  it("offers nothing on a final record", () => {
    for (const o of ["NOT_PREGNANT", "BIRTHED", "STILLBORN", "ABORTED", "FAILED"])
      expect(breedingActions(o)).toEqual({ confirm: false, notPregnant: false, birth: false, aborted: false, failed: false })
  })
})

describe("birthFormError", () => {
  const ok = { date: "2026-11-01", live: "2", stillborn: "" }
  it("accepts a valid form, with an empty stillborn count as zero", () => {
    expect(birthFormError(ok, "2026-03-01", "2026-11-23")).toBeNull()
    expect(birthPayload(ok)).toEqual({ actualBirthDate: "2026-11-01", offspringCount: 2, stillbornCount: 0 })
  })
  it("accepts an all-stillborn birth", () => {
    expect(birthFormError({ date: "2026-11-01", live: "0", stillborn: "1" }, "2026-03-01", "2026-11-23")).toBeNull()
    expect(birthPayload({ date: "2026-11-01", live: "", stillborn: "1" })).toMatchObject({ offspringCount: 0, stillbornCount: 1 })
  })
  it("refuses nothing born", () => {
    expect(birthFormError({ ...ok, live: "0", stillborn: "0" }, "2026-03-01", "2026-11-23")).toMatch(/born alive or stillborn/)
    expect(birthFormError({ ...ok, live: "", stillborn: "" }, "2026-03-01", "2026-11-23")).toMatch(/born alive or stillborn/)
  })
  it("refuses bad dates", () => {
    expect(birthFormError({ ...ok, date: "" }, "2026-03-01", "2026-11-23")).toMatch(/birth date/)
    expect(birthFormError({ ...ok, date: "2026-12-01" }, "2026-03-01", "2026-11-23")).toMatch(/future/)
    expect(birthFormError({ ...ok, date: "2026-02-28" }, "2026-03-01", "2026-11-23")).toMatch(/before the mating/)
  })
  it("refuses negative, fractional and absurd counts", () => {
    expect(birthFormError({ ...ok, live: "-1" }, "2026-03-01", "2026-11-23")).toMatch(/whole numbers/)
    expect(birthFormError({ ...ok, live: "1.5" }, "2026-03-01", "2026-11-23")).toMatch(/whole numbers/)
    expect(birthFormError({ ...ok, live: "101" }, "2026-03-01", "2026-11-23")).toMatch(/credible/)
  })
})

describe("labels", () => {
  it("shows plain names, including the long legacy spelling", () => {
    expect(breedingTypeLabel("AI")).toBe("Artificial insemination")
    expect(breedingTypeLabel("ARTIFICIAL_INSEMINATION")).toBe("Artificial insemination")
    expect(outcomeLabel("BIRTHED")).toBe("Born")
    expect(outcomeLabel("STILLBORN")).toBe("Stillborn")
    expect(outcomeLabel(null)).toBe("Awaiting confirmation")
  })
})

describe("apiErrorMessage", () => {
  it("uses the server's message", () => {
    expect(apiErrorMessage({ response: { status: 409, data: { message: "A breeding record that is failed cannot be changed to born" } } })).toMatch(/failed cannot/)
  })
  it("says permission for a 403 whatever the body says", () => {
    expect(apiErrorMessage({ response: { status: 403, data: { message: "Forbidden" } } })).toMatch(/permission/)
  })
  it("says the server could not be reached when there is no response", () => {
    expect(apiErrorMessage(new Error("Network Error"))).toMatch(/reach the server/)
  })
  it("falls back when the body has no message", () => {
    expect(apiErrorMessage({ response: { status: 400, data: {} } }, "Could not save")).toBe("Could not save")
  })
})
