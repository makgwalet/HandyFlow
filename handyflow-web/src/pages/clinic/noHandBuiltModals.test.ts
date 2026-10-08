import { describe, it, expect } from "vitest"

// Every pop-up in the clinic screens goes through ModalShell (or the in-app dialogs), so Escape, focus, scroll lock,
// the pinned footer and the dialog role behave the same everywhere. A new hand-built backdrop fails this test.
const sources = import.meta.glob("./*.tsx", { query: "?raw", import: "default", eager: true }) as Record<string, string>
const ALLOWED = ["ModalShell.tsx", "dialogs.tsx", "PatientFilePage.tsx"]  // PatientFilePage: the session overlay and minimised bar, not modals

describe("clinic pop-ups", () => {
  it("use the shared modal shell, not a hand-built full-screen backdrop", () => {
    const files = Object.keys(sources).filter(f => !f.endsWith(".test.tsx") && !ALLOWED.some(a => f.endsWith("/" + a)))
    expect(files.length).toBeGreaterThan(20)   // the glob really found the screens
    const offenders = files.filter(f => /position:\s*"fixed"\s*,\s*inset:\s*0/.test(sources[f]))
    expect(offenders).toEqual([])
  })
})
