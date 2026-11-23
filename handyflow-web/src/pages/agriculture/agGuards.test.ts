import { describe, it, expect } from "vitest"
import { readdirSync, readFileSync } from "node:fs"
import { join } from "node:path"

// A screen that saves data must (1) say so when a save fails and (2) not offer the action to someone who cannot manage
// Agriculture. These read the source because a missed screen compiles fine and only shows up as a silent failure.
const DIR = __dirname
const screens = readdirSync(DIR).filter(f => /^Ag.*\.tsx$/.test(f)).map(f => ({ f, src: readFileSync(join(DIR, f), "utf8") }))
// the livestock and farm screens use useAgToastMutation; the crop, cost, labour and sales screens have their own handling
const livestockAndFarm = screens.filter(s => /useAgToastMutation\(/.test(s.src))

describe("agriculture screens", () => {
  it("finds the screens this guard is meant to cover", () => {
    expect(livestockAndFarm.length).toBeGreaterThanOrEqual(15)
  })
  it("no screen calls useMutation directly without its own onError", () => {
    for (const s of screens) {
      const calls = s.src.match(/useMutation\(/g)?.length ?? 0
      const handlers = s.src.match(/onError/g)?.length ?? 0
      expect({ file: s.f, ok: calls === 0 || handlers >= calls }).toEqual({ file: s.f, ok: true })
    }
  })
  it("every livestock/farm screen that saves is permission-gated", () => {
    for (const s of livestockAndFarm) {
      const gated = /<AgManage>/.test(s.src) || /canManage/.test(s.src)
      expect({ file: s.f, gated }).toEqual({ file: s.f, gated: true })
    }
  })
  it("no screen still shows an Add/Register/Record button to everyone", () => {
    for (const s of livestockAndFarm) {
      const ungated = s.src.split("\n").filter(l => /\{!showCreate && <button/.test(l))
      expect({ file: s.f, ungated }).toEqual({ file: s.f, ungated: [] })
    }
  })
})
