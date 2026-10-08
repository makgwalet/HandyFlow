import { describe, it, expect } from "vitest"
import { readdirSync, readFileSync } from "node:fs"
import { join } from "node:path"

// Every pop-up in the clinic screens goes through ModalShell (or the in-app dialogs), so Escape, focus, scroll lock,
// the pinned footer and the dialog role behave the same everywhere. A new hand-built backdrop fails this test.
const DIR = __dirname
const ALLOWED = new Set(["ModalShell.tsx", "dialogs.tsx", "PatientFilePage.tsx"])  // PatientFilePage: the session overlay and minimised bar, not modals

describe("clinic pop-ups", () => {
  it("use the shared modal shell, not a hand-built full-screen backdrop", () => {
    const offenders = readdirSync(DIR)
      .filter(f => f.endsWith(".tsx") && !f.endsWith(".test.tsx") && !ALLOWED.has(f))
      .filter(f => /position:\s*"fixed"\s*,\s*inset:\s*0/.test(readFileSync(join(DIR, f), "utf8")))
    expect(offenders).toEqual([])
  })
})
