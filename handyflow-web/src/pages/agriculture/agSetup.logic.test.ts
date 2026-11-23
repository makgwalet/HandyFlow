import { describe, it, expect } from "vitest"
import { dismissKey, nextStep, setupProgress, setupSteps } from "./agSetup.logic"

const none = { areas: 0, species: 0, animals: 0, groups: 0, stock: 0 }

describe("setupSteps", () => {
  it("starts with everything to do, species first", () => {
    const s = setupSteps(none)
    expect(s.map(x => x.key)).toEqual(["species", "areas", "livestock", "stock"])
    expect(s.every(x => !x.done)).toBe(true)
    expect(nextStep(s)?.key).toBe("species")
  })
  it("ticks a step when what it asks for exists", () => {
    const s = setupSteps({ areas: 2, species: 3, animals: 0, groups: 0, stock: 0 })
    expect(s.filter(x => x.done).map(x => x.key)).toEqual(["species", "areas"])
    expect(nextStep(s)?.key).toBe("livestock")
  })
  it("counts a group as livestock as well as an animal", () => {
    expect(setupSteps({ ...none, groups: 1 }).find(x => x.key === "livestock")?.done).toBe(true)
    expect(setupSteps({ ...none, animals: 1 }).find(x => x.key === "livestock")?.done).toBe(true)
  })
  it("has nothing next when everything is done", () => {
    const s = setupSteps({ areas: 1, species: 1, animals: 1, groups: 0, stock: 1 })
    expect(nextStep(s)).toBeNull()
    expect(setupProgress(s)).toBe("4 of 4 done")
  })
  it("sends each farm step to its own tab, and species to none", () => {
    const s = setupSteps(none)
    expect(s.map(x => x.tab)).toEqual([null, "areas", "animals", "inventory"])
    expect(s.map(x => x.action === null)).toEqual([true, false, false, false])
  })
  it("reports progress", () => {
    expect(setupProgress(setupSteps(none))).toBe("0 of 4 done")
    expect(setupProgress(setupSteps({ ...none, stock: 5 }))).toBe("1 of 4 done")
  })
  it("remembers dismissal per farm", () => {
    expect(dismissKey("a")).not.toBe(dismissKey("b"))
  })
})
